package com.nobodybuilds.listens.data.media

/**
 * Error types for metadata editing operations
 */
enum class MetadataEditError {
    FILE_NOT_FOUND,
    NO_WRITE_PERMISSION,
    INVALID_INPUT,
    UNSUPPORTED_FORMAT,
    TAGLIB_ERROR,
    TIMEOUT,
    FILE_CORRUPTED,
    IO_ERROR,
    UNKNOWN
}

// Data classes
data class SongMetadataEditResult(
    val success: Boolean,
    val updatedAlbumArtUri: String?,
    val error: MetadataEditError? = null,
    val errorMessage: String? = null
)

data class CoverArtUpdate(
    val bytes: ByteArray? = null,
    val mimeType: String = "image/jpeg",
    val isDeletion: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CoverArtUpdate

        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
