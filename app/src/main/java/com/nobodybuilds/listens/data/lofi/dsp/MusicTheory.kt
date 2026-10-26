package com.nobodybuilds.listens.data.lofi.dsp

import com.nobodybuilds.listens.data.lofi.ChordColour
import com.nobodybuilds.listens.data.lofi.LofiMood
import kotlin.math.abs

/** Chord qualities with their interval sets (semitones above the root) per colour. */
enum class ChordQuality(
    private val simple: IntArray,
    private val jazzy: IntArray,
    private val lush: IntArray,
    val symbol: String,
) {
    Maj7(intArrayOf(0, 4, 7, 11), intArrayOf(0, 4, 7, 11, 14), intArrayOf(0, 4, 11, 14, 21), "maj7"),
    Min7(intArrayOf(0, 3, 7, 10), intArrayOf(0, 3, 7, 10, 14), intArrayOf(0, 3, 10, 14, 17), "m7"),
    Dom7(intArrayOf(0, 4, 7, 10), intArrayOf(0, 4, 10, 14), intArrayOf(0, 4, 10, 14, 21), "7"),
    HalfDim(intArrayOf(0, 3, 6, 10), intArrayOf(0, 3, 6, 10, 14), intArrayOf(0, 3, 6, 10, 17), "m7♭5"),
    Sus7(intArrayOf(0, 5, 7, 10), intArrayOf(0, 5, 10, 14), intArrayOf(0, 5, 10, 14, 21), "7sus");

    fun intervals(colour: ChordColour): IntArray = when (colour) {
        ChordColour.Simple -> simple
        ChordColour.Jazzy -> jazzy
        ChordColour.Lush -> lush
    }
}

/** A chord as a scale degree: [degree] is semitones above the tonic. */
data class ChordStep(val degree: Int, val quality: ChordQuality)

data class Progression(val name: String, val minor: Boolean, val chords: List<ChordStep>)

object Progressions {
    private fun c(d: Int, q: ChordQuality) = ChordStep(d, q)
    private val M7 = ChordQuality.Maj7
    private val m7 = ChordQuality.Min7
    private val D7 = ChordQuality.Dom7
    private val hd = ChordQuality.HalfDim

    val iiVIvi = Progression("ii–V–I–vi", false, listOf(c(2, m7), c(7, D7), c(0, M7), c(9, m7)))
    val IviiiV = Progression("I–vi–ii–V", false, listOf(c(0, M7), c(9, m7), c(2, m7), c(7, D7)))
    val IViiiiiI = Progression("IV–iii–ii–I", false, listOf(c(5, M7), c(4, m7), c(2, m7), c(0, M7)))
    val IVViiivi = Progression("IV–V–iii–vi", false, listOf(c(5, M7), c(7, D7), c(4, m7), c(9, m7)))
    val IIV = Progression("I–IV", false, listOf(c(0, M7), c(5, M7), c(0, M7), c(5, M7)))
    val iiiviiiV = Progression("iii–vi–ii–V", false, listOf(c(4, m7), c(9, m7), c(2, m7), c(7, D7)))
    val iiVII = Progression("ii–V–I–I", false, listOf(c(2, m7), c(7, D7), c(0, M7), c(0, M7)))

    val iivVIIIII = Progression("i–iv–♭VII–♭III", true, listOf(c(0, m7), c(5, m7), c(10, D7), c(3, M7)))
    val iiVi = Progression("ii°–V–i–i", true, listOf(c(2, hd), c(7, D7), c(0, m7), c(0, m7)))
    val iVIivV = Progression("i–♭VI–iv–V", true, listOf(c(0, m7), c(8, M7), c(5, m7), c(7, D7)))
    val VIviv = Progression("♭VI–v–iv–i", true, listOf(c(8, M7), c(7, m7), c(5, m7), c(0, m7)))
    val iiv = Progression("i–iv", true, listOf(c(0, m7), c(5, m7), c(0, m7), c(5, ChordQuality.Sus7)))

    fun forMood(mood: LofiMood): List<Progression> = when (mood) {
        LofiMood.Dreamy -> listOf(IIV, IViiiiiI, IVViiivi, IviiiV)
        LofiMood.Rainy -> listOf(iivVIIIII, iiVi, iVIivV, VIviv, iiv)
        LofiMood.Jazzy -> listOf(iiVIvi, IviiiV, iiiviiiV, iiVII)
        LofiMood.Sunny -> listOf(IVViiivi, IviiiV, IViiiiiI, iiVIvi)
    }

    /** Melody scale (pentatonic) for the mood, as semitones above the tonic. */
    fun melodyScale(mood: LofiMood): IntArray = when (mood) {
        LofiMood.Rainy -> intArrayOf(0, 3, 5, 7, 10)
        else -> intArrayOf(0, 2, 4, 7, 9)
    }
}

object VoiceLeading {
    const val DEFAULT_LOW = 50   // D3
    const val DEFAULT_HIGH = 76  // E5

    /**
     * Pitch classes (0..11) for a chord in a key, deduplicated, in interval order. When
     * [dropRoot] is set and the chord has five or more tones, the root is left to the bass
     * (rootless voicing).
     */
    fun pitchClasses(key: Int, step: ChordStep, colour: ChordColour, dropRoot: Boolean): IntArray {
        val intervals = step.quality.intervals(colour)
        val root = key + step.degree
        val out = ArrayList<Int>(intervals.size)
        for (iv in intervals) {
            if (dropRoot && intervals.size >= 5 && iv == 0) continue
            val pc = ((root + iv) % 12 + 12) % 12
            if (pc !in out) out.add(pc)
        }
        return out.toIntArray()
    }

    /** Close-position voicing of [pcs] built up from [pcs][rotation] starting at or above [base]. */
    internal fun stack(pcs: IntArray, rotation: Int, base: Int): IntArray {
        val n = pcs.size
        val notes = IntArray(n)
        var prev = base - 1
        for (i in 0 until n) {
            val pc = pcs[(rotation + i) % n]
            var note = prev + 1
            while (((note % 12) + 12) % 12 != pc) note++
            notes[i] = note
            prev = note
        }
        return notes
    }

    /**
     * Picks the voicing of [pcs] inside [low]..[high] that moves least from [previous]
     * (nearest-note distance in both directions). Without a previous chord it centres on the
     * middle of the range. The result is sorted ascending.
     */
    fun voice(pcs: IntArray, previous: IntArray?, low: Int = DEFAULT_LOW, high: Int = DEFAULT_HIGH): IntArray {
        require(pcs.isNotEmpty())
        val sorted = pcs.sortedArray()
        var best: IntArray? = null
        var bestCost = Float.MAX_VALUE
        val centre = (low + high) / 2f
        for (rotation in sorted.indices) {
            for (base in low..(low + 11)) {
                if (((base % 12) + 12) % 12 != sorted[rotation]) continue
                for (octave in 0..1) {
                    val cand = stack(sorted, rotation, base + octave * 12)
                    val top = cand.last()
                    val rangePenalty = if (top > high) (top - high) * 4f else 0f
                    if (cand.first() < low) continue
                    val cost = if (previous == null || previous.isEmpty()) {
                        abs(cand.average().toFloat() - centre)
                    } else {
                        movement(previous, cand)
                    } + rangePenalty
                    if (cost < bestCost) {
                        bestCost = cost
                        best = cand
                    }
                }
            }
        }
        return (best ?: stack(sorted, 0, low)).sortedArray()
    }

    /** Symmetric nearest-neighbour distance between two voicings, in semitones. */
    fun movement(a: IntArray, b: IntArray): Float {
        var total = 0f
        for (x in b) total += a.minOf { abs(it - x) }
        for (x in a) total += b.minOf { abs(it - x) }
        return total / 2f
    }
}
