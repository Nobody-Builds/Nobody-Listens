@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import kotlin.math.max
import kotlin.math.min
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R

@Composable
internal fun CreateAiPlaylistContent(
    isGenerating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onGenerate: (playlistName: String?, prompt: String, minLength: Int, maxLength: Int) -> Unit
) {
    var playlistName by rememberSaveable { mutableStateOf("") }
    var basePrompt by rememberSaveable { mutableStateOf("") }
    var includeGenres by rememberSaveable { mutableStateOf("") }
    var excludeGenres by rememberSaveable { mutableStateOf("") }
    var preferredLanguage by rememberSaveable { mutableStateOf("") }
    var minSongsInput by rememberSaveable { mutableStateOf("12") }
    var maxSongsInput by rememberSaveable { mutableStateOf("24") }
    var selectedMood by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedActivity by rememberSaveable { mutableStateOf<String?>(null) }
    var energyLevel by rememberSaveable { mutableIntStateOf(3) }
    var discoveryLevel by rememberSaveable { mutableIntStateOf(3) }
    var prioritizeFavorites by rememberSaveable { mutableStateOf(true) }
    var avoidExplicit by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val controlsEnabled = !isGenerating
    val resources = LocalContext.current.resources

    val moodOptions = stringArrayResource(R.array.presentation_batch_e_ai_mood_options).toList()
    val activityOptions = stringArrayResource(R.array.presentation_batch_e_ai_activity_options).toList()
    val eraOptionsList = stringArrayResource(R.array.presentation_batch_e_ai_era_options).toList()
    var selectedEra by remember { mutableStateOf(eraOptionsList.first()) }

    val generatedPromptPreview = buildAiPlaylistPrompt(
        res = resources,
        basePrompt = basePrompt,
        mood = selectedMood,
        activity = selectedActivity,
        era = selectedEra,
        includeGenres = includeGenres,
        excludeGenres = excludeGenres,
        preferredLanguage = preferredLanguage,
        energyLevel = energyLevel,
        discoveryLevel = discoveryLevel,
        prioritizeFavorites = prioritizeFavorites,
        avoidExplicit = avoidExplicit
    )

    val triggerGeneration: () -> Unit = generation@{
        val minSongs = minSongsInput.toIntOrNull()
        val maxSongs = maxSongsInput.toIntOrNull()

        if (generatedPromptPreview.isBlank()) {
            localError = resources.getString(R.string.presentation_batch_e_ai_error_add_instruction)
            return@generation
        }

        if (minSongs == null || maxSongs == null) {
            localError = resources.getString(R.string.presentation_batch_e_ai_error_song_range)
            return@generation
        }

        val normalizedMin = min(minSongs.coerceIn(5, 150), maxSongs.coerceIn(5, 150))
        val normalizedMax = max(minSongs.coerceIn(5, 150), maxSongs.coerceIn(5, 150))

        localError = null
        onGenerate(
            playlistName.trim().ifBlank { null },
            generatedPromptPreview,
            normalizedMin,
            normalizedMax
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.presentation_batch_e_ai_playlist_lab_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        enabled = !isGenerating,
                        onClick = onDismiss,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 2.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        modifier = Modifier.padding(start = 10.dp),
                        onClick = {
                            playlistName = ""
                            basePrompt = ""
                            includeGenres = ""
                            excludeGenres = ""
                            preferredLanguage = ""
                            minSongsInput = "12"
                            maxSongsInput = "24"
                            selectedMood = null
                            selectedActivity = null
                            selectedEra = eraOptionsList.first()
                            energyLevel = 3
                            discoveryLevel = 3
                            prioritizeFavorites = true
                            avoidExplicit = false
                            localError = null
                        },
                        shape = CircleShape,
                        enabled = !isGenerating,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text(stringResource(R.string.presentation_batch_e_ai_reset))
                    }

                    MediumExtendedFloatingActionButton(
                        onClick = {
                            if (!isGenerating) {
                                triggerGeneration()
                            }
                        },
                        modifier = Modifier.alpha(if (controlsEnabled) 1f else 0.72f),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = CircleShape
                    ) {
                        if (isGenerating) {
                            LoadingIndicator(
                                modifier = Modifier.height(28.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.presentation_batch_e_ai_generating))
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.presentation_batch_e_ai_generate))
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            HeroAiCard()

            AiSectionCard(
                title = stringResource(R.string.presentation_batch_e_ai_section_intent),
                enabled = controlsEnabled
            ) {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.presentation_batch_e_ai_playlist_name_optional)) },
                    enabled = controlsEnabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = basePrompt,
                    onValueChange = { basePrompt = it },
                    label = { Text(stringResource(R.string.presentation_batch_e_ai_feel_label)) },
                    placeholder = { Text(stringResource(R.string.presentation_batch_e_ai_feel_placeholder)) },
                    enabled = controlsEnabled,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AiSectionCard(
                title = stringResource(R.string.presentation_batch_e_ai_section_direction),
                enabled = controlsEnabled
            ) {
                Text(stringResource(R.string.presentation_batch_e_ai_mood_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                ChipsSingleSelect(
                    options = moodOptions,
                    selected = selectedMood,
                    enabled = controlsEnabled,
                    onSelectedChange = { selectedMood = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(stringResource(R.string.presentation_batch_e_ai_activity_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                ChipsSingleSelect(
                    options = activityOptions,
                    selected = selectedActivity,
                    enabled = controlsEnabled,
                    onSelectedChange = { selectedActivity = it }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(stringResource(R.string.presentation_batch_e_ai_era_label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                ChipsSingleSelect(
                    options = eraOptionsList,
                    selected = selectedEra,
                    enabled = controlsEnabled,
                    allowCustom = false,
                    onSelectedChange = { selectedEra = it ?: eraOptionsList.first() }
                )
            }

            AiSectionCard(
                title = stringResource(R.string.presentation_batch_e_ai_section_curation),
                enabled = controlsEnabled
            ) {
                LevelSelector(
                    label = stringResource(R.string.presentation_batch_e_ai_energy_label),
                    selectedLevel = energyLevel,
                    enabled = controlsEnabled,
                    description = stringResource(R.string.presentation_batch_e_ai_energy_description),
                    onLevelSelected = { energyLevel = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                LevelSelector(
                    label = stringResource(R.string.presentation_batch_e_ai_discovery_label),
                    selectedLevel = discoveryLevel,
                    enabled = controlsEnabled,
                    description = stringResource(R.string.presentation_batch_e_ai_discovery_description),
                    onLevelSelected = { discoveryLevel = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minSongsInput,
                        onValueChange = { value: String ->
                            minSongsInput = value.filter { ch: Char -> ch.isDigit() }.take(3)
                        },
                        label = { Text(stringResource(R.string.presentation_batch_e_ai_min_songs)) },
                        enabled = controlsEnabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxSongsInput,
                        onValueChange = { value: String ->
                            maxSongsInput = value.filter { ch: Char -> ch.isDigit() }.take(3)
                        },
                        label = { Text(stringResource(R.string.presentation_batch_e_ai_max_songs)) },
                        enabled = controlsEnabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            AiSectionCard(
                title = stringResource(R.string.presentation_batch_e_ai_section_filters),
                enabled = controlsEnabled
            ) {
                OutlinedTextField(
                    value = includeGenres,
                    onValueChange = { includeGenres = it },
                    label = { Text(stringResource(R.string.presentation_batch_e_ai_prioritize_genres)) },
                    placeholder = { Text(stringResource(R.string.presentation_batch_e_ai_prioritize_genres_placeholder)) },
                    enabled = controlsEnabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = excludeGenres,
                    onValueChange = { excludeGenres = it },
                    label = { Text(stringResource(R.string.presentation_batch_e_ai_avoid_genres)) },
                    placeholder = { Text(stringResource(R.string.presentation_batch_e_ai_avoid_genres_placeholder)) },
                    enabled = controlsEnabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = preferredLanguage,
                    onValueChange = { preferredLanguage = it },
                    label = { Text(stringResource(R.string.presentation_batch_e_ai_preferred_language)) },
                    placeholder = { Text(stringResource(R.string.presentation_batch_e_ai_preferred_language_placeholder)) },
                    enabled = controlsEnabled,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                ToggleRow(
                    title = stringResource(R.string.presentation_batch_e_ai_prioritize_favorites),
                    checked = prioritizeFavorites,
                    enabled = controlsEnabled,
                    onCheckedChange = { prioritizeFavorites = it }
                )
                ToggleRow(
                    title = stringResource(R.string.presentation_batch_e_ai_avoid_explicit),
                    checked = avoidExplicit,
                    enabled = controlsEnabled,
                    onCheckedChange = { avoidExplicit = it }
                )
            }

            if (!localError.isNullOrBlank() || !error.isNullOrBlank()) {
                Card(
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 16.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = 16.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = 16.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = 16.dp,
                        smoothnessAsPercentBR = 60
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = localError ?: error.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            AiSectionCard(title = stringResource(R.string.presentation_batch_e_ai_section_prompt_preview)) {
                Text(
                    text = if (generatedPromptPreview.isBlank()) {
                        stringResource(R.string.presentation_batch_e_ai_prompt_preview_empty)
                    } else {
                        generatedPromptPreview
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
