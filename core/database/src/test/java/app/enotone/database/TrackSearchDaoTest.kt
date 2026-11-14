package app.enotone.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FTS4 behavior: source isolation, prefixes, missing files, and safe parameter binding. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrackSearchDaoTest {
    private lateinit var database: EnoToneDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), EnoToneDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun close() = database.close()

    @Test fun prefixesRespectAccountMissingFlagsAndNoDuplicateRows() = runBlocking {
        val tracks = database.remoteTrackDao()
        tracks.upsertAll(listOf(
            track("a", "account-a"),
            track("b", "account-b"),
            track("missing", "account-a", missing = 1),
        ))
        val index = database.trackSearchDao()
        index.insertAll(listOf(
            TrackSearchEntity("a", "account-a", "ежик зеленая дорога"),
            TrackSearchEntity("b", "account-b", "ежик зеленая дорога"),
            TrackSearchEntity("missing", "account-a", "ежик зеленая дорога"),
        ))
        assertEquals(listOf("a"),
            index.searchTrackIds("account-a", "\"еж*\" AND \"дор*\""))
        assertEquals(emptyList<String>(),
            index.searchTrackIds("account-a", "\"ираж*\""))
        assertEquals(listOf("b"), index.searchTrackIds("account-b", "\"еж*\""))
        index.deleteByIds("account-a", listOf("a"))
        index.insertAll(listOf(TrackSearchEntity("a", "account-a", "ежик новый")))
        assertEquals(1, index.allForSource("account-a").count { it.trackId == "a" })
        assertTrue(index.searchTrackIds("account-a", "\"дор*\"").isEmpty())
        assertEquals(2, index.deleteForSource("account-a"))
        assertEquals(listOf("b"), index.searchTrackIds("account-b", "\"еж*\""))
    }

    private fun track(id: String, source: String, missing: Int = 0) = RemoteTrackEntity(
        id = id,
        sourceKey = source,
        encodedPath = "/AllMusic/$id.mp3",
        fileName = "$id.mp3",
        remoteEtag = null,
        remoteSize = 100,
        remoteModified = null,
        lastSeenEpoch = 1,
        missingScans = missing,
    )
}
