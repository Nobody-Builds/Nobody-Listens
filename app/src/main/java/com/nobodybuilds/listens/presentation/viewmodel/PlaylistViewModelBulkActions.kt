package com.nobodybuilds.listens.presentation.viewmodel

import android.util.Log
import android.content.Intent
import android.widget.Toast
import com.nobodybuilds.listens.data.model.Playlist
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.playlist.M3uManager
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.Context
import com.nobodybuilds.listens.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope

/**
 * Multi-select playlist actions (merge, share, export) used by [PlaylistViewModel].
 */
internal class PlaylistBulkActions(
    private val scope: CoroutineScope,
    private val _uiState: StateFlow<PlaylistUiState>,
    private val _playlistCreationEvent: MutableSharedFlow<Boolean>,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val m3uManager: M3uManager,
    private val context: Context
) {

    /**
     * Merge selected playlists into a new playlist
     * Collects all songs from all selected playlists (removing duplicates)
     */
    fun mergeSelectedPlaylists(playlistIds: List<String>, newPlaylistName: String) {
        if (newPlaylistName.isBlank()) return

        scope.launch {
            try {
                // Get all songs from selected playlists
                val selectedPlaylists = _uiState.value.playlists.filter { it.id in playlistIds }
                val mergedSongIds = selectedPlaylists
                    .flatMap { it.songIds }
                    .distinct() // Remove duplicates
                    .toList()

                if (mergedSongIds.isNotEmpty()) {
                    // Create new playlist with merged songs
                    playlistPreferencesRepository.createPlaylist(newPlaylistName, mergedSongIds)
                    _playlistCreationEvent.emit(true)
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error merging playlists", e)
            }
        }
    }

    /**
     * Get all playlists with their song data for bulk operations
     */
    suspend fun getPlaylistsWithSongs(playlistIds: List<String>): List<Pair<Playlist, List<Song>>> {
        return try {
            val selectedPlaylists = _uiState.value.playlists.filter { it.id in playlistIds }
            selectedPlaylists.map { playlist ->
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                playlist to songs
            }
        } catch (e: Exception) {
            Log.e("PlaylistViewModel", "Error getting playlists with songs", e)
            emptyList()
        }
    }

    /**
     * Share all selected playlists as M3U files in a ZIP
     */
    fun shareSelectedPlaylistsAsZip(playlistIds: List<String>, activity: android.app.Activity?) {
        if (activity == null) {
            Log.w("PlaylistViewModel", "Activity is null, cannot share")
            return
        }

        scope.launch {
            try {
                Log.d("PlaylistViewModel", "Starting share of ${playlistIds.size} playlists")
                // Get all selected playlists with their songs
                val playlistsWithSongs = getPlaylistsWithSongs(playlistIds)

                if (playlistsWithSongs.isEmpty()) {
                    Log.w("PlaylistViewModel", "No playlists found to share")
                    Toast.makeText(context, context.getString(R.string.playlist_none_to_share), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val shareFile: File
                val shareFileName: String
                val shareMimeType: String

                if (playlistsWithSongs.size == 1) {
                    // Single playlist: share M3U file directly
                    val (playlist, songs) = playlistsWithSongs.first()
                    val m3uContent = m3uManager.generateM3u(playlist, songs)
                    shareFileName = "${playlist.name}.m3u"
                    shareFile = File(context.cacheDir, shareFileName)
                    shareFile.writeText(m3uContent)
                    shareMimeType = "audio/mpegurl"
                    Log.d("PlaylistViewModel", "Created M3U file: ${shareFile.absolutePath}, size: ${shareFile.length()} bytes")
                } else {
                    // Multiple playlists: create ZIP file
                    val zipFileName = "Playlists_${playlistsWithSongs.first().first.name}_and_${playlistsWithSongs.size - 1}_more.zip"
                    shareFile = File(context.cacheDir, zipFileName)
                    val outputStream = FileOutputStream(shareFile)

                    java.util.zip.ZipOutputStream(outputStream).use { zipOut ->
                        playlistsWithSongs.forEach { (playlist, songs) ->
                            val m3uContent = m3uManager.generateM3u(playlist, songs)
                            val entry = java.util.zip.ZipEntry("${playlist.name}.m3u")
                            zipOut.putNextEntry(entry)
                            zipOut.write(m3uContent.toByteArray())
                            zipOut.closeEntry()
                        }
                    }

                    shareFileName = zipFileName
                    shareMimeType = "application/zip"
                    Log.d("PlaylistViewModel", "Created ZIP file: ${shareFile.absolutePath}, size: ${shareFile.length()} bytes")
                }

                // Share the file
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    shareFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = shareMimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                Log.d("PlaylistViewModel", "Launching share intent for: $shareFileName")
                activity.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.playlist_share_chooser_title)))
                val n = playlistsWithSongs.size
                val sharingMsg = context.resources.getQuantityString(R.plurals.sharing_playlists_message, n, n)
                Toast.makeText(context, sharingMsg, Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error sharing playlists", e)
                Toast.makeText(context, context.getString(R.string.playlist_share_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Merge multiple playlists into one new playlist
     * @param playlistIds List of playlist IDs to merge
     * @param newPlaylistName Name for the merged playlist
     */
    fun mergePlaylistsIntoOne(playlistIds: List<String>, newPlaylistName: String) {
        if (playlistIds.isEmpty() || newPlaylistName.isEmpty()) return

        scope.launch {
            try {
                // Get all playlists first
                val currentPlaylists = _uiState.value.playlists

                // Get all songs from selected playlists
                val allSongs = mutableSetOf<String>()
                playlistIds.forEach { playlistId ->
                    val playlist = currentPlaylists.find { it.id == playlistId }
                    if (playlist != null) {
                        allSongs.addAll(playlist.songIds)
                    }
                }

                // Create new playlist with merged songs
                val newPlaylist = Playlist(
                    id = UUID.randomUUID().toString(),
                    name = newPlaylistName,
                    songIds = allSongs.toList(),
                    createdAt = System.currentTimeMillis(),
                    lastModified = System.currentTimeMillis(),
                    isAiGenerated = false,
                    isQueueGenerated = false
                )

                playlistPreferencesRepository.createPlaylist(
                    name = newPlaylistName,
                    songIds = allSongs.toList(),
                    isAiGenerated = false,
                    isQueueGenerated = false
                )

                Log.d("PlaylistViewModel", "Successfully merged ${playlistIds.size} playlists into '$newPlaylistName' with ${allSongs.size} total unique songs")

            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error merging playlists", e)
            }
        }
    }

    /**
     * Export selected playlists as M3U files to device storage
     */
    fun exportPlaylistsAsM3u(playlistIds: List<String>) {
        if (playlistIds.isEmpty()) return

        scope.launch {
            try {
                Log.d("PlaylistViewModel", "Starting export of ${playlistIds.size} playlists")
                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                if (!musicDir.exists()) {
                    musicDir.mkdirs()
                }

                val exportDir = File(musicDir, "Nobody Listens Exports")
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }

                val playlistsWithSongs = getPlaylistsWithSongs(playlistIds)
                if (playlistsWithSongs.isEmpty()) {
                    Log.w("PlaylistViewModel", "No playlists found to export")
                    Toast.makeText(context, context.getString(R.string.playlist_none_to_export), Toast.LENGTH_SHORT).show()
                    return@launch
                }

                playlistsWithSongs.forEach { (playlist, songs) ->
                    val m3uContent = m3uManager.generateM3u(playlist, songs)
                    val file = File(exportDir, "${playlist.name}.m3u")
                    file.writeText(m3uContent)
                    Log.d("PlaylistViewModel", "Exported playlist '${playlist.name}' to ${file.absolutePath}")
                }

                Log.d("PlaylistViewModel", "Successfully exported ${playlistIds.size} playlists to $exportDir")
                val count = playlistsWithSongs.size
                val folderLabel = context.getString(R.string.playlist_export_folder_display)
                val exportedMsg = context.resources.getQuantityString(R.plurals.exported_playlists_message, count, count, folderLabel)
                Toast.makeText(context, exportedMsg, Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error exporting playlists", e)
                Toast.makeText(context, context.getString(R.string.playlist_export_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
