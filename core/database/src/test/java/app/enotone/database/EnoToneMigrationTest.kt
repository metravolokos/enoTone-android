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
        // Test-only workaround for the `androidx.sqlite` path comparison that breaks on Windows,
        // see PathAwareSQLiteOpenHelperFactory.
        emptyList(), PathAwareSQLiteOpenHelperFactory(),
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

    @Test fun migrationSixToSevenRemovesFtsAndPreservesTracksPlaylistsAndPositions() {
        val name = "test-migrate-six-to-seven"
        helper.createDatabase(name, 6).use { db ->
            db.execSQL("""
                INSERT INTO remote_tracks
                (id, sourceKey, encodedPath, fileName, remoteEtag, remoteSize,
                 remoteModified, title, artist, album, albumArtist, lastSeenEpoch,
                 missingScans, firstSeenEpochMs)
                VALUES ('persist', 'source-a', '/fixture/a.mp3', 'a.mp3', 'etag', 1234,
                        'modified', 'Fixture', 'Artist', 'Album', 'Author', 1, 0, 987654)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO playlists
                (id, sourceKey, name, kind, createdEpochMs, updatedEpochMs)
                VALUES ('playlist-1', 'source-a', 'Example', 'MANUAL', 55, 66)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO playlist_items
                (playlistId, trackId, position, addedEpochMs)
                VALUES ('playlist-1', 'persist', 7, 77)
            """.trimIndent())
            db.execSQL("""
                INSERT INTO track_search (trackId, sourceKey, text)
                VALUES ('persist', 'source-a', 'fixture')
            """.trimIndent())
        }
        helper.runMigrationsAndValidate(name, 7, true, EnoToneDatabase.MIGRATION_6_7).use { db ->
            assertEquals(1, count(db, "remote_tracks"))
            assertEquals(1, count(db, "playlists"))
            assertEquals(1, count(db, "playlist_items"))
            db.query("SELECT firstSeenEpochMs FROM remote_tracks WHERE id = 'persist'")
                .use { c -> assertTrue(c.moveToFirst()); assertEquals(987654L, c.getLong(0)) }
            db.query("SELECT position, addedEpochMs FROM playlist_items").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(7, c.getInt(0))
                assertEquals(77L, c.getLong(1))
            }
            db.query("SELECT name, kind FROM playlists WHERE id = 'playlist-1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Example", c.getString(0))
                assertEquals("MANUAL", c.getString(1))
            }
            db.query("SELECT COUNT(*) FROM sqlite_master WHERE name = 'track_search'")
                .use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test fun directMigrationOneThroughSevenPreservesHistoricalRows() {
        val name = "test-migrate-one-to-seven"
        insertLegacyV1(name)
        helper.runMigrationsAndValidate(name, 7, true, EnoToneDatabase.MIGRATION_6_7).use { db ->
            assertEquals(1, count(db, "remote_tracks"))
            assertEquals(0, count(db, "playlists"))
            db.query("SELECT albumArtist FROM remote_tracks WHERE id = 'id1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Legacy album artist", c.getString(0))
            }
        }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
