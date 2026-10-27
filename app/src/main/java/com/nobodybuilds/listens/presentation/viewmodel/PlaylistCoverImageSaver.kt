package com.nobodybuilds.listens.presentation.viewmodel

import android.net.Uri
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

internal suspend fun savePlaylistCoverImage(
    context: Context,
    uri: Uri,
    uniqueId: String,
    cropScale: Float,
    cropPanX: Float,
    cropPanY: Float
): String? {
    return withContext(Dispatchers.IO) {
        try {
            // Load original bitmap
            val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                    // Optimization: Mutable to support software rendering if needed
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    // Use HARWARE if possible but need to copy for Canvas?
                    // Software is safer for manual Canvas drawing.
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }

            // Target dimensions (Square)
            val targetSize = 1024

            // create target bitmap
            val targetBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(targetBitmap)

            // Calculate base dimensions (fitting smallest dimension to target)
            // Logic must match ImageCropView
            val bitmapWidth = originalBitmap.width.toFloat()
            val bitmapHeight = originalBitmap.height.toFloat()
            val bitmapRatio = bitmapWidth / bitmapHeight

            val (baseWidth, baseHeight) = if (bitmapRatio > 1f) {
                // Wide: Height matches target
                targetSize * bitmapRatio to targetSize.toFloat()
            } else {
                // Tall: Width matches target
                targetSize.toFloat() to targetSize / bitmapRatio
            }

            // Calculate transformations
            // Scaled Dimensions
            val scaledWidth = baseWidth * cropScale
            val scaledHeight = baseHeight * cropScale

            // Center + Pan
            // Center of target is targetSize/2
            // We want to center the Scaled Image at (Center + Pan)
            // TopLeft = CenterX - ScaledW/2 + PanX

            // Pan is normalized relative to Viewport (TargetSize)
            val panPxX = cropPanX * targetSize
            val panPxY = cropPanY * targetSize

            val dx = (targetSize - scaledWidth) / 2f + panPxX
            val dy = (targetSize - scaledHeight) / 2f + panPxY

            // Draw
            // We draw the original bitmap scaled to (scaledWidth, scaledHeight) at (dx, dy)
            val matrix = android.graphics.Matrix()
            matrix.postScale(scaledWidth / bitmapWidth, scaledHeight / bitmapHeight)
            matrix.postTranslate(dx, dy)

            canvas.drawBitmap(originalBitmap, matrix, null)

            // Save
            val fileName = "playlist_cover_$uniqueId.jpg"
            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { out ->
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            // Recycle
            if (originalBitmap != targetBitmap) originalBitmap.recycle()
            // Target bitmap is not recycled here, let GC handle?
            // Or recycle explicitly if immediate memory pressure concern.

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
