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
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
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
            rawYear: String? = null,
            rawTrackNumber: String? = null,
            rawDiscNumber: String? = null,
        ): AudioTagFields = AudioTagFields(
            title = clean(title) ?: titleFromFileName(fallbackFileName),
            artist = clean(artist),
            album = clean(album),
            albumArtist = clean(albumArtist),
            year = parseYear(rawYear),
            trackNumber = parseOrdinal(rawTrackNumber, 999),
            discNumber = parseOrdinal(rawDiscNumber, 99),
        )

        /** Accept a release date YYYY or YYYY-MM[-DD] without fabricating a year. */
        private fun parseYear(raw: String?): Int? {
            val input = raw?.trim()?.takeIf { it.length in 4..10 } ?: return null
            if (!Regex("""[0-9]{4}(?:-[0-9]{2}(?:-[0-9]{2})?)?""")
                .matches(input)) return null
            val year = input.substring(0, 4).toIntOrNull() ?: return null
            return year.takeIf { it in 1000..2999 }
        }

        /** ID3/FLAC ordinals can be "3" or "3/12". No invented 0th track. */
        private fun parseOrdinal(raw: String?, max: Int): Int? {
            val input = raw?.trim()?.takeIf { it.length in 1..12 } ?: return null
            if (!Regex("""[0-9]{1,4}(?:/[0-9]{1,4})?""")
                .matches(input)) return null
            val ordinal = input.substringBefore('/').toIntOrNull() ?: return null
            return ordinal.takeIf { it in 1..max }
        }

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
