package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R

@Composable
fun AiApiKeyItem(
    apiKey: String,
    onApiKeySave: (String) -> Unit,
    title: String,
    subtitle: String
) {
    var localApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    val hasChanges = localApiKey != apiKey
    var showSaved by remember { mutableStateOf(false) }

    LaunchedEffect(showSaved) {
        if (showSaved) {
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = localApiKey,
                onValueChange = { localApiKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.presentation_batch_f_enter_api_key)) },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        onApiKeySave(localApiKey)
                        showSaved = true
                    },
                    enabled = hasChanges
                ) {
                    Text(stringResource(R.string.presentation_batch_f_save))
                }
                if (showSaved) {
                    Text(
                        text = stringResource(R.string.presentation_batch_f_saved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AiSystemPromptItem(
    systemPrompt: String,
    defaultPrompt: String,
    onSystemPromptSave: (String) -> Unit,
    onReset: () -> Unit,
    title: String,
    subtitle: String
) {
    var localPrompt by remember(systemPrompt) { mutableStateOf(systemPrompt) }
    val hasChanges = localPrompt != systemPrompt
    val isDefault = systemPrompt == defaultPrompt
    var showSaved by remember { mutableStateOf(false) }
    val presets = listOf(
        stringResource(R.string.presentation_batch_f_ai_preset_professional_curator_name) to
            stringResource(R.string.presentation_batch_f_ai_preset_professional_curator_prompt),
        stringResource(R.string.presentation_batch_f_ai_preset_creative_maverick_name) to
            stringResource(R.string.presentation_batch_f_ai_preset_creative_maverick_prompt),
        stringResource(R.string.presentation_batch_f_ai_preset_strict_librarian_name) to
            stringResource(R.string.presentation_batch_f_ai_preset_strict_librarian_prompt),
        stringResource(R.string.presentation_batch_f_ai_preset_atmospheric_guide_name) to
            stringResource(R.string.presentation_batch_f_ai_preset_atmospheric_guide_prompt),
        stringResource(R.string.presentation_batch_f_ai_preset_sonic_enthusiast_name) to
            stringResource(R.string.presentation_batch_f_ai_preset_sonic_enthusiast_prompt),
        stringResource(R.string.presentation_batch_f_ai_preset_energy_catalyst_name) to
            stringResource(R.string.presentation_batch_f_ai_preset_energy_catalyst_prompt)
    )

    LaunchedEffect(showSaved) {
        if (showSaved) {
            kotlinx.coroutines.delay(2000)
            showSaved = false
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.presentation_batch_f_preset_prompts),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    OutlinedButton(
                        onClick = { 
                            localPrompt = preset.second
                        },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Text(text = preset.first, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = localPrompt,
                onValueChange = { localPrompt = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 200.dp),
                placeholder = { Text(stringResource(R.string.presentation_batch_f_enter_system_prompt_placeholder)) },
                minLines = 3,
                maxLines = 6
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = {
                        onSystemPromptSave(localPrompt)
                        showSaved = true
                    },
                    enabled = hasChanges
                ) {
                    Text(stringResource(R.string.presentation_batch_f_save))
                }
                if (!isDefault) {
                    OutlinedButton(onClick = {
                        onReset()
                    }) {
                        Text(stringResource(R.string.presentation_batch_f_reset))
                    }
                }
                if (showSaved) {
                    Text(
                        text = stringResource(R.string.presentation_batch_f_saved),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
