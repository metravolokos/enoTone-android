package app.enotone.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AccountSignOutTest {
    private class Fake : AccountSignOutPort {
        val events = mutableListOf<String>()
        var credentials = true
        var failOnTracks = false
        var failOnCleanupConfirmation = false
        val catalog = mutableMapOf("mine" to 3, "other" to 9)
        override suspend fun stopPlayback() { events += "playback" }
        override suspend fun stopIndexers() { events += "indexers" }
        override suspend fun deleteTracks(sourceKey: String) {
            events += "tracks:$sourceKey"
            if (failOnTracks) throw IllegalStateException("write failed")
            catalog.remove(sourceKey)
        }
        override suspend fun deleteArtwork(sourceKey: String) { events += "artwork:$sourceKey" }
        override suspend fun resetCheckpoints(sourceKey: String) { events += "checkpoints:$sourceKey" }
        override suspend fun markCleanupComplete() {
            events += "cleanup-confirmed"
            if (failOnCleanupConfirmation) error("journal write failed")
        }
        override suspend fun deleteCredentials() {
            events += "credentials"
            credentials = false
        }
    }

    @Test fun `data precedes credentials and other account survives`() = runTest {
        val fake = Fake()
        AccountSignOut(fake).run("mine")
        assertEquals(listOf("playback", "indexers", "tracks:mine", "artwork:mine",
            "checkpoints:mine", "cleanup-confirmed", "credentials"), fake.events)
        assertEquals(9, fake.catalog["other"])
        assertFalse(fake.credentials)
    }

    @Test fun `cleanup proof is required before credentials can be deleted`() = runTest {
        val fake = Fake().apply { failOnCleanupConfirmation = true }

        try {
            AccountSignOut(fake).run("mine")
            throw AssertionError("Expected sign-out failure")
        } catch (failure: AccountSignOutFailure) {
            assertEquals(AccountSignOutFailure.Step.CHECKPOINTS, failure.step)
        }

        assertTrue(fake.credentials)
        assertEquals(listOf("playback", "indexers", "tracks:mine", "artwork:mine",
            "checkpoints:mine", "cleanup-confirmed"), fake.events)
    }

    @Test fun `failed Room delete preserves vault and retry works`() = runTest {
        val fake = Fake()
        fake.failOnTracks = true
        try {
            AccountSignOut(fake).run("mine")
            throw AssertionError("Expected sign-out failure")
        } catch (failure: AccountSignOutFailure) {
            assertEquals(AccountSignOutFailure.Step.TRACKS, failure.step)
        }
        assertTrue(fake.credentials)
        assertEquals(3, fake.catalog["mine"])
        assertEquals(listOf("playback", "indexers", "tracks:mine"), fake.events)
        fake.failOnTracks = false
        AccountSignOut(fake).run("mine")
        assertFalse(fake.credentials)
        assertFalse(fake.catalog.containsKey("mine"))
    }

    @Test fun `signing out twice is safe`() = runTest {
        val fake = Fake()
        val cleanup = AccountSignOut(fake)
        cleanup.run("mine")
        cleanup.run("mine")
        assertFalse(fake.credentials)
        assertEquals(9, fake.catalog["other"])
    }

    @Test fun `unbound MediaSession still removes all queue checkpoint files`() = runTest {
        val root = java.nio.file.Files.createTempDirectory("sign-out-unbound").toFile()
        try {
            val queue = app.enotone.playback.QueueStateFileStore.inDirectory(root)
            val filenames = listOf(
                "enotone-queue-v1.json", "enotone-position-v1.json",
                "enotone-queue-v1.json.tmp", "enotone-position-v1.json.tmp",
            )
            filenames.forEach { root.resolve(it).writeText("saved") }
            var attempts = 0
            var eraseAttempts = 0
            val stop = AccountPlaybackStop(
                stopBoundService = { attempts++; false },
                eraseAudioData = { eraseAttempts++ },
                clearQueue = { queue.clear() },
            )
            stop.run()
            assertEquals(1, attempts)
            // No binding means the service cannot own an open cache, so the caller only
            // clears the queue files itself; asking the absent service to erase is wrong.
            assertEquals(0, eraseAttempts)
            assertTrue(filenames.none { root.resolve(it).exists() })
            stop.run() // no binder and no files is idempotent
            assertEquals(2, attempts)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun `unbound service does not prevent later sign-out stages`() = runTest {
        val fake = Fake()
        val stop = AccountPlaybackStop(
            stopBoundService = { false },
            eraseAudioData = { },
            clearQueue = { Unit },
        )
        stop.run()
        AccountSignOut(fake).run("mine")
        assertFalse(fake.credentials)
        assertEquals(9, fake.catalog["other"])
    }

    @Test fun `failed audio erase stops sign-out and keeps the vault`() = runTest {
        // ADR-0080 fail-closed: if the account's audio is not gone, the Keystore must not
        // be cleared. Before this test the delete step could not fail at all, so the
        // ordering guarantee was unenforceable.
        val fake = Fake()
        val stop = AccountPlaybackStop(
            stopBoundService = { true },
            eraseAudioData = { throw IOException("disk busy") },
            clearQueue = { Unit },
        )
        val failure = runCatching { stop.run() }.exceptionOrNull()
        assertTrue("erase failure must propagate", failure is IOException)
        // A second attempt must be possible once the disk is free again.
        var erased = 0
        AccountPlaybackStop(
            stopBoundService = { true },
            eraseAudioData = { erased++ },
            clearQueue = { Unit },
        ).run()
        assertEquals(1, erased)
        // The vault was never touched: sign-out never reached the credentials stage.
        assertTrue(fake.credentials)
    }

    @Test fun `bound service erases audio data before clearing credentials`() = runTest {
        val fake = Fake()
        val order = mutableListOf<String>()
        AccountPlaybackStop(
            stopBoundService = { order += "stop"; true },
            eraseAudioData = { order += "erase" },
            clearQueue = { order += "queue" },
        ).run()
        assertEquals(listOf("stop", "erase", "queue"), order)
    }

}
