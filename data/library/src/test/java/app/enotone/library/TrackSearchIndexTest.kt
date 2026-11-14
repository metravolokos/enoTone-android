package app.enotone.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.enotone.database.EnoToneDatabase
import app.enotone.database.RemoteTrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackSearchIndexTest {
    private lateinit var database: EnoToneDatabase
    private val source = LibrarySnapshotPlanner.accountKey("account@example.test")
    private val other = LibrarySnapshotPlanner.accountKey("other@example.test")

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), EnoToneDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun close() = database.close()

    @Test fun rebuildsFromLocalRoomThenRepairsStaleTagsWithoutNetwork() = runBlocking {
        val tracks = database.remoteTrackDao()
        val index = TrackSearchIndex(database)
        val dao = database.trackSearchDao()
        tracks.upsertAll(listOf(
            track("one", source, "Ёжик"),
            track("two", source, "Иван"),
            track("foreign", other, "Ёжик"),
        ))
        assertTrue(dao.allForSource(source).isEmpty())
        assertEquals(2, index.reconcile(source).size)
        assertEquals(listOf("one"), dao.searchTrackIds(source, "\"еж*\""))
        assertTrue(dao.allForSource(other).isEmpty()) // Other source is not built implicitly.
        tracks.upsertAll(listOf(track("one", source, "Зима")))
        index.reconcile(source) // No WebDAV: repairs unexpected tag writes.
        assertTrue(dao.searchTrackIds(source, "\"еж*\"").isEmpty())
        assertEquals(listOf("one"), dao.searchTrackIds(source, "\"зим*\""))
        index.reconcile(other)
        assertEquals(listOf("foreign"), dao.searchTrackIds(other, "\"еж*\""))
        assertEquals(2, dao.allForSource(source).size)
    }

    @Test fun explicitSingleFileRefreshNeverDuplicatesTheIndex() = runBlocking {
        val tracks = database.remoteTrackDao()
        val dao = database.trackSearchDao()
        val index = TrackSearchIndex(database)
        tracks.upsertAll(listOf(track("one", source, "Ёжик")))
        index.refreshById(source, "one")
        tracks.upsertAll(listOf(track("one", source, "Йод")))
        index.refreshById(source, "one")
        assertEquals(1, dao.allForSource(source).size)
        assertEquals(listOf("one"), dao.searchTrackIds(source, "\"йод*\""))
        assertTrue(dao.searchTrackIds(source, "\"иод*\"").isEmpty())
    }

    private fun track(id: String, sourceKey: String, title: String) = RemoteTrackEntity(
        id = id, sourceKey = sourceKey, encodedPath = "/AllMusic/$id.mp3",
        fileName = "$id.mp3", remoteEtag = null, remoteSize = 500,
        remoteModified = null, title = title, lastSeenEpoch = 1,
    )
}
