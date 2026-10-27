package com.nobodybuilds.listens.data.covers

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pattern families the studio offers. */
enum class CoverStyle(val label: String) {
    SHAPES("Shapes"),
    STRIPES("Stripes"),
    DOTS("Dots"),
    WAVES("Waves"),
    ORBIT("Orbit"),
    BLOCKS("Blocks"),
}

// ---------------------------------------------------------------- design model (canvas units: 0..1000)

sealed interface CoverShape {
    val color: Int
}

data class CoverRect(val left: Float, val top: Float, val right: Float, val bottom: Float, override val color: Int) : CoverShape
data class CoverCircle(val cx: Float, val cy: Float, val radius: Float, override val color: Int) : CoverShape

/** A filled pie slice (semicircles, quarter circles). Angles in degrees, clockwise from 3 o'clock. */
data class CoverArc(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val startAngle: Float,
    val sweepAngle: Float,
    override val color: Int,
) : CoverShape

/** A closed polygon, as x0, y0, x1, y1, … */
data class CoverPolygon(val points: List<Float>, override val color: Int) : CoverShape

data class CoverFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** Text drawn under the pattern, bottom-left. Sizing happens in the renderer (it needs font metrics). */
data class CoverText(
    val text: String,
    val color: Int,
    val left: Float,
    val bottom: Float,
    val maxWidth: Float,
    val maxHeight: Float,
)

/**
 * Everything needed to draw one cover. Pure data: equal inputs give an equal design, so
 * the preview and the saved file are always the same picture.
 */
data class CoverDesign(
    val seed: Long,
    val style: CoverStyle,
    val palette: ResolvedPalette,
    /** Shapes are clipped to this area; outside it is plain background. */
    val frame: CoverFrame,
    val shapes: List<CoverShape>,
    val text: CoverText?,
)

/** What the user picked for one cover. [style] null means "Mixed" (picked per album). */
data class CoverRequest(
    /** Album name, or the title when there is no album. Drives the seed. */
    val key: String,
    /** Text printed on the cover when [showText] is on. */
    val label: String = key,
    val style: CoverStyle? = CoverStyle.SHAPES,
    val palette: CoverPaletteChoice = CoverPaletteChoice.AUTO,
    val variant: Int = 0,
    val showText: Boolean = true,
)

// ---------------------------------------------------------------- seeds

/** Small, fast, fully specified PRNG (SplitMix64) so covers never change between app versions. */
class SeededRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** Uniform in [0, 1). */
    fun nextFloat(): Float = ((nextLong() ushr 40).toFloat() / (1L shl 24).toFloat())

    fun nextFloat(from: Float, until: Float): Float = from + (until - from) * nextFloat()

    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }

    fun nextInt(from: Int, untilInclusive: Int): Int = from + nextInt(untilInclusive - from + 1)

    fun nextBoolean(probability: Float = 0.5f): Boolean = nextFloat() < probability

    fun <T> pick(items: List<T>): T = items[nextInt(items.size)]
}

object CoverSeed {
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val FNV_PRIME = 0x100000001b3L

    /** Stable 64-bit hash of the album name / title (case and outer spaces don't matter). */
    fun hash(key: String): Long {
        var h = FNV_OFFSET
        key.trim().lowercase().encodeToByteArray().forEach { b ->
            h = h xor (b.toLong() and 0xFF)
            h *= FNV_PRIME
        }
        return h
    }

    fun of(key: String, style: CoverStyle, variant: Int): Long =
        mix(hash(key) xor (style.ordinal.toLong() * 0x51_7CC1_B727_220AL) xor (variant.toLong() * 0x2545_F491_4F6C_DD1DL))

    /** Which style "Mixed" gives this album (changes with the variant). */
    fun mixedStyle(key: String, variant: Int): CoverStyle {
        val r = SeededRandom(mix(hash(key) + variant * 7919L))
        return CoverStyle.entries[r.nextInt(CoverStyle.entries.size)]
    }

    private fun mix(value: Long): Long = SeededRandom(value).nextLong()
}

// ---------------------------------------------------------------- generator

object CoverGenerator {
    const val SIZE = 1000f
    private const val MARGIN = 84f
    private const val TEXT_TOP = 716f

    fun design(request: CoverRequest): CoverDesign {
        val style = request.style ?: CoverSeed.mixedStyle(request.key, request.variant)
        val seed = CoverSeed.of(request.key, style, request.variant)
        val rng = SeededRandom(seed)
        val palette = CoverPalettes.resolve(request.palette, request.key, rng)
        val label = request.label.trim()
        val withText = request.showText && label.isNotEmpty()

        val bleedable = style == CoverStyle.STRIPES || style == CoverStyle.WAVES ||
            style == CoverStyle.BLOCKS || style == CoverStyle.ORBIT
        val bleed = bleedable && rng.nextBoolean(0.5f)
        val frame = when {
            bleed && withText -> CoverFrame(0f, 0f, SIZE, TEXT_TOP - 12f)
            bleed -> CoverFrame(0f, 0f, SIZE, SIZE)
            withText -> CoverFrame(MARGIN, MARGIN, SIZE - MARGIN, TEXT_TOP - 28f)
            else -> CoverFrame(MARGIN, MARGIN, SIZE - MARGIN, SIZE - MARGIN)
        }

        val shapes = when (style) {
            CoverStyle.SHAPES -> shapes(frame, palette, rng)
            CoverStyle.STRIPES -> stripes(frame, palette, rng)
            CoverStyle.DOTS -> dots(frame, palette, rng)
            CoverStyle.WAVES -> waves(frame, palette, rng)
            CoverStyle.ORBIT -> orbit(frame, palette, rng)
            CoverStyle.BLOCKS -> blocks(frame, palette, rng)
        }

        val text = if (withText) {
            CoverText(
                text = label,
                color = CoverContrast.textColorOn(palette.background),
                left = MARGIN,
                bottom = SIZE - MARGIN + 8f,
                maxWidth = SIZE - 2 * MARGIN,
                maxHeight = SIZE - MARGIN + 8f - TEXT_TOP - 8f,
            )
        } else null

        return CoverDesign(seed = seed, style = style, palette = palette, frame = frame, shapes = shapes, text = text)
    }

    // -- Shapes: a Bauhaus grid of bold circles, half circles, quarters and blocks.
    private fun shapes(frame: CoverFrame, p: ResolvedPalette, rng: SeededRandom): List<CoverShape> {
        val cols = rng.pick(listOf(2, 3, 3, 4))
        val cw = frame.width / cols
        val rows = max(1, (frame.height / cw).roundToInt())
        val ch = frame.height / rows
        val out = mutableListOf<CoverShape>()
        val cellColors = listOf(p.background, p.primary, p.secondary)
        val weights = floatArrayOf(0.42f, 0.33f, 0.25f)
        var previous = -1
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val l = frame.left + col * cw
                val t = frame.top + row * ch
                val r = l + cw
                val b = t + ch
                var bgIndex = weighted(rng, weights)
                if (bgIndex == previous && bgIndex != 0) bgIndex = 0
                previous = bgIndex
                val cellBg = cellColors[bgIndex]
                val others = cellColors.indices.filter { it != bgIndex }
                val motifColor = cellColors[if (rng.nextBoolean(0.6f)) others[0] else others[1]]
                if (cellBg != p.background) out += CoverRect(l, t, r, b, cellBg)
                val cx = (l + r) / 2f
                val cy = (t + b) / 2f
                when (rng.nextInt(7)) {
                    0, 1 -> out += CoverCircle(cx, cy, min(cw, ch) / 2f, motifColor)
                    2 -> { // half circle on one edge
                        when (rng.nextInt(4)) {
                            0 -> out += CoverArc(cx, t, min(cw / 2f, ch), 0f, 180f, motifColor)
                            1 -> out += CoverArc(cx, b, min(cw / 2f, ch), 180f, 180f, motifColor)
                            2 -> out += CoverArc(l, cy, min(ch / 2f, cw), 270f, 180f, motifColor)
                            else -> out += CoverArc(r, cy, min(ch / 2f, cw), 90f, 180f, motifColor)
                        }
                    }
                    3 -> { // quarter circle from a corner
                        val radius = min(cw, ch)
                        when (rng.nextInt(4)) {
                            0 -> out += CoverArc(l, t, radius, 0f, 90f, motifColor)
                            1 -> out += CoverArc(r, t, radius, 90f, 90f, motifColor)
                            2 -> out += CoverArc(r, b, radius, 180f, 90f, motifColor)
                            else -> out += CoverArc(l, b, radius, 270f, 90f, motifColor)
                        }
                    }
                    4 -> { // half block
                        if (rng.nextBoolean()) out += CoverRect(l, t, cx, b, motifColor)
                        else out += CoverRect(l, cy, r, b, motifColor)
                    }
                    5 -> { // triangle across the diagonal
                        out += if (rng.nextBoolean()) CoverPolygon(listOf(l, t, r, t, l, b), motifColor)
                        else CoverPolygon(listOf(r, t, r, b, l, b), motifColor)
                    }
                    else -> { // small circle for breathing room
                        out += CoverCircle(cx, cy, min(cw, ch) * 0.22f, motifColor)
                    }
                }
            }
        }
        return out
    }

    // -- Stripes: one direction, a rhythm of varied widths.
    private fun stripes(frame: CoverFrame, p: ResolvedPalette, rng: SeededRandom): List<CoverShape> {
        val angleDeg = rng.pick(listOf(45f, -45f, 30f, -60f, 0f, 90f))
        val a = angleDeg / 180f * PI.toFloat()
        val dx = cos(a)
        val dy = sin(a)
        val nx = -dy
        val ny = dx
        val cx = frame.centerX
        val cy = frame.centerY
        val half = hypot(frame.width, frame.height) / 2f + 4f
        val widths = listOf(22f, 36f, 56f, 90f, 140f)
        val colors = if (rng.nextBoolean()) listOf(p.primary, p.secondary) else listOf(p.primary, p.primary, p.secondary)
        val out = mutableListOf<CoverShape>()
        var u = -half + rng.nextFloat(0f, 60f)
        var i = 0
        while (u < half) {
            val w = rng.pick(widths)
            val gap = rng.pick(widths.take(3)) * (if (rng.nextBoolean(0.2f)) 2.5f else 1f)
            val u0 = u
            val u1 = u + w
            out += CoverPolygon(
                listOf(
                    cx + u0 * nx - half * dx, cy + u0 * ny - half * dy,
                    cx + u0 * nx + half * dx, cy + u0 * ny + half * dy,
                    cx + u1 * nx + half * dx, cy + u1 * ny + half * dy,
                    cx + u1 * nx - half * dx, cy + u1 * ny - half * dy,
                ),
                colors[i % colors.size],
            )
            u = u1 + gap
            i++
        }
        return out
    }

    // -- Dots: halftone grid whose dot size follows a gradient.
    private fun dots(frame: CoverFrame, p: ResolvedPalette, rng: SeededRandom): List<CoverShape> {
        val out = mutableListOf<CoverShape>()
        val cols = rng.nextInt(9, 13)
        val sx = frame.width / cols
        val rows = max(1, (frame.height / sx).roundToInt())
        val sy = frame.height / rows
        val cell = min(sx, sy)
        if (rng.nextBoolean(0.5f)) {
            // One large disc behind the dots gives the third colour a place.
            val r = frame.width * rng.nextFloat(0.22f, 0.34f)
            out += CoverCircle(
                frame.left + frame.width * rng.nextFloat(0.3f, 0.7f),
                frame.top + frame.height * rng.nextFloat(0.3f, 0.7f),
                r,
                p.secondary,
            )
        }
        val radial = rng.nextBoolean(0.45f)
        val gx = frame.left + frame.width * rng.nextFloat(0f, 1f)
        val gy = frame.top + frame.height * rng.nextFloat(0f, 1f)
        val angle = rng.nextFloat(0f, 2f * PI.toFloat())
        val ux = cos(angle)
        val uy = sin(angle)
        val maxDist = hypot(frame.width, frame.height)
        val invert = rng.nextBoolean()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = frame.left + (col + 0.5f) * sx
                val y = frame.top + (row + 0.5f) * sy
                var t = if (radial) {
                    (hypot(x - gx, y - gy) / (maxDist * 0.75f)).coerceIn(0f, 1f)
                } else {
                    val proj = (x - frame.centerX) * ux + (y - frame.centerY) * uy
                    ((proj / (maxDist / 2f)) * 0.5f + 0.5f).coerceIn(0f, 1f)
                }
                if (invert) t = 1f - t
                val eased = t * t * (3f - 2f * t)
                val radius = cell * 0.5f * (0.1f + 0.86f * eased)
                if (radius > 1.5f) out += CoverCircle(x, y, radius, p.primary)
            }
        }
        return out
    }

    // -- Waves: layered sine bands.
    private fun waves(frame: CoverFrame, p: ResolvedPalette, rng: SeededRandom): List<CoverShape> {
        val out = mutableListOf<CoverShape>()
        val bands = rng.nextInt(4, 6)
        val freq = rng.pick(listOf(1f, 1.5f, 2f))
        val amp = frame.height * rng.nextFloat(0.035f, 0.07f)
        val phase0 = rng.nextFloat(0f, 2f * PI.toFloat())
        val drift = rng.nextFloat(0.4f, 1.1f)
        val cycle = listOf(p.primary, p.secondary, p.background)
        val start = rng.nextInt(2)
        val steps = 60
        for (i in 0 until bands) {
            val base = frame.top + frame.height * (i + 1f) / (bands + 1f) - amp
            val a = amp * rng.nextFloat(0.75f, 1.25f)
            val phase = phase0 + i * drift
            val pts = ArrayList<Float>((steps + 3) * 2)
            for (s in 0..steps) {
                val x = frame.left + frame.width * s / steps
                val y = base + a * sin(2f * PI.toFloat() * freq * (s.toFloat() / steps) + phase)
                pts += x; pts += y
            }
            pts += frame.right; pts += frame.bottom + 2f
            pts += frame.left; pts += frame.bottom + 2f
            out += CoverPolygon(pts, cycle[(start + i) % cycle.size])
        }
        return out
    }

    // -- Orbit: concentric rings around an off-centre point, with one small moon.
    private fun orbit(frame: CoverFrame, p: ResolvedPalette, rng: SeededRandom): List<CoverShape> {
        val out = mutableListOf<CoverShape>()
        val angle = rng.nextFloat(0f, 2f * PI.toFloat())
        val offset = min(frame.width, frame.height) * rng.nextFloat(0.08f, 0.32f)
        val cx = frame.centerX + cos(angle) * offset
        val cy = frame.centerY + sin(angle) * offset
        val farthest = listOf(
            hypot(frame.left - cx, frame.top - cy), hypot(frame.right - cx, frame.top - cy),
            hypot(frame.left - cx, frame.bottom - cy), hypot(frame.right - cx, frame.bottom - cy),
        ).max()
        val outer = if (rng.nextBoolean(0.5f)) farthest else min(frame.width, frame.height) * 0.48f
        val cycle = if (rng.nextBoolean()) listOf(p.primary, p.background, p.secondary, p.background)
        else listOf(p.primary, p.secondary, p.background)
        val radii = mutableListOf<Float>()
        var r = outer
        val minR = min(frame.width, frame.height) * 0.07f
        while (r > minR) {
            radii += r
            r -= rng.pick(listOf(26f, 40f, 58f, 84f))
        }
        radii.forEachIndexed { i, radius -> out += CoverCircle(cx, cy, radius, cycle[i % cycle.size]) }
        // Moon on one of the rings, in the colour of neither neighbour.
        if (radii.size >= 3) {
            val ringIndex = rng.nextInt(1, radii.size - 1)
            val ringR = radii[ringIndex]
            val moonAngle = rng.nextFloat(0f, 2f * PI.toFloat())
            val moonR = min(frame.width, frame.height) * rng.nextFloat(0.045f, 0.075f)
            val behind = cycle[ringIndex % cycle.size]
            val moonColor = listOf(p.primary, p.secondary, p.background).firstOrNull { it != behind && it != cycle[(ringIndex + 1) % cycle.size] }
                ?: p.secondary
            out += CoverCircle(cx + cos(moonAngle) * ringR, cy + sin(moonAngle) * ringR, moonR, moonColor)
        }
        return out
    }

    // -- Blocks: a mosaic made by splitting the frame a few times.
    private fun blocks(frame: CoverFrame, p: ResolvedPalette, rng: SeededRandom): List<CoverShape> {
        val leaves = mutableListOf<CoverFrame>()
        fun split(rect: CoverFrame, depth: Int) {
            val small = rect.width < 150f || rect.height < 150f
            if (depth == 0 || small || (depth < 3 && rng.nextBoolean(0.28f))) {
                leaves += rect
                return
            }
            val ratio = rng.pick(listOf(1f / 3f, 0.5f, 2f / 3f))
            val vertical = if (rect.width > rect.height * 1.3f) true else if (rect.height > rect.width * 1.3f) false else rng.nextBoolean()
            if (vertical) {
                val x = rect.left + rect.width * ratio
                split(CoverFrame(rect.left, rect.top, x, rect.bottom), depth - 1)
                split(CoverFrame(x, rect.top, rect.right, rect.bottom), depth - 1)
            } else {
                val y = rect.top + rect.height * ratio
                split(CoverFrame(rect.left, rect.top, rect.right, y), depth - 1)
                split(CoverFrame(rect.left, y, rect.right, rect.bottom), depth - 1)
            }
        }
        split(frame, 4)
        val gap = 7f
        val colors = listOf(p.primary, p.secondary, p.background)
        val weights = floatArrayOf(0.4f, 0.32f, 0.28f)
        val out = mutableListOf<CoverShape>()
        // Gaps show the primary colour when the background has none of it, otherwise background.
        leaves.forEach { leaf ->
            val l = leaf.left + gap
            val t = leaf.top + gap
            val r = leaf.right - gap
            val b = leaf.bottom - gap
            if (r - l < 4f || b - t < 4f) return@forEach
            val index = weighted(rng, weights)
            val fill = colors[index]
            if (fill != p.background) out += CoverRect(l, t, r, b, fill)
            if (rng.nextBoolean(0.3f)) {
                val motif = colors[(index + 1 + rng.nextInt(2)) % colors.size]
                val s = min(r - l, b - t)
                if (rng.nextBoolean()) {
                    out += CoverCircle((l + r) / 2f, (t + b) / 2f, s * 0.36f, motif)
                } else {
                    out += CoverArc(l, b, s * 0.9f, 270f, 90f, motif)
                }
            }
        }
        return out
    }

    private fun weighted(rng: SeededRandom, weights: FloatArray): Int {
        val total = weights.sum()
        var x = rng.nextFloat() * total
        weights.forEachIndexed { i, w ->
            x -= w
            if (x < 0f) return i
        }
        return weights.lastIndex
    }
}
