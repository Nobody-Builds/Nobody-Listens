package com.nobodybuilds.listens.data.smart

import kotlin.math.pow

/** One analysed song as seen by the engine. [vec] is centred on the library mean and normalised. */
class SongVector(
    val songId: String,
    val artist: String,
    val vec: FloatArray,
    /** 0..1 energy level. */
    val energy: Float,
    val tempo: Float,
    /** 0..1 strongest vocal class score. */
    val vocal: Float,
    val moods: List<String>,
    val genres: List<String>,
)

/** Raw per-song input for [SmartEngine.buildVectors]. */
class FeatureInput(
    val songId: String,
    val artist: String,
    val scores: FloatArray,
    val rms: Float,
    val bpm: Float,
    val zcr: Float,
    val moods: List<String>,
    val genres: List<String>,
)

/** A listening signal: positive for finished plays, negative for skips. */
data class PlaySignal(val songId: String, val timestamp: Long, val weight: Float)

data class SmartMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val songIds: List<String>,
)

/**
 * Pure recommendation maths: taste centroid, nearest neighbours with MMR diversity,
 * k-means daily mixes and rule-based mood mixes. No Android dependencies.
 */
object SmartEngine {

    const val MIN_MIX = 20
    const val MAX_MIX = 50
    val HALF_LIFE_MS = 30L * 24 * 60 * 60 * 1000

    fun buildVectors(inputs: List<FeatureInput>): List<SongVector> {
        if (inputs.isEmpty()) return emptyList()
        val raw = inputs.map { SmartVectors.embedding(it.scores, it.rms, it.bpm, it.zcr) }
        val mean = SmartVectors.mean(raw)!!
        return inputs.mapIndexed { i, input ->
            SongVector(
                songId = input.songId,
                artist = input.artist,
                vec = SmartVectors.normalized(SmartVectors.subtract(raw[i], mean)),
                energy = SmartTagger.energyLevel(input.rms),
                tempo = input.bpm,
                vocal = SmartTagger.vocalness(input.scores),
                moods = input.moods,
                genres = input.genres,
            )
        }
    }

    fun decay(ageMs: Long, halfLifeMs: Long = HALF_LIFE_MS): Float =
        0.5.pow(ageMs.coerceAtLeast(0L).toDouble() / halfLifeMs).toFloat()

    /** Recency-weighted per-song preference (sum of decayed signals). */
    fun songWeights(signals: List<PlaySignal>, nowMillis: Long, halfLifeMs: Long = HALF_LIFE_MS): Map<String, Float> {
        val out = HashMap<String, Float>()
        signals.forEach { s ->
            out[s.songId] = (out[s.songId] ?: 0f) + s.weight * decay(nowMillis - s.timestamp, halfLifeMs)
        }
        return out
    }

    /** Weighted centroid of the song vectors (normalised), or null without signals. */
    fun tasteCentroid(
        vectors: Map<String, SongVector>,
        signals: List<PlaySignal>,
        nowMillis: Long,
        halfLifeMs: Long = HALF_LIFE_MS,
    ): FloatArray? {
        val weights = songWeights(signals, nowMillis, halfLifeMs)
        val pairs = weights.mapNotNull { (id, w) -> vectors[id]?.let { it.vec to w } }
        if (pairs.isEmpty() || pairs.none { it.second > 0f }) return null
        val sum = SmartVectors.weightedSum(pairs) ?: return null
        return SmartVectors.normalized(sum).takeIf { SmartVectors.norm(it) > 0f }
    }

    /** Plain (unweighted by recency) centroid of the given plays. */
    fun centroidOf(vectors: Map<String, SongVector>, songIds: List<String>): FloatArray? {
        val vs = songIds.mapNotNull { vectors[it]?.vec }
        if (vs.isEmpty()) return null
        return SmartVectors.normalized(SmartVectors.mean(vs)!!)
    }

    /**
     * Greedy MMR ordering: picks maximise `lambda * relevance - (1 - lambda) * maxSimToPicked`,
     * never more than [maxArtistRun] songs by the same artist in a row.
     */
    fun mmr(
        candidates: List<SongVector>,
        relevance: (SongVector) -> Float,
        limit: Int,
        lambda: Float = 0.7f,
        maxArtistRun: Int = 2,
    ): List<SongVector> {
        if (candidates.isEmpty() || limit <= 0) return emptyList()
        val rel = FloatArray(candidates.size) { relevance(candidates[it]) }
        val maxSim = FloatArray(candidates.size) { 0f }
        val used = BooleanArray(candidates.size)
        val picked = ArrayList<SongVector>(limit)
        while (picked.size < limit) {
            val lastArtists = picked.takeLast(maxArtistRun).map { it.artist.lowercase() }
            val blockedArtist = if (lastArtists.size == maxArtistRun && lastArtists.distinct().size == 1) lastArtists.first() else null
            var best = -1
            var bestScore = Float.NEGATIVE_INFINITY
            var fallback = -1
            var fallbackScore = Float.NEGATIVE_INFINITY
            for (i in candidates.indices) {
                if (used[i]) continue
                val score = lambda * rel[i] - (1f - lambda) * (if (picked.isEmpty()) 0f else maxSim[i])
                if (blockedArtist != null && candidates[i].artist.lowercase() == blockedArtist) {
                    if (score > fallbackScore) { fallbackScore = score; fallback = i }
                    continue
                }
                if (score > bestScore) { bestScore = score; best = i }
            }
            val choice = if (best >= 0) best else fallback
            if (choice < 0) break
            used[choice] = true
            val chosen = candidates[choice]
            picked += chosen
            for (i in candidates.indices) {
                if (used[i]) continue
                val s = SmartVectors.dot(candidates[i].vec, chosen.vec)
                if (s > maxSim[i]) maxSim[i] = s
            }
        }
        return picked
    }

    /** Song radio: nearest neighbours of [seed] with MMR diversity. */
    fun similar(seed: SongVector, all: List<SongVector>, limit: Int, poolSize: Int = 300): List<SongVector> {
        val pool = all.asSequence()
            .filter { it.songId != seed.songId }
            .map { it to SmartVectors.dot(it.vec, seed.vec) }
            .sortedByDescending { it.second }
            .take(poolSize.coerceAtLeast(limit))
            .toList()
        val simById = pool.associate { it.first.songId to it.second }
        return mmr(pool.map { it.first }, { simById[it.songId] ?: 0f }, limit)
    }

    /** Deterministic k-means++ / Lloyd. Returns cluster index per vector and centroids. */
    fun kMeans(vectors: List<FloatArray>, k: Int, iterations: Int = 20, seed: Long = 42L): Pair<IntArray, List<FloatArray>> {
        val n = vectors.size
        if (n == 0) return IntArray(0) to emptyList()
        val kk = k.coerceIn(1, n)
        val rnd = java.util.Random(seed)
        val centroids = ArrayList<FloatArray>()
        centroids += vectors[rnd.nextInt(n)].copyOf()
        val dist = FloatArray(n) { Float.MAX_VALUE }
        while (centroids.size < kk) {
            var total = 0.0
            for (i in 0 until n) {
                val d = 1f - SmartVectors.dot(vectors[i], centroids.last())
                if (d < dist[i]) dist[i] = d.coerceAtLeast(0f)
                total += dist[i] * dist[i]
            }
            if (total <= 0.0) break
            var r = rnd.nextDouble() * total
            var chosen = n - 1
            for (i in 0 until n) {
                r -= dist[i] * dist[i]
                if (r <= 0) { chosen = i; break }
            }
            centroids += vectors[chosen].copyOf()
        }
        val assign = IntArray(n)
        repeat(iterations) {
            var changed = false
            for (i in 0 until n) {
                var best = 0
                var bestSim = Float.NEGATIVE_INFINITY
                for (c in centroids.indices) {
                    val s = SmartVectors.dot(vectors[i], centroids[c])
                    if (s > bestSim) { bestSim = s; best = c }
                }
                if (assign[i] != best) { assign[i] = best; changed = true }
            }
            for (c in centroids.indices) {
                val members = (0 until n).filter { assign[it] == c }.map { vectors[it] }
                if (members.isNotEmpty()) centroids[c] = SmartVectors.normalized(SmartVectors.mean(members)!!)
            }
            if (!changed && it > 0) return assign to centroids
        }
        return assign to centroids
    }

    /** "Your daily mix 1–3": clusters over liked songs, each topped up with close neighbours. */
    fun dailyMixes(liked: List<SongVector>, all: List<SongVector>, maxMixes: Int = 3): List<SmartMix> {
        if (liked.size < 6 || all.size < MIN_MIX) return emptyList()
        val k = (liked.size / 20).coerceIn(3, 6).coerceAtMost(liked.size / 2)
        val (assign, centroids) = kMeans(liked.map { it.vec }, k)
        val clusters = centroids.indices
            .map { c -> c to liked.filterIndexed { i, _ -> assign[i] == c } }
            .filter { it.second.size >= 3 }
            .sortedByDescending { it.second.size }
            .take(maxMixes)
        return clusters.mapIndexedNotNull { index, (c, members) ->
            val centroid = centroids[c]
            val memberIds = members.map { it.songId }.toSet()
            val neighbours = all.asSequence()
                .filter { it.songId !in memberIds }
                .map { it to SmartVectors.dot(it.vec, centroid) }
                .sortedByDescending { it.second }
                .take(MAX_MIX * 3)
                .map { it.first }
                .toList()
            val pool = (members + neighbours)
            // Liked members get a relevance bonus so they lead the mix.
            val songs = mmr(pool, { v -> SmartVectors.dot(v.vec, centroid) + if (v.songId in memberIds) 0.25f else 0f }, MAX_MIX, lambda = 0.8f)
            if (songs.size < MIN_MIX) return@mapIndexedNotNull null
            val mood = dominant(members.flatMap { it.moods }) ?: "Mixed"
            val artist = dominant(members.map { it.artist }.filter { it.isNotBlank() && !it.startsWith("<") }) ?: "your library"
            SmartMix(
                id = "smart_daily_${index + 1}",
                title = "Your daily mix ${index + 1}",
                subtitle = "$mood · $artist & more",
                songIds = songs.map { it.songId }
            )
        }
    }

    data class MoodMixSpec(val id: String, val title: String, val subtitle: String, val filter: (SongVector) -> Boolean)

    val moodMixSpecs: List<MoodMixSpec> = listOf(
        MoodMixSpec("smart_calm", "Calm", "Soft and slow, from your library") { SmartModel.MOOD_CALM in it.moods },
        MoodMixSpec("smart_upbeat", "Upbeat", "Bright, happy songs") {
            SmartModel.MOOD_UPBEAT in it.moods || SmartModel.MOOD_PARTY in it.moods
        },
        MoodMixSpec("smart_focus", "Focus", "Few vocals, steady energy") {
            val instrumentalGenre = it.genres.any { g -> g in setOf("Ambient", "Classical", "New age", "Soundtrack", "Electronic", "Jazz") }
            (it.vocal < 0.12f || instrumentalGenre && it.vocal < 0.25f) && it.energy in 0.15f..0.7f
        },
        MoodMixSpec("smart_workout", "Workout", "High energy, 120+ BPM") { it.energy >= 0.6f && it.tempo >= 120f },
        MoodMixSpec("smart_late_night", "Late night", "Low-key, tender and blue") {
            it.energy <= 0.4f && it.moods.any { m ->
                m == SmartModel.MOOD_CALM || m == SmartModel.MOOD_MELANCHOLY || m == SmartModel.MOOD_ROMANTIC
            }
        },
    )

    fun moodMixes(all: List<SongVector>, taste: FloatArray?): List<SmartMix> = moodMixSpecs.mapNotNull { spec ->
        val pool = all.filter(spec.filter)
        if (pool.size < MIN_MIX) return@mapNotNull null
        val songs = mmr(pool, { v -> taste?.let { SmartVectors.dot(v.vec, it) } ?: 0f }, MAX_MIX, lambda = if (taste != null) 0.6f else 0.3f)
        SmartMix(spec.id, spec.title, spec.subtitle, songs.map { it.songId })
    }

    /** Songs close to your taste that you have played at most once. */
    fun discover(all: List<SongVector>, taste: FloatArray?, playCounts: Map<String, Int>, limit: Int = 30): SmartMix? {
        if (taste == null) return null
        val pool = all.filter { (playCounts[it.songId] ?: 0) <= 1 }
            .map { it to SmartVectors.dot(it.vec, taste) }
            .filter { it.second > 0.05f }
            .sortedByDescending { it.second }
            .take(limit * 3)
        if (pool.size < MIN_MIX) return null
        val rel = pool.associate { it.first.songId to it.second }
        val songs = mmr(pool.map { it.first }, { rel[it.songId] ?: 0f }, limit.coerceIn(MIN_MIX, MAX_MIX))
        return SmartMix("smart_discover", "Discover in your library", "Close to your taste, barely played", songs.map { it.songId })
    }

    /** Share of listening per mood tag; a song with two tags splits its plays. */
    fun moodBreakdown(moodsBySong: Map<String, List<String>>, playCounts: Map<String, Int>): Map<String, Float> {
        val totals = LinkedHashMap<String, Float>()
        var sum = 0f
        playCounts.forEach { (id, count) ->
            val moods = moodsBySong[id].orEmpty()
            if (moods.isEmpty() || count <= 0) return@forEach
            moods.forEach { m -> totals[m] = (totals[m] ?: 0f) + count.toFloat() / moods.size }
            sum += count
        }
        if (sum <= 0f) return emptyMap()
        return totals.mapValues { it.value / sum }.entries.sortedByDescending { it.value }.associate { it.key to it.value }
    }

    /** Cosine distance (0..2) between two centroids. */
    fun drift(a: FloatArray?, b: FloatArray?): Float? {
        if (a == null || b == null) return null
        return (1f - SmartVectors.cosine(a, b)).coerceIn(0f, 2f)
    }

    fun driftLabel(distance: Float?): String = when {
        distance == null -> "Not enough listening yet"
        distance < 0.15f -> "Your taste stayed steady"
        distance < 0.4f -> "Your taste changed a little"
        else -> "Your taste changed a lot"
    }

    private fun <T> dominant(items: List<T>): T? = items.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}
