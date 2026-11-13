package app.enotone.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise production auto-migrations using the committed historical JSON schemas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnoToneMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), EnoToneDatabase::class.java,
    )

    private fun insertLegacy(name: String) {
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO remote_tracks " +
                    "(id, sourceKey, encodedPath, fileName, remoteEtag, remoteSize, " +
                    "remoteModified, title, artist, album, albumArtist, lastSeenEpoch, missingScans) " +
                    "VALUES ('id1', 'source-a', '/fixture/1.mp3', '1.mp3', 'etag', 1234, " +
                    "'modified', 'Legacy title', 'Legacy artist', 'Legacy album', " +
                    "'Legacy album artist', 22, 0)",
            )
            close()
        }
    }

    private fun assertPreserved(name: String, version: Int) {
        helper.runMigrationsAndValidate(name, version, true).use { db ->
            db.query("SELECT title, albumArtist, genre, sourceKey FROM remote_tracks WHERE id = 'id1'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Legacy title", cursor.getString(0))
                    assertEquals("Legacy album artist", cursor.getString(1))
                    assertTrue(cursor.isNull(2)) // v4 genre is nullable for old installations
                    assertEquals("source-a", cursor.getString(3))
                }
        }
    }

    @Test fun sequentialMigrationFromOneThroughFourPreservesRows() {
        val name = "test-migrate-sequential"
        insertLegacy(name)
        helper.runMigrationsAndValidate(name, 2, true).close()
        helper.runMigrationsAndValidate(name, 3, true).close()
        assertPreserved(name, 4)
    }

    @Test fun directUpgradeFromOneToFourPreservesRows() {
        val name = "test-migrate-direct"
        insertLegacy(name)
        assertPreserved(name, 4)
    }
}
