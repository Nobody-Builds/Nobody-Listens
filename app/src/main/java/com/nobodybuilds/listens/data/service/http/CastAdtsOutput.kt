package com.nobodybuilds.listens.data.service.http

import android.media.MediaCodec
import java.io.OutputStream

internal class CountingOutputStream(
    private val delegate: OutputStream
) : OutputStream() {
    var bytesWritten: Long = 0L
        private set

    override fun write(b: Int) {
        delegate.write(b)
        bytesWritten += 1L
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        bytesWritten += len.toLong()
    }

    override fun flush() {
        delegate.flush()
    }
}

// -------------------------------------------------------------------------
// TeeOutputStream: writes every byte to two delegates simultaneously.
// -------------------------------------------------------------------------
internal class TeeOutputStream(
    private val primary: OutputStream,
    private val secondary: OutputStream
) : OutputStream() {
    override fun write(b: Int) {
        primary.write(b)
        secondary.write(b)
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        primary.write(b, off, len)
        secondary.write(b, off, len)
    }
    override fun flush() {
        primary.flush()
        secondary.flush()
    }
    // Do NOT close delegates here — caller controls their lifecycles.
}

/** Drains all available encoder output, wrapping each AAC frame with an ADTS header. Returns true when EOS. */
internal fun drainEncoderToAdts(
    encoder: MediaCodec,
    info: MediaCodec.BufferInfo,
    sampleRate: Int,
    channels: Int,
    out: OutputStream
): Boolean {
    var eos = false
    var idx = encoder.dequeueOutputBuffer(info, 0)
    while (idx >= 0 || idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            idx = encoder.dequeueOutputBuffer(info, 0)
            continue
        }
        val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
        if (!isConfig && info.size > 0) {
            val buf = encoder.getOutputBuffer(idx)
            if (buf != null) {
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                writeAdtsHeader(out, sampleRate, channels, info.size)
                val bytes = ByteArray(info.size)
                buf.get(bytes)
                out.write(bytes)
            }
        }
        encoder.releaseOutputBuffer(idx, false)
        if (isEos) { eos = true; break }
        idx = encoder.dequeueOutputBuffer(info, 0)
    }
    return eos
}

/**
 * Writes a 7-byte ADTS header for a single AAC-LC frame.
 * ADTS = Audio Data Transport Stream — allows AAC frames to be streamed without MP4 container.
 */
private fun writeAdtsHeader(out: OutputStream, sampleRate: Int, channels: Int, aacFrameSize: Int) {
    val freqIdx = when (sampleRate) {
        96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3
        44100 -> 4; 32000 -> 5; 24000 -> 6; 22050 -> 7
        16000 -> 8; 12000 -> 9; 11025 -> 10; 8000 -> 11
        7350 -> 12; else -> 4
    }
    val frameLen = aacFrameSize + 7 // total = payload + 7-byte header
    // Profile = AAC-LC (object type 2 → stored as 2-1 = 1 → bits 01)
    // Byte layout per ADTS spec (ISO 13818-7):
    //  [sync:12][id:1][layer:2][protection_absent:1]
    //  [profile_obj_type:2][samp_freq_idx:4][private:1][channel_cfg:3][orig:1][copy:1][home:1]
    //  [copyright_id_bit:1][copyright_id_start:1][frame_length:13][buffer_fullness:11][num_raw_blocks:2]
    out.write(
        byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),  // MPEG-4, layer=0, no CRC
            (0x40 or (freqIdx shl 2) or (channels shr 2)).toByte(),
            ((channels and 0x3) shl 6 or (frameLen shr 11)).toByte(),
            (frameLen shr 3 and 0xFF).toByte(),
            ((frameLen and 0x7) shl 5 or 0x1F).toByte(),    // buf_fullness bits 10..6 = 0x1F
            0xFC.toByte()   // buf_fullness bits 5..0 = 0x3F, num_raw_blocks = 0
        )
    )
}
