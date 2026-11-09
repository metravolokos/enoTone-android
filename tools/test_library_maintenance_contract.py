"""M1 tag maintenance: offline diagnostics, safe refresh and incremental list sync."""
import pathlib
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
