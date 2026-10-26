package com.nobodybuilds.listens.presentation.components

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbArt
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbStatus
import com.nobodybuilds.listens.presentation.nb.NbStatusChip
import com.nobodybuilds.listens.presentation.nb.NbType
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import timber.log.Timber
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.shreyaspatil.capturable.capturable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.ExperimentalComposeUiApi
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import com.nobodybuilds.listens.data.media.CoverArtUpdate
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import java.io.ByteArrayOutputStream
import androidx.compose.ui.res.stringResource

@Composable
internal fun CoverArtEditorCard(
    modifier: Modifier = Modifier,
    albumArtUri: String?,
    artKey: String,
    preview: ImageBitmap?,
    isDeleted: Boolean,
    isAutofilled: Boolean,
    fileName: String?,
    formatLine: String,
    onPickNewArt: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(NbShapes.lg)
                .background(Nb.muted),
            contentAlignment = Alignment.Center
        ) {
            when {
                isDeleted -> {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_music_note_24),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Nb.textMuted
                    )
                }
                preview != null -> {
                    Image(
                        bitmap = preview,
                        contentDescription = stringResource(R.string.cd_cover_art_preview),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    NbArt(
                        model = albumArtUri,
                        title = artKey,
                        size = 92.dp,
                        shape = NbShapes.lg,
                        contentDescription = stringResource(R.string.cd_current_song_cover_art)
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isAutofilled) {
                NbStatusChip(
                    text = "New cover",
                    status = NbStatus.Accent,
                    icon = rememberVectorPainter(Icons.Rounded.AutoAwesome)
                )
            } else if (fileName != null) {
                Text(
                    text = fileName,
                    style = NbType.caption,
                    color = Nb.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NbButton(
                    text = if (albumArtUri != null || preview != null) "Change" else "Add cover",
                    onClick = onPickNewArt,
                    style = NbButtonStyle.Secondary,
                    size = NbButtonSize.Small,
                    icon = rememberVectorPainter(Icons.Rounded.Image)
                )
                if (preview != null || isDeleted) {
                    NbButton(
                        text = stringResource(R.string.action_reset),
                        onClick = onReset,
                        style = NbButtonStyle.Quiet,
                        size = NbButtonSize.Small
                    )
                } else if (albumArtUri != null) {
                    NbButton(
                        text = "Remove",
                        onClick = onDelete,
                        style = NbButtonStyle.DangerQuiet,
                        size = NbButtonSize.Small
                    )
                }
            }
            if (formatLine.isNotBlank()) {
                Text(formatLine, style = NbType.caption, color = Nb.textMuted, maxLines = 1)
            }
        }
    }
}

internal data class CoverArtCropResult(
    val preview: ImageBitmap,
    val update: CoverArtUpdate,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun CoverArtCropperDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (CoverArtCropResult) -> Unit,
) {
    val context = LocalContext.current
    val dialogScope = rememberCoroutineScope()
    val captureController = rememberCaptureController()
    var loadedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(0f) }

    LaunchedEffect(sourceUri) {
        isLoading = true
        loadError = null
        val bitmap = withContext(Dispatchers.IO) { decodeBitmapFromUri(context, sourceUri) }
        if (bitmap != null) {
            loadedBitmap = bitmap.asImageBitmap()
        } else {
            loadError = context.getString(R.string.edit_song_unable_to_load_image)
        }
        isLoading = false
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(containerSize, scale, loadedBitmap) {
        loadedBitmap?.let { bitmap ->
            offset = clampOffset(offset, scale, containerSize, bitmap.width, bitmap.height)
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        loadedBitmap?.let { bitmap ->
            offset = clampOffset(offset + panChange, newScale, containerSize, bitmap.width, bitmap.height)
        }
    }

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    BasicAlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.edit_song_crop_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    val cropSide = minOf(maxWidth, 320.dp)
                    val density = LocalDensity.current
                    val cropSidePx = remember(cropSide) { with(density) { cropSide.toPx() } }

                    LaunchedEffect(cropSidePx) {
                        containerSize = cropSidePx
                    }

                    Box(
                        modifier = Modifier
                            .size(cropSide)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surfaceDim)
                                .clipToBounds()
                        ) {
                            when {
                                isLoading -> {
                                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                                }

                                loadError != null -> {
                                    Text(
                                        text = loadError!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                 loadedBitmap != null -> {
                                    val bitmap = loadedBitmap!!
                                    val baseScale = maxOf(containerSize / bitmap.width, containerSize / bitmap.height)
                                    val displayWidth = with(LocalDensity.current) { (bitmap.width * baseScale).toDp() }
                                    val displayHeight = with(LocalDensity.current) { (bitmap.height * baseScale).toDp() }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clipToBounds()
                                            .capturable(controller = captureController)
                                            .transformable(transformableState)
                                    ) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .requiredSize(displayWidth, displayHeight)
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    translationX = offset.x
                                                    translationY = offset.y
                                                }
                                        )
                                    }

                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val step = size.width / 3f
                                        for (index in 1 until 3) {
                                            val lineOffset = step * index
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(lineOffset, 0f),
                                                end = Offset(lineOffset, size.height),
                                                strokeWidth = 2f
                                            )
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(0f, lineOffset),
                                                end = Offset(size.width, lineOffset),
                                                strokeWidth = 2f
                                            )
                                        }
                                        drawRect(color = gridColor, style = Stroke(width = 3f))
                                    }
                                }
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.edit_song_crop_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = onDismiss
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    val canConfirm = !isLoading && loadError == null && loadedBitmap != null
                    Button(
                        enabled = canConfirm && !isSaving,
                        onClick = {
                            if (!canConfirm) return@Button
                            dialogScope.launch {
                                isSaving = true
                                val captured = captureController.captureAsync().await()
                                if (captured != null) {
                                    val bytes = withContext(Dispatchers.IO) {
                                        imageBitmapToJpeg(captured)
                                    }
                                    if (bytes != null) {
                                        onConfirm(
                                            CoverArtCropResult(
                                                preview = captured,
                                                update = CoverArtUpdate(bytes, COVER_ART_MIME_TYPE)
                                            )
                                        )
                                    } else {
                                        Timber.w("Failed to convert captured cover art to JPEG")
                                    }
                                } else {
                                    Timber.w("CaptureController returned null bitmap")
                                }
                                isSaving = false
                            }
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.edit_song_apply_cover_art))
                    }
                }
            }
        }
    }
}

private const val COVER_ART_MIME_TYPE = "image/jpeg"

private fun clampOffset(
    offset: Offset,
    scale: Float,
    containerSize: Float,
    bitmapWidth: Int,
    bitmapHeight: Int
): Offset {
    if (containerSize <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) return Offset.Zero

    // When using ContentScale.Crop, the image is scaled to fill the container size.
    // The base scale factor is the maximum of the width and height ratios.
    val baseScale = maxOf(containerSize / bitmapWidth, containerSize / bitmapHeight)
    val totalScale = baseScale * scale

    val scaledWidth = bitmapWidth * totalScale
    val scaledHeight = bitmapHeight * totalScale

    // Calculate maximum translation bounds (half of the overflow).
    val maxX = maxOf(0f, (scaledWidth - containerSize) / 2f)
    val maxY = maxOf(0f, (scaledHeight - containerSize) / 2f)

    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private fun imageBitmapToJpeg(imageBitmap: ImageBitmap): ByteArray? {
    return try {
        val bitmap = imageBitmap.asAndroidBitmap()
        ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.toByteArray()
        }
    } catch (error: Exception) {
        Timber.e(error, "Unable to compress image bitmap to JPEG")
        null
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val maxDimension = 2048
            val width = info.size.width
            val height = info.size.height
            val largerSide = maxOf(width, height)
            if (largerSide > maxDimension) {
                val scale = largerSide.toFloat() / maxDimension
                decoder.setTargetSize(
                    (width / scale).roundToInt(),
                    (height / scale).roundToInt()
                )
            }
            decoder.isMutableRequired = false
        }
    } catch (error: Exception) {
        Timber.e(error, "Failed to decode bitmap from $uri")
        null
    }
}
