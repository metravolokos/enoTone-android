package app.enotone.android

import app.enotone.playback.AtomicFileReplace
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Filename digests only; never contain email, WebDAV paths or track names. */
internal data class AccountArtworkKeys(
    val sourceRefs: Set<String> = emptySet(),
    val albumRefs: Set<String> = emptySet(),
)

/** Pure filesystem implementation so shared-master preservation is JVM-testable. */
internal class AccountArtworkFileCleanup(
    private val directory: File,
    /**
     * Legacy v2 cache. Masters there are still resolvable through `migrateLegacy`, so a
     * reference living only in the legacy store keeps its master alive.
     */
    private val legacyDirectory: File? = null,
) {
    fun removeScoped(keys: AccountArtworkKeys) {
        keys.sourceRefs.forEach(::removeSourceRef)
        keys.albumRefs.forEach { key ->
            require(DIGEST.matches(key))
            val file = File(directory, "album-$key.txt")
            if (file.exists() && !file.delete()) throw IOException("Cannot remove album ref")
        }
        // Masters are content-addressed: inspect ALL surviving references, including
        // those belonging to other accounts, before reclaiming an unreferenced file.
        val referenced = survivingReferences() ?: return
        directory.listFiles().orEmpty()
            .filter { it.name.startsWith("art-") && it.name.endsWith(".webp") }
            .forEach { master ->
                val digest = master.name.removePrefix("art-").removeSuffix(".webp")
                if (DIGEST.matches(digest) && digest !in referenced && !master.delete()) {
                    throw IOException("Cannot remove orphaned master")
                }
            }
    }

    /**
     * Removes one account's source reference from both stores. A legacy ref left behind would
     * be migrated back into permanent storage on the next read, so it must go as well.
     */
    private fun removeSourceRef(key: String) {
        require(DIGEST.matches(key))
        val stores = listOfNotNull(File(directory, "ref-$key.txt"),
            legacyDirectory?.let { File(it, "ref-$key.txt") })
        for (file in stores) {
            if (file.exists() && !file.delete()) throw IOException("Cannot remove source ref")
        }
        // The forget marker belongs to the removed account too.
        File(directory, "forget-$key.txt").delete()
    }

    /**
     * Digests named by readable `ref-*.txt` files, or null when any reference cannot be
     * parsed. A reference we cannot read may still point at a live master, so acting on
     * an incomplete set would destroy another account's artwork.
     */
    private fun survivingReferences(): Set<String>? {
        var complete = true
        val referenced = (listReferences(directory) + listReferences(legacyDirectory))
            .mapNotNull { file ->
                val digest = runCatching { file.readText(Charsets.US_ASCII) }.getOrNull()
                if (digest != null && DIGEST.matches(digest)) {
                    digest
                } else {
                    complete = false
                    null
                }
            }.toSet()
        return referenced.takeIf { complete }
    }

    /** Reference files of one store; absent or unreadable directory means "none here". */
    private fun listReferences(store: File?): List<File> = store?.listFiles().orEmpty()
        .filter { it.name.startsWith("ref-") && it.name.endsWith(".txt") }

    private companion object {
        val DIGEST = Regex("[0-9a-f]{64}")
    }
}

/**
 * Pre-delete journal for idempotent retries after Room records disappear.
 * Persisted values are one-way hashes only; no credentials or remote metadata.
 */
internal class AccountArtworkManifest(private val file: File) {
    fun read(): AccountArtworkKeys? {
        if (!file.isFile) return null
        val source = mutableSetOf<String>()
        val albums = mutableSetOf<String>()
        file.forEachLine { line ->
            require(line.length == 66 && line[1] == ':' && DIGEST.matches(line.substring(2)))
            when (line[0]) {
                'S' -> source += line.substring(2)
                'A' -> albums += line.substring(2)
                else -> error("Invalid sign-out manifest")
            }
        }
        return AccountArtworkKeys(source, albums)
    }

    fun save(keys: AccountArtworkKeys) {
        val content = buildString {
            keys.sourceRefs.sorted().forEach { append("S:").append(it).append('\n') }
            keys.albumRefs.sorted().forEach { append("A:").append(it).append('\n') }
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        try {
            temp.writeText(content, Charsets.US_ASCII)
            if (!AtomicFileReplace.replace(temp, file)) {
                throw IOException("Cannot commit sign-out manifest")
            }
        } finally {
            temp.delete()
        }
    }

    /** Rebuild after a completed old sign-out was recovered and its manifest removed. */
    suspend fun ensure(keys: suspend () -> AccountArtworkKeys) {
        if (read() == null) save(keys())
    }

    fun clear() {
        Files.deleteIfExists(file.toPath())
        Files.deleteIfExists(File(file.parentFile, file.name + ".tmp").toPath())
    }

    companion object {
        private val DIGEST = Regex("[0-9a-f]{64}")

        private const val PREFIX = "enotone-signout-"
        private const val SUFFIX = ".keys"

        /**
         * Removes every sign-out manifest in [directory]. Used only when a legacy
         * journal proves the cleanup finished (ADR-0103): the files hold one-way
         * reference hashes and nothing personal, so deleting them needs no account
         * identity. Returns the number of removed manifests.
         */
        fun clearAllOrphaned(directory: File): Int {
            val files = directory.listFiles().orEmpty().filter { candidate ->
                candidate.isFile && candidate.name.startsWith(PREFIX) &&
                    candidate.name.endsWith(SUFFIX) &&
                    DIGEST.matches(
                        candidate.name.removePrefix(PREFIX).removeSuffix(SUFFIX),
                    )
            }
            var removed = 0
            for (candidate in files) {
                if (Files.deleteIfExists(candidate.toPath())) removed++
            }
            val temporaries = directory.listFiles().orEmpty().filter { candidate ->
                candidate.isFile && candidate.name.startsWith(PREFIX) &&
                    candidate.name.endsWith(SUFFIX + ".tmp")
            }
            for (temporary in temporaries) Files.deleteIfExists(temporary.toPath())
            return removed
        }

        /**
         * Whether any sign-out manifest survives in [directory]. A legacy recovery uses
         * this to detect a state that contradicts a finished cleanup (ADR-0103).
         */
        fun hasAny(directory: File): Boolean = directory.listFiles().orEmpty().any { candidate ->
            candidate.isFile && candidate.name.startsWith(PREFIX) &&
                candidate.name.endsWith(SUFFIX) &&
                DIGEST.matches(candidate.name.removePrefix(PREFIX).removeSuffix(SUFFIX))
        }

        fun forSource(directory: File, sourceKey: String): AccountArtworkManifest {
            require(DIGEST.matches(sourceKey))
            return AccountArtworkManifest(File(directory, "enotone-signout-$sourceKey.keys"))
        }
    }
}
