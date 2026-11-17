package app.enotone.library

import app.enotone.database.RemoteTrackEntity
import java.nio.charset.StandardCharsets
import java.util.Locale

/** URL equivalence only: no case folding of filenames, Unicode normalization or tag matching. */
internal object LibraryTrackIdentity {
    fun pathKey(path: String): String {
        val ascii = path.toByteArray(StandardCharsets.UTF_8).joinToString("") { byte ->
            val value = byte.toInt() and BYTE_MASK
            if (value <= ASCII_MAX) value.toChar().toString() else "%%%02X".format(Locale.ROOT, value)
        }
        return PERCENT_ESCAPE.replace(ascii) { match ->
            val value = match.value.substring(1).toInt(HEX_RADIX)
            val character = value.toChar()
            if (character in UNRESERVED) character.toString() else "%%%02X".format(Locale.ROOT, value)
        }
    }

    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xff
    private const val ASCII_MAX = 127
    private val PERCENT_ESCAPE = Regex("%[0-9a-fA-F]{2}")
    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
}

/** Numeric-only result; raw Room rows are inspected before catalogue de-duplication. */
data class LibraryDuplicateAudit(val duplicateGroups: Int, val affectedAlbums: Int)

object LibraryDuplicateDiagnostics {
    fun inspect(sourceKey: String, rows: List<RemoteTrackEntity>): LibraryDuplicateAudit {
        val present = rows.filter { it.sourceKey == sourceKey && it.missingScans == 0 }
        val duplicates = present.groupBy { LibraryTrackIdentity.pathKey(it.encodedPath) }
            .values.filter { it.size > 1 }
        val ids = duplicates.flatten().mapTo(hashSetOf()) { it.id }
        val albums = OfflineLibraryGrouping.albums(OfflineTrackCatalog.build(sourceKey, present))
        return LibraryDuplicateAudit(duplicates.size, albums.count { group ->
            group.trackIds.any { it in ids }
        })
    }
}
