package app.enotone.android

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.enotone.library.AlbumTagRefreshReport
import app.enotone.library.LibraryRepository
import app.enotone.library.LibrarySnapshot
import app.enotone.library.LibrarySnapshotPlanner
import app.enotone.library.OfflineTrackRow
import app.enotone.network.NetworkMonitor
import app.enotone.playback.PersistedQueueState
import app.enotone.playback.PlaybackModes
import app.enotone.playback.QueueTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.Provider
import java.security.Security

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultCredentialsProviderReauthenticationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var previousKeyStoreProvider: Provider? = null

    @Before fun installKeyStoreFixture() {
        previousKeyStoreProvider = Security.getProvider(KEYSTORE_PROVIDER)
        Security.removeProvider(KEYSTORE_PROVIDER)
        Security.insertProviderAt(MainActivityTestKeyStoreProvider(), 1)
        clearFixture()
    }

    @After fun restoreKeyStoreFixture() {
        clearFixture()
        Security.removeProvider(KEYSTORE_PROVIDER)
        previousKeyStoreProvider?.let { Security.insertProviderAt(it, 1) }
    }

    @Test fun providerReturnsStoredCredentialsOnlyWhenReauthenticationIsNotRequired() = runTest {
        val expected = StoredWebDavLogin(EMAIL, PASSWORD)
        LocalCredentialVault.save(context, expected.email, expected.password)
        val provider = VaultCredentialsProvider(context)

        FirstRunPreferences(context).setReauthenticationRequired(false)
        assertEquals(expected, provider.credentials())

        FirstRunPreferences(context).setReauthenticationRequired(true)
        assertNull(provider.credentials())
    }

    @Test fun playerSkipsStartupRestoreAndRemoteArtworkGetUntilAcceptedReauthentication() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val preferences = FirstRunPreferences(context)
            preferences.setFirstRunCompleted(true)
            LocalCredentialVault.save(context, EMAIL, PASSWORD)
            preferences.setReauthenticationRequired(true)

            val blockedRepository = FixtureRepository()
            val blockedConnection = FixtureConnection()
            val blockedArtwork = CountingArtworkLoader()
            PlayerViewModel(
                blockedConnection,
                blockedRepository,
                VaultCredentialsProvider(context),
                blockedArtwork,
                dispatcher,
                { true },
            )
            advanceUntilIdle()

            assertEquals(0, blockedRepository.loads)
            assertEquals(0, blockedRepository.queueReads)
            assertEquals(0, blockedConnection.restores)
            assertEquals(0, blockedArtwork.remoteGets)

            val container = AppContainer(context, io = dispatcher, main = dispatcher, networkMonitor = NoNetwork)
            AndroidAccountSettingsGateway(context, container).save(EMAIL, NEW_PASSWORD)
            advanceUntilIdle()
            assertFalse(preferences.reauthenticationRequired())

            val allowedRepository = FixtureRepository()
            val allowedConnection = FixtureConnection()
            val allowedArtwork = CountingArtworkLoader()
            PlayerViewModel(
                allowedConnection,
                allowedRepository,
                VaultCredentialsProvider(context),
                allowedArtwork,
                dispatcher,
                { true },
                accountEvents = container.accountSession.state,
            )
            advanceUntilIdle()

            assertEquals(1, allowedRepository.loads)
            assertEquals(1, allowedRepository.queueReads)
            assertEquals(1, allowedConnection.restores)
            assertEquals(NEW_PASSWORD, allowedConnection.restoredPassword)
            assertEquals(0, allowedConnection.plays)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun clearFixture() {
        context.getSharedPreferences(ACCOUNT_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
        runCatching { LocalCredentialVault.clear(context) }
    }

    private class FixtureRepository : LibraryRepository {
        private val id = TRACK_ID
        private val track = QueueTrack(id, "Track", "/AllMusic/track.mp3", 128L, "etag")
        private val snapshot = LibrarySnapshot(
            tracks = listOf(OfflineTrackRow(id, "Track", "Artist", "Album")),
            preparedById = mapOf(id to track),
        )
        var loads = 0
        var queueReads = 0
        private val checkpoint = PersistedQueueState(
            trackIds = listOf(id),
            selectedTrackId = id,
            positionMs = 0L,
            repeatMode = Player.REPEAT_MODE_OFF,
            shuffleEnabled = false,
            shuffledIndices = null,
            sourceKey = LibrarySnapshotPlanner.accountKey(EMAIL),
            savedAtEpochMs = 1L,
            queueRevision = "01234567-89ab-cdef-0123-456789abcdef",
        )

        override suspend fun load(email: String): LibrarySnapshot {
            loads++
            return snapshot
        }

        override suspend fun refreshAlbum(
            email: String,
            password: String,
            ids: List<String>,
        ): AlbumTagRefreshReport = error("Not used by player restoration")

        override suspend fun readSavedQueue(): PersistedQueueState? {
            queueReads++
            return checkpoint
        }
    }

    private class FixtureConnection : PlaybackConnection {
        private val mutable = MutableStateFlow(PlaybackSnapshot(connected = true))
        override val state: StateFlow<PlaybackSnapshot> = mutable
        var restores = 0
        var plays = 0
        var restoredPassword: String? = null

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun playQueue(
            email: String,
            password: String,
            queue: List<QueueTrack>,
            selectedId: String,
            useCache: Boolean,
            sourceKey: String,
        ) { plays++ }

        override fun restoreQueue(
            email: String,
            password: String,
            queue: List<QueueTrack>,
            saved: PersistedQueueState,
            useCache: Boolean,
            filtered: Boolean,
        ): Boolean {
            restores++
            restoredPassword = password
            mutable.value = PlaybackSnapshot(
                connected = true,
                playbackState = "PAUSED",
                currentTrackId = saved.selectedTrackId,
                sourceKey = saved.sourceKey,
                title = "Track",
                queuePosition = 1,
                queueTotal = queue.size,
            )
            return true
        }

        override fun stopPlayback(): Boolean = true
        override fun playPause() = Unit
        override fun playNext() = Unit
        override fun playPrevious() = Unit
        override fun cycleRepeatMode() { mutable.value = mutable.value.copy(
            repeatMode = PlaybackModes.nextRepeat(mutable.value.repeatMode),
        ) }
        override fun toggleShuffle() { mutable.value = mutable.value.copy(
            shuffleEnabled = !mutable.value.shuffleEnabled,
        ) }
        override fun seekToFraction(fraction: Float) = Unit
    }

    private class CountingArtworkLoader : LibraryArtworkLoader {
        var remoteGets = 0
        override fun peek(email: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun cached(email: String, group: AlbumArtworkGroup, size: Int): Bitmap? = null
        override suspend fun fetch(
            email: String,
            password: String,
            group: AlbumArtworkGroup,
            size: Int,
        ): Bitmap? {
            remoteGets++
            return null
        }

        override suspend fun invalidate(email: String, targets: Collection<PlayingArtworkTarget>) = Unit
    }

    private object NoNetwork : NetworkMonitor {
        private val unavailable = MutableStateFlow(false)
        override val hasValidatedInternet: StateFlow<Boolean> = unavailable
        override fun close() = Unit
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val EMAIL = "first-run@example.invalid"
        const val PASSWORD = "stored-password"
        const val NEW_PASSWORD = "accepted-password"
        const val TRACK_ID = "0000000000000000000000000000000000000000000000000000000000000001"
    }
}
