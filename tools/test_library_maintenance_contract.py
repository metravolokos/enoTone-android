"""M1 tag maintenance: offline diagnostics, safe refresh and incremental list sync."""
import pathlib
import re
import sqlite3
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class TagMaintenanceContractTest(unittest.TestCase):
    def text(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_missing_album_diagnosis_is_offline_and_account_scoped(self):
        dao = self.text("core/database/src/main/java/app/enotone/database/RemoteTrackDao.kt")
        helper = self.text("data/library/src/main/java/app/enotone/library/TagMaintenanceStore.kt")
        for needle in (
            "data class MissingAlbumDiagnostic(",
            "album IS NULL OR TRIM(album) = ''",
            "WHERE sourceKey = :sourceKey AND missingScans = 0",
            "ORDER BY LOWER(fileName), id",
            "LIMIT :limit",
            "suspend fun missingAlbums(",
        ):
            self.assertIn(needle, dao)
        self.assertIn("LibrarySnapshotPlanner.accountKey(email)", helper)
        self.assertIn("missingAlbums(", helper)
        self.assertNotIn("WebDavRangeReader", helper)
        self.assertNotIn("FlatLibraryWebDavClient", helper)

    def test_requested_reindex_keeps_old_values_until_replacement(self):
        dao = self.text("core/database/src/main/java/app/enotone/database/RemoteTrackDao.kt")
        selector = self.text("data/library/src/main/java/app/enotone/library/PilotTagIndexer.kt")
        store = self.text("data/library/src/main/java/app/enotone/library/TagMaintenanceStore.kt")
        for needle in (
            "UPDATE remote_tracks SET tagScanCode = 'RETRY_REFRESH'",
            "suspend fun requestTagRefresh(id: String, sourceKey: String): Int",
            "suspend fun requestFullTagRefresh(sourceKey: String): Int",
            "tagScanCode = 'RETRY_REFRESH' OR",
            "WHEN tagScanCode = 'RETRY_REFRESH' AND :code LIKE 'RETRY_%'",
            "THEN 'RETRY_REFRESH'",
            "THEN title ELSE :title END",
            "THEN album ELSE :album END",
            "remoteEtag IS :etag AND remoteSize IS :size",
        ):
            self.assertIn(needle, dao)
        self.assertIn('it.tagScanCode == "RETRY_REFRESH"', selector)
        self.assertIn('it.tagScanCode != "RETRY_REFRESH"', selector)
        self.assertIn("scheduleOne", store)
        self.assertIn("scheduleAll", store)
        self.assertNotIn("DELETE FROM remote_tracks", dao)

    def test_actual_sql_keeps_old_metadata_during_failed_refresh(self):
        source = self.text("core/database/src/main/java/app/enotone/database/RemoteTrackDao.kt")
        sql = dict((name, query) for query, name in re.findall(
            r'@Query\(\s*"""(.*?)"""\s*\)\s*suspend fun (\w+)\(',
            source, flags=re.S,
        ))
        self.assertIn("requestTagRefresh", sql)
        self.assertIn("recordFastScanIfUnchanged", sql)
        self.assertIn("pendingFastScans", sql)

        db = sqlite3.connect(":memory:")
        try:
            db.execute("""
                CREATE TABLE remote_tracks (
                    id TEXT PRIMARY KEY, sourceKey TEXT, encodedPath TEXT,
                    fileName TEXT, remoteEtag TEXT, remoteSize INTEGER,
                    remoteModified TEXT, missingScans INTEGER,
                    title TEXT, artist TEXT, album TEXT, albumArtist TEXT,
                    tagScanCode TEXT
                )
            """)
            db.execute("""
                INSERT INTO remote_tracks VALUES
                    ('a', 'source-a', '/AllMusic/a.flac', 'a.flac',
                     'etag-a', 80000, NULL, 0,
                     'Old title', 'Old artist', NULL, 'Old Album Artist',
                     'FLAC_VORBIS')
            """)
            db.execute(sql["requestTagRefresh"], {"id": "a", "sourceKey": "source-a"})
            self.assertEqual(db.execute(
                "SELECT title, artist, tagScanCode FROM remote_tracks WHERE id='a'"
            ).fetchone(), ("Old title", "Old artist", "RETRY_REFRESH"))
            self.assertEqual(len(db.execute(
                sql["pendingFastScans"], {"sourceKey": "source-a"}
            ).fetchall()), 1)

            args = dict(id="a", sourceKey="source-a", path="/AllMusic/a.flac",
                        etag="etag-a", size=80000, modified=None,
                        code="RETRY_NETWORK", title=None, artist=None,
                        album=None, albumArtist=None)
            db.execute(sql["recordFastScanIfUnchanged"], args)
            self.assertEqual(db.execute(
                "SELECT title, artist, tagScanCode FROM remote_tracks WHERE id='a'"
            ).fetchone(), ("Old title", "Old artist", "RETRY_REFRESH"))

            args.update(code="FLAC_VORBIS", title="New title",
                        artist="New artist", album="Now has album",
                        albumArtist="Album Artist")
            db.execute(sql["recordFastScanIfUnchanged"], args)
            self.assertEqual(db.execute(
                "SELECT title, album, tagScanCode FROM remote_tracks WHERE id='a'"
            ).fetchone(), ("New title", "Now has album", "FLAC_VORBIS"))
            self.assertEqual(len(db.execute(
                sql["pendingFastScans"], {"sourceKey": "source-a"}
            ).fetchall()), 0)
        finally:
            db.close()

    def test_album_source_probe_is_bounded_and_never_writes_room(self):
        code = self.text("data/library/src/main/java/app/enotone/library/TagMaintenanceStore.kt")
        ui = self.text("app/src/main/java/app/enotone/android/MainActivity.kt")
        for item in (
            "suspend fun probeAlbum(",
            "playableById(id, key)",
            "eligibleForSingleTagWrite(key, selected)",
            "FastAudioTagReader().read(",
            "maxRequests = 6",
            "maxDownloadedBytes = 6L * 65536L",
            "result.embeddedAlbum",
            '"READ_ERROR", false, false',
            "catch (cancelled: CancellationException)",
        ):
            self.assertIn(item, code)
        probe = code[code.index("suspend fun probeAlbum("):
                     code.index("suspend fun scheduleOne(")]
        self.assertNotIn("recordFastScanIfUnchanged", probe)
        self.assertNotIn("saveTagsIfSnapshotMatches", probe)
        for item in (
            "R.string.library_probe_album_source",
            "R.string.library_album_probe_report",
            "TagMaintenanceStore(db).probeAlbum(",
        ):
            self.assertIn(item, ui)

    def test_ui_has_opt_in_confirmation_no_secret_or_accidental_bulk_scan(self):
        ui = self.text("app/src/main/java/app/enotone/android/MainActivity.kt")
        for needle in (
            "R.string.library_find_missing_album",
            "R.string.library_missing_album_filename",
            "TagMaintenanceStore(db).missingAlbumFiles(email)",
            "TagMaintenanceStore(db).scheduleOne(email, item.id)",
            "TagMaintenanceStore(db).scheduleAll(email)",
            "confirmFullTagRefresh",
            "startManagedSession(false)",
            "startManagedSession(true)",
            "R.string.library_sync_changes",
            "LibrarySnapshotStore(database)",
            "managedStatus.state != ManagedScanState.RUNNING",
        ):
            self.assertIn(needle, ui)
        # No filenames or paths are added to the public sanitized report.
        self.assertNotIn("Log.d(", ui)
        self.assertNotIn("putExtra(\"password\"", ui)

    def test_same_room_schema_and_existing_sync_does_not_drop_tags(self):
        schema = self.text("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        planner = self.text("data/library/src/main/java/app/enotone/library/LibrarySnapshotPlanner.kt")
        self.assertIn("version = 2", schema)
        self.assertIn("title = if (sameVersion) old?.title else null", planner)
        self.assertIn("album = if (sameVersion) old?.album else null", planner)
        self.assertIn("tagScanCode = if (sameVersion) old?.tagScanCode else null", planner)
        self.assertIn("dao.upsertAll(plan.upserts)", self.text(
            "data/library/src/main/java/app/enotone/library/LibrarySnapshotStore.kt"))


if __name__ == "__main__":
    unittest.main()
