package com.nobodybuilds.listens.data.service

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaSession
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.auto.AutoMediaBrowseTree
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Android Auto / external controllers: turns a requested song into its browsed context's queue
 * (album, artist, playlist…), resolves media ids, and grants read access to artwork URIs.
 */
internal class AutoContextQueueResolver(
    private val context: Context,
    private val autoMediaBrowseTree: AutoMediaBrowseTree,
    private val musicRepository: MusicRepository,
    private val artworkLoader: WidgetArtworkLoader
) {
    private val packageName: String get() = context.packageName
    private val controllerLastBrowsedParent = mutableMapOf<String, String>()

    data class ContextQueueResolution(
        val mediaItems: MutableList<MediaItem>,
        val startIndex: Int
    )

    private fun controllerKey(controller: MediaSession.ControllerInfo): String {
        return "${controller.packageName}:${controller.uid}"
    }

    fun rememberLastBrowsedParent(controller: MediaSession.ControllerInfo, parentId: String) {
        synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent[controllerKey(controller)] = parentId
        }
    }

    private fun getLastBrowsedParent(controller: MediaSession.ControllerInfo): String? {
        return synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent[controllerKey(controller)]
        }
    }

    fun clearLastBrowsedParent(controller: MediaSession.ControllerInfo) {
        synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent.remove(controllerKey(controller))
        }
    }

    suspend fun resolveContextQueueForRequestedItem(
        requestedItem: MediaItem,
        controller: MediaSession.ControllerInfo
    ): ContextQueueResolution? {
        var contextType = requestedItem.mediaMetadata.extras
            ?.getString(AutoMediaBrowseTree.CONTEXT_TYPE_EXTRA)
        var contextId = requestedItem.mediaMetadata.extras
            ?.getString(AutoMediaBrowseTree.CONTEXT_ID_EXTRA)

        if (contextType.isNullOrBlank()) {
            val parentId = requestedItem.mediaMetadata.extras
                ?.getString(AutoMediaBrowseTree.CONTEXT_PARENT_ID_EXTRA)
                ?: getLastBrowsedParent(controller)
            val parentContext = parentId?.let { resolveAutoContextFromParentId(it) }
            contextType = parentContext?.first
            contextId = parentContext?.second
        }

        if (contextType.isNullOrBlank()) {
            return null
        }

        val queueSongs = autoMediaBrowseTree.getSongsForContext(contextType, contextId)
        if (queueSongs.isEmpty()) {
            return null
        }

        val startIndex = queueSongs.indexOfFirst { it.id == requestedItem.mediaId }
        if (startIndex < 0) {
            return null
        }

        val queueMediaItems = queueSongs.map { song ->
            MediaItemBuilder.buildForExternalController(context, song)
        }.toMutableList()

        return ContextQueueResolution(
            mediaItems = queueMediaItems,
            startIndex = startIndex
        )
    }

    suspend fun resolveMediaItemsByIds(
        requestedItems: List<MediaItem>
    ): TrustedMediaItemsResolution {
        val songIds = requestedItems.map { it.mediaId }
        val songs = musicRepository.getSongsByIds(songIds).first()
        val songMap = songs.associateBy { it.id }

        return resolveMediaItemsWithTrustedArtworkGrants(requestedItems) { mediaId ->
            songMap[mediaId]?.let { song ->
                MediaItemBuilder.buildForExternalController(context, song)
            }
        }
    }

    fun grantArtworkUriPermissions(
        targetPackage: String,
        mediaItems: List<MediaItem>
    ) {
        if (targetPackage.isBlank()) return

        val providerAuthority = "$packageName.provider"
        mediaItems.forEach { mediaItem ->
            val artworkUri = artworkLoader.resolveArtworkUri(mediaItem.mediaMetadata) ?: return@forEach
            if (artworkUri.scheme?.lowercase() != "content" || artworkUri.authority != providerAuthority) {
                return@forEach
            }

            runCatching {
                context.grantUriPermission(targetPackage, artworkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { error ->
                Timber.tag(TAG).w(
                    error,
                    "Failed to grant artwork URI permission to package=%s uri=%s",
                    targetPackage,
                    artworkUri
                )
            }
        }
    }

    private fun resolveAutoContextFromParentId(parentId: String): Pair<String, String?>? {
        return when {
            parentId == AutoMediaBrowseTree.RECENT_ID -> AUTO_CONTEXT_RECENT to null
            parentId == AutoMediaBrowseTree.FAVORITES_ID -> AUTO_CONTEXT_FAVORITES to null
            parentId == AutoMediaBrowseTree.SONGS_ID -> AUTO_CONTEXT_ALL_SONGS to null
            parentId.startsWith(AutoMediaBrowseTree.ALBUM_PREFIX) -> {
                AUTO_CONTEXT_ALBUM to parentId.removePrefix(AutoMediaBrowseTree.ALBUM_PREFIX)
            }
            parentId.startsWith(AutoMediaBrowseTree.ARTIST_PREFIX) -> {
                AUTO_CONTEXT_ARTIST to parentId.removePrefix(AutoMediaBrowseTree.ARTIST_PREFIX)
            }
            parentId.startsWith(AutoMediaBrowseTree.PLAYLIST_PREFIX) -> {
                AUTO_CONTEXT_PLAYLIST to parentId.removePrefix(AutoMediaBrowseTree.PLAYLIST_PREFIX)
            }
            else -> null
        }
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
        const val AUTO_CONTEXT_RECENT = "recent"
        const val AUTO_CONTEXT_FAVORITES = "favorites"
        const val AUTO_CONTEXT_ALL_SONGS = "all_songs"
        const val AUTO_CONTEXT_ALBUM = "album"
        const val AUTO_CONTEXT_ARTIST = "artist"
        const val AUTO_CONTEXT_PLAYLIST = "playlist"
    }
}
