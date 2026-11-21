package app.enotone.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Filesystem behaviour of the sign-out manifest helpers used by the legacy recovery
 * (ADR-0103). They must remove only this project's manifest files and never touch
 * anything else that happens to live in `noBackupFilesDir`.
 */
class AccountArtworkManifestCleanupTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val digestA = "a".repeat(64)
    private val digestB = "b".repeat(64)

    private fun manifest(root: File, digest: String): File =
        File(root, "enotone-signout-$digest.keys").apply { writeText("S:$digest\n") }

    @Test
    fun `hasAny finds a valid manifest`() {
        val root = temporary.newFolder()
        manifest(root, digestA)

        assertTrue(AccountArtworkManifest.hasAny(root))
    }

    @Test
    fun `hasAny is false for an empty directory`() {
        assertFalse(AccountArtworkManifest.hasAny(temporary.newFolder()))
    }

    @Test
    fun `hasAny ignores files that are not sign-out manifests`() {
        val root = temporary.newFolder()
        File(root, "enotone-signout-not-a-digest.keys").writeText("x")
        File(root, "unrelated.txt").writeText("x")
        File(root, "enotone-signout-$digestA.tmp").writeText("x")

        assertFalse(AccountArtworkManifest.hasAny(root))
    }

    @Test
    fun `clearAllOrphaned removes every manifest and reports the count`() {
        val root = temporary.newFolder()
        manifest(root, digestA)
        manifest(root, digestB)

        val removed = AccountArtworkManifest.clearAllOrphaned(root)

        assertEquals(2, removed)
        assertFalse(AccountArtworkManifest.hasAny(root))
    }

    @Test
    fun `clearAllOrphaned keeps unrelated files intact`() {
        val root = temporary.newFolder()
        manifest(root, digestA)
        val keep = File(root, "keep-me.bin").apply { writeText("payload") }
        val otherAccountRef = File(root, "ref-$digestB.txt").apply { writeText(digestA) }

        AccountArtworkManifest.clearAllOrphaned(root)

        assertTrue("an unrelated file must survive", keep.isFile)
        assertTrue("artwork references are not manifests", otherAccountRef.isFile)
    }

    @Test
    fun `clearAllOrphaned also removes leftover temporaries`() {
        val root = temporary.newFolder()
        val temporaryFile = File(root, "enotone-signout-$digestA.keys.tmp")
            .apply { writeText("partial") }

        AccountArtworkManifest.clearAllOrphaned(root)

        assertFalse(temporaryFile.exists())
    }

    @Test
    fun `clearAllOrphaned on an empty directory changes nothing`() {
        assertEquals(0, AccountArtworkManifest.clearAllOrphaned(temporary.newFolder()))
    }
}
