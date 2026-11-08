package app.enotone.library

import java.text.Normalizer
import java.util.Locale

/**
 * User-private embedded audio metadata. Never log or export fields to CI diagnostics.
 * Independent from Android media APIs to allow deterministic JVM unit tests.
 */
data class AudioTagFields(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
) {
    companion object {
        private const val MAX_FIELD_CHARS = 512
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "opus", "m4a", "aac", "wav")

        fun fromRaw(
            title: String?,
            artist: String?,
            album: String?,
            albumArtist: String?,
            fallbackFileName: String,
        ): AudioTagFields = AudioTagFields(
            title = clean(title) ?: titleFromFileName(fallbackFileName),
            artist = clean(artist),
            album = clean(album),
            albumArtist = clean(albumArtist),
        )

        private fun titleFromFileName(filename: String): String? {
            val candidate = filename.trim()
            if (candidate.isEmpty()) return null
            val ext = candidate.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val basename = if (ext in AUDIO_EXTENSIONS) candidate.substringBeforeLast('.') else candidate
            return clean(basename)
        }

        private fun clean(raw: String?): String? {
            if (raw == null) return null
            // Control characters from file tags must not alter UI layout or logs.
            val clean = raw.map { c -> if (c.isISOControl()) ' ' else c }.joinToString("").trim()
            if (clean.isEmpty()) return null
            return Normalizer.normalize(clean, Normalizer.Form.NFC)
                .take(MAX_FIELD_CHARS)
                .trim()
                .takeIf { it.isNotEmpty() }
        }
    }
}
