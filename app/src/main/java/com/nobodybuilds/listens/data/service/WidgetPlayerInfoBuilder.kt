package com.nobodybuilds.listens.data.service

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.nobodybuilds.listens.data.model.PlayerInfo
import com.nobodybuilds.listens.data.model.WidgetThemeColors
import com.nobodybuilds.listens.data.preferences.AlbumArtColorAccuracy
import com.nobodybuilds.listens.data.preferences.AlbumArtPaletteStyle
import com.nobodybuilds.listens.data.preferences.ThemePreference
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.data.service.wear.buildWearThemePalette
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemePair
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemeProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Builds the [PlayerInfo] the home-screen widgets and the watch show: the current song (or the
 * Cast receiver's), its art and colours, the next few songs, and a queue revision for the watch.
 */
internal class WidgetPlayerInfoBuilder(
    private val applicationContext: Context,
    private val engine: DualPlayerEngine,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val castRemoteSync: CastRemoteSync,
    private val artworkLoader: WidgetArtworkLoader,
    private val state: MusicServiceState
) {
    private val packageName: String get() = applicationContext.packageName
    private val isManualShuffleEnabled by state::isManualShuffleEnabled

    // Color scheme cache: skip recomputation when art URI, palette style, and accuracy haven't changed
    private var cachedSchemeArtUri: String? = null
    private var cachedSchemePaletteStyle: AlbumArtPaletteStyle? = null
    private var cachedSchemeColorAccuracy: Int = AlbumArtColorAccuracy.DEFAULT
    private var cachedColorSchemePair: ColorSchemePair? = null

    private fun buildWearQueueRevision(
        timeline: Timeline,
        currentIndex: Int,
        currentMediaId: String?,
    ): String {
        val remoteClient = castRemoteSync.currentRemoteClient()
        val remoteStatus = remoteClient?.mediaStatus
        val remoteQueueItems = remoteStatus?.queueItems.orEmpty()
        if (remoteQueueItems.isNotEmpty()) {
            val remoteCurrentIndex = remoteQueueItems.indexOfFirst {
                it.itemId == remoteStatus?.currentItemId
            }.takeIf { it >= 0 } ?: 0
            val remoteTokens = remoteQueueItems.map { item ->
                item.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
                    ?: item.media?.contentId
                    ?: item.itemId.toString()
            }
            return encodeWearQueueRevision(remoteTokens, remoteStatus?.currentItemId ?: 0)
        }

        if (timeline.isEmpty) {
            return currentMediaId.orEmpty()
        }

        val window = Timeline.Window()
        val tokens = buildList(timeline.windowCount) {
            for (index in 0 until timeline.windowCount) {
                timeline.getWindow(index, window)
                val mediaItem = window.mediaItem
                add(
                    mediaItem.mediaId.ifBlank {
                        mediaItem.localConfiguration?.uri?.toString()
                            ?: mediaItem.mediaMetadata.title?.toString()
                            ?: index.toString()
                    }
                )
            }
        }
        val safeCurrentIndex = currentIndex.coerceIn(0, (timeline.windowCount - 1).coerceAtLeast(0))
        return encodeWearQueueRevision(tokens, safeCurrentIndex)
    }

    private fun encodeWearQueueRevision(queueTokens: List<String>, currentIndex: Int): String {
        if (queueTokens.isEmpty()) return ""
        return buildString {
            append(currentIndex)
            append('|')
            queueTokens.forEachIndexed { index, token ->
                if (index > 0) append(',')
                append(token)
            }
        }.hashCode().toString()
    }

    suspend fun buildPlayerInfo(): PlayerInfo {
        val player = engine.masterPlayer
        // Batch all main-thread reads into a single context switch (was 7 separate hops → 1)
        var currentItem: MediaItem? = null
        var isPlaying = false
        var repeatMode = Player.REPEAT_MODE_OFF
        var currentPosition = 0L
        var totalDuration = 0L
        var snapshotWindowIndex = 0
        var snapshotTimeline: Timeline = Timeline.EMPTY

        withContext(Dispatchers.Main) {
            currentItem = player.currentMediaItem
            isPlaying = player.isPlaying
            repeatMode = player.repeatMode
            currentPosition = player.currentPosition
            totalDuration = player.duration.coerceAtLeast(0)
            snapshotWindowIndex = player.currentMediaItemIndex
            snapshotTimeline = player.currentTimeline
        }

        var shuffleEnabled = isManualShuffleEnabled // Manual shuffle for sync with PlayerViewModel

        var title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
        var artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
        var mediaId = currentItem?.mediaId
        var artworkUri = artworkLoader.resolveWidgetArtworkUriCandidates(currentItem?.mediaMetadata).firstOrNull()
        var artworkData = currentItem?.mediaMetadata?.artworkData

        castRemoteSync.resolveCastRemoteSnapshot()?.let { remote ->
            if (remote.title.isNotBlank()) {
                title = remote.title
            }
            if (remote.artist.isNotBlank()) {
                artist = remote.artist
            }
            if (!remote.songId.isNullOrBlank()) {
                mediaId = remote.songId
            }
            if (remote.artworkUri != null) {
                artworkUri = remote.artworkUri
                artworkData = null
            }
            isPlaying = remote.isPlaying
            currentPosition = remote.currentPositionMs
            if (remote.totalDurationMs > 0L) {
                totalDuration = remote.totalDurationMs
            }
            repeatMode = remote.repeatMode
            shuffleEnabled = remote.isShuffleEnabled
        }

        val artworkCandidates = artworkLoader.resolveWidgetArtworkUriCandidates(
            metadata = currentItem?.mediaMetadata,
            preferredArtworkUri = artworkUri,
        )
        val (artBytes, artUriString) = artworkLoader.getAlbumArtForWidget(
            mediaId = mediaId,
            embeddedArt = artworkData,
            artUris = artworkCandidates,
        )

        // Merge theme preference reads into a single context switch
        val (playerTheme, paletteStyle, colorAccuracyLevel) = withContext(Dispatchers.IO) {
            Triple(
                themePreferencesRepository.playerThemePreferenceFlow.first(),
                AlbumArtPaletteStyle.fromStorageKey(themePreferencesRepository.albumArtPaletteStyleFlow.first().storageKey),
                AlbumArtColorAccuracy.clamp(themePreferencesRepository.albumArtColorAccuracyFlow.first())
            )
        }

        val schemePair: ColorSchemePair? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && playerTheme == ThemePreference.DYNAMIC ->
                ColorSchemePair(
                    light = dynamicLightColorScheme(applicationContext),
                    dark = dynamicDarkColorScheme(applicationContext)
                )
            artUriString != null ->
                // Skip heavy palette recomputation when art, style, and accuracy haven't changed
                if (
                    artUriString == cachedSchemeArtUri &&
                    paletteStyle == cachedSchemePaletteStyle &&
                    colorAccuracyLevel == cachedSchemeColorAccuracy
                ) {
                    cachedColorSchemePair
                } else {
                    colorSchemeProcessor.getOrGenerateColorScheme(
                        albumArtUri = artUriString,
                        paletteStyle = paletteStyle,
                        colorAccuracyLevel = colorAccuracyLevel
                    ).also {
                        cachedSchemeArtUri = artUriString
                        cachedSchemePaletteStyle = paletteStyle
                        cachedSchemeColorAccuracy = colorAccuracyLevel
                        cachedColorSchemePair = it
                    }
                }
            else -> null
        }

        val widgetColors = schemePair?.let {
            WidgetThemeColors(
                lightSurfaceContainer = it.light.surfaceContainer.toArgb(),
                lightSurfaceContainerLowest = it.light.surfaceContainerLowest.toArgb(),
                lightSurfaceContainerLow = it.light.surfaceContainerLow.toArgb(),
                lightSurfaceContainerHigh = it.light.surfaceContainerHigh.toArgb(),
                lightSurfaceContainerHighest = it.light.surfaceContainerHighest.toArgb(),
                lightTitle = it.light.onSurface.toArgb(),
                lightArtist = it.light.onSurfaceVariant.toArgb(),
                lightPlayPauseBackground = it.light.primary.toArgb(),
                lightPlayPauseIcon = it.light.onPrimary.toArgb(),
                lightPrevNextBackground = it.light.onPrimary.toArgb(),
                lightPrevNextIcon = it.light.primary.toArgb(),
                
                darkSurfaceContainer = it.dark.surfaceContainer.toArgb(),
                darkSurfaceContainerLowest = it.dark.surfaceContainerLowest.toArgb(),
                darkSurfaceContainerLow = it.dark.surfaceContainerLow.toArgb(),
                darkSurfaceContainerHigh = it.dark.surfaceContainerHigh.toArgb(),
                darkSurfaceContainerHighest = it.dark.surfaceContainerHighest.toArgb(),
                darkTitle = it.dark.onSurface.toArgb(),
                darkArtist = it.dark.onSurfaceVariant.toArgb(),
                darkPlayPauseBackground = it.dark.primary.toArgb(),
                darkPlayPauseIcon = it.dark.onPrimary.toArgb(),
                darkPrevNextBackground = it.dark.onPrimary.toArgb(),
                darkPrevNextIcon = it.dark.primary.toArgb()
            )
        }
        val wearThemePalette = schemePair?.let { buildWearThemePalette(it.dark) }

        val isFavorite = state.isSongFavorite(mediaId)
        val wearQueueRevision = buildWearQueueRevision(
            timeline = snapshotTimeline,
            currentIndex = snapshotWindowIndex,
            currentMediaId = mediaId,
        )

        val queueItems = mutableListOf<com.nobodybuilds.listens.data.model.QueueItem>()
        // Reuse snapshotTimeline / snapshotWindowIndex captured at the top — no extra main-thread hop
        if (!snapshotTimeline.isEmpty) {
            val window = Timeline.Window()

            // Up next: the songs after the current one (wrap to the start only with repeat all).
            val startIndex = when {
                snapshotWindowIndex + 1 < snapshotTimeline.windowCount -> snapshotWindowIndex + 1
                repeatMode == Player.REPEAT_MODE_ALL -> 0
                else -> snapshotTimeline.windowCount
            }

            // Limitar el número de elementos de la cola a 4
            val endIndex = (startIndex + 4).coerceAtMost(snapshotTimeline.windowCount)
            for (i in startIndex until endIndex) {
                snapshotTimeline.getWindow(i, window)
                val mediaItem = window.mediaItem
                val songId = mediaItem.mediaId.toLongOrNull()
                if (songId != null) {
                    val initialQueueArtworkUri = artworkLoader.resolveWidgetArtworkUriCandidates(mediaItem.mediaMetadata)
                        .firstOrNull()
                    val queueArtworkUri = when {
                        initialQueueArtworkUri == null -> artworkLoader.resolveRepositoryArtworkUri(mediaItem.mediaId)
                        initialQueueArtworkUri.scheme?.lowercase() == "content" &&
                            initialQueueArtworkUri.authority == "$packageName.provider" ->
                            artworkLoader.resolveRepositoryArtworkUri(mediaItem.mediaId) ?: initialQueueArtworkUri
                        else -> initialQueueArtworkUri
                    }
                    queueItems.add(
                        com.nobodybuilds.listens.data.model.QueueItem(
                            id = songId,
                            albumArtUri = queueArtworkUri?.toString(),
                            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                            artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                        )
                    )
                }
            }
        }

        return PlayerInfo(
            songTitle = title,
            artistName = artist,
            isPlaying = isPlaying,
            albumArtUri = artUriString,
            albumArtBitmapData = artBytes,
            currentPositionMs = currentPosition,
            totalDurationMs = totalDuration,
            isFavorite = isFavorite,
            queue = queueItems,
            themeColors = widgetColors,
            isShuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            wearThemePalette = wearThemePalette,
            wearQueueRevision = wearQueueRevision,
        )
    }
}
