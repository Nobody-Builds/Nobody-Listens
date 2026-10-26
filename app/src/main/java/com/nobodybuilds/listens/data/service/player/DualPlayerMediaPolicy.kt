package com.nobodybuilds.listens.data.service.player

import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.MediaItem

internal val LOCAL_MEDIA_SCHEMES = setOf("content", "file", "android.resource")
internal val REMOTE_MEDIA_SCHEMES = setOf("http", "https", "telegram", "netease", "qqmusic", "navidrome", "jellyfin", "gdrive", "ytmusic")

internal fun isLikelyLocalMedia(mediaItem: MediaItem?): Boolean {
    val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
    return scheme == null || scheme in LOCAL_MEDIA_SCHEMES
}

internal fun wakeModeFor(mediaItem: MediaItem?): Int {
    val scheme = mediaItem?.localConfiguration?.uri?.scheme?.lowercase()
    return if (scheme != null && scheme in REMOTE_MEDIA_SCHEMES) {
        C.WAKE_MODE_NETWORK
    } else {
        C.WAKE_MODE_LOCAL
    }
}

internal fun shouldDisableAudioOffloadByDefault(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    val isXiaomiFamilyDevice = manufacturer == "xiaomi" || brand == "xiaomi" || brand == "redmi" || brand == "poco"
    if (isXiaomiFamilyDevice && Build.VERSION.SDK_INT >= 36) return true

    val isGooglePixel = manufacturer == "google" && brand == "google"
    if (isGooglePixel && Build.VERSION.SDK_INT >= 34) return true

    return false
}
