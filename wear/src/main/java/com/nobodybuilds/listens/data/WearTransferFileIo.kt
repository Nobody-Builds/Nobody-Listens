package com.nobodybuilds.listens.data

import com.nobodybuilds.listens.data.local.LocalSongEntity
import java.io.File
import java.io.InputStream

internal fun cleanupReplacedSongFiles(
    previousSong: LocalSongEntity?,
    currentAudioPath: String,
    currentArtworkPath: String?,
) {
    if (previousSong == null) return
    if (previousSong.localPath != currentAudioPath) {
        runCatching { File(previousSong.localPath).delete() }
    }
    val oldArtwork = previousSong.artworkPath
    if (oldArtwork != null && oldArtwork != currentArtworkPath) {
        runCatching { File(oldArtwork).delete() }
    }
}

internal fun readLengthPrefixedString(inputStream: InputStream, label: String): String {
    val lengthBytes = ByteArray(4)
    var totalRead = 0
    while (totalRead < 4) {
        val read = inputStream.read(lengthBytes, totalRead, 4 - totalRead)
        if (read == -1) throw Exception("Stream ended before $label length")
        totalRead += read
    }

    val length = java.nio.ByteBuffer.wrap(lengthBytes).int
    if (length < 0 || length > 1024 * 1024) {
        throw Exception("Invalid $label length: $length")
    }

    val data = ByteArray(length)
    totalRead = 0
    while (totalRead < length) {
        val read = inputStream.read(data, totalRead, length - totalRead)
        if (read == -1) throw Exception("Stream ended before $label data")
        totalRead += read
    }
    return String(data, Charsets.UTF_8)
}

internal fun InputStream.readBytesSafely(): ByteArray {
    val buffer = ByteArray(8192)
    val output = java.io.ByteArrayOutputStream()
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
