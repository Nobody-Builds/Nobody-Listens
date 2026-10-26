package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.viewmodel.SettingsUiState
import com.nobodybuilds.listens.presentation.viewmodel.SettingsViewModel
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings > AI integration: provider, credentials, model, prompt and usage report. */
@Composable
internal fun SettingsAiCategoryContent(
    uiState: SettingsUiState,
    settingsViewModel: SettingsViewModel,
    aiProvider: String,
    currentAiApiKey: String,
    currentAiModel: String,
    currentAiSystemPrompt: String
) {
    // AI Provider Selection
    SettingsSubsection(title = stringResource(R.string.setcat_ai_provider_section)) {
        ThemeSelectorItem(
            label = stringResource(R.string.setcat_ai_provider_label),
            description = stringResource(R.string.setcat_ai_provider_desc),
            options = com.nobodybuilds.listens.data.ai.provider.AiProvider.entries.associate { it.name to it.displayName },
            selectedKey = aiProvider,
            onSelectionChanged = { settingsViewModel.onAiProviderChange(it) },
            leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.secondary) }
        )
        SwitchSettingItem(
            title = stringResource(R.string.setcat_safe_token_title),
            subtitle = if (uiState.isSafeTokenLimitEnabled) {
                stringResource(R.string.setcat_safe_token_on)
            } else {
                stringResource(R.string.setcat_safe_token_off)
            },
            checked = uiState.isSafeTokenLimitEnabled,
            onCheckedChange = { settingsViewModel.setSafeTokenLimitEnabled(it) },
            leadingIcon = {
                Icon(
                    painterResource(R.drawable.rounded_monitoring_24),
                    null,
                    tint = if (uiState.isSafeTokenLimitEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
            }
        )
    }
    
    // Consolidated API Key Section
    SettingsSubsection(title = stringResource(R.string.setcat_credentials)) {
        val provider = com.nobodybuilds.listens.data.ai.provider.AiProvider.fromString(aiProvider)
        val sourceLabel = when(provider) {
            com.nobodybuilds.listens.data.ai.provider.AiProvider.GEMINI -> stringResource(R.string.setcat_ai_source_gemini)
            com.nobodybuilds.listens.data.ai.provider.AiProvider.DEEPSEEK -> stringResource(R.string.setcat_ai_source_deepseek)
            com.nobodybuilds.listens.data.ai.provider.AiProvider.GROQ -> stringResource(R.string.setcat_ai_source_groq)
            com.nobodybuilds.listens.data.ai.provider.AiProvider.MISTRAL -> stringResource(R.string.setcat_ai_source_mistral)
            com.nobodybuilds.listens.data.ai.provider.AiProvider.NVIDIA -> stringResource(R.string.setcat_ai_source_nvidia)
            com.nobodybuilds.listens.data.ai.provider.AiProvider.KIMI -> stringResource(R.string.setcat_ai_source_kimi)
            com.nobodybuilds.listens.data.ai.provider.AiProvider.GLM -> stringResource(R.string.setcat_ai_source_glm)
            com.nobodybuilds.listens.data.ai.provider.AiProvider.OPENAI -> stringResource(R.string.setcat_ai_source_openai)
            com.nobodybuilds.listens.data.ai.provider.AiProvider.OPENROUTER -> "OpenRouter (openrouter.ai)"
        }
        
        AiApiKeyItem(
            apiKey = currentAiApiKey,
            onApiKeySave = { settingsViewModel.onAiApiKeyChange(it) },
            title = stringResource(R.string.setcat_ai_api_key_title, provider.displayName),
            subtitle = stringResource(R.string.setcat_ai_api_key_subtitle, sourceLabel)
        )
    }

    // Model Selection Section
    if (currentAiApiKey.isNotBlank()) {
        SettingsSubsection(title = stringResource(R.string.setcat_model_selection)) {
            if (uiState.isLoadingModels) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = stringResource(R.string.setcat_loading_models),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (uiState.modelsFetchError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.modelsFetchError ?: stringResource(R.string.models_fetch_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (uiState.availableModels.isNotEmpty()) {
                ThemeSelectorItem(
                    label = stringResource(R.string.setcat_ai_model_label),
                    description = stringResource(R.string.setcat_ai_model_desc),
                    options = uiState.availableModels.associate { it.name to it.displayName },
                    selectedKey = currentAiModel.ifEmpty { uiState.availableModels.firstOrNull()?.name ?: "" },
                    onSelectionChanged = { settingsViewModel.onAiModelChange(it) },
                    leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.secondary) }
                )
            }
        }
    }

    // Prompt Behavior Section
    SettingsSubsection(
        title = stringResource(R.string.setcat_prompt_behavior),
        addBottomSpace = false
    ) {
        AiSystemPromptItem(
            systemPrompt = currentAiSystemPrompt,
            defaultPrompt = com.nobodybuilds.listens.data.preferences.AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT,
            onSystemPromptSave = { settingsViewModel.onAiSystemPromptChange(it) },
            onReset = { settingsViewModel.resetAiSystemPrompt() },
            title = stringResource(R.string.setcat_system_prompt_title),
            subtitle = stringResource(R.string.setcat_system_prompt_subtitle)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSubsection(title = stringResource(R.string.setcat_ai_usage_report)) {
        val recentAiUsage by settingsViewModel.recentAiUsage.collectAsStateWithLifecycle()
        val totalPromptTokens by settingsViewModel.totalPromptTokens.collectAsStateWithLifecycle()
        val totalOutputTokens by settingsViewModel.totalOutputTokens.collectAsStateWithLifecycle()
        val totalThoughtTokens by settingsViewModel.totalThoughtTokens.collectAsStateWithLifecycle()

        val totalTokens = totalPromptTokens + totalOutputTokens + totalThoughtTokens
        val totalTokStr = String.format(Locale.US, "%,d", totalTokens)
        val promptTokStr = String.format(Locale.US, "%,d", totalPromptTokens)
        val outputTokStr = String.format(Locale.US, "%,d", totalOutputTokens)
        val thoughtTokStr = String.format(Locale.US, "%,d", totalThoughtTokens)

        ActionSettingsItem(
            title = stringResource(R.string.setcat_total_consumption_title),
            subtitle = stringResource(
                R.string.setcat_ai_usage_tokens_subtitle,
                totalTokStr,
                promptTokStr,
                outputTokStr,
                thoughtTokStr
            ),
            icon = {
                Icon(
                    painter = painterResource(R.drawable.rounded_monitoring_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            primaryActionLabel = stringResource(R.string.setcat_ai_clear_logs),
            onPrimaryAction = { settingsViewModel.clearAiUsageData() }
        )

        if (recentAiUsage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            var expanded by remember { mutableStateOf(false) }
            val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_monitoring_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.setcat_ai_activity_log_title, recentAiUsage.size),
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.cd_hide) else stringResource(R.string.cd_show),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                ) {
                    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    val groupedUsage = recentAiUsage.groupBy { 
                        dateFormat.format(Date(it.timestamp)) 
                    }

                    groupedUsage.forEach { (date, items) ->
                        AiUsageDateHeader(date = date)
                        items.forEach { usage ->
                            AiUsageLogItem(usage = usage)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
