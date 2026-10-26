@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbChip
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbSegmented
import com.nobodybuilds.listens.presentation.nb.NbTextField
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nobodybuilds.listens.presentation.components.ImageCropView
import com.nobodybuilds.listens.data.model.PlaylistShapeType
import com.nobodybuilds.listens.data.model.SmartPlaylistRule
import com.nobodybuilds.listens.utils.shapes.RoundedStarShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R

@Composable
internal fun PlaylistFormContent(
    modifier: Modifier = Modifier,
    playlistName: String,
    onNameChange: (String) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    selectedImageUri: Uri?,
    showCropUi: Boolean,
    onShowCropUiChange: (Boolean) -> Unit,
    cropScale: Float,
    onCropScaleChange: (Float) -> Unit,
    cropOffset: androidx.compose.ui.geometry.Offset,
    onCropOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit,
    imageBitmap: ImageBitmap?,
    imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    selectedColor: Int?,
    onColorChange: (Int) -> Unit,
    selectedIconName: String?,
    onIconChange: (String) -> Unit,
    selectedShapeType: PlaylistShapeType,
    onShapeTypeChange: (PlaylistShapeType) -> Unit,
    smoothRectCornerRadius: Float,
    onSmoothRectCornerRadiusChange: (Float) -> Unit,
    smoothRectSmoothness: Float,
    onSmoothRectSmoothnessChange: (Float) -> Unit,
    starSides: Int,
    onStarSidesChange: (Int) -> Unit,
    starCurve: Double,
    onStarCurveChange: (Double) -> Unit,
    starRotation: Float,
    onStarRotationChange: (Float) -> Unit,
    starScale: Float,
    onStarScaleChange: (Float) -> Unit,
    showCreationModeSelector: Boolean = true,
    creationMode: PlaylistCreationMode,
    onCreationModeChange: (PlaylistCreationMode) -> Unit,
    selectedSmartRule: SmartPlaylistRule,
    onSmartRuleChange: (SmartPlaylistRule) -> Unit,
    onGenerateClick: (() -> Unit)? = null
) {
    if (showCropUi && imageBitmap != null) {
         // Fullscreen Crop UI overrides normal content
         Box(modifier = modifier.fillMaxSize().padding(16.dp).clip(RoundedCornerShape(24.dp))) {
             ImageCropView(
                 imageBitmap = imageBitmap,
                 modifier = Modifier.fillMaxSize(),
                 scale = cropScale,
                 pan = cropOffset,
                 enabled = true,
                 onCrop = { scale, pan -> 
                     onCropScaleChange(scale)
                     onCropOffsetChange(pan)
                 }
             )
             FilledIconButton(
                onClick = { onShowCropUiChange(false) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.presentation_batch_f_cd_confirm_crop))
            }
         }
         return
    }

    Column(modifier = modifier
        .fillMaxSize()
        .imePadding()) {
        
         // PREVIEW SECTION
        AnimatedContent(
             targetState = selectedTab,
             transitionSpec = {
                 fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
             },
             label = "preview_transition",
             modifier = Modifier
                 .fillMaxWidth()
                 .padding(top = 8.dp, bottom = 4.dp)
        ) { targetTab ->
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                when (targetTab) {
                    0 -> { // Default
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                 Icon(
                                    Icons.Rounded.GridView,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.presentation_batch_f_auto_generated_collage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    1 -> { // Image
                         // Image Preview (Read Only)
                         if (imageBitmap != null) {
                             Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .clickable { imagePickerLauncher.launch("image/*") }
                             ) {
                                 if (cropScale == 1f && cropOffset == androidx.compose.ui.geometry.Offset.Zero) {
                                     AsyncImage(
                                         model = selectedImageUri,
                                         contentDescription = null,
                                         modifier = Modifier.fillMaxSize(),
                                         contentScale = ContentScale.Crop
                                     )
                                 } else {
                                     ImageCropView(
                                         imageBitmap = imageBitmap,
                                         modifier = Modifier.fillMaxSize(),
                                         scale = cropScale,
                                         pan = cropOffset,
                                         enabled = false,
                                         onCrop = { _, _ -> }
                                     )
                                 }
                             }
                         } else {
                             Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { imagePickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                             ) {
                                 Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                     Icon(
                                         Icons.Rounded.AddPhotoAlternate,
                                         contentDescription = stringResource(R.string.presentation_batch_f_cd_add_photo),
                                         modifier = Modifier.size(56.dp),
                                         tint = MaterialTheme.colorScheme.primary
                                     )
                                     Spacer(Modifier.height(12.dp))
                                     Text(stringResource(R.string.presentation_batch_f_pick_image), style = MaterialTheme.typography.titleSmall)
                                 }
                             }
                         }
                    }
                    2 -> { // Icon / Custom Shape
                         AnimatedContent(
                             targetState = selectedShapeType,
                             transitionSpec = {
                                 fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f) togetherWith 
                                 fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.8f)
                             },
                             label = "shape_transition"
                         ) { currentShapeType ->
                             val currentShape: Shape = when(currentShapeType) {
                                 PlaylistShapeType.Circle -> CircleShape
                                 PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(
                                     cornerRadiusTL = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentTR = smoothRectSmoothness.toInt(),
                                     cornerRadiusTR = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentTL = smoothRectSmoothness.toInt(),
                                     cornerRadiusBR = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentBR = smoothRectSmoothness.toInt(),
                                     cornerRadiusBL = smoothRectCornerRadius.dp,
                                     smoothnessAsPercentBL = smoothRectSmoothness.toInt(),
                                 )
                                 PlaylistShapeType.RotatedPill -> {
                                     androidx.compose.foundation.shape.GenericShape { size, _ ->
                                         val w = size.width
                                         val h = size.height
                                         val pillW = w * 0.75f
                                         val offset = (w - pillW) / 2
                                         addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                                     }
                                 }
                                 PlaylistShapeType.Star -> RoundedStarShape(
                                     sides = starSides,
                                     curve = starCurve,
                                     rotation = starRotation
                                 )
                             }
                             
                             val shapeMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                             val iconMod = if(currentShapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = -45f) else Modifier
                             val scaleMod = if(currentShapeType == PlaylistShapeType.Star) Modifier.graphicsLayer(scaleX = starScale, scaleY = starScale) else Modifier

                             Box(
                                 modifier = Modifier
                                     .size(180.dp)
                                     .then(scaleMod)
                                     .then(shapeMod)
                                     .clip(currentShape)
                                     .background(selectedColor?.let { Color(it) }
                                         ?: MaterialTheme.colorScheme.primaryContainer),
                                 contentAlignment = Alignment.Center
                             ) {
                                 if (selectedIconName != null) {
                                     val icon = getIconByName(selectedIconName!!) ?: Icons.Rounded.MusicNote
                                     Icon(
                                         imageVector = icon,
                                         contentDescription = null,
                                         modifier = Modifier
                                             .size(80.dp)
                                             .then(iconMod),
                                         tint = selectedColor?.let { getThemeContentColor(it, MaterialTheme.colorScheme) } 
                                               ?: MaterialTheme.colorScheme.onPrimaryContainer
                                     )
                                 }
                             }
                         }
                    }
                }
            }
        }

        // CONTROL SECTION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            
            NbTextField(
                value = playlistName,
                onValueChange = onNameChange,
                label = stringResource(R.string.presentation_batch_f_playlist_name_label),
                placeholder = stringResource(R.string.presentation_batch_f_playlist_name_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (showCreationModeSelector) {
                NbSegmented(
                    options = listOf(
                        stringResource(R.string.presentation_batch_f_creation_mode_manual),
                        stringResource(R.string.presentation_batch_f_creation_mode_smart)
                    ),
                    selectedIndex = if (creationMode == PlaylistCreationMode.SMART) 1 else 0,
                    onSelect = { index ->
                        onCreationModeChange(if (index == 1) PlaylistCreationMode.SMART else PlaylistCreationMode.MANUAL)
                    },
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
            }

            // AI Generation Button - only show in Create mode (not Edit mode)
            if (onGenerateClick != null) {
                NbButton(
                    text = stringResource(R.string.presentation_batch_f_generate_with_ai),
                    onClick = onGenerateClick,
                    style = NbButtonStyle.Secondary,
                    icon = rememberVectorPainter(Icons.Rounded.AutoAwesome),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 4.dp)
                )
            }

            AnimatedVisibility(visible = creationMode == PlaylistCreationMode.SMART) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NbOverline(stringResource(R.string.presentation_batch_f_smart_rule))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SmartPlaylistRule.entries.forEach { rule ->
                            NbChip(
                                text = smartPlaylistRuleTitle(rule),
                                selected = selectedSmartRule == rule,
                                onClick = { onSmartRuleChange(rule) }
                            )
                        }
                    }

                    Text(
                        text = smartPlaylistRuleSubtitle(selectedSmartRule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val tabs = listOf(
                stringResource(R.string.presentation_batch_f_tab_default),
                stringResource(R.string.presentation_batch_f_tab_image),
                stringResource(R.string.presentation_batch_f_tab_icon)
            )
            ExpressiveButtonGroup(
                items = tabs,
                selectedIndex = selectedTab,
                onItemClick = onTabChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            )
        
            AnimatedVisibility(visible = selectedTab == 2) {
                 Column(
                     modifier = Modifier.padding(top = 14.dp),
                     verticalArrangement = Arrangement.spacedBy(16.dp)
                 ) {
                     // Colors
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.presentation_batch_f_background_color),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )
                     
                     val colors = listOf( 
                         MaterialTheme.colorScheme.primary.toArgb(),
                         MaterialTheme.colorScheme.primaryContainer.toArgb(),
                         MaterialTheme.colorScheme.secondary.toArgb(),
                         MaterialTheme.colorScheme.secondaryContainer.toArgb(),
                         MaterialTheme.colorScheme.tertiary.toArgb(),
                         MaterialTheme.colorScheme.tertiaryContainer.toArgb(),
                         MaterialTheme.colorScheme.error.toArgb(),
                         MaterialTheme.colorScheme.errorContainer.toArgb(),
                         MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
                         MaterialTheme.colorScheme.inverseSurface.toArgb()
                     )
                     
                     FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                     ) {
                         colors.forEach { color ->
                             val isSelected = selectedColor == color
                             val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")
                             
                             Box(
                                 modifier = Modifier
                                     .size(52.dp)
                                    .clip(RoundedCornerShape(cornerRadius))
                                     .background(if (isSelected) Color(color) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color(color) else Color.Transparent,
                                        shape = RoundedCornerShape(cornerRadius)
                                    ),
                                 contentAlignment = Alignment.Center
                             ) {
                                  Box(
                                     modifier = Modifier
                                         .size(if (isSelected) 42.dp else 48.dp)
                                        .clip(RoundedCornerShape(if (isSelected) 8.dp else cornerRadius))
                                        .background(Color(color))
                                         .clickable { onColorChange(color) }
                                  )
                             }
                         }
                     }
                     
                     Spacer(Modifier.height(8.dp))

                     // Icons
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.presentation_batch_f_icon_symbol),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )

                     val icons = listOf(
                        "MusicNote", "Headphones", "Album", "Mic", "Speaker", "Favorite", "Piano", "Queue"
                     )
                     
                     FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                     ) {
                         icons.forEach { iconName ->
                             val isSelected = selectedIconName == iconName
                             val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                             Box(
                                 modifier = Modifier
                                     .size(52.dp)
                                     .clip(RoundedCornerShape(cornerRadius))
                                     .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                     .clickable { onIconChange(iconName) },
                                 contentAlignment = Alignment.Center
                             ) {
                                 Icon(
                                     imageVector = getIconByName(iconName) ?: Icons.Rounded.MusicNote,
                                     contentDescription = null,
                                     tint = if(isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                 )
                             }
                         }
                     }

                     Spacer(Modifier.height(16.dp))

                     // Shapes
                     Text(
                         modifier = Modifier.padding(start = 22.dp),
                         text = stringResource(R.string.presentation_batch_f_shape_style),
                         style = MaterialTheme.typography.titleSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                     )

                     LazyRow(
                         modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                         horizontalArrangement = Arrangement.spacedBy(2.dp),
                         contentPadding = PaddingValues(horizontal = 16.dp)
                     ) {
                         item {
                             PlaylistShapeType.entries.forEach { shapeType ->
                                 val isSelected = selectedShapeType == shapeType
                                 val previewShape = when(shapeType) {
                                     PlaylistShapeType.Circle -> CircleShape
                                     PlaylistShapeType.SmoothRect -> AbsoluteSmoothCornerShape(12.dp, 60, 12.dp, 60, 12.dp, 60, 12.dp, 60)
                                     PlaylistShapeType.RotatedPill -> androidx.compose.foundation.shape.GenericShape { size, _ ->
                                         val w = size.width
                                         val h = size.height
                                         val pillW = w * 0.75f
                                         val offset = (w - pillW) / 2
                                         addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                                     }
                                     PlaylistShapeType.Star -> RoundedStarShape(5, 0.15, 0f)
                                 }

                                 val rotationM = if(shapeType == PlaylistShapeType.RotatedPill) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
                                 val cornerRadius by animateDpAsState(targetValue = if (isSelected) 12.dp else 24.dp, label = "CornerRadius")

                                 Row(modifier = Modifier.padding(2.dp)) {
                                     Spacer(Modifier.width(2.dp))
                                     Column(
                                         horizontalAlignment = Alignment.CenterHorizontally,
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(cornerRadius))
                                             .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                             .clickable { onShapeTypeChange(shapeType) }
                                             .padding(12.dp)
                                     ) {
                                         Box(
                                             modifier = Modifier
                                                 .size(50.dp)
                                                 .then(rotationM)
                                                 .clip(previewShape)
                                                 .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant),
                                             contentAlignment = Alignment.Center
                                         ) {}
                                     }
                                     Spacer(Modifier.width(2.dp))
                                 }
                             }
                         }
                     }
                     
                     // Params
                     AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.SmoothRect) {
                         Column(
                             modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                             verticalArrangement = Arrangement.spacedBy(16.dp)
                         ) {
                             Text(stringResource(R.string.presentation_batch_f_shape_parameters), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_corner_radius), smoothRectCornerRadius, 0f..50f, onSmoothRectCornerRadiusChange, { it.toInt().toString() })
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_smoothness), smoothRectSmoothness, 0f..100f, onSmoothRectSmoothnessChange, { "${it.toInt()}%" })
                         }
                     }
                     
                     AnimatedVisibility(visible = selectedShapeType == PlaylistShapeType.Star) {
                         Column(
                             modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp),
                             verticalArrangement = Arrangement.spacedBy(16.dp)
                         ) {
                             Text(stringResource(R.string.presentation_batch_f_shape_parameters), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_sides), starSides.toFloat(), 3f..20f, { onStarSidesChange(it.toInt()) }, { it.toInt().toString() }, steps = 17)
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_curve), starCurve.toFloat(), 0f..0.5f, { onStarCurveChange(it.toDouble()) }, { String.format("%.2f", it) })
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_rotation), starRotation, 0f..360f, onStarRotationChange, { "${it.toInt()}°" })
                             ShapeParameterCard(stringResource(R.string.presentation_batch_f_scale), starScale, 0.5f..1.5f, onStarScaleChange, { String.format("%.1fx", it) })
                         }
                     }
                 }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}
