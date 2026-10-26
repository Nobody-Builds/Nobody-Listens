package com.twilighty.lmusic.presentation.garden

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.twilighty.lmusic.data.garden.GardenSeason
import com.twilighty.lmusic.data.garden.GardenStage
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// =====================================================================================
// Procedural listening-garden art. Everything is laid out once in a unit square
// (x, y in 0..1, y down) from a seed, so the same garden always looks the same; drawing
// only scales the layout. Used by the screen (Canvas), share image and the widget bitmap.
// =====================================================================================

data class GardenPalette(
    val leaf: Color,
    val leafDeep: Color,
    val vein: Color,
    val stem: Color,
    val pot: Color,
    val potShade: Color,
    val potRim: Color,
    val soil: Color,
    val mound: Color,
    val moundShade: Color,
    val trunk: Color,
    val seed: Color,
    val flowerCenter: Color,
    val snow: Color,
    val rain: Color,
    val blossom: Color,
    val warmLeaf: Color,
) {
    companion object {
        val Light = GardenPalette(
            leaf = Color(0xFF4F9D66), leafDeep = Color(0xFF2F7A4A), vein = Color(0x66E6F5EA),
            stem = Color(0xFF3E7C4D), pot = Color(0xFFD27D57), potShade = Color(0xFFB4613F),
            potRim = Color(0xFFDD8C66), soil = Color(0xFF6B4A36), mound = Color(0xFF86BF92),
            moundShade = Color(0xFF66A274), trunk = Color(0xFF8A5A3C), seed = Color(0xFF8B5E3C),
            flowerCenter = Color(0xFFF2B33D), snow = Color(0xFFB4C4D8), rain = Color(0xFF6FA8DC),
            blossom = Color(0xFFF2A2B8), warmLeaf = Color(0xFFD9822B),
        )
        val Dark = GardenPalette(
            leaf = Color(0xFF62B67B), leafDeep = Color(0xFF3F8F58), vein = Color(0x55E6F5EA),
            stem = Color(0xFF5A9E68), pot = Color(0xFFBC6C4B), potShade = Color(0xFF975231),
            potRim = Color(0xFFCB7E5B), soil = Color(0xFF3E2A1F), mound = Color(0xFF3F7A4E),
            moundShade = Color(0xFF2F6140), trunk = Color(0xFF9C6C4B), seed = Color(0xFFA0714C),
            flowerCenter = Color(0xFFF6C453), snow = Color(0xFFE8EEF5), rain = Color(0xFF8FBCE8),
            blossom = Color(0xFFF0A3B8), warmLeaf = Color(0xFFE39A45),
        )
    }
}

object GardenColors {
    /** Mood names are SmartModel's. */
    fun moodColor(mood: String): Color? = when (mood) {
        "Calm" -> Color(0xFF5B8DEF)
        "Upbeat" -> Color(0xFFD9541E) // Ember orange, a touch brighter for petals
        "Melancholy" -> Color(0xFFB9AEFF)
        "Energetic" -> Color(0xFFE5484D)
        "Romantic" -> Color(0xFFF07FAE)
        "Dark" -> Color(0xFF6B3FA0)
        "Party" -> Color(0xFFF5C518)
        else -> null
    }

    /** Daylight accents for when Smart features are off. */
    val AccentFlowers = listOf(
        Color(0xFFD9541E), Color(0xFFB9AEFF), Color(0xFFF59E7A), Color(0xFFF07FAE), Color(0xFFF2B53A)
    )

    /** Ten flower "slots", shared out by mood share so the strongest moods bloom most. */
    fun flowerColors(moodMix: Map<String, Float>): List<Color> {
        val ranked = moodMix.entries
            .mapNotNull { (mood, share) -> moodColor(mood)?.let { it to share } }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
        if (ranked.isEmpty()) return AccentFlowers
        val total = ranked.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(1e-6f)
        val slots = ArrayList<Color>(10)
        ranked.forEach { (color, share) ->
            val n = max(1, (share / total * 10f).roundToInt())
            repeat(n) { if (slots.size < 10) slots += color }
        }
        // Interleave so neighbouring flowers differ.
        return slots.withIndex().sortedBy { (i, _) -> (i * 7) % slots.size }.map { it.value }
    }
}

data class GardenSceneSpec(
    val stage: GardenStage,
    val treeMaturity: Float,
    val seed: Long,
    val flowerColors: List<Color>,
    val season: GardenSeason,
    val milestones: List<Int>,
)

// ---------------------------------------------------------------- Layout

internal class LeafSpec(val x: Float, val y: Float, val angle: Float, val length: Float, val deep: Boolean, val warmth: Float)
internal class FlowerSpec(
    val x: Float, val y: Float, val radius: Float, val color: Color, val petals: Int, val rotation: Float,
    /** Optional stalk as a quadratic curve: fromX, fromY, ctrlX, ctrlY (ends at the flower). */
    val stalk: FloatArray? = null,
)
internal class SpecialSpec(val milestone: Int, val flower: FlowerSpec, val inner: Color)
internal class BlobSpec(val x: Float, val y: Float, val r: Float, val deep: Boolean, val warmth: Float)
internal class ParticleSpec(val x: Float, val y: Float, val size: Float, val angle: Float)

class GardenLayout internal constructor(
    val stage: GardenStage,
    val season: GardenSeason,
    val baseX: Float,
    val baseY: Float,
    internal val isTree: Boolean,
    internal val stem: FloatArray?,
    internal val stemWidth: Float,
    internal val leaves: List<LeafSpec>,
    internal val blobs: List<BlobSpec>,
    internal val branches: List<FloatArray>,
    internal val flowers: List<FlowerSpec>,
    internal val bud: FlowerSpec?,
    internal val specials: List<SpecialSpec>,
    internal val particles: List<ParticleSpec>,
    internal val fallen: List<LeafSpec>,
)

private fun Random.range(a: Float, b: Float) = a + nextFloat() * (b - a)

private fun cubicPoint(c: FloatArray, t: Float): Offset {
    val u = 1 - t
    val a = u * u * u; val b = 3 * u * u * t; val cc = 3 * u * t * t; val d = t * t * t
    return Offset(a * c[0] + b * c[2] + cc * c[4] + d * c[6], a * c[1] + b * c[3] + cc * c[5] + d * c[7])
}

private fun cubicAngle(c: FloatArray, t: Float): Float {
    val u = 1 - t
    val dx = 3 * u * u * (c[2] - c[0]) + 6 * u * t * (c[4] - c[2]) + 3 * t * t * (c[6] - c[4])
    val dy = 3 * u * u * (c[3] - c[1]) + 6 * u * t * (c[5] - c[3]) + 3 * t * t * (c[7] - c[5])
    return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
}

private val STEM_HEIGHT = floatArrayOf(0f, 0.10f, 0.19f, 0.28f, 0.37f, 0.44f, 0.49f, 0.53f, 0.56f, 0.40f)
private val LEAF_COUNT = intArrayOf(0, 0, 4, 6, 9, 10, 11, 12, 13, 0)

fun buildGardenLayout(spec: GardenSceneSpec): GardenLayout {
    val r = Random(spec.seed)
    val stage = spec.stage
    val tree = stage == GardenStage.LittleTree
    val baseX = 0.5f
    val baseY = if (tree) 0.885f else 0.775f
    val colors = spec.flowerColors.ifEmpty { GardenColors.AccentFlowers }
    var colorIndex = (spec.seed and 0xff).toInt()
    fun nextColor(): Color = colors[(colorIndex++).mod(colors.size)]
    val petals = if (r.nextBoolean()) 5 else 6
    val autumn = spec.season == GardenSeason.Autumn

    val leaves = ArrayList<LeafSpec>()
    val flowers = ArrayList<FlowerSpec>()
    val blobs = ArrayList<BlobSpec>()
    val branches = ArrayList<FloatArray>()
    var bud: FlowerSpec? = null

    // Stem (or trunk)
    val h = STEM_HEIGHT[stage.ordinal] * if (tree) (0.94f + 0.06f * spec.treeMaturity) else 1f
    val drift = if (stage.ordinal <= 1) 0.015f else 0.05f
    val tipX = baseX + r.range(-drift, drift)
    val stem = if (h <= 0f) null else floatArrayOf(
        baseX, baseY,
        baseX + r.range(-0.07f, 0.07f) * (h / 0.56f), baseY - h * 0.35f,
        tipX + r.range(-0.07f, 0.07f) * (h / 0.56f), baseY - h * 0.7f,
        tipX, baseY - h,
    )
    val stemWidth = if (tree) 0.036f + 0.006f * spec.treeMaturity else 0.010f + 0.0016f * stage.ordinal

    if (stem != null && !tree) {
        val tipAngle = cubicAngle(stem, 1f)
        if (stage == GardenStage.Sprout) {
            leaves += LeafSpec(stem[6], stem[7], tipAngle - 58f, 0.06f, false, 0f)
            leaves += LeafSpec(stem[6], stem[7], tipAngle + 58f, 0.06f, true, 0f)
        } else {
            val n = LEAF_COUNT[stage.ordinal]
            val grow = (stage.ordinal - 2) / 6f
            for (i in 0 until n) {
                val t = 0.16f + 0.72f * (i + 0.5f) / n
                val p = cubicPoint(stem, t)
                val a = cubicAngle(stem, t)
                val side = if (i % 2 == 0) 1f else -1f
                val len = (0.07f + 0.035f * grow) * (1f - 0.35f * t) * r.range(0.88f, 1.1f)
                val warmth = if (autumn) (if (i % 3 == 1) 0.6f else 0.18f) else 0f
                leaves += LeafSpec(p.x, p.y, a + side * r.range(46f, 66f), len, i % 3 == 0, warmth)
            }
            // A small pair near the tip
            val tp = cubicPoint(stem, 0.96f)
            leaves += LeafSpec(tp.x, tp.y, tipAngle - 34f, 0.038f, false, if (autumn) 0.2f else 0f)
            leaves += LeafSpec(tp.x, tp.y, tipAngle + 34f, 0.034f, true, if (autumn) 0.2f else 0f)
        }

        fun sideFlower(t: Float, side: Float, radius: Float) {
            val p = cubicPoint(stem, t)
            val fx = p.x + side * r.range(0.075f, 0.105f)
            val fy = p.y - r.range(0.04f, 0.07f)
            flowers += FlowerSpec(
                fx, fy, radius, nextColor(), petals, r.range(0f, 72f),
                stalk = floatArrayOf(p.x, p.y, p.x + side * 0.025f, fy + 0.035f)
            )
        }
        when (stage) {
            GardenStage.FirstBud -> bud = FlowerSpec(stem[6], stem[7], 0.034f, nextColor(), petals, 0f)
            GardenStage.Bloom -> flowers += FlowerSpec(stem[6], stem[7], 0.052f, nextColor(), petals, r.range(0f, 72f))
            GardenStage.Flowering -> {
                flowers += FlowerSpec(stem[6], stem[7], 0.054f, nextColor(), petals, r.range(0f, 72f))
                sideFlower(0.55f, 1f, 0.036f)
                sideFlower(0.74f, -1f, 0.034f)
            }
            GardenStage.FullBloom -> {
                flowers += FlowerSpec(stem[6], stem[7], 0.058f, nextColor(), petals, r.range(0f, 72f))
                sideFlower(0.38f, -1f, 0.034f)
                sideFlower(0.52f, 1f, 0.038f)
                sideFlower(0.68f, -1f, 0.036f)
                sideFlower(0.82f, 1f, 0.032f)
            }
            else -> Unit
        }
    }

    if (stem != null && tree) {
        val tip = Offset(stem[6], stem[7])
        val cx = tip.x
        val cy = tip.y - 0.10f
        val radius = 0.20f * (0.86f + 0.14f * spec.treeMaturity)
        val count = 10
        for (i in 0 until count) {
            val ang = i * (360f / count) + r.range(-12f, 12f)
            val dist = radius * r.range(0.35f, 0.78f)
            val bx = cx + cos(Math.toRadians(ang.toDouble())).toFloat() * dist
            val by = cy + sin(Math.toRadians(ang.toDouble())).toFloat() * dist * 0.72f
            val warmth = if (autumn) (if (i % 3 == 0) 0.65f else 0.15f) else 0f
            blobs += BlobSpec(bx, by, radius * r.range(0.36f, 0.48f), i % 2 == 0, warmth)
        }
        blobs += BlobSpec(cx, cy, radius * 0.55f, false, if (autumn) 0.15f else 0f)
        // Branches from the trunk into the canopy
        listOf(0.72f to -1f, 0.84f to 1f, 0.95f to -0.3f).forEach { (t, side) ->
            val p = cubicPoint(stem, t)
            val ex = cx + side * radius * 0.55f
            val ey = cy + r.range(-0.03f, 0.03f)
            branches += floatArrayOf(p.x, p.y, (p.x + ex) / 2f, p.y - 0.02f, ex, ey)
        }
        val flowerCount = 5 + (spec.treeMaturity * 6).roundToInt()
        for (i in 0 until flowerCount) {
            val ang = r.range(0f, 360f)
            val dist = radius * r.range(0.2f, 0.95f)
            flowers += FlowerSpec(
                cx + cos(Math.toRadians(ang.toDouble())).toFloat() * dist,
                cy + sin(Math.toRadians(ang.toDouble())).toFloat() * dist * 0.7f,
                r.range(0.022f, 0.03f), nextColor(), petals, r.range(0f, 72f)
            )
        }
    }

    // Milestone flowers: companions growing beside the plant, and a crown at 100 days.
    val specials = ArrayList<SpecialSpec>()
    val soilY = baseY + 0.004f
    if (7 in spec.milestones) {
        val from = if (tree) baseX - 0.17f else baseX - 0.06f
        val fx = from - 0.08f; val fy = baseY - (if (tree) 0.12f else 0.15f)
        specials += SpecialSpec(
            7, FlowerSpec(fx, fy, 0.040f, Color(0xFFF2B53A), 8, 10f, floatArrayOf(from, soilY, from - 0.01f, fy + 0.06f)),
            Color(0xFFFFE29A)
        )
    }
    if (30 in spec.milestones) {
        val from = if (tree) baseX + 0.17f else baseX + 0.06f
        val fx = from + 0.09f; val fy = baseY - (if (tree) 0.15f else 0.21f)
        specials += SpecialSpec(
            30, FlowerSpec(fx, fy, 0.044f, Color(0xFFE8638B), 8, 0f, floatArrayOf(from, soilY, from + 0.01f, fy + 0.08f)),
            Color(0xFFFFC2D4)
        )
    }
    if (100 in spec.milestones && stem != null) {
        val cx = stem[6]
        val top = if (tree) stem[7] - 0.10f - 0.20f * 0.72f * 0.9f else stem[7] - 0.07f
        specials += SpecialSpec(100, FlowerSpec(cx, top, 0.055f, Color(0xFFE8590C), 10, 0f), Color(0xFFFDBA74))
    }

    // Season details from a separate random stream so the plant's shape never changes with the month.
    val pr = Random(spec.seed xor (spec.season.ordinal * 7_777L + 1))
    val particles = ArrayList<ParticleSpec>()
    val fallen = ArrayList<LeafSpec>()
    when (spec.season) {
        GardenSeason.Spring -> repeat(7) {
            particles += ParticleSpec(pr.range(0.08f, 0.92f), pr.range(0.06f, 0.62f), pr.range(0.011f, 0.016f), pr.range(0f, 360f))
        }
        GardenSeason.Monsoon -> repeat(11) {
            particles += ParticleSpec(pr.range(0.06f, 0.94f), pr.range(0.03f, 0.58f), pr.range(0.026f, 0.04f), 0f)
        }
        GardenSeason.Autumn -> {
            val groundY = if (tree) 0.915f else 0.968f
            fallen += LeafSpec(baseX - pr.range(0.24f, 0.30f), groundY, pr.range(-20f, 10f), 0.045f, false, 0.75f)
            fallen += LeafSpec(baseX + pr.range(0.22f, 0.30f), groundY, pr.range(170f, 200f), 0.04f, true, 0.9f)
            repeat(2) {
                particles += ParticleSpec(pr.range(0.12f, 0.88f), pr.range(0.1f, 0.5f), 0.032f, pr.range(0f, 360f))
            }
        }
        GardenSeason.Winter -> repeat(16) {
            particles += ParticleSpec(pr.range(0.04f, 0.96f), pr.range(0.03f, 0.7f), pr.range(0.005f, 0.010f), 0f)
        }
    }

    return GardenLayout(
        stage = stage, season = spec.season, baseX = baseX, baseY = baseY, isTree = tree,
        stem = stem, stemWidth = stemWidth, leaves = leaves, blobs = blobs, branches = branches,
        flowers = flowers, bud = bud, specials = specials, particles = particles, fallen = fallen,
    )
}

// ---------------------------------------------------------------- Drawing

private class Frame(val ox: Float, val oy: Float, val s: Float) {
    fun x(v: Float) = ox + v * s
    fun y(v: Float) = oy + v * s
    fun o(x: Float, y: Float) = Offset(x(x), y(y))
    fun d(v: Float) = v * s
}

private fun DrawScope.frame(): Frame {
    val s = min(size.width, size.height)
    return Frame((size.width - s) / 2f, size.height - s, s)
}

/** Season details behind the plant, and the pot or ground mound. */
fun DrawScope.drawGardenGround(layout: GardenLayout, palette: GardenPalette) {
    val f = frame()
    drawSeasonParticles(f, layout, palette)
    if (layout.isTree) drawMound(f, layout, palette) else drawPot(f, layout, palette)
    layout.fallen.forEach { leaf ->
        drawLeaf(f, leaf.x, leaf.y, leaf.angle, leaf.length, lerp(palette.leaf, palette.warmLeaf, leaf.warmth), palette.vein)
    }
}

/** The plant itself — drawn on its own so the screen can sway it with a graphics layer. */
fun DrawScope.drawGardenPlant(layout: GardenLayout, palette: GardenPalette) {
    val f = frame()
    // Companion milestone stalks sit behind the main stem.
    layout.specials.forEach { sp -> sp.flower.stalk?.let { drawStalk(f, it, sp.flower, palette) } }

    val stem = layout.stem
    if (stem == null) {
        drawSeed(f, layout, palette)
    } else {
        drawTaperedStem(f, stem, layout.stemWidth, if (layout.isTree) palette.trunk else palette.stem)
    }
    layout.branches.forEach { b ->
        val path = Path().apply {
            moveTo(f.x(b[0]), f.y(b[1])); quadraticTo(f.x(b[2]), f.y(b[3]), f.x(b[4]), f.y(b[5]))
        }
        drawPath(path, palette.trunk, style = Stroke(width = f.d(0.014f), cap = StrokeCap.Round))
    }
    // Canopy: deep blobs behind, light ones in front.
    layout.blobs.sortedBy { if (it.deep) 0 else 1 }.forEach { blob ->
        val base = if (blob.deep) palette.leafDeep else palette.leaf
        val color = lerp(base, palette.warmLeaf, blob.warmth)
        val c = f.o(blob.x, blob.y)
        val rr = f.d(blob.r)
        drawCircle(color, rr, c)
        for (k in 0 until 4) {
            val a = Math.toRadians((k * 90 + 45).toDouble())
            drawCircle(color, rr * 0.62f, Offset(c.x + cos(a).toFloat() * rr * 0.62f, c.y + sin(a).toFloat() * rr * 0.5f))
        }
    }
    layout.leaves.forEach { leaf ->
        val base = if (leaf.deep) palette.leafDeep else palette.leaf
        drawLeaf(f, leaf.x, leaf.y, leaf.angle, leaf.length, lerp(base, palette.warmLeaf, leaf.warmth), palette.vein)
    }
    layout.flowers.forEach { fl ->
        fl.stalk?.let { drawStalk(f, it, fl, palette) }
    }
    layout.flowers.forEach { fl -> drawFlower(f, fl, palette.flowerCenter) }
    layout.bud?.let { drawBud(f, it, palette) }
    layout.specials.forEach { drawSpecial(f, it, palette) }
}

fun DrawScope.drawGardenScene(layout: GardenLayout, palette: GardenPalette) {
    drawGardenGround(layout, palette)
    drawGardenPlant(layout, palette)
}

private fun DrawScope.drawSeasonParticles(f: Frame, layout: GardenLayout, palette: GardenPalette) {
    when (layout.season) {
        GardenSeason.Spring -> layout.particles.forEach { p ->
            val c = f.o(p.x, p.y); val s = f.d(p.size)
            rotate(p.angle, c) {
                drawOval(palette.blossom.copy(alpha = 0.85f), Offset(c.x - s, c.y - s * 0.6f), Size(s * 2f, s * 1.2f))
            }
        }
        GardenSeason.Monsoon -> layout.particles.forEach { p ->
            val start = f.o(p.x, p.y)
            drawLine(
                palette.rain.copy(alpha = 0.45f), start,
                Offset(start.x - f.d(p.size * 0.3f), start.y + f.d(p.size)),
                strokeWidth = f.d(0.0045f), cap = StrokeCap.Round
            )
        }
        GardenSeason.Autumn -> layout.particles.forEach { p ->
            drawLeaf(f, p.x, p.y, p.angle, p.size, palette.warmLeaf.copy(alpha = 0.8f), palette.vein)
        }
        GardenSeason.Winter -> layout.particles.forEach { p ->
            drawCircle(palette.snow.copy(alpha = 0.9f), f.d(p.size), f.o(p.x, p.y))
        }
    }
}

private fun DrawScope.drawPot(f: Frame, layout: GardenLayout, palette: GardenPalette) {
    // Body: a soft trapezoid.
    val body = Path().apply {
        moveTo(f.x(0.315f), f.y(0.80f))
        lineTo(f.x(0.685f), f.y(0.80f))
        lineTo(f.x(0.648f), f.y(0.95f))
        quadraticTo(f.x(0.643f), f.y(0.968f), f.x(0.625f), f.y(0.968f))
        lineTo(f.x(0.375f), f.y(0.968f))
        quadraticTo(f.x(0.357f), f.y(0.968f), f.x(0.352f), f.y(0.95f))
        close()
    }
    drawPath(body, palette.pot)
    // Gentle shade on the right third.
    val shade = Path().apply {
        moveTo(f.x(0.58f), f.y(0.80f))
        lineTo(f.x(0.685f), f.y(0.80f))
        lineTo(f.x(0.648f), f.y(0.95f))
        quadraticTo(f.x(0.643f), f.y(0.968f), f.x(0.625f), f.y(0.968f))
        lineTo(f.x(0.565f), f.y(0.968f))
        close()
    }
    drawPath(shade, palette.potShade.copy(alpha = 0.55f))
    // Rim
    drawRoundRect(
        palette.potRim,
        topLeft = f.o(0.295f, 0.762f), size = Size(f.d(0.41f), f.d(0.05f)),
        cornerRadius = CornerRadius(f.d(0.014f))
    )
    // Soil
    drawOval(palette.soil, topLeft = f.o(0.325f, 0.764f), size = Size(f.d(0.35f), f.d(0.022f)))
    if (layout.season == GardenSeason.Winter) {
        drawRoundRect(
            palette.snow, topLeft = f.o(0.30f, 0.757f), size = Size(f.d(0.40f), f.d(0.014f)),
            cornerRadius = CornerRadius(f.d(0.007f))
        )
    }
}

private fun DrawScope.drawMound(f: Frame, layout: GardenLayout, palette: GardenPalette) {
    val mound = Path().apply {
        moveTo(f.x(0.06f), f.y(0.985f))
        cubicTo(f.x(0.2f), f.y(0.85f), f.x(0.8f), f.y(0.85f), f.x(0.94f), f.y(0.985f))
        close()
    }
    drawPath(mound, palette.mound)
    val shade = Path().apply {
        moveTo(f.x(0.22f), f.y(0.985f))
        cubicTo(f.x(0.35f), f.y(0.93f), f.x(0.75f), f.y(0.92f), f.x(0.94f), f.y(0.985f))
        close()
    }
    drawPath(shade, palette.moundShade.copy(alpha = 0.6f))
    if (layout.season == GardenSeason.Winter) {
        val cap = Path().apply {
            moveTo(f.x(0.14f), f.y(0.94f))
            cubicTo(f.x(0.26f), f.y(0.865f), f.x(0.74f), f.y(0.865f), f.x(0.86f), f.y(0.94f))
        }
        drawPath(cap, palette.snow, style = Stroke(width = f.d(0.016f), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawSeed(f: Frame, layout: GardenLayout, palette: GardenPalette) {
    val c = f.o(layout.baseX, layout.baseY - 0.004f)
    val w = f.d(0.05f); val h = f.d(0.032f)
    rotate(-18f, c) {
        drawOval(palette.seed, Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
        drawOval(Color.White.copy(alpha = 0.25f), Offset(c.x - w * 0.3f, c.y - h * 0.38f), Size(w * 0.3f, h * 0.3f))
    }
}

private fun DrawScope.drawTaperedStem(f: Frame, c: FloatArray, width: Float, color: Color) {
    val steps = 18
    val left = ArrayList<Offset>(steps + 1)
    val right = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val p = cubicPoint(c, t)
        val a = Math.toRadians(cubicAngle(c, t).toDouble() + 90.0)
        val w = width * (1f - 0.55f * t) / 2f
        val nx = cos(a).toFloat() * w; val ny = sin(a).toFloat() * w
        left += f.o(p.x + nx, p.y + ny)
        right += f.o(p.x - nx, p.y - ny)
    }
    val path = Path().apply {
        moveTo(left[0].x, left[0].y)
        for (i in 1..steps) lineTo(left[i].x, left[i].y)
        for (i in steps downTo 0) lineTo(right[i].x, right[i].y)
        close()
    }
    drawPath(path, color)
    val tip = f.o(c[6], c[7])
    drawCircle(color, f.d(width * 0.45f / 2f), tip)
}

private fun DrawScope.drawLeaf(f: Frame, x: Float, y: Float, angle: Float, length: Float, color: Color, vein: Color) {
    val l = f.d(length)
    val w = l * 0.42f
    withTransform({
        translate(f.x(x), f.y(y))
        rotate(angle, pivot = Offset.Zero)
    }) {
        val p = Path().apply {
            moveTo(0f, 0f)
            quadraticTo(l * 0.45f, -w, l, 0f)
            quadraticTo(l * 0.45f, w, 0f, 0f)
            close()
        }
        drawPath(p, color)
        drawLine(vein, Offset(l * 0.1f, 0f), Offset(l * 0.78f, 0f), strokeWidth = max(1f, l * 0.045f), cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawStalk(f: Frame, s: FloatArray, flower: FlowerSpec, palette: GardenPalette) {
    val path = Path().apply {
        moveTo(f.x(s[0]), f.y(s[1]))
        quadraticTo(f.x(s[2]), f.y(s[3]), f.x(flower.x), f.y(flower.y))
    }
    drawPath(path, palette.stem, style = Stroke(width = f.d(0.0075f), cap = StrokeCap.Round))
}

private fun DrawScope.drawFlower(f: Frame, fl: FlowerSpec, center: Color) {
    val c = f.o(fl.x, fl.y)
    val r = f.d(fl.radius)
    val inner = lerp(fl.color, Color.White, 0.38f)
    for (i in 0 until fl.petals) {
        rotate(fl.rotation + i * 360f / fl.petals, c) {
            drawOval(fl.color, Offset(c.x - r * 0.34f, c.y - r), Size(r * 0.68f, r * 1.02f))
        }
    }
    for (i in 0 until fl.petals) {
        rotate(fl.rotation + (i + 0.5f) * 360f / fl.petals, c) {
            drawOval(inner, Offset(c.x - r * 0.18f, c.y - r * 0.62f), Size(r * 0.36f, r * 0.62f))
        }
    }
    drawCircle(center, r * 0.3f, c)
    drawCircle(lerp(center, Color.White, 0.45f), r * 0.11f, Offset(c.x - r * 0.08f, c.y - r * 0.08f))
}

private fun DrawScope.drawBud(f: Frame, b: FlowerSpec, palette: GardenPalette) {
    val c = f.o(b.x, b.y)
    val r = f.d(b.radius)
    drawOval(b.color, Offset(c.x - r * 0.42f, c.y - r * 1.25f), Size(r * 0.84f, r * 1.3f))
    drawOval(lerp(b.color, Color.White, 0.35f), Offset(c.x - r * 0.16f, c.y - r * 1.1f), Size(r * 0.22f, r * 0.6f))
    drawLeaf(f, b.x, b.y, -125f, b.radius * 0.95f, palette.leafDeep, palette.vein)
    drawLeaf(f, b.x, b.y, -55f, b.radius * 0.95f, palette.leaf, palette.vein)
}

private fun DrawScope.drawSpecial(f: Frame, sp: SpecialSpec, palette: GardenPalette) {
    val fl = sp.flower
    val c = f.o(fl.x, fl.y)
    val r = f.d(fl.radius)
    drawCircle(fl.color.copy(alpha = 0.14f), r * 1.65f, c)
    // Pointed outer petals
    for (i in 0 until fl.petals) {
        rotate(fl.rotation + i * 360f / fl.petals, c) {
            val p = Path().apply {
                moveTo(c.x, c.y)
                quadraticTo(c.x - r * 0.36f, c.y - r * 0.55f, c.x, c.y - r * 1.08f)
                quadraticTo(c.x + r * 0.36f, c.y - r * 0.55f, c.x, c.y)
                close()
            }
            drawPath(p, fl.color)
        }
    }
    for (i in 0 until fl.petals) {
        rotate(fl.rotation + (i + 0.5f) * 360f / fl.petals, c) {
            drawOval(sp.inner, Offset(c.x - r * 0.16f, c.y - r * 0.66f), Size(r * 0.32f, r * 0.66f))
        }
    }
    drawCircle(palette.flowerCenter, r * 0.3f, c)
    for (k in 0 until 5) {
        val a = Math.toRadians((k * 72 - 90).toDouble())
        drawCircle(lerp(palette.flowerCenter, Color(0xFF7C4A12), 0.5f), r * 0.045f, Offset(c.x + cos(a).toFloat() * r * 0.16f, c.y + sin(a).toFloat() * r * 0.16f))
    }
}

/** Renders the garden into a software bitmap. Safe off the main thread. */
fun renderGardenBitmap(layout: GardenLayout, palette: GardenPalette, sizePx: Int, background: Color? = null): Bitmap {
    val image = ImageBitmap(sizePx, sizePx)
    val canvas = androidx.compose.ui.graphics.Canvas(image)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(sizePx.toFloat(), sizePx.toFloat())) {
        background?.let { drawRect(it) }
        drawGardenScene(layout, palette)
    }
    return image.asAndroidBitmap()
}
