package app.enotone.android

import java.io.File
import java.io.IOException

/** Filename digests only; never contain email, WebDAV paths or track names. */
internal data class AccountArtworkKeys(
    val sourceRefs: Set<String> = emptySet(),
    val albumRefs: Set<String> = emptySet(),
)

/** Pure filesystem implementation so shared-master preservation is JVM-testable. */
internal class AccountArtworkFileCleanup(private val directory: File) {
    fun removeScoped(keys: AccountArtworkKeys) {
        keys.sourceRefs.forEach { key ->
            require(DIGEST.matches(key))
            val file = File(directory, "ref-$key.txt")
            if (file.exists() && !file.delete()) throw IOException("Cannot remove source ref")
        }
        keys.albumRefs.forEach { key ->
            require(DIGEST.matches(key))
            val file = File(directory, "album-$key.txt")
            if (file.exists() && !file.delete()) throw IOException("Cannot remove album ref")
        }
        // Masters are content-addressed: inspect ALL surviving references, including
        // those belonging to other accounts, before reclaiming an unreferenced file.
        val referenced = directory.listFiles().orEmpty()
            .asSequence().filter { it.name.startsWith("ref-") && it.name.endsWith(".txt") }
            .mapNotNull { file ->
                runCatching { file.readText(Charsets.US_ASCII) }.getOrNull()
                    ?.takeIf { DIGEST.matches(it) }
            }.toSet()
        directory.listFiles().orEmpty()
            .filter { it.name.startsWith("art-") && it.name.endsWith(".webp") }
            .forEach { master ->
                val digest = master.name.removePrefix("art-").removeSuffix(".webp")
                if (DIGEST.matches(digest) && digest !in referenced && !master.delete()) {
                    throw IOException("Cannot remove orphaned master")
                }
            }
    }

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
            if (!temp.renameTo(file)) throw IOException("Cannot commit sign-out manifest")
        } finally {
            temp.delete()
        }
    }

    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    private companion object {
        val DIGEST = Regex("[0-9a-f]{64}")
    }
}
