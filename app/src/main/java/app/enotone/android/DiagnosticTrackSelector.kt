package app.enotone.android

import app.enotone.database.RemoteTrackEntity
import java.text.Normalizer

internal object DiagnosticTrackSelector {
    fun select(rows: List<RemoteTrackEntity>, file: String, fast: Boolean): RemoteTrackEntity? {
        val candidates = rows.filter { it.missingScans == 0 && (it.remoteSize ?: 0L) > 0L }
            .filter { !fast || it.fileName.endsWith(".mp3", true) || it.fileName.endsWith(".flac", true) }
        if (file.isBlank()) {
            return candidates.minWithOrNull(compareBy<RemoteTrackEntity> { !it.fileName.endsWith(".mp3", true) }
                .thenBy { it.id })
        }
        val path = file.trim()
        val name = path.removePrefix("/AllMusic/")
        return if (!path.startsWith("/AllMusic/") || '/' in name || name.isBlank()) null
        else candidates.singleOrNull { it.fileName == Normalizer.normalize(name, Normalizer.Form.NFC) }
    }
}
