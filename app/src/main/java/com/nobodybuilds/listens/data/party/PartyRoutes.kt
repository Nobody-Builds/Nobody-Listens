package com.nobodybuilds.listens.data.party

import android.content.res.AssetManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json

/** The party web page and its JSON API, served to guests by [PartyServerService]. */
internal class PartyRoutes(
    private val assets: AssetManager,
    private val manager: PartyManager,
    private val libraryCache: PartyLibraryCache,
    private val artRenderer: PartyArtRenderer,
    private val pageHtml: String,
    private val checkExpired: () -> Boolean,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun install(routing: io.ktor.server.routing.Routing) {
        routing.partyRoutes()
    }

    private fun io.ktor.server.routing.Routing.partyRoutes() {
        get("/") { call.respondPage() }
        get("/tv") { call.respondPage() }
        // Site icons from the Nobody Listens kit; favicon.svg follows the guest's light or dark mode.
        get("/favicon.svg") { call.respondAsset("party/favicon.svg", ContentType.Image.SVG) }
        get("/apple-touch-icon.png") { call.respondAsset("party/apple-touch-icon.png", ContentType.Image.PNG) }

        post("/api/pin") {
            if (checkExpired()) {
                call.respondError(HttpStatusCode.Unauthorized, "ended", "The party has ended"); return@post
            }
            val params = runCatching { call.receiveParameters() }.getOrNull()
            when (val r = manager.exchangePin(params?.get("pin"), call.request.origin.remoteHost)) {
                is PartyManager.PinResult.Ok -> call.respondJson(json.encodeToString(TokenDto.serializer(), TokenDto(r.token)))
                PartyManager.PinResult.Wrong -> call.respondError(HttpStatusCode.Forbidden, "wrong_pin", "That PIN doesn't match")
                is PartyManager.PinResult.Locked -> call.respondError(
                    HttpStatusCode.TooManyRequests, "locked", "Too many tries. Wait ${r.retryInMs / 1000 + 1} s"
                )
            }
        }

        post("/api/join") {
            if (!call.checkToken()) return@post
            val params = runCatching { call.receiveParameters() }.getOrNull()
            val name = params?.get("name").orEmpty()
            when (val r = manager.join(name, call.request.origin.remoteHost)) {
                is PartyManager.JoinResult.Ok -> call.respondJson(
                    json.encodeToString(JoinDto.serializer(), JoinDto(r.guest.id, r.guest.name, r.key))
                )
                PartyManager.JoinResult.Banned -> call.respondError(HttpStatusCode.Forbidden, "kicked", "The host removed you from this party")
                PartyManager.JoinResult.Full -> call.respondError(HttpStatusCode.Forbidden, "full", "This party is full")
            }
        }

        get("/api/state") {
            if (!call.checkToken()) return@get
            val guest = call.optionalGuest()
            if (guest === KICKED) return@get
            call.respondJson(json.encodeToString(StateDto.serializer(), buildState(guest)))
        }

        get("/api/search") {
            if (!call.checkToken()) return@get
            call.requireGuest() ?: return@get
            val q = call.request.queryParameters["q"].orEmpty()
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val index = libraryCache.loadLibrary()
            if (index == null) {
                call.respondJson(json.encodeToString(SearchDto.serializer(), SearchDto(emptyList(), 0, false, 0)))
                return@get
            }
            val result = index.search(q, page, 20)
            val active = manager.read { it.activeSongIds() }.orEmpty()
            call.respondJson(
                json.encodeToString(
                    SearchDto.serializer(),
                    SearchDto(
                        items = result.items.map { SongDto(it.id, it.title, it.artist, it.album, it.durationMs, it.id in active) },
                        page = result.page,
                        hasMore = result.hasMore,
                        total = result.total,
                    )
                )
            )
        }

        post("/api/add") {
            if (!call.checkToken()) return@post
            val guest = call.requireGuest() ?: return@post
            val songId = runCatching { call.receiveParameters() }.getOrNull()?.get("songId").orEmpty()
            val song = libraryCache.loadLibrary()?.get(songId)
            if (song == null) {
                call.respondError(HttpStatusCode.NotFound, "not_found", "That song isn't in the library")
                return@post
            }
            when (val r = manager.add(guest.id, song)) {
                is PartyAddResult.Added -> call.respondJson(
                    json.encodeToString(
                        AddDto.serializer(),
                        AddDto(if (r.request.status == PartyRequestStatus.AwaitingApproval) "awaiting" else "added", "Added ${song.title}")
                    )
                )
                is PartyAddResult.Voted -> call.respondJson(
                    json.encodeToString(AddDto.serializer(), AddDto("voted", "Already requested — you voted for it"))
                )
                is PartyAddResult.TooSoon -> call.respondError(
                    HttpStatusCode.TooManyRequests, "too_soon", "Wait ${r.retryInMs / 1000 + 1} s before adding another"
                )
                PartyAddResult.TooManyPending -> call.respondError(
                    HttpStatusCode.TooManyRequests, "too_many", "You have 5 songs waiting. Let some play first"
                )
                PartyAddResult.PartyFull -> call.respondError(HttpStatusCode.TooManyRequests, "full", "The queue is full")
                PartyAddResult.UnknownGuest -> call.respondError(HttpStatusCode.Unauthorized, "no_guest", "Join the party first")
            }
        }

        post("/api/vote") {
            if (!call.checkToken()) return@post
            val guest = call.requireGuest() ?: return@post
            val requestId = runCatching { call.receiveParameters() }.getOrNull()?.get("requestId").orEmpty()
            val r = manager.toggleVote(guest.id, requestId)
            if (r == null) call.respondError(HttpStatusCode.NotFound, "not_found", "That request is gone")
            else call.respondJson("""{"ok":true,"votes":${r.votes}}""")
        }

        get("/api/art/{songId}") {
            if (!call.checkToken()) return@get
            val songId = call.parameters["songId"].orEmpty()
            val size = (call.request.queryParameters["s"]?.toIntOrNull() ?: 160).coerceIn(48, 640)
            // Only library songs or what's playing — never arbitrary ids/paths.
            val song = libraryCache.librarySongs[songId]?.takeIf {
                libraryCache.library?.get(songId) != null || manager.nowPlaying()?.songId == songId
            }
            val bytes = song?.let { runCatching { artRenderer.artJpeg(it, size) }.getOrNull() }
            if (bytes == null) {
                call.response.header("Cache-Control", "private, max-age=300")
                call.respondText("", ContentType.Text.Plain, HttpStatusCode.NotFound)
                return@get
            }
            call.response.header("Cache-Control", "private, max-age=3600")
            call.respondBytes(bytes, ContentType.Image.JPEG)
        }

        get("/api/qr.svg") {
            if (!call.checkToken()) return@get
            val url = manager.state.value.joinUrl ?: return@get call.respondError(HttpStatusCode.NotFound, "gone", "Party ended")
            call.response.header("Cache-Control", "private, max-age=3600")
            call.respondText(PartyQr.svg(url), ContentType.Image.SVG)
        }
    }

    private fun buildState(guest: PartyGuest?): StateDto {
        val now = System.currentTimeMillis()
        val s = manager.state.value
        val np = manager.nowPlaying()
        val data = manager.read { q ->
            Triple(
                q.upNext(),
                guest?.let { g -> q.awaitingApproval().filter { it.guestId == g.id } }.orEmpty(),
                guest?.let { g -> q.pendingCountFor(g.id) to q.cooldownMs(g.id, now) },
            )
        }
        fun PartyRequest.dto() = RequestDto(
            id = id, songId = song.id, title = song.title, artist = song.artist, durationMs = song.durationMs,
            addedBy = guestName, votes = votes, votedByMe = guest != null && guest.id in voters,
            mine = guest != null && guest.id == guestId, staged = status == PartyRequestStatus.Staged,
        )
        return StateDto(
            running = s.status == PartyStatus.Running,
            endsAt = s.endsAt,
            now = now,
            guests = s.guests.size,
            approveEach = s.approveEach,
            nowPlaying = np?.let {
                NowPlayingDto(
                    songId = it.songId, title = it.title, artist = it.artist, album = it.album,
                    durationMs = it.durationMs, positionMs = it.positionMs, positionAt = it.positionAt,
                    isPlaying = it.isPlaying, addedBy = it.addedBy,
                    hasArt = it.songId != null && libraryCache.librarySongs.containsKey(it.songId),
                )
            },
            upNext = data?.first.orEmpty().take(50).map { it.dto() },
            awaiting = data?.second.orEmpty().map { it.dto() },
            me = guest?.let { g ->
                MeDto(g.id, g.name, data?.third?.first ?: 0, data?.third?.second ?: 0L)
            },
        )
    }

    // ------------------------------------------------------- call helpers

    private suspend fun ApplicationCall.respondPage() {
        noStore()
        response.header("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'")
        respondText(pageHtml, ContentType.Text.Html.withParameter("charset", "utf-8"))
    }

    private suspend fun ApplicationCall.respondAsset(path: String, type: ContentType) {
        val bytes = runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()
            ?: return respondText("", ContentType.Text.Plain, HttpStatusCode.NotFound)
        response.header("Cache-Control", "public, max-age=86400")
        respondBytes(bytes, type)
    }

    private suspend fun ApplicationCall.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) {
        noStore()
        respondText(body, ContentType.Application.Json, status)
    }

    private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) =
        respondJson(json.encodeToString(ErrorDto.serializer(), ErrorDto(code, message)), status)

    private fun ApplicationCall.noStore() {
        response.header("Cache-Control", "no-store")
        response.header("Referrer-Policy", "no-referrer")
        response.header("X-Content-Type-Options", "nosniff")
    }

    private suspend fun ApplicationCall.checkToken(): Boolean {
        // Every API call (incl. the state poll) checks the wall-clock end first.
        if (checkExpired()) {
            respondError(HttpStatusCode.Unauthorized, "ended", "The party has ended")
            return false
        }
        val t = request.headers["X-Party-Token"] ?: request.queryParameters["t"]
        if (manager.checkToken(t)) return true
        respondError(HttpStatusCode.Unauthorized, if (manager.isRunning) "bad_token" else "ended",
            if (manager.isRunning) "This link isn't valid. Ask the host for the QR code" else "The party has ended")
        return false
    }

    private fun ApplicationCall.guestKey(): String? = request.headers["X-Party-Guest"] ?: request.queryParameters["g"]

    /** Guest if a valid key was sent; null for anonymous (TV) callers; [KICKED] after responding 403. */
    private suspend fun ApplicationCall.optionalGuest(): PartyGuest? {
        val key = guestKey() ?: return null
        return when (val a = manager.authenticateGuest(key)) {
            is PartyManager.GuestAuth.Ok -> a.guest
            PartyManager.GuestAuth.Kicked -> {
                respondError(HttpStatusCode.Forbidden, "kicked", "The host removed you from this party"); KICKED
            }
            PartyManager.GuestAuth.Unknown -> null
        }
    }

    private suspend fun ApplicationCall.requireGuest(): PartyGuest? {
        val key = guestKey()
        return when (val a = manager.authenticateGuest(key)) {
            is PartyManager.GuestAuth.Ok -> a.guest
            PartyManager.GuestAuth.Kicked -> {
                respondError(HttpStatusCode.Forbidden, "kicked", "The host removed you from this party"); null
            }
            PartyManager.GuestAuth.Unknown -> {
                respondError(HttpStatusCode.Unauthorized, "no_guest", "Join the party first"); null
            }
        }
    }

    private companion object {
        val KICKED = PartyGuest("", "", 0L)
    }
}
