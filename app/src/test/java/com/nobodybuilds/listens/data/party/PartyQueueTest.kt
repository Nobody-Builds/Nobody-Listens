package com.nobodybuilds.listens.data.party

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PartyQueueTest {

    private var ids = 0
    private lateinit var queue: PartyQueue
    private var now = 1_000_000L

    private fun song(n: Int) = PartySong("s$n", "Song $n", "Artist $n", "Album $n", 180_000L)

    @BeforeEach
    fun setUp() {
        ids = 0
        queue = PartyQueue(PartyRateLimiter(minIntervalMs = 10_000L, maxPending = 5), idGenerator = { "id${ids++}" })
    }

    private fun join(name: String) = queue.join(name, now)!!

    private fun added(result: PartyAddResult): PartyRequest {
        assertTrue(result is PartyAddResult.Added, "expected Added but was $result")
        return (result as PartyAddResult.Added).request
    }

    @Test
    fun `requests are ordered by votes then by arrival`() {
        val a = join("Asha"); val b = join("Ben"); val c = join("Cara")
        val r1 = added(queue.add(a.id, song(1), now))
        val r2 = added(queue.add(b.id, song(2), now))
        val r3 = added(queue.add(c.id, song(3), now))
        assertEquals(listOf(r1.id, r2.id, r3.id), queue.queued().map { it.id })

        queue.toggleVote(a.id, r3.id)
        queue.toggleVote(b.id, r3.id)
        queue.toggleVote(c.id, r2.id)
        // r3: 3 votes, r2: 2 votes, r1: 1 vote
        assertEquals(listOf(r3.id, r2.id, r1.id), queue.queued().map { it.id })
        assertEquals(r3.id, queue.nextToStage()?.id)
    }

    @Test
    fun `ties keep first come first served`() {
        val a = join("A"); val b = join("B")
        val r1 = added(queue.add(a.id, song(1), now))
        val r2 = added(queue.add(b.id, song(2), now))
        queue.toggleVote(b.id, r1.id)
        queue.toggleVote(a.id, r2.id)
        assertEquals(listOf(r1.id, r2.id), queue.queued().map { it.id })
    }

    @Test
    fun `one vote per guest, voting again removes it, own vote is fixed`() {
        val a = join("A"); val b = join("B")
        val r = added(queue.add(a.id, song(1), now))
        assertEquals(1, r.votes)
        assertEquals(2, queue.toggleVote(b.id, r.id)!!.votes)
        assertEquals(1, queue.toggleVote(b.id, r.id)!!.votes)
        assertEquals(1, queue.toggleVote(a.id, r.id)!!.votes)
        assertNull(queue.toggleVote("nobody", r.id))
    }

    @Test
    fun `adding an already requested song counts as a vote and skips the rate limit`() {
        val a = join("A"); val b = join("B")
        val r = added(queue.add(a.id, song(1), now))
        added(queue.add(b.id, song(2), now)) // b's cooldown starts
        val result = queue.add(b.id, song(1), now + 1)
        assertTrue(result is PartyAddResult.Voted)
        assertEquals(2, queue.request(r.id)!!.votes)
        assertEquals(2, queue.queued().size)
    }

    @Test
    fun `approve each holds requests until approved`() {
        queue.setApproveEach(true)
        val a = join("A")
        val r = added(queue.add(a.id, song(1), now))
        assertEquals(PartyRequestStatus.AwaitingApproval, r.status)
        assertTrue(queue.queued().isEmpty())
        assertNull(queue.nextToStage())
        assertTrue(queue.approve(r.id))
        assertEquals(r.id, queue.nextToStage()?.id)
    }

    @Test
    fun `turning approve each off releases waiting requests`() {
        queue.setApproveEach(true)
        val a = join("A")
        added(queue.add(a.id, song(1), now))
        queue.setApproveEach(false)
        assertEquals(1, queue.queued().size)
        assertTrue(queue.awaitingApproval().isEmpty())
    }

    @Test
    fun `staged then played lifecycle, up next shows staged first`() {
        val a = join("A"); val b = join("B")
        val r1 = added(queue.add(a.id, song(1), now))
        val r2 = added(queue.add(b.id, song(2), now))
        assertTrue(queue.markStaged(r1.id))
        // r2 gets more votes, but the staged song stays at the front.
        queue.toggleVote(a.id, r2.id)
        assertEquals(listOf(r1.id, r2.id), queue.upNext().map { it.id })
        assertEquals(r2.id, queue.nextToStage()?.id)
        assertFalse(queue.markPlayed(r2.id)) // not staged
        assertTrue(queue.markPlayed(r1.id))
        assertNull(queue.staged())
        assertEquals("A", queue.lastPlayedFor("s1")?.guestName)
    }

    @Test
    fun `kick removes guest requests and their votes`() {
        val a = join("A"); val b = join("B")
        val ra = added(queue.add(a.id, song(1), now))
        val rb = added(queue.add(b.id, song(2), now))
        queue.toggleVote(a.id, rb.id)
        assertTrue(queue.kick(a.id))
        assertEquals(PartyRequestStatus.Removed, queue.request(ra.id)!!.status)
        assertEquals(1, queue.request(rb.id)!!.votes)
        assertTrue(queue.isKicked(a.id))
        assertTrue(queue.add(a.id, song(3), now + 60_000) is PartyAddResult.UnknownGuest)
    }

    @Test
    fun `names are cleaned and made unique`() {
        assertEquals("Guest", join("   ").name)
        assertEquals("Sam", join("  Sam  ").name)
        assertEquals("sam 2", join("sam").name)
        assertEquals("ab", PartyQueue.cleanName("<a>b<"))
        assertEquals(PartyQueue.MAX_NAME_LENGTH, PartyQueue.cleanName("x".repeat(100)).length)
    }

    @Test
    fun `summary counts played songs, friends and top contributor`() {
        val a = join("Asha"); val b = join("Ben")
        val ids = listOf(
            added(queue.add(a.id, song(1), now)),
            added(queue.add(b.id, song(2), now)),
        ).map { it.id } + listOf(
            added(queue.add(a.id, song(3), now + 20_000)).id,
        )
        ids.forEach { id -> queue.markStaged(id); queue.markPlayed(id) }
        val s = queue.summary()
        assertEquals(3, s.songsPlayed)
        assertEquals(2, s.contributors)
        assertEquals("Asha", s.topContributor)
        assertEquals(2, s.topContributorSongs)
    }

    @Nested
    inner class Limits {
        @Test
        fun `one add per interval per guest`() {
            val a = join("A")
            added(queue.add(a.id, song(1), now))
            val tooSoon = queue.add(a.id, song(2), now + 4_000)
            assertTrue(tooSoon is PartyAddResult.TooSoon)
            assertEquals(6_000L, (tooSoon as PartyAddResult.TooSoon).retryInMs)
            added(queue.add(a.id, song(2), now + 10_000))
        }

        @Test
        fun `max five pending per guest`() {
            val a = join("A")
            repeat(5) { i -> added(queue.add(a.id, song(i), now + i * 10_000L)) }
            assertTrue(queue.add(a.id, song(99), now + 100_000) is PartyAddResult.TooManyPending)
            // Once one plays, there's room again.
            val first = queue.nextToStage()!!
            queue.markStaged(first.id); queue.markPlayed(first.id)
            added(queue.add(a.id, song(99), now + 110_000))
        }

        @Test
        fun `guests limits are independent`() {
            val a = join("A"); val b = join("B")
            added(queue.add(a.id, song(1), now))
            added(queue.add(b.id, song(2), now))
        }
    }
}

class PartyRateLimiterTest {

    @Test
    fun `allows first add and enforces interval`() {
        val l = PartyRateLimiter(minIntervalMs = 10_000L, maxPending = 5)
        assertEquals(PartyRateLimiter.Decision.Allowed, l.check("g", 0L, 0))
        l.record("g", 0L)
        assertEquals(PartyRateLimiter.Decision.TooSoon(1_000L), l.check("g", 9_000L, 1))
        assertEquals(PartyRateLimiter.Decision.Allowed, l.check("g", 10_000L, 1))
        assertEquals(500L, l.cooldownMs("g", 9_500L))
        assertEquals(0L, l.cooldownMs("other", 9_500L))
    }

    @Test
    fun `pending cap wins over interval`() {
        val l = PartyRateLimiter(minIntervalMs = 10_000L, maxPending = 2)
        assertEquals(PartyRateLimiter.Decision.TooManyPending, l.check("g", 0L, 2))
    }

    @Test
    fun `forget clears cooldown`() {
        val l = PartyRateLimiter()
        l.record("g", 0L)
        l.forget("g")
        assertEquals(PartyRateLimiter.Decision.Allowed, l.check("g", 1L, 0))
    }
}

class PartyLibraryIndexTest {

    private val index = PartyLibraryIndex(
        listOf(
            PartyLibraryIndex.Entry(PartySong("1", "Blue Monday", "New Order", "Power, Corruption & Lies", 0), dateAdded = 10),
            PartyLibraryIndex.Entry(PartySong("2", "Monday Morning", "Fleetwood Mac", "Fleetwood Mac", 0), dateAdded = 30),
            PartyLibraryIndex.Entry(PartySong("3", "Ceremony", "New Order", "Substance", 0), dateAdded = 20),
        )
    )

    @Test
    fun `matches title artist and album, all words`() {
        assertEquals(listOf("2", "1"), index.search("monday", 0).items.map { it.id })
        assertEquals(listOf("3", "1").sorted(), index.search("new order", 0).items.map { it.id }.sorted())
        assertEquals(listOf("3"), index.search("substance", 0).items.map { it.id })
        assertEquals(listOf("1"), index.search("order monday", 0).items.map { it.id })
        assertTrue(index.search("zzz", 0).items.isEmpty())
    }

    @Test
    fun `blank query lists recently added first, paginated`() {
        val p0 = index.search("", 0, pageSize = 2)
        assertEquals(listOf("2", "3"), p0.items.map { it.id })
        assertTrue(p0.hasMore)
        val p1 = index.search("", 1, pageSize = 2)
        assertEquals(listOf("1"), p1.items.map { it.id })
        assertFalse(p1.hasMore)
        assertTrue(index.search("", 5, pageSize = 2).items.isEmpty())
    }
}
