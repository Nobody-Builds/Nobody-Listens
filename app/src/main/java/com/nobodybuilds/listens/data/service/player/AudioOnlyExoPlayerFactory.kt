package com.nobodybuilds.listens.data.service.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicCache

/**
 * Builds one of [DualPlayerEngine]'s audio-only ExoPlayers: platform decoder policy, Hi-Fi float
 * output, the YouTube stream cache, and cloud URIs resolved through [resolver].
 */
@OptIn(UnstableApi::class)
internal fun buildAudioOnlyExoPlayer(
    context: Context,
    hiFiModeEnabled: Boolean,
    audioOffloadEnabled: Boolean,
    youTubeMusicCache: YouTubeMusicCache,
    resolver: ResolvingDataSource.Resolver
): ExoPlayer {
    val mediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val decoderInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )

        AudioDecoderPolicy.selectPlatformDecoders(mimeType, decoderInfos)
    }
    val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParams: Boolean
        ): AudioSink {
            return DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(hiFiModeEnabled)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        HiResSampleRateCapAudioProcessor(),
                        SurroundDownmixProcessor()
                    )
                )
                .build()
        }

        override fun buildVideoRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            eventHandler: android.os.Handler,
            eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
            allowedVideoJoiningTimeMs: Long,
            out: ArrayList<Renderer>
        ) {
            // Audio-only player: skip video renderers to save memory and "renderers" count.
        }

        override fun buildTextRenderers(
            context: Context,
            eventListener: androidx.media3.exoplayer.text.TextOutput,
            outputLooper: android.os.Looper,
            extensionRendererMode: Int,
            out: ArrayList<Renderer>
        ) {
            // Audio-only player: skip text renderers.
        }

        override fun buildCameraMotionRenderers(
            context: Context,
            extensionRendererMode: Int,
            out: ArrayList<Renderer>
        ) {
            // Audio-only player: skip camera motion renderers.
        }
    }.setEnableAudioFloatOutput(hiFiModeEnabled)
     .setMediaCodecSelector(mediaCodecSelector)
     .setEnableDecoderFallback(true)
     .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

    val audioAttributes = Media3AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    // YouTube streams go through a 7-day, 1 GB audio cache; everything else is read directly.
    val dataSourceFactory = youTubeMusicCache.dataSourceFactory(DefaultDataSource.Factory(context))
    val resolvingFactory = ResolvingDataSource.Factory(dataSourceFactory, resolver)
    val extractorsFactory = DefaultExtractorsFactory()
        .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(30_000, 60_000, 5_000, 5_000)
        .build()

    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory, extractorsFactory))
        .setLoadControl(loadControl)
        .build().apply {
        setAudioAttributes(audioAttributes, false)
        val offloadPreferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(
                if (audioOffloadEnabled) {
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                } else {
                    TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                }
            )
            .build()
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(offloadPreferences)
            .build()
        setHandleAudioBecomingNoisy(true)
        setWakeMode(C.WAKE_MODE_LOCAL)
        playWhenReady = false
    }
}
