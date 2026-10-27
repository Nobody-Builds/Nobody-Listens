package com.nobodybuilds.listens.data.party

/**
 * Pure (Android-free) model of a party: guests, song requests, votes and the per-guest
 * add limits. Not thread-safe — [PartyManager] serialises access with a lock.
 */

/** The bits of a library song a guest is allowed to see. Never carries a file path. */
data class PartySong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
)

data class PartyGuest(
    val id: String,
    val name: String,
    val joinedAt: Long,
)

enum class PartyRequestStatus {
    /** Host has "approve each request" on and hasn't decided yet. */
    AwaitingApproval,

    /** Approved, waiting in the party queue (ordered by votes). */
    Queued,

    /** Inserted into the player queue right after the current song. */
    Staged,

    /** Started playing. */
    Played,

    /** Removed by the host, by a kick, or dropped from the player queue. */
    Removed,
}

data class PartyRequest(
    val id: String,
    val song: PartySong,
    val guestId: String,
    val guestName: String,
    val createdAt: Long,
    /** Insertion order; breaks vote ties (earlier first). */
    val seq: Long,
    val voters: Set<String>,
    val status: PartyRequestStatus,
) {
    val votes: Int get() = voters.size
    val isPending: Boolean
        get() = status == PartyRequestStatus.AwaitingApproval || status == PartyRequestStatus.Queued
}

/** Orders the approved party queue: most votes first, then first come first served. */
val PartyRequestOrder: Comparator<PartyRequest> =
    compareByDescending<PartyRequest> { it.votes }.thenBy { it.seq }

sealed interface PartyAddResult {
    data class Added(val request: PartyRequest) : PartyAddResult

    /** Someone already asked for this song; the add counted as a vote instead. */
    data class Voted(val request: PartyRequest) : PartyAddResult
    data class TooSoon(val retryInMs: Long) : PartyAddResult
    data object TooManyPending : PartyAddResult
    data object UnknownGuest : PartyAddResult
    data object PartyFull : PartyAddResult
}

data class PartySummary(
    val songsPlayed: Int,
    val songsRequested: Int,
    val contributors: Int,
    val guests: Int,
    val topContributor: String?,
    val topContributorSongs: Int,
)

/**
 * Per-guest add limiter: at most one add every [minIntervalMs], and at most [maxPending]
 * requests waiting at a time.
 */
class PartyRateLimiter(
    val minIntervalMs: Long = 10_000L,
    val maxPending: Int = 5,
) {
    private val lastAdd = HashMap<String, Long>()

    sealed interface Decision {
        data object Allowed : Decision
        data class TooSoon(val retryInMs: Long) : Decision
        data object TooManyPending : Decision
    }

    fun check(guestId: String, now: Long, pendingCount: Int): Decision {
        if (pendingCount >= maxPending) return Decision.TooManyPending
        val last = lastAdd[guestId] ?: return Decision.Allowed
        val elapsed = now - last
        return if (elapsed >= minIntervalMs) Decision.Allowed else Decision.TooSoon(minIntervalMs - elapsed)
    }

    /** Milliseconds until [guestId] may add again (0 when allowed now). */
    fun cooldownMs(guestId: String, now: Long): Long {
        val last = lastAdd[guestId] ?: return 0L
        return (minIntervalMs - (now - last)).coerceAtLeast(0L)
    }

    fun record(guestId: String, now: Long) {
        lastAdd[guestId] = now
    }

    fun forget(guestId: String) {
        lastAdd.remove(guestId)
    }
}

class PartyQueue(
    private val rateLimiter: PartyRateLimiter = PartyRateLimiter(),
    private val maxGuests: Int = 50,
    private val idGenerator: () -> String,
) {
    private val guests = LinkedHashMap<String, PartyGuest>()
    private val kicked = HashSet<String>()
    private val requests = ArrayList<PartyRequest>()
    private var nextSeq = 0L

    var approveEach: Boolean = false
        private set

    // ------------------------------------------------------------------ guests

    fun join(name: String, now: Long): PartyGuest? {
        if (guests.size >= maxGuests) return null
        val clean = cleanName(name)
        val guest = PartyGuest(id = idGenerator(), name = uniqueName(clean), joinedAt = now)
        guests[guest.id] = guest
        return guest
    }

    fun guest(id: String): PartyGuest? = guests[id]
    fun guests(): List<PartyGuest> = guests.values.toList()
    fun isKicked(id: String): Boolean = id in kicked

    /** Removes a guest, their pending requests and their votes on pending requests. */
    fun kick(guestId: String): Boolean {
        val removed = guests.remove(guestId) ?: return false
        kicked += removed.id
        rateLimiter.forget(guestId)
        for (i in requests.indices) {
            val r = requests[i]
            if (!r.isPending) continue
            requests[i] = when {
                r.guestId == guestId -> r.copy(status = PartyRequestStatus.Removed)
                guestId in r.voters -> r.copy(voters = r.voters - guestId)
                else -> r
            }
        }
        return true
    }

    // ---------------------------------------------------------------- requests

    fun add(guestId: String, song: PartySong, now: Long): PartyAddResult {
        val guest = guests[guestId] ?: return PartyAddResult.UnknownGuest
        // Someone already asked for it (and it hasn't played yet): count it as a vote.
        val existingIndex = requests.indexOfFirst {
            it.song.id == song.id && (it.isPending || it.status == PartyRequestStatus.Staged)
        }
        if (existingIndex >= 0) {
            val existing = requests[existingIndex]
            val updated = existing.copy(voters = existing.voters + guest.id)
            requests[existingIndex] = updated
            return PartyAddResult.Voted(updated)
        }
        if (requests.count { it.isPending } >= MAX_PENDING_TOTAL) return PartyAddResult.PartyFull
        when (val decision = rateLimiter.check(guest.id, now, pendingCountFor(guest.id))) {
            is PartyRateLimiter.Decision.TooSoon -> return PartyAddResult.TooSoon(decision.retryInMs)
            PartyRateLimiter.Decision.TooManyPending -> return PartyAddResult.TooManyPending
            PartyRateLimiter.Decision.Allowed -> Unit
        }
        rateLimiter.record(guest.id, now)
        val request = PartyRequest(
            id = idGenerator(),
            song = song,
            guestId = guest.id,
            guestName = guest.name,
            createdAt = now,
            seq = nextSeq++,
            voters = setOf(guest.id),
            status = if (approveEach) PartyRequestStatus.AwaitingApproval else PartyRequestStatus.Queued,
        )
        requests += request
        return PartyAddResult.Added(request)
    }

    /** One vote per guest per request; voting twice removes the vote. Returns the new state. */
    fun toggleVote(guestId: String, requestId: String): PartyRequest? {
        if (guestId !in guests) return null
        val index = requests.indexOfFirst { it.id == requestId }
        if (index < 0) return null
        val r = requests[index]
        if (!r.isPending) return r
        // The requester's own vote is fixed.
        if (r.guestId == guestId) return r
        val updated = if (guestId in r.voters) r.copy(voters = r.voters - guestId) else r.copy(voters = r.voters + guestId)
        requests[index] = updated
        return updated
    }

    fun setApproveEach(enabled: Boolean) {
        approveEach = enabled
        if (!enabled) {
            // Nothing should be left waiting on a switch that's now off.
            for (i in requests.indices) {
                if (requests[i].status == PartyRequestStatus.AwaitingApproval) {
                    requests[i] = requests[i].copy(status = PartyRequestStatus.Queued)
                }
            }
        }
    }

    fun approve(requestId: String): Boolean =
        transition(requestId, from = setOf(PartyRequestStatus.AwaitingApproval), to = PartyRequestStatus.Queued)

    fun remove(requestId: String): Boolean = transition(
        requestId,
        from = setOf(PartyRequestStatus.AwaitingApproval, PartyRequestStatus.Queued, PartyRequestStatus.Staged),
        to = PartyRequestStatus.Removed,
    )

    fun markStaged(requestId: String): Boolean =
        transition(requestId, from = setOf(PartyRequestStatus.Queued), to = PartyRequestStatus.Staged)

    fun markPlayed(requestId: String): Boolean =
        transition(requestId, from = setOf(PartyRequestStatus.Staged), to = PartyRequestStatus.Played)

    private fun transition(requestId: String, from: Set<PartyRequestStatus>, to: PartyRequestStatus): Boolean {
        val index = requests.indexOfFirst { it.id == requestId }
        if (index < 0 || requests[index].status !in from) return false
        requests[index] = requests[index].copy(status = to)
        return true
    }

    // ------------------------------------------------------------------ views

    fun request(id: String): PartyRequest? = requests.firstOrNull { it.id == id }

    fun staged(): PartyRequest? = requests.firstOrNull { it.status == PartyRequestStatus.Staged }

    /** The approved queue, best first. Doesn't include the staged song. */
    fun queued(): List<PartyRequest> =
        requests.filter { it.status == PartyRequestStatus.Queued }.sortedWith(PartyRequestOrder)

    /** What guests see as "Up next": the staged song, then the vote-ordered queue. */
    fun upNext(): List<PartyRequest> = listOfNotNull(staged()) + queued()

    fun awaitingApproval(): List<PartyRequest> =
        requests.filter { it.status == PartyRequestStatus.AwaitingApproval }.sortedBy { it.seq }

    fun nextToStage(): PartyRequest? = queued().firstOrNull()

    /** Most recent played request for [songId] (for "added by" on now playing). */
    fun lastPlayedFor(songId: String): PartyRequest? =
        requests.lastOrNull { it.song.id == songId && it.status == PartyRequestStatus.Played }

    fun pendingCountFor(guestId: String): Int = requests.count { it.guestId == guestId && it.isPending }

    fun pendingFor(guestId: String): List<PartyRequest> = requests.filter { it.guestId == guestId && it.isPending }

    fun cooldownMs(guestId: String, now: Long): Long = rateLimiter.cooldownMs(guestId, now)

    /** Song ids that are requested and not yet played (so the UI can show "Requested"). */
    fun activeSongIds(): Set<String> = requests
        .filter { it.isPending || it.status == PartyRequestStatus.Staged }
        .mapTo(HashSet()) { it.song.id }

    fun summary(): PartySummary {
        val played = requests.filter { it.status == PartyRequestStatus.Played }
        val byGuest = played.groupBy { it.guestId }
        val top = byGuest.maxWithOrNull(
            compareBy<Map.Entry<String, List<PartyRequest>>> { it.value.size }
                .thenByDescending { e -> e.value.minOf { it.seq } }
        )
        return PartySummary(
            songsPlayed = played.size,
            songsRequested = requests.size,
            contributors = byGuest.size,
            guests = guests.size + kicked.size,
            topContributor = top?.value?.first()?.guestName,
            topContributorSongs = top?.value?.size ?: 0,
        )
    }

    private fun uniqueName(base: String): String {
        val taken = guests.values.mapTo(HashSet()) { it.name.lowercase() }
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }

    companion object {
        const val MAX_NAME_LENGTH = 24
        const val MAX_PENDING_TOTAL = 200

        fun cleanName(raw: String): String {
            val cleaned = raw
                .filter { !it.isISOControl() && it != '<' && it != '>' }
                .trim()
                .replace(Regex("\\s+"), " ")
                .take(MAX_NAME_LENGTH)
                .trim()
            return cleaned.ifEmpty { "Guest" }
        }
    }
}
