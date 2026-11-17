package app.enotone.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Audit of the existing SQL pipeline: no HTTP, no owner library and no data repair. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryReindexAuditTest {
    private lateinit var database: EnoToneDatabase
    private val source = LibrarySnapshotPlanner.accountKey("fixture@example.invalid")

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), EnoToneDatabase::class.java,
        ).build()
    }

    @After fun closeDatabase() = database.close()

    private fun row(number: Int): RemoteTrackEntity {
        val path = "/AllMusic/fixture-$number.mp3"
        return RemoteTrackEntity(
            LibrarySnapshotPlanner.trackId(source, path), source, path, "fixture-$number.mp3",
            "v1", 100L, "mtime", title = "Fixture $number", artist = "Fixture",
            album = "Fixture", trackNumber = number, durationMs = 1000L,
            lastSeenEpoch = 1, tagScanCode = "MP3_ID3V24",
        )
    }

    @Test fun repeatedAlbumAndConcurrentFullReindexDoNotInsertRows() = runBlocking {
        val dao = database.remoteTrackDao()
        dao.upsertAll((1..12).map(::row))
        val coordinator = LibraryReindexCoordinator(database)
        val group = OfflineLibraryGrouping.albums(LibraryCatalogMapper.map(source,
            dao.allForSource(source)).tracks).single()
        repeat(3) {
            coroutineScope {
                val album = async { coordinator.queue("fixture@example.invalid",
                    LibraryReindexScope.Album(group.key)) }
                val full = async { coordinator.queue("fixture@example.invalid",
                    LibraryReindexScope.EntireLibrary) }
                album.await()
                full.await()
            }
            assertCatalogueCount()
            dao.pendingFastScans(source).forEach { track ->
                dao.recordFastScanIfUnchanged(track.id, source, track.encodedPath, track.remoteEtag,
                    track.remoteSize, track.remoteModified, "MP3_ID3V24", track.title, track.artist,
                    track.album, track.albumArtist, track.year, track.trackNumber, track.discNumber,
                    track.genre, track.durationMs)
                assertCatalogueCount()
            }
            // A fresh mapper/repository read has no in-memory album or queue state to reuse.
            assertCatalogueCount()
        }
    }

    private suspend fun assertCatalogueCount() {
        val stored = database.remoteTrackDao().allForSource(source)
        val snapshot = LibraryCatalogMapper.map(source, stored)
        assertEquals(12, stored.size)
        assertEquals(12, snapshot.tracks.size)
        assertEquals(12, snapshot.tracks.map { it.id }.distinct().size)
        assertEquals(12, OfflineLibraryGrouping.albums(snapshot.tracks).single().trackIds.size)
        assertEquals(LibraryDuplicateAudit(0, 0), LibraryDuplicateDiagnostics.inspect(source, stored))
    }
}
