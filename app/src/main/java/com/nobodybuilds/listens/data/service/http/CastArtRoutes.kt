package com.nobodybuilds.listens.data.service.http

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import timber.log.Timber

/** GET and HEAD /art/{songId}: serves a song's artwork, or a transparent placeholder. */
internal class CastArtRoutes(
    private val mediaSources: CastMediaSourceResolver
) {
    fun register(route: Route) {
        with(route) {
            get("/art/{songId}") {
                val songId = call.parameters["songId"]
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Song ID is missing")
                    return@get
                }
                if (!call.ensureAuthorizedCastMediaRequest(songId)) return@get

                val song = mediaSources.resolveSongForServing(songId)
                if (song == null) {
                    Timber.tag(CAST_HTTP_LOG_TAG).w(
                        "GET /art unresolved songId=%s (repository+MediaStore fallback miss)",
                        songId
                    )
                    Timber.tag("PX_CAST_HTTP")
                        .e("GET /art unresolved songId=$songId")
                    call.respond(HttpStatusCode.NotFound, "Song not found")
                    return@get
                }

                try {
                    val artSource = mediaSources.resolveArtStreamSource(song)
                    Timber.tag(CAST_HTTP_LOG_TAG).i(
                        "GET /art songId=%s source=%s length=%s type=%s",
                        song.id,
                        artSource.sourceLabel,
                        artSource.contentLength,
                        artSource.contentType
                    )
                    Timber.tag("PX_CAST_HTTP")
                        .i("GET /art songId=${song.id} source=${artSource.sourceLabel} len=${artSource.contentLength} type=${artSource.contentType}")
                    artSource.lastModifiedEpochMs?.let { lastModified ->
                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                    }
                    call.respondOutputStream(artSource.contentType) {
                        artSource.inputStreamFactory().use { inputStream ->
                            inputStream.copyTo(this)
                        }
                    }
                } catch (e: Exception) {
                    if (e.isClientAbortDuringResponse()) {
                        Timber.tag(CAST_HTTP_LOG_TAG).d("GET /art client disconnected. songId=%s", song.id)
                        Timber.tag("PX_CAST_HTTP").w("GET /art client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                        return@get
                    }
                    Timber.tag(CAST_HTTP_LOG_TAG).e(e, "GET /art exception. songId=%s", song.id)
                    Timber.tag("PX_CAST_HTTP").e(e, "GET /art exception songId=${song.id}")
                    val fallbackSource = mediaSources.placeholderArtSource()
                    call.respondOutputStream(fallbackSource.contentType) {
                        fallbackSource.inputStreamFactory().use { inputStream ->
                            inputStream.copyTo(this)
                        }
                    }
                }
            }
            head("/art/{songId}") {
                val songId = call.parameters["songId"]
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@head
                }
                if (!call.ensureAuthorizedCastMediaRequest(songId)) return@head

                val song = mediaSources.resolveSongForServing(songId)
                if (song == null) {
                    Timber.tag(CAST_HTTP_LOG_TAG).w(
                        "HEAD /art unresolved songId=%s (repository+MediaStore fallback miss)",
                        songId
                    )
                    Timber.tag("PX_CAST_HTTP").e("HEAD /art unresolved songId=$songId")
                    call.respond(HttpStatusCode.NotFound)
                    return@head
                }

                try {
                    val artSource = mediaSources.resolveArtStreamSource(song)
                    call.response.header(HttpHeaders.ContentType, artSource.contentType.toString())
                    if (artSource.contentLength != null && artSource.contentLength > 0L) {
                        call.response.header(HttpHeaders.ContentLength, artSource.contentLength.toString())
                    }
                    artSource.lastModifiedEpochMs?.let { lastModified ->
                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                    }
                    Timber.tag(CAST_HTTP_LOG_TAG).d(
                        "HEAD /art songId=%s source=%s length=%s type=%s",
                        song.id,
                        artSource.sourceLabel,
                        artSource.contentLength,
                        artSource.contentType
                    )
                    call.respond(HttpStatusCode.OK)
                } catch (e: Exception) {
                    if (e.isClientAbortDuringResponse()) {
                        Timber.tag(CAST_HTTP_LOG_TAG).d("HEAD /art client disconnected. songId=%s", song.id)
                        Timber.tag("PX_CAST_HTTP").w("HEAD /art client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                        return@head
                    }
                    Timber.tag(CAST_HTTP_LOG_TAG).e(e, "HEAD /art exception. songId=%s", song.id)
                    Timber.tag("PX_CAST_HTTP").e(e, "HEAD /art exception songId=${song.id}")
                    val fallbackSource = mediaSources.placeholderArtSource()
                    call.response.header(HttpHeaders.ContentType, fallbackSource.contentType.toString())
                    fallbackSource.contentLength?.let { length ->
                        call.response.header(HttpHeaders.ContentLength, length.toString())
                    }
                    fallbackSource.lastModifiedEpochMs?.let { lastModified ->
                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                    }
                    call.respond(HttpStatusCode.OK)
                }
            }
        }
    }
}
