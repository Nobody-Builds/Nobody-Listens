package com.nobodybuilds.listens.data.ytmusic

import org.gagravarr.ogg.OggPacket
import org.gagravarr.opus.OpusAudioData
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusInfo
import org.gagravarr.opus.OpusTags
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/**
 * Copies the Opus packets of a YouTube WebM audio file into an Ogg Opus file,
 * without re-encoding, so the download keeps its quality and can carry tags.
 */
object YouTubeMusicOpusRemuxer {

    data class Tags(
        val title: String,
        val artist: String,
        val album: String,
        val cover: ByteArray? = null,
        val coverMimeType: String = "image/jpeg",
    )

    fun remux(input: InputStream, output: OutputStream, tags: Tags) {
        val webm = readWebm(input)
        val info = OpusInfo(OggPacket(webm.opusHead))
        val opusTags = OpusTags().apply {
            addComment(KEY_TITLE, tags.title)
            if (tags.artist.isNotBlank()) addComment(KEY_ARTIST, tags.artist)
            if (tags.album.isNotBlank()) addComment(KEY_ALBUM, tags.album)
            tags.cover?.let { addComment("METADATA_BLOCK_PICTURE", pictureBlock(it, tags.coverMimeType)) }
        }
        val opus = OpusFile(output, info, opusTags)
        // OpusFile starts a new page whenever the granule changes, so share one granule
        // per small group of packets instead of paying a page header for every packet.
        var granule = info.preSkip.toLong()
        webm.packets.chunked(PACKETS_PER_PAGE).forEach { group ->
            val audio = group.map { OpusAudioData(it) }
            granule += audio.sumOf { it.numberOfSamples.toLong() }
            audio.forEach { packet ->
                packet.granulePosition = granule
                opus.writeAudioData(packet)
            }
        }
        opus.close()
    }

    private class WebmAudio(val opusHead: ByteArray, val packets: List<ByteArray>)

    private fun readWebm(input: InputStream): WebmAudio {
        val data = DataInputStream(BufferedInputStream(input, 64 * 1024))
        var codecId: String? = null
        var codecPrivate: ByteArray? = null
        val packets = mutableListOf<ByteArray>()
        while (true) {
            val id = readId(data) ?: break
            val size = readSize(data)
            when (id) {
                // Master elements are entered in place, which also copes with unknown sizes.
                SEGMENT, CLUSTER, TRACKS, TRACK_ENTRY, BLOCK_GROUP -> Unit
                CODEC_ID -> codecId = String(readBytes(data, size), Charsets.US_ASCII).trimEnd('\u0000')
                CODEC_PRIVATE -> codecPrivate = readBytes(data, size)
                SIMPLE_BLOCK, BLOCK -> packets += blockFrame(readBytes(data, size))
                else -> skip(data, size)
            }
        }
        if (codecId != "A_OPUS") throw IOException("Not an Opus WebM file ($codecId)")
        val head = codecPrivate ?: throw IOException("Missing OpusHead")
        if (packets.isEmpty()) throw IOException("No audio in the WebM file")
        return WebmAudio(head, packets)
    }

    /** Block layout: track number (vint), 16-bit timecode, flags, then the frame. */
    private fun blockFrame(block: ByteArray): ByteArray {
        val trackLength = vintLength(block[0].toInt() and 0xFF)
        val flags = block[trackLength + 2].toInt() and 0xFF
        if (flags and 0x06 != 0) throw IOException("Laced WebM blocks are not supported")
        return block.copyOfRange(trackLength + 3, block.size)
    }

    private fun readId(data: DataInputStream): Long? {
        val first = data.read()
        if (first < 0) return null
        val length = vintLength(first)
        var value = first.toLong()
        repeat(length - 1) { value = (value shl 8) or data.readUnsignedByte().toLong() }
        return value
    }

    private fun readSize(data: DataInputStream): Long {
        val first = data.readUnsignedByte()
        val length = vintLength(first)
        var value = (first and (0xFF shr length)).toLong()
        var allOnes = value == (0xFF shr length).toLong()
        repeat(length - 1) {
            val next = data.readUnsignedByte()
            if (next != 0xFF) allOnes = false
            value = (value shl 8) or next.toLong()
        }
        return if (allOnes) UNKNOWN_SIZE else value
    }

    private fun vintLength(first: Int): Int {
        for (length in 1..8) {
            if (first and (0x80 shr (length - 1)) != 0) return length
        }
        throw IOException("Invalid EBML variable-length integer")
    }

    private fun readBytes(data: DataInputStream, size: Long): ByteArray {
        if (size == UNKNOWN_SIZE || size > MAX_ELEMENT_BYTES) throw IOException("WebM element too large")
        return ByteArray(size.toInt()).also { data.readFully(it) }
    }

    private fun skip(data: DataInputStream, size: Long) {
        if (size == UNKNOWN_SIZE) throw IOException("Unknown-size WebM element")
        var left = size
        while (left > 0) {
            val skipped = data.skip(left)
            if (skipped <= 0) {
                if (data.read() < 0) throw EOFException()
                left -= 1
            } else {
                left -= skipped
            }
        }
    }

    /** FLAC picture block, the cover art format Ogg Vorbis comments use. */
    internal fun pictureBlock(image: ByteArray, mimeType: String): String {
        val out = ByteArrayOutputStream()
        fun int(value: Int) {
            out.write(value ushr 24 and 0xFF)
            out.write(value ushr 16 and 0xFF)
            out.write(value ushr 8 and 0xFF)
            out.write(value and 0xFF)
        }
        val mime = mimeType.toByteArray(Charsets.UTF_8)
        val description = "Front Cover".toByteArray(Charsets.UTF_8)
        int(3)
        int(mime.size)
        out.write(mime)
        int(description.size)
        out.write(description)
        repeat(4) { int(0) } // width, height, depth, colors: optional
        int(image.size)
        out.write(image)
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }

    private const val KEY_TITLE = "TITLE"
    private const val KEY_ARTIST = "ARTIST"
    private const val KEY_ALBUM = "ALBUM"
    private const val PACKETS_PER_PAGE = 20
    private const val UNKNOWN_SIZE = -1L
    private const val MAX_ELEMENT_BYTES = 16L * 1024 * 1024

    private const val SEGMENT = 0x18538067L
    private const val CLUSTER = 0x1F43B675L
    private const val TRACKS = 0x1654AE6BL
    private const val TRACK_ENTRY = 0xAEL
    private const val BLOCK_GROUP = 0xA0L
    private const val CODEC_ID = 0x86L
    private const val CODEC_PRIVATE = 0x63A2L
    private const val SIMPLE_BLOCK = 0xA3L
    private const val BLOCK = 0xA1L
}
