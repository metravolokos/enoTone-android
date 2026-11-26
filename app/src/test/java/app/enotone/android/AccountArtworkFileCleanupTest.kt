package app.enotone.android

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountArtworkFileCleanupTest {
    private val account = "a".repeat(64)
    private val other = "b".repeat(64)
    private val shared = "c".repeat(64)
    private val exclusive = "d".repeat(64)
    private val album = "e".repeat(64)

    @Test fun `shared WebP survives account removal`() {
        val root = Files.createTempDirectory("artwork-account").toFile()
        try {
            val directory = root.resolve("art")
            directory.mkdirs()
            directory.resolve("ref-$account.txt").writeText(shared)
            directory.resolve("ref-$other.txt").writeText(shared)
            directory.resolve("ref-$exclusive.txt").writeText(exclusive)
            directory.resolve("art-$shared.webp").writeText("webp")
            directory.resolve("art-$exclusive.webp").writeText("webp")
            directory.resolve("album-$album.txt").writeText("selection")
            AccountArtworkFileCleanup(directory).removeScoped(
                AccountArtworkKeys(setOf(account, exclusive), setOf(album)),
            )
            assertTrue(directory.resolve("art-$shared.webp").exists())
            assertTrue(directory.resolve("ref-$other.txt").exists())
            assertFalse(directory.resolve("art-$exclusive.webp").exists())
            assertFalse(directory.resolve("ref-$account.txt").exists())
            assertFalse(directory.resolve("album-$album.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `a master referenced only from the legacy store survives removal`() {
        // migrateLegacy() can still resolve a legacy ref, so deleting the master would make
        // another account lose its artwork while the legacy copy claims it is alive.
        val root = Files.createTempDirectory("artwork-legacy").toFile()
        try {
            val directory = root.resolve("art").apply { mkdirs() }
            val legacy = root.resolve("legacy").apply { mkdirs() }
            directory.resolve("ref-$account.txt").writeText(shared)
            directory.resolve("art-$shared.webp").writeText("webp")
            // Only the legacy store still points at this master.
            legacy.resolve("ref-$other.txt").writeText(shared)
            AccountArtworkFileCleanup(directory, legacy).removeScoped(
                AccountArtworkKeys(setOf(account), emptySet()),
            )
            assertTrue("legacy-referenced master must survive",
                directory.resolve("art-$shared.webp").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `removal also drops the legacy reference and the forget marker`() {
        val root = Files.createTempDirectory("artwork-legacy-ref").toFile()
        try {
            val directory = root.resolve("art").apply { mkdirs() }
            val legacy = root.resolve("legacy").apply { mkdirs() }
            directory.resolve("ref-$account.txt").writeText(shared)
            legacy.resolve("ref-$account.txt").writeText(shared)
            directory.resolve("forget-$account.txt").writeText("1")
            AccountArtworkFileCleanup(directory, legacy).removeScoped(
                AccountArtworkKeys(setOf(account), emptySet()),
            )
            assertFalse("a legacy ref would be migrated back",
                legacy.resolve("ref-$account.txt").exists())
            assertFalse(directory.resolve("forget-$account.txt").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `manifest survives retry after Room deletion`() {
        val root = Files.createTempDirectory("signout-manifest").toFile()
        try {
            val manifest = AccountArtworkManifest(root.resolve("signout.keys"))
            val keys = AccountArtworkKeys(setOf(account), setOf(album))
            manifest.save(keys)
            assertEquals(keys, manifest.read())
            manifest.clear()
            assertEquals(null, manifest.read())
        } finally {
            root.deleteRecursively()
        }
    }
}
