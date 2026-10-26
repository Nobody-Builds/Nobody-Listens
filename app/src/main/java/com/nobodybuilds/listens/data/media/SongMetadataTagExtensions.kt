package com.nobodybuilds.listens.data.media

import android.graphics.BitmapFactory
import android.util.Base64
import org.gagravarr.opus.OpusTags
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.AbstractID3v2Frame
import org.jaudiotagger.tag.id3.AbstractID3v2Tag
import org.jaudiotagger.tag.id3.ID3v23Frame
import org.jaudiotagger.tag.id3.ID3v23Frames
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.id3.ID3v24Frame
import org.jaudiotagger.tag.id3.ID3v24Frames
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.jaudiotagger.tag.wav.WavTag
import timber.log.Timber
import java.io.ByteArrayOutputStream

private const val TAG = "SongMetadataEditor"

internal const val REPLAYGAIN_TRACK_GAIN_KEY = "REPLAYGAIN_TRACK_GAIN"
internal const val REPLAYGAIN_ALBUM_GAIN_KEY = "REPLAYGAIN_ALBUM_GAIN"
private const val MP4_REVERSE_DNS_ISSUER = "com.apple.iTunes"

internal sealed interface ReplayGainUpdate {
    data object Keep : ReplayGainUpdate
    data object Clear : ReplayGainUpdate
    data class Set(val formattedValue: String) : ReplayGainUpdate
}

internal fun OpusTags.replaceSingleComment(key: String, value: String?) {
    removeComments(key)
    if (!value.isNullOrBlank()) {
        addComment(key, value)
    }
}

internal fun OpusTags.applyReplayGainUpdate(key: String, update: ReplayGainUpdate) {
    when (update) {
        ReplayGainUpdate.Keep -> Unit
        ReplayGainUpdate.Clear -> removeComments(key)
        is ReplayGainUpdate.Set -> replaceSingleComment(key, update.formattedValue)
    }
}

internal fun OpusTags.applyCoverArtUpdate(update: CoverArtUpdate) {
    removeComments("METADATA_BLOCK_PICTURE")
    removeComments("COVERART")
    removeComments("COVERARTMIME")

    if (update.isDeletion) {
        Timber.tag(TAG).d("VORBISJAVA: Removed cover art")
        return
    }

    val imageBytes = update.bytes
    if (imageBytes == null) {
        Timber.tag(TAG).w("VORBISJAVA: Ignoring invalid CoverArtUpdate with no bytes and no deletion flag")
        return
    }

    addComment("METADATA_BLOCK_PICTURE", buildVorbisPictureBlock(imageBytes, update.mimeType))
    Timber.tag(TAG).d("VORBISJAVA: Embedded cover art (${update.mimeType}, ${imageBytes.size} bytes)")
}

private fun buildVorbisPictureBlock(imageBytes: ByteArray, mimeType: String): String {
    val safeMimeType = mimeType.takeIf { it.isNotBlank() } ?: "image/jpeg"
    val mimeBytes = safeMimeType.toByteArray(Charsets.UTF_8)
    val descriptionBytes = "Front Cover".toByteArray(Charsets.UTF_8)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
    val width = options.outWidth.takeIf { it > 0 } ?: 0
    val height = options.outHeight.takeIf { it > 0 } ?: 0

    val output = ByteArrayOutputStream()
    output.writeIntBigEndian(3)
    output.writeIntBigEndian(mimeBytes.size)
    output.write(mimeBytes)
    output.writeIntBigEndian(descriptionBytes.size)
    output.write(descriptionBytes)
    output.writeIntBigEndian(width)
    output.writeIntBigEndian(height)
    output.writeIntBigEndian(0)
    output.writeIntBigEndian(0)
    output.writeIntBigEndian(imageBytes.size)
    output.write(imageBytes)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private fun ByteArrayOutputStream.writeIntBigEndian(value: Int) {
    write((value ushr 24) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 8) and 0xFF)
    write(value and 0xFF)
}

internal fun MutableMap<String, Array<String>>.upsertOrRemove(key: String, value: String?) {
    if (value.isNullOrBlank()) {
        remove(key)
    } else {
        this[key] = arrayOf(value)
    }
}

internal fun MutableMap<String, Array<String>>.applyReplayGainUpdate(
    key: String,
    update: ReplayGainUpdate
) {
    when (update) {
        ReplayGainUpdate.Keep -> Unit
        ReplayGainUpdate.Clear -> remove(key)
        is ReplayGainUpdate.Set -> this[key] = arrayOf(update.formattedValue)
    }
}

internal fun Tag.applyReplayGainUpdate(key: String, update: ReplayGainUpdate) {
    when (update) {
        ReplayGainUpdate.Keep -> Unit
        ReplayGainUpdate.Clear -> removeReplayGainField(key)
        is ReplayGainUpdate.Set -> upsertReplayGainField(key, update.formattedValue)
    }
}

private fun Tag.upsertReplayGainField(key: String, value: String) {
    when (this) {
        is AbstractID3v2Tag -> upsertReplayGainId3Field(key, value)
        is WavTag -> {
            val id3Tag = getID3Tag() ?: ID3v24Tag().also(::setID3Tag)
            id3Tag.upsertReplayGainId3Field(key, value)
        }
        is FlacTag -> setField(key, value)
        is VorbisCommentTag -> setField(key, value)
        is Mp4Tag -> {
            val fieldId = replayGainMp4FieldId(key)
            deleteField(fieldId)
            setField(Mp4TagReverseDnsField(fieldId, MP4_REVERSE_DNS_ISSUER, key, value))
        }
        else -> Timber.tag(TAG).w("ReplayGain update is not supported for tag type: ${this::class.java.simpleName}")
    }
}

private fun Tag.removeReplayGainField(key: String) {
    when (this) {
        is AbstractID3v2Tag -> removeReplayGainId3Field(key)
        is WavTag -> getID3Tag()?.removeReplayGainId3Field(key)
        is FlacTag -> deleteField(key)
        is VorbisCommentTag -> deleteField(key)
        is Mp4Tag -> deleteField(replayGainMp4FieldId(key))
        else -> Timber.tag(TAG).w("ReplayGain removal is not supported for tag type: ${this::class.java.simpleName}")
    }
}

private fun AbstractID3v2Tag.upsertReplayGainId3Field(key: String, value: String) {
    val frame = if (this is ID3v23Tag) {
        ID3v23Frame(ID3v23Frames.FRAME_ID_V3_USER_DEFINED_INFO)
    } else {
        ID3v24Frame(ID3v24Frames.FRAME_ID_USER_DEFINED_INFO)
    }
    frame.body = FrameBodyTXXX().apply {
        setDescription(key)
        setText(value)
    }
    setField(frame)
}

private fun AbstractID3v2Tag.removeReplayGainId3Field(key: String) {
    val frameId = if (this is ID3v23Tag) {
        ID3v23Frames.FRAME_ID_V3_USER_DEFINED_INFO
    } else {
        ID3v24Frames.FRAME_ID_USER_DEFINED_INFO
    }
    val frames = getFields(frameId)
    val iterator = frames.listIterator()
    while (iterator.hasNext()) {
        val frame = iterator.next() as? AbstractID3v2Frame ?: continue
        val body = frame.body as? FrameBodyTXXX ?: continue
        if (body.description == key) {
            if (frames.size == 1) {
                removeFrame(frameId)
            } else {
                iterator.remove()
            }
        }
    }
}

private fun replayGainMp4FieldId(key: String): String = "----:$MP4_REVERSE_DNS_ISSUER:$key"
