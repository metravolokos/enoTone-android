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

/** FTS4 behavior: prefix tokens, source isolation, missing tracks and rebuilds. */
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

    private suspend fun seed() {
        database.remoteTrackDao().upsertAll(listOf(
            track("a", "account-a"), track("b", "account-b"),
            track("missing", "account-a", missing = 1),
        ))
        database.trackSearchDao().insertAll(listOf(
            TrackSearchEntity("a", "account-a", "ежик зеленая дорога"),
            TrackSearchEntity("b", "account-b", "ежик зеленая дорога"),
            TrackSearchEntity("missing", "account-a", "ежик зеленая дорога"),
        ))
    }

    @Test fun prefixAndMatchesOnlyPresentTracksInOneAccount() = runBlocking {
        seed()
        val dao = database.trackSearchDao()
        assertEquals(listOf("a"),
            dao.searchTrackIds("account-a", "\"еж*\" \"дор*\""))
        assertEquals(emptyList<String>(), dao.searchTrackIds("account-a", "\"ираж*\""))
    }

    @Test fun anotherAccountIsNotExposed() = runBlocking {
        seed()
        assertEquals(listOf("b"),
            database.trackSearchDao().searchTrackIds("account-b", "\"еж*\""))
    }

    @Test fun changingIndexTextRemovesOldMatchesWithoutDuplicates() = runBlocking {
        seed()
        val dao = database.trackSearchDao()
        dao.deleteByIds("account-a", listOf("a"))
        dao.insertAll(listOf(TrackSearchEntity("a", "account-a", "ежик новый")))
        assertEquals(1, dao.allForSource("account-a").count { it.trackId == "a" })
        assertTrue(dao.searchTrackIds("account-a", "\"дор*\"").isEmpty())
    }

    @Test fun deletingOneSourceKeepsAnotherIntact() = runBlocking {
        seed()
        val dao = database.trackSearchDao()
        assertEquals(2, dao.deleteForSource("account-a"))
        assertEquals(listOf("b"), dao.searchTrackIds("account-b", "\"еж*\""))
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
