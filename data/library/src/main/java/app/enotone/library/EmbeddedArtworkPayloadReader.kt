package app.enotone.library

import java.io.IOException

/**
 * Reads only one explicitly requested embedded cover after a bounded locator
 * found it. Caller supplies the existing validated Range transport.
 *
 * Never reads a full audio file, never updates Room, and never invokes this
 * from a catalogue import. On failure no partial image escapes.
 */
class EmbeddedArtworkPayloadReader {
    fun read(
        fileName: String,
        fileSize: Long,
        readAt: (Long, ByteArray, Int, Int) -> Int,
    ): ByteArray? {
        val located = EmbeddedArtworkLocator().locate(fileName, fileSize, readAt)
        val image = located.artwork ?: return null
        if (located.code != ArtworkLookupCode.FOUND ||
            image.length !in 1..MAX_PAYLOAD_BYTES ||
            image.offset < 0L || image.offset > fileSize ||
            image.length.toLong() > fileSize - image.offset) return null
        val output = ByteArray(image.length)
        var copied = 0
        try {
            while (copied < output.size) {
                val length = minOf(CHUNK, output.size - copied)
                val received = readAt(image.offset + copied, output, copied, length)
                if (received !in 1..length) {
                    output.fill(0)
                    return null
                }
                copied += received
            }
            return output
        } catch (_: IOException) {
            output.fill(0)
            return null
        }
    }

    private companion object {
        const val CHUNK = 16 * 1024
        const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024
    }
}
