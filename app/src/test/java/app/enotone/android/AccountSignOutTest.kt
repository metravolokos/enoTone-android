package app.enotone.android

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSignOutTest {
    private class Fake : AccountSignOutPort {
        val events = mutableListOf<String>()
        var credentials = true
        var failOnTracks = false
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
        override suspend fun deleteCredentials() {
            events += "credentials"
            credentials = false
        }
    }

    @Test fun `data precedes credentials and other account survives`() = runTest {
        val fake = Fake()
        AccountSignOut(fake).run("mine")
        assertEquals(listOf("playback", "indexers", "tracks:mine", "artwork:mine",
            "checkpoints:mine", "credentials"), fake.events)
        assertEquals(9, fake.catalog["other"])
        assertFalse(fake.credentials)
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
            val stop = AccountPlaybackStop(
                stopBoundService = { attempts++; false },
                clearQueue = { queue.clear() },
            )
            stop.run()
            assertEquals(1, attempts)
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
            clearQueue = { Unit },
        )
        stop.run()
        AccountSignOut(fake).run("mine")
        assertFalse(fake.credentials)
        assertEquals(9, fake.catalog["other"])
    }

}
