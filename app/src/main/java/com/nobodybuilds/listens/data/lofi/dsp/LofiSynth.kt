package com.nobodybuilds.listens.data.lofi.dsp

import com.nobodybuilds.listens.data.lofi.ChordColour
import com.nobodybuilds.listens.data.lofi.LofiMood
import com.nobodybuilds.listens.data.lofi.LofiSettings
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/** Something that fills stereo float buffers. Returns false once it has finished (faded out). */
interface StereoSource {
    fun render(left: FloatArray, right: FloatArray, frames: Int): Boolean
}

/**
 * The lofi generator: sequencer + instruments + textures + master bus, rendered sample by
 * sample. Plain Kotlin, deterministic from [LofiSettings.seed].
 *
 * Threading: [submit], [fadeIn], [fadeOut], [setDucked] may be called from any thread; they
 * only publish values that the render thread picks up at the start of the next block.
 */
class LofiSynth(
    private val sampleRate: Int = 44_100,
    initial: LofiSettings = LofiSettings(),
) : StereoSource {

    // ------------------------------------------------------------------ cross-thread inputs
    @Volatile private var pending: LofiSettings? = initial.normalized()
    @Volatile private var fadeTarget = 0f
    @Volatile private var fadeMs = 1500f
    @Volatile private var fadeSerial = 0
    @Volatile private var stopWhenSilent = false
    @Volatile private var ducked = false

    /** Output meters for visuals, updated once per block: 0 master, 1 kick, 2 snare, 3 hat, 4 bass, 5 keys. */
    val meters = FloatArray(6)

    fun submit(settings: LofiSettings) { pending = settings.normalized() }

    fun fadeIn(ms: Float = 1500f) {
        stopWhenSilent = false
        fadeTarget = 1f; fadeMs = ms; fadeSerial++
    }

    /** Fades to silence; render() returns false once silent. */
    fun fadeOut(ms: Float = 1200f) {
        stopWhenSilent = true
        fadeTarget = 0f; fadeMs = ms; fadeSerial++
    }

    fun setDucked(value: Boolean) { ducked = value }

    // ------------------------------------------------------------------ state (render thread)
    private var s: LofiSettings = initial.normalized()
    private var appliedFadeSerial = -1

    private val chordVoices = Array(12) { EpVoice(sampleRate) }
    private val melodyVoices = Array(4) { EpVoice(sampleRate) }
    private val noiseRng = FastRandom(initial.seed xor 0xD2)
    private val bass = BassVoice(sampleRate)
    private val kick = KickVoice(sampleRate, noiseRng)
    private val snare = SnareVoice(sampleRate, noiseRng)
    private val hat = HatVoice(sampleRate, noiseRng)
    private val crackle = VinylCrackle(sampleRate, initial.seed)
    private val rain = RainVoice(sampleRate, initial.seed)
    private val cafe = CafeVoice(sampleRate, initial.seed)

    private val bpm = SmoothedValue(s.tempoBpm.toFloat(), 1500f, sampleRate)
    private val swing = SmoothedValue(s.swing, 400f, sampleRate)
    private val drumsGain = SmoothedValue(drumsTarget(s), 150f, sampleRate)
    private val melodyGain = SmoothedValue(melodyTarget(s), 150f, sampleRate)
    private val crackleGain = SmoothedValue(s.crackle, 150f, sampleRate)
    private val rainGain = SmoothedValue(s.rain, 250f, sampleRate)
    private val cafeGain = SmoothedValue(s.cafe, 250f, sampleRate)
    private val wobble = SmoothedValue(s.wobble, 400f, sampleRate)
    private val warmth = SmoothedValue(s.warmth, 300f, sampleRate)
    private val master = SmoothedValue(s.masterVolume, 120f, sampleRate)
    private val duck = SmoothedValue(1f, 300f, sampleRate)
    private val outputFade = LinearRamp(0f, sampleRate)
    private val tuneFade = LinearRamp(1f, sampleRate)

    private val warmL1 = OnePoleLowPass(sampleRate, 8000f)
    private val warmL2 = OnePoleLowPass(sampleRate, 8000f)
    private val warmR1 = OnePoleLowPass(sampleRate, 8000f)
    private val warmR2 = OnePoleLowPass(sampleRate, 8000f)
    private val dcL = OnePoleLowPass(sampleRate, 20f)
    private val dcR = OnePoleLowPass(sampleRate, 20f)

    private var compEnv = 0f
    private val compAttack = 1f - decayCoef(0.003f, sampleRate)
    private val compRelease = 1f - decayCoef(0.25f, sampleRate)

    private var wobblePhase1 = 0f
    private var wobblePhase2 = 0f
    private var tremPhase = 0f
    private var controlCounter = 0
    private val masterLevel = LevelFollower(0.9f)

    // Sequencer
    private var gridPos = 0.0
    private var nextStep = 0L
    private var pendingReseed = false
    private var composedSeed = Long.MIN_VALUE
    private var composedMood: LofiMood? = null

    // Composition
    private var composeRng = FastRandom(1)
    private var progA = Progressions.IIV
    private var progB = Progressions.IViiiiiI
    private var kickPattern = KICKS[0]
    private var bassPattern = 0
    private var compA = COMPS[0]
    private var compB = COMPS[1]
    private var hatStyle = 0
    private var melodyDensity = 0.22f
    private var prevVoicing: IntArray? = null
    private var currentVoicing = IntArray(0)
    private var currentRoot = 0
    private var currentPcs = IntArray(0)
    private var currentComp = COMPS[0]
    private var barIndex = 0L
    private val melodyNote = IntArray(16) { -1 }
    private val melodyLen = IntArray(16)
    private val melodyVel = FloatArray(16)
    private var drumsThisBar = true
    private var kickThisBar = true
    private var fillThisBar = false

    private fun drumsTarget(x: LofiSettings) = if (x.drumsOn) x.drumsVolume else 0f
    private fun melodyTarget(x: LofiSettings) = if (x.melodyOn) x.melodyVolume else 0f

    // ------------------------------------------------------------------ render
    override fun render(left: FloatArray, right: FloatArray, frames: Int): Boolean {
        pending?.let { next ->
            pending = null
            applySettings(next)
        }
        val serial = fadeSerial
        if (serial != appliedFadeSerial) {
            appliedFadeSerial = serial
            outputFade.rampTo(fadeTarget, fadeMs)
        }
        duck.target = if (ducked) 0.3f else 1f

        var peak = 0f
        for (i in 0 until frames) {
            if (controlCounter-- <= 0) {
                controlCounter = 64
                updateControl()
            }
            if (pendingReseed && tuneFade.value <= 0f) {
                pendingReseed = false
                compose(s.seed, resetPosition = true)
                tuneFade.rampTo(1f, 700f)
            }

            // Sequencer clock (sixteenth steps, swing delays the odd ones).
            val stepsPerSample = bpm.next() / 60.0 * 4.0 / sampleRate
            gridPos += stepsPerSample
            val sw = swing.next() * 0.5
            while (true) {
                val offset = if ((nextStep and 1L) == 1L) sw else 0.0
                if (gridPos < nextStep + offset) break
                fireStep(nextStep)
                nextStep++
            }

            // Tape wobble: a slow wow plus a slower drift, up to ~±10 cents.
            val wDepth = wobble.next()
            wobblePhase1 = wrapPhase(wobblePhase1 + 0.55f / sampleRate)
            wobblePhase2 = wrapPhase(wobblePhase2 + 0.17f / sampleRate)
            val rate = 1f + wDepth * (0.0042f * SineTable.sin(wobblePhase1) + 0.0018f * SineTable.sin(wobblePhase2))

            // Keys with a gentle stereo tremolo.
            var kl = 0f
            var kr = 0f
            for (v in chordVoices) {
                if (!v.active) continue
                val x = v.process(rate) * 0.12f
                kl += x * v.panL
                kr += x * v.panR
            }
            tremPhase = wrapPhase(tremPhase + 4.2f / sampleRate)
            val trem = 0.1f * SineTable.sin(tremPhase)
            kl *= 1f + trem
            kr *= 1f - trem

            var ml = 0f
            var mr = 0f
            for (v in melodyVoices) {
                if (!v.active) continue
                val x = v.process(rate) * 0.16f
                ml += x * v.panL
                mr += x * v.panR
            }
            val mg = melodyGain.next()
            val b = bass.process(rate) * 0.34f
            val dg = drumsGain.next()
            val k = kick.process() * 0.55f
            val sn = snare.process() * 0.22f
            val h = hat.process() * 0.11f
            val drumsMono = (k + sn) * dg
            val music = b + drumsMono

            // The tiny offset keeps filter states out of the (slow) denormal range; the DC
            // blocker removes it again.
            var l = kl + ml * mg + music + h * dg * 0.8f + ANTI_DENORMAL
            var r = kr + mr * mg + music + h * dg * 1.1f + ANTI_DENORMAL

            // Warmth: two-pole low-pass and soft saturation on the music bus.
            l = warmL2.process(warmL1.process(l))
            r = warmR2.process(warmR1.process(r))
            val drive = 1f + warmth.value * 1.4f
            l = softClip(l * drive) / drive
            r = softClip(r * drive) / drive

            // Textures.
            val cg = crackleGain.next()
            if (cg > 0.0005f) { crackle.process(); l += crackle.l * cg; r += crackle.r * cg }
            val rg = rainGain.next()
            if (rg > 0.0005f) { rain.process(); l += rain.l * rg * 1.6f; r += rain.r * rg * 1.6f }
            val cfg = cafeGain.next()
            if (cfg > 0.0005f) { cafe.process(); l += cafe.l * cfg * 0.9f; r += cafe.r * cfg * 0.9f }

            // DC block.
            l -= dcL.process(l)
            r -= dcR.process(r)

            // Compressor (peak follower, 3:1 above 0.45).
            val p = max(abs(l), abs(r))
            compEnv += (p - compEnv) * (if (p > compEnv) compAttack else compRelease) + ANTI_DENORMAL
            val gr = if (compEnv > 0.45f) (0.45f + (compEnv - 0.45f) / 3f) / compEnv else 1f
            val g = gr * master.next() * duck.next() * outputFade.next() * tuneFade.next()
            l = softClip(l * g * 2.1f) * 0.92f
            r = softClip(r * g * 2.1f) * 0.92f
            left[i] = l
            right[i] = r
            val ap = max(abs(l), abs(r))
            if (ap > peak) peak = ap
        }
        masterLevel.push(peak)
        meters[0] = masterLevel.level
        meters[1] = kick.amp * drumsGain.value
        meters[2] = snare.amp * drumsGain.value
        meters[3] = hat.amp * drumsGain.value
        meters[4] = bass.amp
        var keys = 0f
        for (v in chordVoices) if (v.active && v.amp > keys) keys = v.amp
        for (v in melodyVoices) if (v.active && v.amp * melodyGain.value > keys) keys = v.amp * melodyGain.value
        meters[5] = keys

        return !(stopWhenSilent && outputFade.isDone && outputFade.value <= 0f)
    }

    private fun updateControl() {
        val w = warmth.advance(64)
        val moodTone = when (s.mood) {
            LofiMood.Sunny -> 1.25f
            LofiMood.Rainy -> 0.8f
            else -> 1f
        }
        val cutoff = 15_000f * 0.14f.pow(w) * moodTone
        warmL1.setCutoff(cutoff); warmL2.setCutoff(cutoff)
        warmR1.setCutoff(cutoff); warmR2.setCutoff(cutoff)
    }

    private fun applySettings(next: LofiSettings) {
        val old = s
        s = next
        bpm.target = next.tempoBpm.toFloat()
        swing.target = next.swing
        drumsGain.target = drumsTarget(next)
        melodyGain.target = melodyTarget(next)
        crackleGain.target = next.crackle
        rainGain.target = next.rain
        cafeGain.target = next.cafe
        wobble.target = next.wobble
        warmth.target = next.warmth
        master.target = next.masterVolume
        if (composedSeed == Long.MIN_VALUE) {
            compose(next.seed, resetPosition = true)
            bpm.snap(next.tempoBpm.toFloat())
            swing.snap(next.swing)
            drumsGain.snap(); melodyGain.snap(); crackleGain.snap(); rainGain.snap(); cafeGain.snap()
            wobble.snap(); warmth.snap(); master.snap()
        } else if (next.seed != old.seed) {
            pendingReseed = true
            tuneFade.rampTo(0f, 350f)
        }
        // Key, colour and mood changes are picked up at the next bar line.
    }

    // ------------------------------------------------------------------ composition
    private fun compose(seed: Long, resetPosition: Boolean) {
        composedSeed = seed
        composedMood = s.mood
        composeRng = FastRandom(seed * 31 + s.mood.ordinal)
        val list = Progressions.forMood(s.mood)
        val a = composeRng.nextInt(list.size)
        val b = (a + 1 + composeRng.nextInt(list.size - 1)) % list.size
        progA = list[a]
        progB = list[b]
        kickPattern = KICKS[composeRng.nextInt(KICKS.size)]
        bassPattern = composeRng.nextInt(BASS_PATTERNS)
        compA = COMPS[composeRng.nextInt(COMPS.size)]
        compB = COMPS[composeRng.nextInt(COMPS.size)]
        hatStyle = composeRng.nextInt(3)
        melodyDensity = when (s.mood) {
            LofiMood.Dreamy -> 0.2f
            LofiMood.Rainy -> 0.18f
            LofiMood.Jazzy -> 0.27f
            LofiMood.Sunny -> 0.3f
        }
        if (resetPosition) {
            for (v in chordVoices) v.release()
            for (v in melodyVoices) v.release()
            bass.release()
            prevVoicing = null
            gridPos = -0.5 // a short breath before the first downbeat
            nextStep = 0L
        }
    }

    private fun fireStep(step: Long) {
        val inBar = (step and 15L).toInt()
        if (inBar == 0) startBar(step shr 4)

        // Releases scheduled for this step.
        for (v in melodyVoices) if (v.active && v.releaseStep == step) v.release()

        // Keys.
        val compVel = currentComp[inBar]
        if (compVel > 0f) {
            for (v in chordVoices) v.release()
            val strum = if (inBar == 0) 0.012f else 0.006f
            val brightness = when (s.chordColour) {
                ChordColour.Simple -> 0.35f
                ChordColour.Jazzy -> 0.5f
                ChordColour.Lush -> 0.42f
            }
            val decaySec = if (inBar == 0) 3.6f else 1.6f
            for ((n, note) in currentVoicing.withIndex()) {
                val voice = freeVoice(chordVoices)
                val delay = (sampleRate * (strum * n + composeRng.nextFloat() * 0.006f)).toInt()
                val pan = 0.3f + 0.4f * n / max(1, currentVoicing.size - 1)
                val vel = compVel * (0.75f + composeRng.nextFloat() * 0.2f)
                voice.trigger(note, vel, delay, pan, brightness, decaySec)
            }
        }

        // Bass.
        val bassInterval = bassStep(bassPattern, inBar)
        if (bassInterval != Int.MIN_VALUE) {
            val delay = (sampleRate * composeRng.nextFloat() * 0.008f).toInt()
            bass.trigger(currentRoot + bassInterval, if (inBar == 0) 1f else 0.8f, delay)
        }

        // Drums.
        if (drumsThisBar) {
            if (kickThisBar && kickPattern[inBar]) kick.trigger(if (inBar == 0) 1f else 0.85f)
            if (inBar == 4 || inBar == 12) snare.trigger(0.9f + composeRng.nextFloat() * 0.1f)
            if (fillThisBar && inBar >= 13) snare.trigger(0.3f + 0.12f * (inBar - 13))
            when (hatStyle) {
                0 -> if (inBar % 2 == 0) hat.trigger(if (inBar % 4 == 0) 0.9f else 0.55f, false)
                1 -> if (inBar % 2 == 0) hat.trigger(if (inBar % 4 == 0) 0.85f else 0.6f, false)
                     else if (composeRng.chance(0.2f)) hat.trigger(0.3f, false)
                else -> if (inBar % 4 == 2) hat.trigger(0.7f, inBar == 14 && composeRng.chance(0.5f))
                        else if (inBar % 4 == 0) hat.trigger(0.5f, false)
            }
        }

        // Melody.
        val mNote = melodyNote[inBar]
        if (mNote >= 0) {
            val voice = freeVoice(melodyVoices)
            for (v in melodyVoices) if (v !== voice && v.active) v.release()
            val delay = (sampleRate * composeRng.nextFloat() * 0.014f).toInt()
            voice.trigger(mNote, melodyVel[inBar], delay, 0.45f + composeRng.nextFloat() * 0.2f, 0.75f, 1.6f)
            voice.releaseStep = step + melodyLen[inBar]
        }
    }

    private fun startBar(bar: Long) {
        barIndex = bar
        if (s.mood != composedMood) compose(s.seed, resetPosition = false)
        val section = ((bar / 8) % 2).toInt()
        val prog = if (section == 0) progA else progB
        val chord = prog.chords[(bar % 4).toInt()]
        val rootless = s.chordColour != ChordColour.Simple
        currentPcs = VoiceLeading.pitchClasses(s.key, chord, s.chordColour, dropRoot = rootless)
        currentVoicing = VoiceLeading.voice(currentPcs, prevVoicing)
        prevVoicing = currentVoicing
        val rootPc = ((s.key + chord.degree) % 12 + 12) % 12
        currentRoot = 36 + rootPc
        if (currentRoot > 43) currentRoot -= 12
        currentRoot += 12 // C3-ish range so phones can hear it; harmonic helps too
        currentComp = if (section == 0) compA else compB

        val inCycle = (bar % 16).toInt()
        drumsThisBar = bar > 0
        kickThisBar = inCycle != 12
        fillThisBar = inCycle % 8 == 7
        planMelody(bar)
    }

    private fun planMelody(bar: Long) {
        for (i in 0 until 16) { melodyNote[i] = -1; melodyLen[i] = 0 }
        if (bar < 2) return
        val phraseBar = (bar % 4).toInt()
        val section = ((bar / 8) % 2).toInt()
        val variation = (bar % 8) >= 4
        val rng = FastRandom(s.seed * 1_000_003L + phraseBar * 7919L + section * 104_729L + s.mood.ordinal)
        if (rng.chance(0.28f)) return // breathing room

        val scale = Progressions.melodyScale(s.mood)
        // Pentatonic notes between G4 and C6.
        var count = 0
        for (midi in 67..84) {
            val rel = ((midi - s.key) % 12 + 12) % 12
            if (rel in scale) scaleBuf[count++] = midi
        }
        if (count == 0) return
        var idx = (count / 2 + rng.nextInt(3) - 1).coerceIn(0, count - 1)
        var step = 0
        var lastStart = -1
        while (step < 16) {
            val p = if (step % 2 == 0) melodyDensity else melodyDensity * 0.35f
            if (rng.chance(p)) {
                val moves = MELODY_MOVES[rng.nextInt(MELODY_MOVES.size)]
                idx = (idx + moves).coerceIn(0, count - 1)
                var note = scaleBuf[idx]
                if (step % 4 == 0) note = nearestChordTone(note)
                val len = 2 + rng.nextInt(5)
                melodyNote[step] = note
                melodyLen[step] = len
                melodyVel[step] = 0.55f + rng.nextFloat() * 0.35f
                lastStart = step
                step += len
            } else {
                step++
            }
        }
        if (variation && lastStart >= 0) {
            val vr = FastRandom(s.seed + bar)
            if (vr.chance(0.6f)) {
                val shifted = (idx + if (vr.chance(0.5f)) 1 else -1).coerceIn(0, count - 1)
                melodyNote[lastStart] = scaleBuf[shifted]
            }
        }
    }

    private val scaleBuf = IntArray(24)

    private fun nearestChordTone(note: Int): Int {
        var best = note
        var bestDist = 99
        for (d in -2..2) {
            val pc = ((note + d) % 12 + 12) % 12
            if (pc in currentPcs || pc == ((currentRoot % 12) + 12) % 12) {
                if (abs(d) < bestDist) { bestDist = abs(d); best = note + d }
            }
        }
        return best
    }

    private fun freeVoice(pool: Array<EpVoice>): EpVoice {
        var quietest = pool[0]
        for (v in pool) {
            if (!v.active) return v
            if (v.amp < quietest.amp) quietest = v
        }
        return quietest
    }

    private companion object {
        const val ANTI_DENORMAL = 1e-9f
        val MELODY_MOVES = intArrayOf(-2, -1, -1, 0, 1, 1, 2, -3, 3)

        fun steps(vararg on: Int) = BooleanArray(16) { it in on }
        fun comp(vararg pairs: Pair<Int, Float>) = FloatArray(16).also { a -> pairs.forEach { a[it.first] = it.second } }

        val KICKS = arrayOf(
            steps(0, 10),
            steps(0, 7, 10),
            steps(0, 3, 10),
            steps(0, 8, 11),
            steps(0, 6, 10, 14),
        )
        val COMPS = arrayOf(
            comp(0 to 1f),
            comp(0 to 1f, 10 to 0.6f),
            comp(0 to 1f, 6 to 0.5f, 11 to 0.6f),
            comp(0 to 1f, 8 to 0.65f),
            comp(0 to 1f, 14 to 0.55f),
        )
        const val BASS_PATTERNS = 4

        /** Interval above the bar's root for the bass at [inBar], or MIN_VALUE for none. */
        fun bassStep(pattern: Int, inBar: Int): Int = when (pattern) {
            0 -> when (inBar) { 0 -> 0; 10 -> 0; else -> Int.MIN_VALUE }
            1 -> when (inBar) { 0 -> 0; 7 -> 7; 10 -> 0; else -> Int.MIN_VALUE }
            2 -> when (inBar) { 0 -> 0; 6 -> 0; 14 -> 7; else -> Int.MIN_VALUE }
            else -> when (inBar) { 0 -> 0; 11 -> 12; 14 -> 7; else -> Int.MIN_VALUE }
        }
    }
}
