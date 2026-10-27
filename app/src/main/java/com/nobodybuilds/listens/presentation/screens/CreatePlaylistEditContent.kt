@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.material3.FloatingActionButtonDefaults
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbType
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.request.ImageRequest
import com.nobodybuilds.listens.data.model.PlaylistShapeType
import com.nobodybuilds.listens.data.model.SmartPlaylistRule
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R

@Composable
fun EditPlaylistContent(
    initialName: String,
    initialImageUri: String?,
    initialColor: Int?,
    initialIconName: String?,
    initialShapeType: PlaylistShapeType?,
    initialShapeDetail1: Float?,
    initialShapeDetail2: Float?,
    initialShapeDetail3: Float?,
    initialShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
    val context = LocalContext.current
    
    // Initial State Setup
    var playlistName by remember { mutableStateOf(initialName) }
    
    // Determine initial tab
    // 0=Default, 1=Image, 2=Icon
    // Logic: If imageUri present -> Image. If Color/Icon present -> Icon. Else Default.
    // NOTE: existing playlist usually has one of these.
    var selectedTab by remember { 
        mutableStateOf(
            when {
                // If it's a file path or content URI
                initialImageUri != null -> 1 
                // If it has specific color/icon (and not just defaults potentially, though defaults are allowed)
                // We check if image is null. If image is null, do we have custom icon?
                initialColor != null || initialIconName != null -> 2
                else -> 0
            }
        )
    }

    var selectedImageUri by remember { mutableStateOf<Uri?>(initialImageUri?.let { Uri.parse(it) }) }
    var showCropUi by remember { mutableStateOf(false) }
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }
    
    // Crop: We don't store crop params in DB currently for playlist updates properly unless we re-save image.
    // But assuming we start with scale 1f if editing.
    var cropScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf(initialColor ?: defaultColor) }
    var selectedIconName by remember { mutableStateOf(initialIconName ?: "MusicNote") }

    var selectedShapeType by remember { mutableStateOf(initialShapeType ?: PlaylistShapeType.Circle) }
    
    // Shape Params
    var smoothRectCornerRadius by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail1 ?: 20f) }
    var smoothRectSmoothness by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail2 ?: 60f) }
    
    var starCurve by remember { androidx.compose.runtime.mutableDoubleStateOf(initialShapeDetail1?.toDouble() ?: 0.15) }
    var starRotation by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail2 ?: 0f) }
    var starScale by remember { androidx.compose.runtime.mutableFloatStateOf(initialShapeDetail3 ?: 1f) }
    var starSides by remember { androidx.compose.runtime.mutableIntStateOf(initialShapeDetail4?.toInt() ?: 5) }

    // Constants needed for Form
    val searchQuery = "" // Not used in Edit

    // Image Loader
    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = ImageLoader(context)
             val request = ImageRequest.Builder(context)
                 .data(selectedImageUri)
                 .allowHardware(false)
                 .build()
             val result = loader.execute(request)
             val drawable = result.drawable
             if (drawable is android.graphics.drawable.BitmapDrawable) {
                 imageBitmap = drawable.bitmap.asImageBitmap()
             }
         } else {
             imageBitmap = null
             cropScale = 1f
             cropOffset = androidx.compose.ui.geometry.Offset.Zero
         }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            showCropUi = true
            selectedTab = 1 // Force switch to image tab
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.presentation_batch_f_edit_playlist),
                        style = NbType.h2,
                        color = Nb.text
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Nb.text
                        ),
                        onClick = onDismiss
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.presentation_batch_f_cd_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Nb.canvas
                )
            )
        },
        floatingActionButton = {
            if (!showCropUi) {
                MediumExtendedFloatingActionButton(
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                    text = { Text(stringResource(R.string.presentation_batch_f_save)) },
                    icon = { Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        val imageUriString = if(selectedTab == 1) selectedImageUri?.toString() else null
                        val color = if(selectedTab == 2) selectedColor else null
                        val icon = if(selectedTab == 2) selectedIconName else null
                        
                        val scale = if(selectedTab == 1) cropScale else 1f
                        val panX = if(selectedTab == 1) cropOffset.x else 0f
                        val panY = if(selectedTab == 1) cropOffset.y else 0f
                        
                        val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                        val (d1, d2, d3, d4) = if (selectedTab == 2) {
                            when (selectedShapeType) {
                                PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                else -> Quadruple(0f, 0f, 0f, 0f)
                            }
                        } else Quadruple(null, null, null, null)

                        onSave(
                            playlistName,
                            imageUriString,
                            color,
                            icon,
                            scale,
                            panX,
                            panY,
                            shapeTypeForSave,
                            d1, d2, d3, d4
                        )
                    },
                    expanded = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 8.dp, end = 8.dp)
                        .height(56.dp),
                    containerColor = Nb.ink,
                    contentColor = Nb.onInk,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
         PlaylistFormContent(
             modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
             playlistName = playlistName,
             onNameChange = { playlistName = it },
             selectedTab = selectedTab,
             onTabChange = { selectedTab = it },
             selectedImageUri = selectedImageUri,
             showCropUi = showCropUi,
             onShowCropUiChange = { showCropUi = it },
             cropScale = cropScale,
             onCropScaleChange = { cropScale = it },
             cropOffset = cropOffset,
             onCropOffsetChange = { cropOffset = it },
             imageBitmap = imageBitmap,
             imagePickerLauncher = imagePickerLauncher,
             selectedColor = selectedColor,
             onColorChange = { selectedColor = it },
             selectedIconName = selectedIconName,
             onIconChange = { selectedIconName = it },
             selectedShapeType = selectedShapeType,
             onShapeTypeChange = { selectedShapeType = it },
             smoothRectCornerRadius = smoothRectCornerRadius,
             onSmoothRectCornerRadiusChange = { smoothRectCornerRadius = it },
             smoothRectSmoothness = smoothRectSmoothness,
             onSmoothRectSmoothnessChange = { smoothRectSmoothness = it },
             starSides = starSides,
             onStarSidesChange = { starSides = it },
             starCurve = starCurve,
             onStarCurveChange = { starCurve = it },
             starRotation = starRotation,
             onStarRotationChange = { starRotation = it },
             starScale = starScale,
             onStarScaleChange = { starScale = it },
             showCreationModeSelector = false,
             creationMode = PlaylistCreationMode.MANUAL,
             onCreationModeChange = { },
             selectedSmartRule = SmartPlaylistRule.TOP_PLAYED,
             onSmartRuleChange = { }
         )
    }
}
