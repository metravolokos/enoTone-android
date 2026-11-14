package app.enotone.playlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.enotone.database.EnoToneDatabase
import app.enotone.database.PlaylistEntity
import app.enotone.database.PlaylistItemEntity
import app.enotone.database.RemoteTrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistRepositoryTest {
    private lateinit var database: EnoToneDatabase
    private lateinit var repository: PlaylistRepository
    private val sourceA = "a".repeat(64)
    private val sourceB = "b".repeat(64)

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), EnoToneDatabase::class.java,
        ).build()
        repository = PlaylistRepository(database, Dispatchers.IO) { 100L }
    }

    @After fun closeDatabase() = database.close()

    private fun track(id: String, source: String = sourceA) = RemoteTrackEntity(
        id = id,
        sourceKey = source,
        encodedPath = "/AllMusic/$id.mp3",
        fileName = "$id.mp3",
        remoteEtag = "etag-$id",
        remoteSize = 1024L,
        remoteModified = "modified-$id",
        lastSeenEpoch = 1L,
    )

    @Test fun concurrentFavoritesCallsCreateOneSystemPlaylistPerAccount() = runBlocking {
        val results = coroutineScope {
            (1..16).map { async(Dispatchers.IO) { repository.favorites(sourceA) } }.awaitAll()
        }
        assertEquals(1, results.map { it.id }.distinct().size)
        assertEquals("FAVORITES", results.first().kind)
        assertEquals("Избранное", results.first().name)
        assertEquals(1, database.playlistDao().allForSource(sourceA).size)
        val other = repository.favorites(sourceB)
        assertTrue(other.id != results.first().id)
        assertEquals(2, database.playlistDao().allForSource(sourceA).size +
            database.playlistDao().allForSource(sourceB).size)
    }

    @Test fun favoritesAddRemoveAndReactiveMembershipAreIdempotent() = runBlocking {
        database.remoteTrackDao().upsertAll(listOf(track("track-a"), track("track-b")))
        val playlist = repository.favorites(sourceA)
        assertTrue(repository.addToFavorites(sourceA, "track-a"))
        assertFalse(repository.addToFavorites(sourceA, "track-a"))
        assertTrue(repository.addToFavorites(sourceA, "track-b"))
        assertTrue(repository.isFavorite("track-a").first())
        assertFalse(repository.isFavorite("track-other").first())
        assertEquals(listOf("track-a", "track-b"),
            repository.items(playlist.id).first().map { it.trackId })
        assertEquals(listOf(0, 1), repository.items(playlist.id).first().map { it.position })
        assertTrue(repository.removeFromFavorites(sourceA, "track-a"))
        assertFalse(repository.removeFromFavorites(sourceA, "track-a"))
        assertFalse(repository.isFavorite("track-a").first())
    }

    @Test fun itemFlowOrdersByPositionAndMarksMissingTracksNotPresent() = runBlocking {
        database.remoteTrackDao().upsertAll(listOf(track("first"), track("second")))
        val playlist = database.playlistDao().run {
            PlaylistEntity("user-list", sourceA, "Road", "USER", 1L, 1L)
                .also { insertPlaylist(it) }
        }
        database.playlistDao().insertItem(PlaylistItemEntity(playlist.id, "first", 0, 10))
        database.playlistDao().insertItem(PlaylistItemEntity(playlist.id, "second", 1, 11))
        database.playlistDao().setPosition(playlist.id, "first", 1)
        database.playlistDao().setPosition(playlist.id, "second", 0)
        database.remoteTrackDao().upsertAll(listOf(track("first").copy(missingScans = 2)))
        val items = repository.items(playlist.id).first()
        assertEquals(listOf("second", "first"), items.map { it.trackId })
        assertEquals(listOf(true, false), items.map { it.present })
    }

    @Test fun deletingPlaylistOrTrackCascadesAndSignOutKeepsOtherAccountRows() = runBlocking {
        database.remoteTrackDao().upsertAll(listOf(track("a-track"), track("b-track", sourceB)))
        val favoritesA = repository.favorites(sourceA)
        val favoritesB = repository.favorites(sourceB)
        assertTrue(repository.addToFavorites(sourceA, "a-track"))
        assertTrue(repository.addToFavorites(sourceB, "b-track"))
        assertEquals(1, database.playlistDao().itemCount(favoritesA.id))
        database.playlistDao().deletePlaylist(favoritesA.id)
        assertEquals(0, database.playlistDao().itemCount(favoritesA.id))
        assertEquals(1, database.playlistDao().itemCount(favoritesB.id))

        val recreated = repository.favorites(sourceA)
        assertTrue(repository.addToFavorites(sourceA, "a-track"))
        database.remoteTrackDao().deleteAllForSource(sourceA)
        assertEquals(0, database.playlistDao().itemCount(recreated.id))
        database.remoteTrackDao().upsertAll(listOf(track("a-track")))
        assertTrue(repository.addToFavorites(sourceA, "a-track"))

        database.deleteAccountData(sourceA)
        assertTrue(database.playlistDao().allForSource(sourceA).isEmpty())
        assertTrue(database.remoteTrackDao().allForSource(sourceA).isEmpty())
        assertEquals(listOf(favoritesB.id),
            database.playlistDao().allForSource(sourceB).map { it.id })
        assertEquals(1, database.playlistDao().itemCount(favoritesB.id))
        database.openHelper.writableDatabase.query("PRAGMA foreign_keys").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }
}
