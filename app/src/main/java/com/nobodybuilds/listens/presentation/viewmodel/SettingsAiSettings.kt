package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.ai.provider.AiClientFactory
import com.nobodybuilds.listens.data.ai.provider.AiProvider
import com.nobodybuilds.listens.data.database.AiUsageDao
import com.nobodybuilds.listens.data.database.AiUsageEntity
import com.nobodybuilds.listens.data.preferences.AiPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** AI settings behind [SettingsViewModel]: provider, API keys, models, system prompts and usage. */
internal class SettingsAiSettings(
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val aiClientFactory: AiClientFactory,
    private val aiUsageDao: AiUsageDao,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SettingsUiState>,
    private val context: Context
) {
    // AI Provider State
    val aiProvider: StateFlow<String> = aiPreferencesRepository.aiProvider
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "GEMINI")

    // Generic AI settings reactive to the selected provider
    val currentAiApiKey: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getApiKey(AiProvider.fromString(provider)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    val currentAiModel: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getModel(AiProvider.fromString(provider)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    val currentAiSystemPrompt: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getSystemPrompt(AiProvider.fromString(provider)) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT)

    // Specific Provider StateFlows for UI Compatibility
    val geminiApiKey: StateFlow<String> = aiPreferencesRepository.geminiApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val geminiModel: StateFlow<String> = aiPreferencesRepository.geminiModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val geminiSystemPrompt: StateFlow<String> = aiPreferencesRepository.geminiSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT)

    val deepseekApiKey: StateFlow<String> = aiPreferencesRepository.deepseekApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val deepseekModel: StateFlow<String> = aiPreferencesRepository.deepseekModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val deepseekSystemPrompt: StateFlow<String> = aiPreferencesRepository.deepseekSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_DEEPSEEK_SYSTEM_PROMPT)

    val groqApiKey: StateFlow<String> = aiPreferencesRepository.groqApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val groqModel: StateFlow<String> = aiPreferencesRepository.groqModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val groqSystemPrompt: StateFlow<String> = aiPreferencesRepository.groqSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_GROQ_SYSTEM_PROMPT)

    val mistralApiKey: StateFlow<String> = aiPreferencesRepository.mistralApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val mistralModel: StateFlow<String> = aiPreferencesRepository.mistralModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val mistralSystemPrompt: StateFlow<String> = aiPreferencesRepository.mistralSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_MISTRAL_SYSTEM_PROMPT)

    val nvidiaApiKey: StateFlow<String> = aiPreferencesRepository.nvidiaApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val nvidiaModel: StateFlow<String> = aiPreferencesRepository.nvidiaModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val nvidiaSystemPrompt: StateFlow<String> = aiPreferencesRepository.nvidiaSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_NVIDIA_SYSTEM_PROMPT)

    val kimiApiKey: StateFlow<String> = aiPreferencesRepository.kimiApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val kimiModel: StateFlow<String> = aiPreferencesRepository.kimiModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val kimiSystemPrompt: StateFlow<String> = aiPreferencesRepository.kimiSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_KIMI_SYSTEM_PROMPT)

    val glmApiKey: StateFlow<String> = aiPreferencesRepository.glmApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val glmModel: StateFlow<String> = aiPreferencesRepository.glmModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val glmSystemPrompt: StateFlow<String> = aiPreferencesRepository.glmSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_GLM_SYSTEM_PROMPT)

    val openaiApiKey: StateFlow<String> = aiPreferencesRepository.openaiApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val openaiModel: StateFlow<String> = aiPreferencesRepository.openaiModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val openaiSystemPrompt: StateFlow<String> = aiPreferencesRepository.openaiSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_OPENAI_SYSTEM_PROMPT)

    val openrouterApiKey: StateFlow<String> = aiPreferencesRepository.openrouterApiKey
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val openrouterModel: StateFlow<String> = aiPreferencesRepository.openrouterModel
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")
    val openrouterSystemPrompt: StateFlow<String> = aiPreferencesRepository.openrouterSystemPrompt
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_OPENROUTER_SYSTEM_PROMPT)

    fun onAiApiKeyChange(apiKey: String) {
        scope.launch {
            val providerStr = aiProvider.value
            val provider = AiProvider.fromString(providerStr)
            aiPreferencesRepository.setApiKey(provider, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, providerStr)
            else clearModelsState(providerStr)
        }
    }

    // Specific on-change methods for UI binding
    fun onGeminiApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.GEMINI, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "GEMINI")
            else clearModelsState("GEMINI")
        }
    }
    fun onDeepseekApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.DEEPSEEK, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "DEEPSEEK")
            else clearModelsState("DEEPSEEK")
        }
    }
    fun onGroqApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.GROQ, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "GROQ")
            else clearModelsState("GROQ")
        }
    }
    fun onMistralApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.MISTRAL, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "MISTRAL")
            else clearModelsState("MISTRAL")
        }
    }
    fun onNvidiaApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.NVIDIA, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "NVIDIA")
            else clearModelsState("NVIDIA")
        }
    }
    fun onKimiApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.KIMI, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "KIMI")
            else clearModelsState("KIMI")
        }
    }
    fun onGlmApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.GLM, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "GLM")
            else clearModelsState("GLM")
        }
    }
    fun onOpenAiApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.OPENAI, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "OPENAI")
            else clearModelsState("OPENAI")
        }
    }
    fun onOpenrouterApiKeyChange(apiKey: String) {
        scope.launch {
            aiPreferencesRepository.setApiKey(AiProvider.OPENROUTER, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, "OPENROUTER")
            else clearModelsState("OPENROUTER")
        }
    }

    fun onAiModelChange(model: String) {
        scope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.setModel(provider, model)
        }
    }

    fun onGeminiModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.GEMINI, model) }
    fun onDeepseekModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.DEEPSEEK, model) }
    fun onGroqModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.GROQ, model) }
    fun onMistralModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.MISTRAL, model) }
    fun onNvidiaModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.NVIDIA, model) }
    fun onKimiModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.KIMI, model) }
    fun onGlmModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.GLM, model) }
    fun onOpenAiModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.OPENAI, model) }
    fun onOpenrouterModelChange(model: String) = scope.launch { aiPreferencesRepository.setModel(AiProvider.OPENROUTER, model) }

    fun onAiSystemPromptChange(prompt: String) {
        scope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.setSystemPrompt(provider, prompt)
        }
    }

    fun onGeminiSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.GEMINI, prompt) }
    fun onDeepseekSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.DEEPSEEK, prompt) }
    fun onGroqSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.GROQ, prompt) }
    fun onMistralSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.MISTRAL, prompt) }
    fun onNvidiaSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.NVIDIA, prompt) }
    fun onKimiSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.KIMI, prompt) }
    fun onGlmSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.GLM, prompt) }
    fun onOpenAiSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.OPENAI, prompt) }
    fun onOpenrouterSystemPromptChange(prompt: String) = scope.launch { aiPreferencesRepository.setSystemPrompt(AiProvider.OPENROUTER, prompt) }

    fun resetAiSystemPrompt() {
        scope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.resetSystemPrompt(provider)
        }
    }

    fun resetGeminiSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.GEMINI) }
    fun resetDeepseekSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.DEEPSEEK) }
    fun resetGroqSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.GROQ) }
    fun resetMistralSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.MISTRAL) }
    fun resetNvidiaSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.NVIDIA) }
    fun resetKimiSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.KIMI) }
    fun resetGlmSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.GLM) }
    fun resetOpenAiSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.OPENAI) }
    fun resetOpenrouterSystemPrompt() = scope.launch { aiPreferencesRepository.resetSystemPrompt(AiProvider.OPENROUTER) }

    fun clearAiUsageData() {
        scope.launch {
            aiUsageDao.clearUsage()
        }
    }

    val isSafeTokenLimitEnabled: StateFlow<Boolean> = aiPreferencesRepository.isSafeTokenLimitEnabled
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), true)

    val recentAiUsage: StateFlow<List<AiUsageEntity>> = aiUsageDao.getRecentUsages(20)
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPromptTokens: StateFlow<Int> = aiUsageDao.getTotalPromptTokens()
        .map { it ?: 0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val totalOutputTokens: StateFlow<Int> = aiUsageDao.getTotalOutputTokens()
        .map { it ?: 0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    val totalThoughtTokens: StateFlow<Int> = aiUsageDao.getTotalThoughtTokens()
        .map { it ?: 0 }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    fun setSafeTokenLimitEnabled(enabled: Boolean) {
        scope.launch {
            aiPreferencesRepository.setSafeTokenLimitEnabled(enabled)
        }
    }

    fun onAiProviderChange(provider: String) {
        scope.launch {
            aiPreferencesRepository.setAiProvider(provider)

            // Clear existing models immediately to show loading state
            uiState.update {
                it.copy(
                    availableModels = emptyList(),
                    modelsFetchError = null,
                    isLoadingModels = false
                )
            }

            // Small delay to let the provider preference propagate to StateFlows
            delay(100)

            // Fetch models for the newly selected provider if we have an API key
            val apiKey = aiPreferencesRepository.getApiKey(AiProvider.fromString(provider)).first()

            if (apiKey.isNotBlank()) {
                fetchAvailableModels(apiKey, provider)
            }
        }
    }

    fun loadModelsForCurrentProvider() {
        scope.launch {
            if (uiState.value.isLoadingModels) return@launch
            if (uiState.value.availableModels.isNotEmpty()) return@launch
            
            val provider = aiProvider.value
            val apiKey = aiPreferencesRepository.getApiKey(AiProvider.fromString(provider)).first()
            
            if (apiKey.isNotBlank()) {
                fetchAvailableModels(apiKey, provider)
            }
        }
    }

    private fun clearModelsState(provider: String) {
        uiState.update {
            it.copy(
                availableModels = emptyList(),
                modelsFetchError = null
            )
        }
        scope.launch {
            aiPreferencesRepository.setModel(AiProvider.fromString(provider), "")
        }
    }

    private fun fetchAvailableModels(apiKey: String, providerName: String) {
        scope.launch {
            uiState.update { it.copy(isLoadingModels = true, modelsFetchError = null) }
            try {
                val provider = AiProvider.fromString(providerName)
                val aiClient = aiClientFactory.createClient(provider, apiKey)
                val modelStrings = aiClient.getAvailableModels(apiKey)
                val models = modelStrings
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map { com.nobodybuilds.listens.data.ai.GeminiModel(it, formatModelDisplayName(it)) }
                
                uiState.update { 
                    it.copy(
                        availableModels = models, 
                        isLoadingModels = false,
                        modelsFetchError = null
                    ) 
                }

                // Auto-select first model if nothing is selected yet
                val currentModel = aiPreferencesRepository.getModel(provider).first()
                val availableModelNames = models.map { it.name }.toSet()
                if (models.isNotEmpty() && (currentModel.isBlank() || currentModel !in availableModelNames)) {
                    val firstModel = models.first().name
                    aiPreferencesRepository.setModel(provider, firstModel)
                }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(
                        isLoadingModels = false,
                        modelsFetchError = e.message ?: context.getString(R.string.models_fetch_failed),
                    )
                }
            }
        }
    }

    private fun formatModelDisplayName(modelName: String): String {
        return modelName
            .removePrefix("models/")
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
