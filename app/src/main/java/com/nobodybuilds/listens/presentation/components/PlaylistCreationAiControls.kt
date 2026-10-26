@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.res.Resources
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R

@Composable
internal fun HeroAiCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
        ),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 30.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 20.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 20.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 30.dp,
            smoothnessAsPercentBR = 60
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 14.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTR = 28.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBL = 28.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBR = 14.dp,
                        smoothnessAsPercentBR = 60
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.presentation_batch_e_ai_hero_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_e_ai_hero_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f)
            )
            Text(
                text = stringResource(R.string.presentation_batch_e_ai_hero_library_note),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
internal fun AiSectionCard(
    title: String,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 20.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = 20.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBL = 20.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 20.dp,
            smoothnessAsPercentBR = 60
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.6f)
                .padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun ChipsSingleSelect(
    options: List<String>,
    selected: String?,
    enabled: Boolean = true,
    allowCustom: Boolean = true,
    onSelectedChange: (String?) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var customInputValue by remember { mutableStateOf("") }
    
    // Check if current selection is a custom value (not in predefined options)
    val isCustomSelection = selected != null && options.none { it == selected }
    
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = selected == option
            val usePrimaryPalette = index % 2 == 0
            val selectedContainer = if (usePrimaryPalette) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
            val selectedLabel = if (usePrimaryPalette) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }
            FilterChip(
                selected = isSelected,
                enabled = enabled,
                shape = CircleShape,
                onClick = {
                    onSelectedChange(if (isSelected) null else option)
                },
                border = BorderStroke(
                    color = Color.Transparent,
                    width = 0.dp
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = selectedContainer,
                    selectedLabelColor = selectedLabel,
                    selectedLeadingIconColor = selectedLabel,
                    containerColor = selectedContainer.copy(alpha = 0.24f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                label = { Text(option) }
            )
        }
        
        // Add custom chip
        if (allowCustom) {
            val customIndex = options.size
            val usePrimaryPalette = customIndex % 2 == 0
            val selectedContainer = if (usePrimaryPalette) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
            val selectedLabel = if (usePrimaryPalette) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }
            
            FilterChip(
                selected = isCustomSelection,
                enabled = enabled,
                shape = CircleShape,
                onClick = {
                    if (isCustomSelection) {
                        onSelectedChange(null)
                    } else {
                        customInputValue = selected ?: ""
                        showCustomDialog = true
                    }
                },
                border = BorderStroke(
                    color = Color.Transparent,
                    width = 0.dp
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = selectedContainer,
                    selectedLabelColor = selectedLabel,
                    selectedLeadingIconColor = selectedLabel,
                    containerColor = selectedContainer.copy(alpha = 0.24f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                leadingIcon = if (!isCustomSelection) {
                    { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp)) }
                } else null,
                label = { 
                    Text(
                        if (isCustomSelection) selected!! 
                        else stringResource(R.string.presentation_batch_e_ai_custom_chip)
                    ) 
                }
            )
        }
    }
    
    // Custom input dialog
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            icon = { Icon(Icons.Rounded.Add, null) },
            title = { Text(stringResource(R.string.presentation_batch_e_ai_enter_custom_title)) },
            text = {
                OutlinedTextField(
                    value = customInputValue,
                    onValueChange = { customInputValue = it },
                    label = { Text(stringResource(R.string.presentation_batch_e_ai_enter_custom_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (customInputValue.isNotBlank()) {
                            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().success()
                            onSelectedChange(customInputValue.trim())
                        }
                        showCustomDialog = false
                        customInputValue = ""
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                FilledTonalButton(onClick = { 
                    showCustomDialog = false
                    customInputValue = ""
                }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }
}

@Composable
internal fun LevelSelector(
    label: String,
    selectedLevel: Int,
    enabled: Boolean = true,
    description: String? = null,
    onLevelSelected: (Int) -> Unit
) {
    var showDescription by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (description != null) {
                    IconButton(
                        onClick = { showDescription = !showDescription },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (showDescription) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.presentation_batch_e_ai_level_out_of_five, selectedLevel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (showDescription && description != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            (1..5).forEach { level ->
                SegmentedButton(
                    selected = selectedLevel == level,
                    enabled = enabled,
                    onClick = { onLevelSelected(level) },
                    shape = SegmentedButtonDefaults.itemShape(index = level - 1, count = 5),
                    label = { Text(level.toString()) }
                )
            }
        }
    }
}

@Composable
internal fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
internal fun buildAiPlaylistPrompt(
    res: Resources,
    basePrompt: String,
    mood: String?,
    activity: String?,
    era: String,
    includeGenres: String,
    excludeGenres: String,
    preferredLanguage: String,
    energyLevel: Int,
    discoveryLevel: Int,
    prioritizeFavorites: Boolean,
    avoidExplicit: Boolean
): String {
    val anyEraText = res.getString(R.string.presentation_batch_e_ai_era_any)
    val promptParts = mutableListOf<String>()

    if (basePrompt.isNotBlank()) {
        promptParts += res.getString(
            R.string.presentation_batch_e_ai_prompt_core_request,
            basePrompt.trim()
        )
    }
    if (!mood.isNullOrBlank()) {
        promptParts += res.getString(R.string.presentation_batch_e_ai_prompt_mood, mood)
    }
    if (!activity.isNullOrBlank()) {
        promptParts += res.getString(R.string.presentation_batch_e_ai_prompt_activity, activity)
    }
    if (era != anyEraText) {
        promptParts += res.getString(R.string.presentation_batch_e_ai_prompt_era, era)
    }
    if (includeGenres.isNotBlank()) {
        promptParts += res.getString(
            R.string.presentation_batch_e_ai_prompt_prioritize_genres,
            includeGenres.trim()
        )
    }
    if (excludeGenres.isNotBlank()) {
        promptParts += res.getString(
            R.string.presentation_batch_e_ai_prompt_avoid_genres,
            excludeGenres.trim()
        )
    }
    if (preferredLanguage.isNotBlank()) {
        promptParts += res.getString(
            R.string.presentation_batch_e_ai_prompt_preferred_language,
            preferredLanguage.trim()
        )
    }

    val e = energyLevel.coerceIn(1, 5)
    val d = discoveryLevel.coerceIn(1, 5)
    promptParts += res.getString(R.string.presentation_batch_e_ai_prompt_energy, e)
    promptParts += res.getString(R.string.presentation_batch_e_ai_prompt_discovery, d)

    if (prioritizeFavorites) {
        promptParts += res.getString(R.string.presentation_batch_e_ai_prompt_prioritize_favorites)
    }
    if (avoidExplicit) {
        promptParts += res.getString(R.string.presentation_batch_e_ai_prompt_avoid_explicit)
    }

    promptParts += res.getString(R.string.presentation_batch_e_ai_prompt_smooth_transitions)

    return promptParts.joinToString(separator = " ").trim()
}
