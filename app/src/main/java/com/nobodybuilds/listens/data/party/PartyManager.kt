package com.nobodybuilds.listens.data.party

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

enum class PartyStatus { Idle, Starting, Running }

data class PartyNowPlaying(
    val songId: String?,
    val title: String,
    val artist: String,
    val album: String,
    val artUri: String?,
    val durationMs: Long,
    val positionMs: Long,
    /** Wall-clock time [positionMs] was sampled, so clients can extrapolate. */
    val positionAt: Long,
    val isPlaying: Boolean,
    val addedBy: String?,
)

data class PartyHostState(
    val status: PartyStatus = PartyStatus.Idle,
    val baseUrl: String? = null,
    val joinUrl: String? = null,
    val tvUrl: String? = null,
    val pin: String? = null,
    val startedAt: Long = 0L,
    val endsAt: Long = 0L,
    val approveEach: Boolean = false,
    val guests: List<PartyGuest> = emptyList(),
    val awaiting: List<PartyRequest> = emptyList(),
    val upNext: List<PartyRequest> = emptyList(),
    val nowPlaying: PartyNowPlaying? = null,
    val summarySoFar: PartySummary? = null,
    val error: String? = null,
    /** Shown after a party ends, until the host dismisses it or starts a new one. */
    val lastSummary: PartySummary? = null,
)

/**
 * App-scoped owner of the party session: credentials, the [PartyQueue] and the host-facing
 * state. Holds no network resources itself — [PartyServerService] does. Constructor is cheap.
 */
@Singleton
class PartyManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    private val lock = Any()
    private val random = SecureRandom()

    private val _state = MutableStateFlow(PartyHostState())
    val state: StateFlow<PartyHostState> = _state.asStateFlow()

    private var queue: PartyQueue? = null
    private var token: String? = null
    private var pin: String? = null
    private val guestKeys = HashMap<String, String>() // key -> guestId
    private val guestIps = HashMap<String, String>() // guestId -> ip
    private val bannedIps = HashSet<String>()
    private val pinFailures = HashMap<String, Pair<Int, Long>>() // ip -> (count, windowStart)
    @Volatile private var nowPlaying: PartyNowPlaying? = null

    /** Called (on any thread) after a change that may affect what the player should queue. */
    @Volatile var queueListener: (() -> Unit)? = null

    val isRunning: Boolean get() = _state.value.status == PartyStatus.Running

    // ------------------------------------------------------------- lifecycle

    fun start(durationMs: Long, approveEach: Boolean) {
        synchronized(lock) {
            if (_state.value.status != PartyStatus.Idle) return
            token = randomToken(18)
            pin = randomPin(6)
            queue = PartyQueue(idGenerator = { randomToken(9) }).also { it.setApproveEach(approveEach) }
            guestKeys.clear(); guestIps.clear(); bannedIps.clear(); pinFailures.clear()
            nowPlaying = null
            _state.value = PartyHostState(
                status = PartyStatus.Starting,
                approveEach = approveEach,
                pin = pin,
            )
        }
        val intent = Intent(appContext, PartyServerService::class.java)
            .setAction(PartyServerService.ACTION_START)
            .putExtra(PartyServerService.EXTRA_DURATION_MS, durationMs)
        runCatching { ContextCompat.startForegroundService(appContext, intent) }
            .onFailure { onServerFailed("Couldn't start the party: ${it.message ?: it.javaClass.simpleName}") }
    }

    fun end() {
        if (_state.value.status == PartyStatus.Idle) return
        val intent = Intent(appContext, PartyServerService::class.java).setAction(PartyServerService.ACTION_END)
        runCatching { appContext.startService(intent) }.onFailure { onStopped() }
    }

    fun dismissSummary() {
        _state.value = _state.value.copy(lastSummary = null, error = null)
    }

    internal fun onServerStarted(ip: String, port: Int, startedAt: Long, endsAt: Long) {
        synchronized(lock) {
            val t = token ?: return
            val base = "http://$ip:$port"
            _state.value = _state.value.copy(
                status = PartyStatus.Running,
                baseUrl = base,
                joinUrl = "$base/?t=$t",
                tvUrl = "$base/tv?t=$t",
                startedAt = startedAt,
                endsAt = endsAt,
                error = null,
            )
        }
        publish()
    }

    internal fun onServerFailed(message: String) {
        synchronized(lock) {
            clearSession()
            _state.value = PartyHostState(error = message)
        }
    }

    /** Service stopped (host, timer, Wi-Fi drop). Keeps a summary card around. */
    internal fun onStopped(reason: String? = null) {
        synchronized(lock) {
            val summary = queue?.summary()
            val wasActive = _state.value.status != PartyStatus.Idle
            clearSession()
            if (!wasActive) return // e.g. after onServerFailed: keep its error message
            _state.value = PartyHostState(
                lastSummary = summary,
                error = reason,
            )
        }
    }

    private fun clearSession() {
        queue = null; token = null; pin = null; nowPlaying = null
        guestKeys.clear(); guestIps.clear(); bannedIps.clear(); pinFailures.clear()
    }

    // ------------------------------------------------------------ auth

    fun checkToken(candidate: String?): Boolean {
        val t = token ?: return false
        if (candidate == null || !isRunning) return false
        return MessageDigest.isEqual(t.toByteArray(), candidate.toByteArray())
    }

    sealed interface PinResult {
        data class Ok(val token: String) : PinResult
        data object Wrong : PinResult
        data class Locked(val retryInMs: Long) : PinResult
    }

    fun exchangePin(candidate: String?, ip: String, now: Long = System.currentTimeMillis()): PinResult = synchronized(lock) {
        val p = pin ?: return PinResult.Wrong
        val t = token ?: return PinResult.Wrong
        val (count, windowStart) = pinFailures[ip] ?: (0 to now)
        val inWindow = now - windowStart < PIN_LOCK_WINDOW_MS
        if (inWindow && count >= PIN_MAX_FAILURES) {
            return PinResult.Locked(PIN_LOCK_WINDOW_MS - (now - windowStart))
        }
        val ok = candidate != null && MessageDigest.isEqual(p.toByteArray(), candidate.trim().toByteArray())
        if (ok) {
            pinFailures.remove(ip)
            PinResult.Ok(t)
        } else {
            pinFailures[ip] = if (inWindow) (count + 1) to windowStart else 1 to now
            PinResult.Wrong
        }
    }

    sealed interface JoinResult {
        data class Ok(val guest: PartyGuest, val key: String) : JoinResult
        data object Banned : JoinResult
        data object Full : JoinResult
    }

    fun join(name: String, ip: String, now: Long = System.currentTimeMillis()): JoinResult {
        val result = synchronized(lock) {
            val q = queue ?: return JoinResult.Full
            if (ip in bannedIps) return JoinResult.Banned
            val guest = q.join(name, now) ?: return JoinResult.Full
            val key = randomToken(18)
            guestKeys[key] = guest.id
            guestIps[guest.id] = ip
            JoinResult.Ok(guest, key)
        }
        publish()
        return result
    }

    sealed interface GuestAuth {
        data class Ok(val guest: PartyGuest) : GuestAuth
        data object Kicked : GuestAuth
        data object Unknown : GuestAuth
    }

    fun authenticateGuest(key: String?): GuestAuth = synchronized(lock) {
        val q = queue ?: return GuestAuth.Unknown
        val id = key?.let { guestKeys[it] } ?: return GuestAuth.Unknown
        if (q.isKicked(id)) return GuestAuth.Kicked
        val g = q.guest(id) ?: return GuestAuth.Unknown
        GuestAuth.Ok(g)
    }

    // ------------------------------------------------------------ queue ops

    fun <T> read(block: (PartyQueue) -> T): T? = synchronized(lock) { queue?.let(block) }

    fun add(guestId: String, song: PartySong, now: Long = System.currentTimeMillis()): PartyAddResult {
        val result = synchronized(lock) { queue?.add(guestId, song, now) } ?: PartyAddResult.UnknownGuest
        if (result is PartyAddResult.Added || result is PartyAddResult.Voted) changed()
        return result
    }

    fun toggleVote(guestId: String, requestId: String): PartyRequest? {
        val r = synchronized(lock) { queue?.toggleVote(guestId, requestId) }
        if (r != null) changed()
        return r
    }

    fun approve(requestId: String) = mutate { it.approve(requestId) }
    fun remove(requestId: String) = mutate { it.remove(requestId) }
    fun approveAll() = mutate { q -> q.awaitingApproval().forEach { q.approve(it.id) }; true }
    fun setApproveEach(enabled: Boolean) = mutate { it.setApproveEach(enabled); true }

    fun kick(guestId: String) = mutate { q ->
        val ok = q.kick(guestId)
        if (ok) {
            guestIps[guestId]?.let { bannedIps += it }
            guestKeys.entries.removeAll { it.value == guestId }
        }
        ok
    }

    // Player bridge (called by the service on the main thread).
    internal fun staged(): PartyRequest? = read { it.staged() }
    internal fun nextToStage(): PartyRequest? = read { it.nextToStage() }
    internal fun markStaged(id: String) = mutate(notifyPlayer = false) { it.markStaged(id) }
    internal fun markPlayed(id: String) = mutate(notifyPlayer = false) { it.markPlayed(id) }
    internal fun markDropped(id: String) = mutate(notifyPlayer = false) { it.remove(id) }

    internal fun setNowPlaying(np: PartyNowPlaying?) {
        val withAddedBy = np?.let { n ->
            val by = n.songId?.let { id -> read { it.lastPlayedFor(id)?.guestName } }
            n.copy(addedBy = by)
        }
        nowPlaying = withAddedBy
        publish()
    }

    fun nowPlaying(): PartyNowPlaying? = nowPlaying

    private inline fun mutate(notifyPlayer: Boolean = true, block: (PartyQueue) -> Boolean): Boolean {
        val ok = synchronized(lock) { queue?.let(block) } ?: false
        if (ok) { if (notifyPlayer) changed() else publish() }
        return ok
    }

    private fun changed() {
        publish()
        queueListener?.invoke()
    }

    private fun publish() {
        synchronized(lock) {
            val q = queue ?: return
            _state.value = _state.value.copy(
                approveEach = q.approveEach,
                guests = q.guests(),
                awaiting = q.awaitingApproval(),
                upNext = q.upNext(),
                nowPlaying = nowPlaying,
                summarySoFar = q.summary(),
            )
        }
    }

    // ------------------------------------------------------------ helpers

    private fun randomToken(bytes: Int): String {
        val b = ByteArray(bytes)
        random.nextBytes(b)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    private fun randomPin(digits: Int): String {
        val sb = StringBuilder()
        repeat(digits) { sb.append(random.nextInt(10)) }
        // Avoid trivially guessable PINs like 000000 / 111111.
        if (sb.toString().toSet().size == 1) return randomPin(digits)
        return sb.toString()
    }

    companion object {
        const val PIN_MAX_FAILURES = 5
        const val PIN_LOCK_WINDOW_MS = 60_000L
    }
}
