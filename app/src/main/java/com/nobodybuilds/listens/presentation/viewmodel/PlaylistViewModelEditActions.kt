package com.nobodybuilds.listens.presentation.viewmodel

import android.net.Uri
import com.nobodybuilds.listens.data.DailyMixManager
import com.nobodybuilds.listens.data.model.SmartPlaylistRule
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.Context
import com.nobodybuilds.listens.data.ai.AiPlaylistGenerator
import com.nobodybuilds.listens.R
import java.util.UUID
import kotlinx.coroutines.CoroutineScope

/**
 * Creating and editing playlists (manual, smart and AI) for [PlaylistViewModel].
 */
internal class PlaylistEditActions(
    private val scope: CoroutineScope,
    private val _uiState: MutableStateFlow<PlaylistUiState>,
    private val _playlistCreationEvent: MutableSharedFlow<Boolean>,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val dailyMixManager: DailyMixManager,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val context: Context
) {

    private companion object {
        const val SMART_PLAYLIST_MAX_ITEMS = 100
    }

    fun createPlaylist(
        name: String,
        coverImageUri: String? = null,
        coverColor: Int? = null,
        coverIcon: String? = null,
        songIds: List<String> = emptyList(), // Added songIds parameter
        cropScale: Float = 1f,
        cropPanX: Float = 0f,
        cropPanY: Float = 0f,
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
        source: String = "LOCAL", // Mark source
        smartRuleKey: String? = null
    ) {
        com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().success()
        scope.launch {
            var savedCoverPath: String? = null

            if (coverImageUri != null) {
                // Generate a unique ID for the image file since we don't have the playlist ID yet
                val imageId = UUID.randomUUID().toString()
                savedCoverPath = savePlaylistCoverImage(
                    context,
                    Uri.parse(coverImageUri),
                    imageId,
                    cropScale,
                    cropPanX,
                    cropPanY
                )
            }

            val resolvedSmartRule = SmartPlaylistRule.fromStorageKey(smartRuleKey)
            val resolvedSongIds = if (resolvedSmartRule != null) {
                buildSmartPlaylistSongIds(
                    musicRepository = musicRepository,
                    dailyMixManager = dailyMixManager,
                    rule = resolvedSmartRule,
                    limit = SMART_PLAYLIST_MAX_ITEMS
                )
            } else {
                songIds
            }
            val resolvedSource = when {
                resolvedSmartRule != null && source == "LOCAL" -> "SMART"
                else -> source
            }

            playlistPreferencesRepository.createPlaylist(
                name = name,
                songIds = resolvedSongIds,
                isAiGenerated = isAiGenerated,
                isQueueGenerated = isQueueGenerated,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4,
                source = resolvedSource
            )
            _playlistCreationEvent.emit(true)
        }
    }

    fun updatePlaylistParameters(
        playlistId: String,
        name: String,
        coverImageUri: String?,
        coverColor: Int?,
        coverIcon: String?,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float,
        coverShapeType: String?,
        coverShapeDetail1: Float?,
        coverShapeDetail2: Float?,
        coverShapeDetail3: Float?,
        coverShapeDetail4: Float?
    ) {
        val currentPlaylist = _uiState.value.currentPlaylistDetails ?: return
        if (currentPlaylist.id != playlistId) return

        scope.launch {
            var savedCoverPath: String? = currentPlaylist.coverImageUri

            // If a new URI is provided and it's different from the existing one (and not null)
            // Or if we need to re-save because crop params changed?
            // For simplicity, if coverImageUri is passed and it's a content URI, we save it.
            // If it's the same string as savedCoverPath, we assume it's unchanged unless we want to force re-crop.
            // The UI will pass the Uri string. If it's a local file path, it's likely already saved.
            // But if the user selected a new image, it will be a content content:// uri.

            if (coverImageUri != null && coverImageUri != currentPlaylist.coverImageUri) {
                // Check if it is a content URI or a file path that is NOT the existing saved path
                if (coverImageUri.startsWith("content://") || (coverImageUri.startsWith("/") && coverImageUri != currentPlaylist.coverImageUri)) {
                    val imageId = UUID.randomUUID().toString()
                    val newPath = savePlaylistCoverImage(
                        context,
                        Uri.parse(coverImageUri),
                        imageId,
                        cropScale,
                        cropPanX,
                        cropPanY
                    )
                    if (newPath != null) {
                        savedCoverPath = newPath
                    }
                }
            } else if (coverImageUri == null) {
                // If passed null, it might mean remove cover? Or just no change?
                // For this implementation let's assume if the user cleared it, the UI passes null.
                // But we need to distinguish "no change" vs "remove".
                // In CreatePlaylist we have "selectedImageUri".
                // Let's assume the UI sends the desired final state.
                // NOTE: If the user didn't change the image, the UI might send the existing coverImageUri (which is a file path).
                // Or if they removed it, they send null.

                // However, we also have crop parameters. If image is unchanged but crop changed, we should re-save (re-crop)
                // if we have the original source. But we don't have the original source for the existing cover (we only have the cropped result).
                // So, we can only re-crop if we have a source URI.
                // This limitation implies: We can only update crop if we pick an image.
                // So if coverImageUri is the existing path, we ignore crop params.
                savedCoverPath = null // If explicit null passed, we remove it.
            }
            // Logic correction: 
            // If the UI passes the EXISTING file path, implies NO CHANGE to image.
            // If the UI passes a NEW content URI, implies NEW IMAGE (and we use crop params).
            // If the UI passes NULL, implies REMOVE IMAGE.
            if (coverImageUri == currentPlaylist.coverImageUri) {
                savedCoverPath = currentPlaylist.coverImageUri
            }


            val updatedPlaylist = currentPlaylist.copy(
                name = name,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4
            )

            // Optimistic update
            _uiState.update {
                it.copy(currentPlaylistDetails = updatedPlaylist)
            }

            playlistPreferencesRepository.updatePlaylist(updatedPlaylist)
        }
    }

    fun generateAiPlaylist(prompt: String, minLength: Int = 10, maxLength: Int = 50) {
        scope.launch {
            _uiState.update { it.copy(isAiGenerating = true, aiGenerationError = null) }

            try {
                val allSongs = withContext(Dispatchers.IO) {
                    musicRepository.getAllSongsOnce()
                }

                // Call AiPlaylistGenerator
                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongs,
                    minLength = minLength,
                    maxLength = maxLength
                )

                result.onSuccess { selectedSongs ->
                    // Create Playlist
                    val playlistName = "AI: $prompt".take(50)

                    playlistPreferencesRepository.createPlaylist(
                        name = playlistName,
                        songIds = selectedSongs.map { it.id },
                        isAiGenerated = true,
                        source = "AI" // Mark as AI source
                    )

                    _uiState.update { it.copy(isAiGenerating = false) }
                    _playlistCreationEvent.emit(true)
                }.onFailure { e ->
                    val errorMessage = if (e.message?.contains("API Key") == true) {
                        context.getString(R.string.ai_playlist_gemini_key_required)
                    } else {
                        e.message ?: context.getString(R.string.error_unknown)
                    }
                    _uiState.update { it.copy(isAiGenerating = false, aiGenerationError = errorMessage) }
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(isAiGenerating = false, aiGenerationError = e.message) }
            }
        }
    }
}
