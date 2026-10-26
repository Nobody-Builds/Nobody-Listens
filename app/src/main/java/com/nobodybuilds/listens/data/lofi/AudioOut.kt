package com.nobodybuilds.listens.data.lofi

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.nobodybuilds.listens.data.lofi.dsp.StereoSource
import timber.log.Timber

/**
 * Plays a [StereoSource] through a float stereo [AudioTrack] on its own audio-priority thread.
 * The thread ends when the source reports it is finished (faded out) or [stop] is called.
 */
class AudioOut(
    private val source: StereoSource,
    private val name: String,
    private val sampleRate: Int = SAMPLE_RATE,
    private val onFinished: (() -> Unit)? = null,
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    val isRunning: Boolean get() = running

    @Synchronized
    fun start(): Boolean {
        if (running) return true
        val track = runCatching { buildTrack() }.getOrElse {
            Timber.tag(TAG).w(it, "Could not create AudioTrack for %s", name)
            return false
        }
        running = true
        thread = Thread({ loop(track) }, "nobody-listens-$name").apply { start() }
        return true
    }

    /** Stops immediately (use the source's fade first for a click-free stop). */
    @Synchronized
    fun stop() {
        running = false
        val t = thread
        thread = null
        if (t != null && t !== Thread.currentThread()) runCatching { t.join(500) }
    }

    private fun buildTrack(): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val targetBytes = (sampleRate / 10) * 2 * 4 // ~100 ms of stereo float
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBytes, targetBytes))
            .build()
    }

    private fun loop(track: AudioTrack) {
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        val left = FloatArray(BLOCK)
        val right = FloatArray(BLOCK)
        val interleaved = FloatArray(BLOCK * 2)
        try {
            track.play()
            while (running) {
                val more = source.render(left, right, BLOCK)
                var j = 0
                for (i in 0 until BLOCK) {
                    interleaved[j++] = left[i]
                    interleaved[j++] = right[i]
                }
                var offset = 0
                while (offset < interleaved.size && running) {
                    val written = track.write(interleaved, offset, interleaved.size - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) {
                        Timber.tag(TAG).w("AudioTrack write error %d (%s)", written, name)
                        running = false
                        break
                    }
                    offset += written
                }
                if (!more) break
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Render loop failed (%s)", name)
        } finally {
            running = false
            runCatching { track.pause(); track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
            onFinished?.invoke()
        }
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        private const val BLOCK = 512
        private const val TAG = "LofiAudioOut"
    }
}
