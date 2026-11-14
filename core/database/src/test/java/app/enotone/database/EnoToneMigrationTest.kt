package app.enotone.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun insertLegacyV1(name: String) {
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

    private fun insertVersionFourRows(name: String, count: Int) {
        helper.createDatabase(name, 4).use { db ->
            for (index in 1..count) {
                db.execSQL(
                    "INSERT INTO remote_tracks " +
                        "(id, sourceKey, encodedPath, fileName, remoteEtag, remoteSize, " +
                        "remoteModified, title, artist, album, albumArtist, year, trackNumber, " +
                        "discNumber, genre, lastSeenEpoch, missingScans, tagScanCode) " +
                        "VALUES ('id$index', 'source-a', '/fixture/$index.mp3', 'file$index.mp3', " +
                        "'etag-$index', ${index + 1000}, 'modified-$index', 'title-$index', " +
                        "'artist-$index', 'album-$index', 'album-artist-$index', ${2000 + index % 25}, " +
                        "${index % 99 + 1}, ${index % 2 + 1}, 'genre-$index', $index, " +
                        "${index % 2}, 'MP3_ID3V24')",
                )
            }
        }
    }

    private fun assertV1DataPreserved(name: String) {
        helper.runMigrationsAndValidate(name, 5, true).use { db ->
            db.query("SELECT title, albumArtist, genre, sourceKey, durationMs FROM remote_tracks WHERE id = 'id1'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Legacy title", cursor.getString(0))
                    assertEquals("Legacy album artist", cursor.getString(1))
                    assertNull(cursor.getString(2)) // v1 had no genre field
                    assertEquals("source-a", cursor.getString(3))
                    assertNull(cursor.getString(4))
                }
            assertEquals(0, count(db, "playlists"))
            assertEquals(0, count(db, "playlist_items"))
        }
    }

    @Test fun sequentialMigrationFromOneThroughFivePreservesRows() {
        val name = "test-migrate-sequential"
        insertLegacyV1(name)
        helper.runMigrationsAndValidate(name, 2, true).close()
        helper.runMigrationsAndValidate(name, 3, true).close()
        helper.runMigrationsAndValidate(name, 4, true).close()
        assertV1DataPreserved(name)
    }

    @Test fun directUpgradeFromOneToFivePreservesRows() {
        val name = "test-migrate-direct"
        insertLegacyV1(name)
        assertV1DataPreserved(name)
    }

    @Test fun migrationFromFourKeepsAllHundredRowsAndFieldsAndCreatesEmptyPlaylistTables() {
        val name = "test-migrate-four-to-five"
        val rows = 120
        insertVersionFourRows(name, rows)
        helper.runMigrationsAndValidate(name, 5, true).use { db ->
            assertEquals(rows, count(db, "remote_tracks"))
            assertEquals(0, count(db, "playlists"))
            assertEquals(0, count(db, "playlist_items"))
            db.query("""
                SELECT id, sourceKey, encodedPath, fileName, remoteEtag, remoteSize,
                    remoteModified, title, artist, album, albumArtist, year,
                    trackNumber, discNumber, genre, lastSeenEpoch, missingScans,
                    tagScanCode, durationMs, firstSeenEpochMs
                FROM remote_tracks ORDER BY CAST(SUBSTR(id, 3) AS INTEGER)
            """).use { cursor ->
                var index = 0
                while (cursor.moveToNext()) {
                    index++
                    assertEquals("id$index", cursor.getString(0))
                    assertEquals("source-a", cursor.getString(1))
                    assertEquals("/fixture/$index.mp3", cursor.getString(2))
                    assertEquals("file$index.mp3", cursor.getString(3))
                    assertEquals("etag-$index", cursor.getString(4))
                    assertEquals(index + 1000L, cursor.getLong(5))
                    assertEquals("modified-$index", cursor.getString(6))
                    assertEquals("title-$index", cursor.getString(7))
                    assertEquals("artist-$index", cursor.getString(8))
                    assertEquals("album-$index", cursor.getString(9))
                    assertEquals("album-artist-$index", cursor.getString(10))
                    assertEquals(2000 + index % 25, cursor.getInt(11))
                    assertEquals(index % 99 + 1, cursor.getInt(12))
                    assertEquals(index % 2 + 1, cursor.getInt(13))
                    assertEquals("genre-$index", cursor.getString(14))
                    assertEquals(index.toLong(), cursor.getLong(15))
                    assertEquals(index % 2, cursor.getInt(16))
                    assertEquals("MP3_ID3V24", cursor.getString(17))
                    assertTrue(cursor.isNull(18))
                    // Строки до v5 — «добавлены до учёта», не «недавно добавленные».
                    assertTrue(cursor.isNull(19))
                }
                assertEquals(rows, index)
            }
        }
    }

    @Test fun migrationFiveToSixCreatesEmptyFtsAndPreservesTheLibrary() {
        val name = "test-migrate-five-to-six"
        helper.createDatabase(name, 5).use { db ->
            db.execSQL("""
                INSERT INTO remote_tracks
                (id, sourceKey, encodedPath, fileName, remoteEtag, remoteSize,
                 remoteModified, title, artist, album, albumArtist, lastSeenEpoch, missingScans)
                VALUES ('persist', 'source-a', '/fixture/a.mp3', 'a.mp3', 'etag', 1234,
                        'modified', 'Ёжик', 'Иван', 'Сборник', 'Автор', 1, 0)
            """.trimIndent())
        }
        helper.runMigrationsAndValidate(name, 6, true).use { db ->
            assertEquals(1, count(db, "remote_tracks"))
            assertEquals(0, count(db, "track_search"))
            db.query("SELECT title, artist, albumArtist FROM remote_tracks WHERE id = 'persist'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("Ёжик", cursor.getString(0))
                    assertEquals("Иван", cursor.getString(1))
                    assertEquals("Автор", cursor.getString(2))
                }
        }
    }

    @Test fun directMigrationOneToSixRetainsHistoricalRowsAndCreatesFts() {
        val name = "test-migrate-one-to-six"
        insertLegacyV1(name)
        helper.runMigrationsAndValidate(name, 6, true).use { db ->
            assertEquals(1, count(db, "remote_tracks"))
            assertEquals(0, count(db, "track_search"))
            assertEquals(0, count(db, "playlist_items"))
        }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
