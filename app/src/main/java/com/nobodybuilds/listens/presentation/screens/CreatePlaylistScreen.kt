@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.material3.FloatingActionButtonDefaults
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbType
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.request.ImageRequest
import com.nobodybuilds.listens.data.model.PlaylistShapeType
import com.nobodybuilds.listens.data.model.SmartPlaylistRule
import com.nobodybuilds.listens.presentation.components.SongPickerSelectionPane
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
internal fun smartPlaylistRuleTitle(rule: SmartPlaylistRule): String =
    stringResource(
        when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> R.string.presentation_batch_f_smart_rule_top_played_title
            SmartPlaylistRule.RECENTLY_PLAYED -> R.string.presentation_batch_f_smart_rule_recently_played_title
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> R.string.presentation_batch_f_smart_rule_forgotten_favorites_title
            SmartPlaylistRule.NEW_GEMS -> R.string.presentation_batch_f_smart_rule_new_gems_title
        }
    )

@Composable
internal fun smartPlaylistRuleSubtitle(rule: SmartPlaylistRule): String =
    stringResource(
        when (rule) {
            SmartPlaylistRule.TOP_PLAYED -> R.string.presentation_batch_f_smart_rule_top_played_subtitle
            SmartPlaylistRule.RECENTLY_PLAYED -> R.string.presentation_batch_f_smart_rule_recently_played_subtitle
            SmartPlaylistRule.FORGOTTEN_FAVORITES -> R.string.presentation_batch_f_smart_rule_forgotten_favorites_subtitle
            SmartPlaylistRule.NEW_GEMS -> R.string.presentation_batch_f_smart_rule_new_gems_subtitle
        }
    )

internal enum class PlaylistCreationMode {
    MANUAL,
    SMART
}

@Composable
fun CreatePlaylistDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "create_playlist_dialog"
            ) {
                CreatePlaylistContent(
                    onDismiss = onDismiss,
                    onGenerateClick = onGenerateClick,
                    onCreate = onCreate
                )
            }
        }
    }
}

// Enum removed, using com.nobodybuilds.listens.data.model.PlaylistShapeType

@Composable
fun EditPlaylistDialog(
    visible: Boolean,
    currentName: String,
    currentImageUri: String?,
    currentColor: Int?,
    currentIconName: String?,
    currentShapeType: PlaylistShapeType?,
    currentShapeDetail1: Float?,
    currentShapeDetail2: Float?,
    currentShapeDetail3: Float?,
    currentShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "edit_playlist_dialog"
            ) {
                 EditPlaylistContent(
                    initialName = currentName,
                    initialImageUri = currentImageUri,
                    initialColor = currentColor,
                    initialIconName = currentIconName,
                    initialShapeType = currentShapeType,
                    initialShapeDetail1 = currentShapeDetail1,
                    initialShapeDetail2 = currentShapeDetail2,
                    initialShapeDetail3 = currentShapeDetail3,
                    initialShapeDetail4 = currentShapeDetail4,
                    onDismiss = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePlaylistContent(
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?) -> Unit
) {
    val context = LocalContext.current

    // Shared State
    var playlistName by remember { mutableStateOf("") }
    
    // Step 1: Info State
    var currentStep by remember { mutableStateOf(0) } // 0: Info, 1: Songs
    var selectedTab by remember { mutableStateOf(0) } // 0: Default, 1: Image, 2: Icon
    var creationMode by remember { mutableStateOf(PlaylistCreationMode.MANUAL) }
    var selectedSmartRule by remember { mutableStateOf(SmartPlaylistRule.TOP_PLAYED) }
    
    // Songs State
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }
    
    // Image State
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCropUi by remember { mutableStateOf(false) }
    // Lifted Bitmap State for Preview Consistency
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }

    // Crop State
    var cropScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Custom Color/Icon State
    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf<Int?>(defaultColor) }
    var selectedIconName by remember { mutableStateOf<String?>("MusicNote") }

    // Shape State
    var selectedShapeType by remember { mutableStateOf(PlaylistShapeType.Circle) }
    
    // SmoothRect Params
    var smoothRectCornerRadius by remember { androidx.compose.runtime.mutableFloatStateOf(20f) } // 0-50
    var smoothRectSmoothness by remember { androidx.compose.runtime.mutableFloatStateOf(60f) } // 0-100
    
    var starCurve by remember { androidx.compose.runtime.mutableDoubleStateOf(0.15) } // 0.0 - 0.5
    var starRotation by remember { androidx.compose.runtime.mutableFloatStateOf(0f) } // 0 - 360
    var starScale by remember { androidx.compose.runtime.mutableFloatStateOf(1f) } // 0.5 - 1.5
    var starSides by remember { androidx.compose.runtime.mutableIntStateOf(5) } // 3 - 20

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
        }
    }
    
    // Back Handler for Step 2
    BackHandler(enabled = currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL) {
        currentStep = 0
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedContent(targetState = currentStep, label = "Title Animation") { step ->
                        Text(
                            if (step == 0) {
                                if (creationMode == PlaylistCreationMode.SMART) {
                                    stringResource(R.string.presentation_batch_f_new_smart_playlist)
                                } else {
                                    stringResource(R.string.presentation_batch_f_new_playlist)
                                }
                            } else {
                                stringResource(R.string.presentation_batch_f_add_songs)
                            },
                            style = NbType.h2,
                            color = Nb.text
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Nb.text
                        ),
                        onClick = {
                            if (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL) currentStep = 0 else onDismiss()
                        }
                    ) {
                        Icon(
                            if (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL) {
                                Icons.AutoMirrored.Rounded.ArrowBack
                            } else {
                                Icons.Rounded.Close
                            },
                            contentDescription = stringResource(R.string.presentation_batch_f_cd_back_or_cancel)
                        )
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
                    text = {
                        Text(
                            if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) {
                                stringResource(R.string.presentation_batch_f_next)
                            } else {
                                stringResource(R.string.presentation_batch_f_create)
                            }
                        )
                    },
                    icon = { 
                        Icon(
                            if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) {
                                Icons.AutoMirrored.Rounded.ArrowForward
                            } else {
                                Icons.Rounded.Check
                            }, 
                            contentDescription = null
                        ) 
                    },
                    onClick = {
                        if (currentStep == 0) {
                            if (playlistName.isNotBlank()) {
                                if (creationMode == PlaylistCreationMode.MANUAL) {
                                    currentStep = 1
                                } else {
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

                                    onCreate(
                                        playlistName,
                                        imageUriString,
                                        color,
                                        icon,
                                        emptyList(),
                                        scale,
                                        panX,
                                        panY,
                                        shapeTypeForSave,
                                        d1, d2, d3, d4,
                                        selectedSmartRule.storageKey
                                    )
                                }
                            }
                        } else {
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

                            onCreate(
                                playlistName, 
                                imageUriString, 
                                color, 
                                icon, 
                                selectedSongIds.filterValues { it }.keys.toList(),
                                scale,
                                panX,
                                panY,
                                shapeTypeForSave,
                                d1, d2, d3, d4,
                                null
                            )
                        }
                    },
                    expanded = true,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = 8.dp, end = 8.dp)
                        .height(56.dp), // Standard height, feels substantial
                    containerColor = if (currentStep == 0 && playlistName.isBlank()) Nb.emphasis else Nb.ink,
                    contentColor = if (currentStep == 0 && playlistName.isBlank()) Nb.textMuted else Nb.onInk,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding()),
            label = "Step Transition"
        ) { step ->
            if (step == 0) {
                 PlaylistFormContent(
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
                     creationMode = creationMode,
                     onCreationModeChange = { creationMode = it },
                     selectedSmartRule = selectedSmartRule,
                     onSmartRuleChange = { selectedSmartRule = it },
                     onGenerateClick = onGenerateClick
                 )
            } else {
                SongPickerSelectionPane(
                    selectedSongIds = selectedSongIds,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
                )
            }
        }
    }
}
