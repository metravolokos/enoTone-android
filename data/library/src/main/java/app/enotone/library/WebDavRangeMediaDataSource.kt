package app.enotone.library

import android.media.MediaDataSource

/** Bridges the bounded WebDAV Range reader into platform MediaMetadataRetriever. */
class WebDavRangeMediaDataSource(private val reader: WebDavRangeReader) : MediaDataSource() {
    override fun getSize(): Long = reader.fileSize
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
        reader.readAt(position, buffer, offset, size)
    override fun close() = reader.close()
}
