package com.twilighty.lmusic.data.covers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.twilighty.lmusic.R
import java.io.ByteArrayOutputStream

/**
 * Draws a [CoverDesign] onto an android.graphics Canvas. Plain Bitmap/Canvas work, so it is safe
 * off the main thread. Saved covers are 1000×1000 JPEG at quality 90.
 */
object CoverRenderer {
    const val OUTPUT_SIZE = 1000
    const val JPEG_QUALITY = 90

    @Volatile
    private var cachedTypeface: Typeface? = null

    private fun typeface(context: Context): Typeface {
        cachedTypeface?.let { return it }
        val loaded = runCatching { ResourcesCompat.getFont(context, R.font.gilroy_extrabold) }.getOrNull()
            ?: Typeface.DEFAULT_BOLD
        cachedTypeface = loaded
        return loaded
    }

    fun render(context: Context, design: CoverDesign, sizePx: Int = OUTPUT_SIZE): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(context, canvas, design, sizePx)
        return bitmap
    }

    fun renderJpeg(context: Context, design: CoverDesign): ByteArray {
        val bitmap = render(context, design, OUTPUT_SIZE)
        return try {
            ByteArrayOutputStream(200_000).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun draw(context: Context, canvas: Canvas, design: CoverDesign, sizePx: Int) {
        val scale = sizePx / CoverGenerator.SIZE
        canvas.save()
        canvas.scale(scale, scale)
        canvas.drawColor(design.palette.background)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val f = design.frame
        canvas.save()
        canvas.clipRect(f.left, f.top, f.right, f.bottom)
        val path = Path()
        val oval = RectF()
        design.shapes.forEach { shape ->
            paint.color = shape.color
            when (shape) {
                is CoverRect -> canvas.drawRect(shape.left, shape.top, shape.right, shape.bottom, paint)
                is CoverCircle -> canvas.drawCircle(shape.cx, shape.cy, shape.radius, paint)
                is CoverArc -> {
                    oval.set(shape.cx - shape.radius, shape.cy - shape.radius, shape.cx + shape.radius, shape.cy + shape.radius)
                    canvas.drawArc(oval, shape.startAngle, shape.sweepAngle, true, paint)
                }
                is CoverPolygon -> {
                    val pts = shape.points
                    if (pts.size >= 6) {
                        path.reset()
                        path.moveTo(pts[0], pts[1])
                        var i = 2
                        while (i + 1 < pts.size) {
                            path.lineTo(pts[i], pts[i + 1])
                            i += 2
                        }
                        path.close()
                        canvas.drawPath(path, paint)
                    }
                }
            }
        }
        canvas.restore()

        design.text?.let { drawText(context, canvas, it) }
        canvas.restore()
    }

    /** Bottom-left title in Gilroy ExtraBold, shrunk until it fits in two lines. */
    private fun drawText(context: Context, canvas: Canvas, text: CoverText) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = text.color
            typeface = typeface(context)
            runCatching { fontVariationSettings = "'wght' 800, 'opsz' 48" }
            letterSpacing = -0.02f
        }
        val width = text.maxWidth.toInt()
        var size = 132f
        var layout: StaticLayout
        while (true) {
            paint.textSize = size
            layout = StaticLayout.Builder.obtain(text.text, 0, text.text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 0.92f)
                .setIncludePad(false)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            val fitsHeight = layout.height <= text.maxHeight
            val noWordBroken = (0 until layout.lineCount).none { line ->
                val end = layout.getLineEnd(line)
                end in 1 until text.text.length &&
                    text.text[end - 1].isLetterOrDigit() && text.text[end].isLetterOrDigit()
            }
            val notEllipsized = (0 until layout.lineCount).none { layout.getEllipsisCount(it) > 0 }
            if ((fitsHeight && noWordBroken && notEllipsized) || size <= 44f) break
            size -= 6f
        }
        canvas.save()
        canvas.translate(text.left, text.bottom - layout.height)
        layout.draw(canvas)
        canvas.restore()
    }
}
