package app.enotone.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** In-memory SQL tests: no file network, mock databases or source-text matching. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemoteTrackDaoTest {
    private lateinit var database: EnoToneDatabase
    private val dao get() = database.remoteTrackDao()

    @Before fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), EnoToneDatabase::class.java,
        ).build()
    }

    @After fun closeDatabase() = database.close()

    private fun track(
        id: String, source: String = "account-a", extension: String = ".mp3",
        album: String? = null, artist: String? = null,
    ) = RemoteTrackEntity(
        id = id, sourceKey = source, encodedPath = "/fixture/$id$extension",
        fileName = "$id$extension", remoteEtag = "v1", remoteSize = 1024,
        remoteModified = "date-1", title = "Old title", artist = "Artist",
        album = album, albumArtist = artist, lastSeenEpoch = 10,
        tagScanCode = "MP3_ID3V24",
    )

    @Test fun accountIsolationAndExplicitSignOut() = runBlocking {
        dao.upsertAll(listOf(track("a1"), track("b1", source = "account-b")))
        assertEquals(listOf("a1"), dao.allForSource("account-a").map { it.id })
        assertEquals(listOf("b1"), dao.allForSource("account-b").map { it.id })
        assertNull(dao.playableById("b1", "account-a"))
        assertEquals("b1", dao.playableById("b1", "account-b")?.id)
        assertEquals(1, dao.countOtherSources("account-a"))
        assertEquals(1, dao.deleteAllForSource("account-a"))
        assertTrue(dao.allForSource("account-a").isEmpty())
        assertEquals("b1", dao.allForSource("account-b").single().id)
    }

    @Test fun tagCompareAndSetRejectsOtherAccountAndChangedVersion() = runBlocking {
        dao.upsertAll(listOf(track("a1"), track("b1", source = "account-b")))
        suspend fun save(source: String, etag: String, path: String = "/fixture/a1.mp3") =
            dao.saveTagsIfSnapshotMatches(
                "a1", source, path, etag, 1024, "date-1",
                "New title", "New artist", "New album", "New album artist",
                2026, 2, 1, "Rock",
            )
        assertEquals(0, save("account-b", "v1"))
        assertEquals(0, save("account-a", "v2"))
        assertEquals(0, save("account-a", "v1", "/wrong.mp3"))
        assertEquals("Old title", dao.allForSource("account-a").single().title)
        assertEquals(1, save("account-a", "v1"))
        val row = dao.allForSource("account-a").single()
        assertEquals("New title", row.title)
        assertEquals("Rock", row.genre)
        assertEquals("Old title", dao.allForSource("account-b").single().title)
    }

    @Test fun fastScanDoesNotOverwriteTagsOnErrorOrChangedSnapshot() = runBlocking {
        dao.upsertAll(listOf(track("a1").copy(tagScanCode = "RETRY_REFRESH")))
        suspend fun scan(etag: String, code: String) = dao.recordFastScanIfUnchanged(
            "a1", "account-a", "/fixture/a1.mp3", etag, 1024, "date-1",
            code, "Fresh title", "Fresh artist", "Fresh album", "Fresh album artist",
            2025, 3, 1, "Jazz",
        )
        assertEquals(0, scan("v2", "MP3_ID3V24"))
        assertEquals(1, scan("v1", "RETRY_IO"))
        assertEquals("Old title", dao.allForSource("account-a").single().title)
        assertEquals(1, scan("v1", "MP3_ID3V24"))
        val row = dao.allForSource("account-a").single()
        assertEquals("Fresh title", row.title)
        assertEquals("Jazz", row.genre)
        assertEquals(0, scan("v1", "MP3_ID3V24"))
    }

    @Test fun targetedAndAllRefreshKeepTagsAndRespectAccounts() = runBlocking {
        dao.upsertAll(listOf(
            track("a1"), track("a2", extension = ".flac"),
            track("ignored", extension = ".wav"), track("b1", source = "account-b"),
        ))
        assertEquals(1, dao.queueTagRefreshByIds("account-a", listOf("a1", "b1", "missing")))
        assertEquals("RETRY_REFRESH", dao.allForSource("account-a").first { it.id == "a1" }.tagScanCode)
        assertEquals("Old title", dao.allForSource("account-a").first { it.id == "a1" }.title)
        assertEquals("MP3_ID3V24", dao.allForSource("account-b").single().tagScanCode)
        assertEquals(2, dao.queueAllTagRefresh("account-a"))
        val rows = dao.allForSource("account-a").associateBy { it.id }
        assertEquals("RETRY_REFRESH", rows.getValue("a2").tagScanCode)
        assertEquals("MP3_ID3V24", rows.getValue("ignored").tagScanCode)
        assertTrue(rows.values.all { it.title == "Old title" })
        assertEquals("Old title", dao.allForSource("account-b").single().title)
    }

    @Test fun tagAuditsHaveDefaultLimitThirtyAndExcludeOtherAccounts() = runBlocking {
        val records = (1..35).map { index ->
            track("a-" + index.toString().padStart(2, '0'), artist = null)
        } + track("b-no-album", source = "account-b")
        dao.upsertAll(records)
        assertEquals(30, dao.tracksWithoutAlbum("account-a").size)
        assertEquals(30, dao.tracksWithoutAlbumArtist("account-a").size)
        assertEquals(35, dao.tracksWithoutAlbum("account-a", 100).size)
        assertFalse(dao.tracksWithoutAlbum("account-a").any { it.sourceKey == "account-b" })
    }
}
