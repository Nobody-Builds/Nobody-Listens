package com.nobodybuilds.listens.presentation.garden

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.nobodybuilds.listens.R
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Renders the garden with a caption into a 1080x1350 image and opens the share sheet. */
suspend fun shareGarden(
    context: Context,
    layout: GardenLayout,
    dark: Boolean,
    title: String,
    subtitle: String,
    fileName: String,
) {
    runCatching {
        val file = withContext(Dispatchers.Default) {
            val bitmap = renderShareCard(context, layout, dark, title, subtitle)
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "garden").apply { mkdirs() }
                File(dir, "$fileName.png").also { out ->
                    FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share your garden").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Timber.w(it, "Garden: share failed") }
}

private fun renderShareCard(context: Context, layout: GardenLayout, dark: Boolean, title: String, subtitle: String): Bitmap {
    val w = 1080
    val h = 1350
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val bg = if (dark) Color(0xFF111113) else Color.White
    val tile = if (dark) Color(0xFF10261E) else Color(0xFFE9FBF3)
    val ink = if (dark) Color(0xFFF4F4F5) else Color(0xFF18181B)
    val muted = if (dark) Color(0xFFA1A1AA) else Color(0xFF5F5E5A)
    canvas.drawColor(bg.toArgb())

    val tileRect = RectF(60f, 60f, 1020f, 1020f)
    canvas.drawRoundRect(tileRect, 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tile.toArgb() })

    val sceneSize = 860f
    canvas.save()
    canvas.translate(110f, 120f)
    CanvasDrawScope().draw(
        Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(sceneSize, sceneSize)
    ) {
        drawGardenScene(layout, if (dark) GardenPalette.Dark else GardenPalette.Light)
    }
    canvas.restore()

    val display = runCatching { ResourcesCompat.getFont(context, R.font.gilroy_extrabold) }.getOrNull()
    val body = runCatching { ResourcesCompat.getFont(context, R.font.gflex_variable) }.getOrNull()
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink.toArgb()
        textSize = 68f
        typeface = Typeface.create(display ?: Typeface.DEFAULT, 800, false)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = muted.toArgb()
        textSize = 40f
        typeface = Typeface.create(body ?: Typeface.DEFAULT, 500, false)
    }
    val footPaint = Paint(subPaint).apply { textSize = 32f }
    canvas.drawText(title, 72f, 1130f, titlePaint)
    canvas.drawText(subtitle, 72f, 1196f, subPaint)
    canvas.drawText("Nobody Listens · listening garden", 72f, 1290f, footPaint)
    return bitmap
}
