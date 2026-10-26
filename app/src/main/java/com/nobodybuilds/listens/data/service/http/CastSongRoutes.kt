package com.nobodybuilds.listens.data.service.http

import androidx.core.net.toUri
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import timber.log.Timber

/** GET and HEAD /song/{songId}: serves a song's audio, transcoding what Cast cannot play. */
internal class CastSongRoutes(
    private val mediaSources: CastMediaSourceResolver,
    private val mimeResolver: CastAudioMimeResolver,
    private val codecSupport: CastCodecSupport,
    private val transcodeCache: CastTranscodeCache
) {
    fun register(route: Route) {
        with(route) {
            get("/song/{songId}") {
                val songId = call.parameters["songId"]
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Song ID is missing")
                    return@get
                }
                if (!call.ensureAuthorizedCastMediaRequest(songId)) return@get

                val song = mediaSources.resolveSongForServing(songId)
                if (song == null) {
                    Timber.tag(CAST_HTTP_LOG_TAG).w(
                        "GET /song unresolved songId=%s (repository+MediaStore fallback miss)",
                        songId
                    )
                    Timber.tag("PX_CAST_HTTP")
                        .e("GET /song unresolved songId=$songId")
                    call.respond(HttpStatusCode.NotFound, "Song not found")
                    return@get
                }

                try {
                    val uri = song.contentUriString.toUri()
                    val codecInfo = codecSupport.detectAudioCodecViaExtractor(song, uri)
                    val isAlac = codecInfo?.codecMime == "audio/alac"
                    // Only transcode if ALAC AND a working decoder is available.
                    // On devices where c2.qti.alac.sw.decoder reports
                    // "Unsupported mediaType audio/alac" in AudioCapabilities,
                    // findDecoderForFormat returns null and we fall back to
                    // serving the raw M4A file as audio/mp4 instead.
                    val canTranscode = isAlac && codecSupport.isAlacTranscodeSupported(checkNotNull(codecInfo))

                    if (canTranscode) {
                        // ALAC codec inside M4A — Cast DMR cannot play ALAC.
                        // Serve via temp-file transcode cache so Cast can seek.
                        if (codecInfo != null) {
                            Timber.tag(CAST_HTTP_LOG_TAG).i(
                                "GET /song ALAC→AAC transcode-cache songId=%s sr=%d ch=%d",
                                song.id, codecInfo.sampleRate, codecInfo.channelCount
                            )
                        }
                        if (codecInfo != null) {
                            Timber.tag("PX_CAST_HTTP")
                                .i("GET /song transcode_alac songId=${song.id} sr=${codecInfo.sampleRate} ch=${codecInfo.channelCount}")
                        }
                        if (codecInfo != null) {
                            transcodeCache.respondTranscoded(call,
                                song = song, codecInfo = codecInfo, uri = uri,
                                rangeHeader = call.request.headers[HttpHeaders.Range]
                            )
                        }
                        return@get
                    }

                    if (isAlac && !canTranscode) {
                        // ALAC decoder unavailable on this device — serve the raw
                        // M4A container. Newer Cast devices support ALAC natively.
                        if (codecInfo != null) {
                            Timber.tag(CAST_HTTP_LOG_TAG).w(
                                "GET /song ALAC decoder unavailable songId=%s sr=%d, serving raw M4A",
                                song.id, codecInfo.sampleRate
                            )
                        }
                        if (codecInfo != null) {
                            Timber.tag("PX_CAST_HTTP")
                                .w("GET /song alac_fallback_raw songId=${song.id} sr=${codecInfo.sampleRate}")
                        }
                        val rangeHeader = call.request.headers[HttpHeaders.Range]
                        val source = mediaSources.resolveAudioStreamSource(song, uri)
                        if (source == null) {
                            call.respond(HttpStatusCode.NotFound, "File not found")
                            return@get
                        }
                        source.lastModifiedEpochMs?.let { lastModified ->
                            call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                        }
                        call.respondWithAudioStream(
                            contentType = ContentType.parse("audio/mp4"),
                            fileSize = source.fileSize,
                            rangeHeader = rangeHeader
                        ) { source.inputStreamFactory() }
                        return@get
                    }

                    // FLAC: Cast DMR supports FLAC natively but its duration
                    // estimate is wrong (VBR + missing seektable → reported duration
                    // differs from actual), so seeking overshoots and lands near EOF
                    // causing an involuntary track skip. Transcode to AAC-ADTS so
                    // Cast gets a CBR stream it can seek accurately.
                    val isFlac = codecInfo?.codecMime == "audio/flac"
                    if (isFlac && codecInfo != null && codecSupport.isFlacTranscodeSupported(codecInfo)) {
                        Timber.tag(CAST_HTTP_LOG_TAG).i(
                            "GET /song FLAC→AAC transcode-cache songId=%s sr=%d ch=%d",
                            song.id, codecInfo.sampleRate, codecInfo.channelCount
                        )
                        Timber.tag("PX_CAST_HTTP")
                            .i("GET /song transcode_flac songId=${song.id} sr=${codecInfo.sampleRate} ch=${codecInfo.channelCount}")
                        transcodeCache.respondTranscoded(call,
                            song = song, codecInfo = codecInfo, uri = uri,
                            rangeHeader = call.request.headers[HttpHeaders.Range]
                        )
                        return@get
                    }

                    // AC3/EAC3: Cast DMR cannot play Dolby audio. Transcode to AAC
                    // when a decoder is available (Snapdragon Dolby decoder).
                    val isAc3 = codecInfo?.codecMime == "audio/ac3" || codecInfo?.codecMime == "audio/eac3"
                    if (isAc3 && codecInfo != null && codecSupport.isAc3TranscodeSupported(codecInfo)) {
                        Timber.tag(CAST_HTTP_LOG_TAG).i(
                            "GET /song AC3/EAC3→AAC transcode-cache songId=%s sr=%d ch=%d",
                            song.id, codecInfo.sampleRate, codecInfo.channelCount
                        )
                        Timber.tag("PX_CAST_HTTP")
                            .i("GET /song transcode_ac3 songId=${song.id} sr=${codecInfo.sampleRate} ch=${codecInfo.channelCount}")
                        transcodeCache.respondTranscoded(call,
                            song = song, codecInfo = codecInfo, uri = uri,
                            rangeHeader = call.request.headers[HttpHeaders.Range]
                        )
                        return@get
                    }

                    val contentType = mimeResolver.resolveAudioContentType(mimeResolver.resolvePreferredAudioMimeType(song, uri))
                    val rangeHeader = call.request.headers[HttpHeaders.Range]
                    val source = mediaSources.resolveAudioStreamSource(song, uri)

                    if (source == null) {
                        Timber.tag(CAST_HTTP_LOG_TAG).w(
                            "GET /song failed to resolve source. songId=%s uri=%s path=%s mime=%s",
                            song.id,
                            song.contentUriString,
                            song.path,
                            song.mimeType
                        )
                        call.respond(HttpStatusCode.NotFound, "File not found")
                        return@get
                    }

                    Timber.tag(CAST_HTTP_LOG_TAG).i(
                        "GET /song songId=%s source=%s range=%s size=%d type=%s",
                        song.id,
                        source.sourceLabel,
                        rangeHeader,
                        source.fileSize,
                        contentType
                    )
                    Timber.tag("PX_CAST_HTTP")
                        .i("GET /song songId=${song.id} source=${source.sourceLabel} range=$rangeHeader size=${source.fileSize} type=$contentType")
                    source.lastModifiedEpochMs?.let { lastModified ->
                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                    }

                    call.respondWithAudioStream(
                        contentType = contentType,
                        fileSize = source.fileSize,
                        rangeHeader = rangeHeader
                    ) {
                        source.inputStreamFactory()
                    }
                } catch (e: Exception) {
                    if (e.isClientAbortDuringResponse()) {
                        Timber.tag(CAST_HTTP_LOG_TAG).d(
                            "GET /song client disconnected. songId=%s uri=%s",
                            song.id,
                            song.contentUriString
                        )
                        Timber.tag("PX_CAST_HTTP")
                            .w("GET /song client_closed songId=${song.id} uri=${song.contentUriString} error=${e.javaClass.simpleName}")
                        return@get
                    }
                    Timber.tag(CAST_HTTP_LOG_TAG).e(
                        e,
                        "GET /song exception. songId=%s uri=%s",
                        song.id,
                        song.contentUriString
                    )
                    Timber.tag("PX_CAST_HTTP").e(
                        e,
                        "GET /song exception songId=${song.id} uri=${song.contentUriString}"
                    )
                    call.respond(HttpStatusCode.InternalServerError, "Error serving file: ${e.message}")
                }
            }
            head("/song/{songId}") {
                val songId = call.parameters["songId"]
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@head
                }
                if (!call.ensureAuthorizedCastMediaRequest(songId)) return@head

                val song = mediaSources.resolveSongForServing(songId)
                if (song == null) {
                    Timber.tag(CAST_HTTP_LOG_TAG).w(
                        "HEAD /song unresolved songId=%s (repository+MediaStore fallback miss)",
                        songId
                    )
                    Timber.tag("PX_CAST_HTTP")
                        .e("HEAD /song unresolved songId=$songId")
                    call.respond(HttpStatusCode.NotFound)
                    return@head
                }

                try {
                    val uri = song.contentUriString.toUri()
                    val codecInfo = codecSupport.detectAudioCodecViaExtractor(song, uri)
                    val isAlac = codecInfo?.codecMime == "audio/alac"
                    val canTranscode = isAlac && codecSupport.isAlacTranscodeSupported(checkNotNull(codecInfo))

                    if (canTranscode) {
                        // ALAC: serve Content-Length from cache if temp file is ready.
                        call.response.header(HttpHeaders.ContentType, "audio/aac")
                        val entry = transcodeCache[song.id]
                        if (entry != null && entry.done && entry.tempFile.exists()) {
                            val tempSize = entry.tempFile.length()
                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                            call.response.header(HttpHeaders.ContentLength, tempSize.toString())
                            Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /song ALAC songId=%s -> audio/aac size=%d (cached)", song.id, tempSize)
                        } else {
                            Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /song ALAC songId=%s -> audio/aac (no cache yet)", song.id)
                        }
                        call.respond(HttpStatusCode.OK)
                        return@head
                    }

                    if (isAlac && !canTranscode) {
                        // ALAC decoder unavailable — raw M4A fallback
                        call.response.header(HttpHeaders.ContentType, "audio/mp4")
                        Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /song ALAC fallback songId=%s -> audio/mp4 (decoder unavailable)", song.id)
                        call.respond(HttpStatusCode.OK)
                        return@head
                    }

                    // FLAC: transcoded to AAC-ADTS for reliable Cast seeking.
                    val isFlac = codecInfo?.codecMime == "audio/flac"
                    if (isFlac && codecInfo != null && codecSupport.isFlacTranscodeSupported(codecInfo)) {
                        call.response.header(HttpHeaders.ContentType, "audio/aac")
                        val entry = transcodeCache[song.id]
                        if (entry != null && entry.done && entry.tempFile.exists()) {
                            val tempSize = entry.tempFile.length()
                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                            call.response.header(HttpHeaders.ContentLength, tempSize.toString())
                            Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /song FLAC songId=%s -> audio/aac size=%d (cached)", song.id, tempSize)
                        } else {
                            Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /song FLAC songId=%s -> audio/aac (no cache yet)", song.id)
                        }
                        call.respond(HttpStatusCode.OK)
                        return@head
                    }

                    // AC3/EAC3: transcoded to AAC for Cast.
                    val isAc3 = codecInfo?.codecMime == "audio/ac3" || codecInfo?.codecMime == "audio/eac3"
                    if (isAc3 && codecInfo != null && codecSupport.isAc3TranscodeSupported(codecInfo)) {
                        call.response.header(HttpHeaders.ContentType, "audio/aac")
                        val entry = transcodeCache[song.id]
                        if (entry != null && entry.done && entry.tempFile.exists()) {
                            val tempSize = entry.tempFile.length()
                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                            call.response.header(HttpHeaders.ContentLength, tempSize.toString())
                            Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /song AC3 songId=%s -> audio/aac size=%d (cached)", song.id, tempSize)
                        } else {
                            Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /song AC3 songId=%s -> audio/aac (no cache yet)", song.id)
                        }
                        call.respond(HttpStatusCode.OK)
                        return@head
                    }

                    val contentType = mimeResolver.resolveAudioContentType(mimeResolver.resolvePreferredAudioMimeType(song, uri))
                    val source = mediaSources.resolveAudioStreamSource(song, uri)

                    if (source == null) {
                    Timber.tag(CAST_HTTP_LOG_TAG).w(
                        "HEAD /song failed to resolve source. songId=%s uri=%s path=%s",
                        song.id,
                        song.contentUriString,
                        song.path
                    )
                        Timber.tag("PX_CAST_HTTP")
                            .w("HEAD /song no source songId=${song.id} uri=${song.contentUriString}")
                    call.respond(HttpStatusCode.NotFound)
                    return@head
                }

                    call.response.header(HttpHeaders.ContentType, contentType.toString())
                    if (source.fileSize > 0) {
                        call.response.header(HttpHeaders.AcceptRanges, "bytes")
                        call.response.header(HttpHeaders.ContentLength, source.fileSize.toString())
                    }
                    source.lastModifiedEpochMs?.let { lastModified ->
                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                    }
                    Timber.tag(CAST_HTTP_LOG_TAG).d(
                        "HEAD /song songId=%s source=%s size=%d type=%s",
                        song.id,
                        source.sourceLabel,
                        source.fileSize,
                        contentType
                    )
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    if (e.isClientAbortDuringResponse()) {
                        Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /song client disconnected. songId=%s", song.id)
                        Timber.tag("PX_CAST_HTTP")
                            .w("HEAD /song client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                        return@head
                    }
                    Timber.tag(CAST_HTTP_LOG_TAG).e(e, "HEAD /song exception. songId=%s", song.id)
                    Timber.tag("PX_CAST_HTTP")
                        .e(e, "HEAD /song exception songId=${song.id}")
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }
}
