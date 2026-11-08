package app.enotone.library

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import java.io.IOException

/**
 * A single-file metadata probe using Android's platform extractor (no Google services).
 *
 * This class does not issue HTTP requests. A separately tested MediaDataSource must
 * supply bounded random-access reads before calling it for remote WebDAV tracks.
 * The caller is responsible for closing the source.
 */
class AndroidAudioTagReader {
    fun read(source: MediaDataSource, fallbackFileName: String): AudioTagFields {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source)
            AudioTagFields.fromRaw(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                fallbackFileName = fallbackFileName,
            )
        } catch (_: Exception) {
            // Platform exception messages may contain private file or remote source details.
            throw IOException("Unable to extract audio metadata")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Nothing private goes to logs. Source lifecycle remains with the caller.
            }
        }
    }
}
