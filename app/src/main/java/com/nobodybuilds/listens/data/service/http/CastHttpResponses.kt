package com.nobodybuilds.listens.data.service.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import timber.log.Timber
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.nio.channels.ClosedChannelException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Request checks and response helpers shared by the Cast HTTP routes.

internal const val CAST_HTTP_LOG_TAG = "CastHttpServer"

private val httpDateFormatter: DateTimeFormatter =
    DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

internal suspend fun ApplicationCall.ensureLoopbackHealthRequest(): Boolean {
    val remoteAddress = request.origin.remoteHost
    if (CastSessionSecurity.isLoopbackAddress(remoteAddress)) {
        return true
    }
    Timber.tag(CAST_HTTP_LOG_TAG).w("Rejected Cast health request from non-loopback client=%s", remoteAddress)
    respond(HttpStatusCode.Forbidden, "Forbidden")
    return false
}

internal suspend fun ApplicationCall.ensureAuthorizedCastMediaRequest(songId: String): Boolean {
    val remoteAddress = request.origin.remoteHost
    val policy = MediaFileHttpServerService.currentCastAccessPolicy()

    if (!CastSessionSecurity.isAuthorizedClientAddress(remoteAddress, policy)) {
        Timber.tag(CAST_HTTP_LOG_TAG).w(
            "Rejected Cast media request from unauthorized client=%s songId=%s",
            remoteAddress,
            songId
        )
        respond(HttpStatusCode.Forbidden, "Forbidden")
        return false
    }

    val providedToken = request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]
    if (!CastSessionSecurity.isAuthorizedSongRequest(providedToken, songId, policy)) {
        val hasValidToken = !policy.authToken.isNullOrBlank() && providedToken == policy.authToken
        if (!hasValidToken) {
            Timber.tag(CAST_HTTP_LOG_TAG).w(
                "Rejected Cast media request with invalid token client=%s songId=%s",
                remoteAddress,
                songId
            )
            respond(HttpStatusCode.Unauthorized, "Unauthorized")
        } else {
            Timber.tag(CAST_HTTP_LOG_TAG).w(
                "Rejected Cast media request for non-whitelisted song client=%s songId=%s",
                remoteAddress,
                songId
            )
            respond(HttpStatusCode.NotFound, "Song not found")
        }
        return false
    }

    return true
}

internal fun formatHttpDate(epochMs: Long): String {
    return httpDateFormatter.format(Instant.ofEpochMilli(epochMs))
}

internal suspend fun ApplicationCall.respondWithAudioStream(
    contentType: ContentType,
    fileSize: Long,
    rangeHeader: String?,
    inputStreamFactory: () -> InputStream
) {
    if (rangeHeader != null && fileSize > 0) {
        val rangesSpecifier = io.ktor.http.parseRangesSpecifier(rangeHeader)
        val ranges = rangesSpecifier?.ranges

        if (ranges.isNullOrEmpty()) {
            Timber.tag(CAST_HTTP_LOG_TAG).w("Invalid range header: %s", rangeHeader)
            Timber.tag("PX_CAST_HTTP").w("Invalid range header: $rangeHeader")
            respond(HttpStatusCode.BadRequest, "Invalid range")
            return
        }

        val range = ranges.first()
        val start = when (range) {
            is io.ktor.http.ContentRange.Bounded -> range.from
            is io.ktor.http.ContentRange.TailFrom -> range.from
            is io.ktor.http.ContentRange.Suffix -> fileSize - range.lastCount
            else -> 0L
        }
        val end = when (range) {
            is io.ktor.http.ContentRange.Bounded -> range.to
            is io.ktor.http.ContentRange.TailFrom -> fileSize - 1
            is io.ktor.http.ContentRange.Suffix -> fileSize - 1
            else -> fileSize - 1
        }

        val clampedStart = start.coerceAtLeast(0L)
        val clampedEnd = end.coerceAtMost(fileSize - 1)
        val length = clampedEnd - clampedStart + 1

        if (length <= 0) {
            Timber.tag(CAST_HTTP_LOG_TAG).w(
                "Unsatisfiable range. header=%s start=%d end=%d size=%d",
                rangeHeader,
                clampedStart,
                clampedEnd,
                fileSize
            )
            Timber.tag("PX_CAST_HTTP")
                .w("Unsatisfiable range header=$rangeHeader start=$clampedStart end=$clampedEnd size=$fileSize")
            respond(HttpStatusCode.RequestedRangeNotSatisfiable, "Range not satisfiable")
            return
        }

        response.header(HttpHeaders.ContentRange, "bytes $clampedStart-$clampedEnd/$fileSize")
        response.header(HttpHeaders.AcceptRanges, "bytes")
        response.header(HttpHeaders.ContentLength, length.toString())

        respondOutputStream(contentType, HttpStatusCode.PartialContent) {
            inputStreamFactory().use { inputStream ->
                if (!skipFully(inputStream, clampedStart)) {
                    return@use
                }
                copyLimited(inputStream, this, length)
            }
        }
        return
    }

    if (fileSize > 0) {
        response.header(HttpHeaders.AcceptRanges, "bytes")
        response.header(HttpHeaders.ContentLength, fileSize.toString())
    }

    respondOutputStream(contentType) {
        inputStreamFactory().use { inputStream ->
            inputStream.copyTo(this)
        }
    }
}

private fun skipFully(inputStream: InputStream, bytesToSkip: Long): Boolean {
    var remaining = bytesToSkip
    while (remaining > 0) {
        val skipped = inputStream.skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        if (inputStream.read() == -1) {
            return false
        }
        remaining--
    }
    return true
}

private fun copyLimited(inputStream: InputStream, outputStream: OutputStream, length: Long) {
    var remaining = length
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (remaining > 0) {
        val read = inputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (read == -1) break
        outputStream.write(buffer, 0, read)
        remaining -= read.toLong()
    }
}

internal fun Throwable.isClientAbortDuringResponse(): Boolean {
    return generateSequence(this) { it.cause }.any { cause ->
        cause is ClosedChannelException ||
            cause is EOFException ||
            (cause is SocketException && (
                cause.message?.contains("Connection reset", ignoreCase = true) == true ||
                    cause.message?.contains("Broken pipe", ignoreCase = true) == true ||
                    cause.message?.contains("Socket closed", ignoreCase = true) == true
                )) ||
            // Ktor CIO throws plain IOException (not SocketException) for broken pipe on NIO channels
            (cause is IOException && cause.message?.contains("Broken pipe", ignoreCase = true) == true) ||
            cause.javaClass.name.contains("ChannelWriteException")
    }
}
