"""Final M1 contract: incremental sync, explicit reindex scopes and durable tags."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def src(path):
    return (ROOT / path).read_text(encoding="utf-8")


class IndexingFinalizationContractTest(unittest.TestCase):
    def test_incremental_button_never_forces_all_tags(self):
        ui = src("app/src/main/java/app/enotone/android/MainActivity.kt")
        start = ui.index("// One explicit action: quick listing + background metadata completion.")
        end = ui.index("// Explicit full reindex is a DIFFERENT action", start)
        part = ui[start:end]
        for token in ("val listing = client.list(email, password)",
                      "val plan = store.save(email, listing)",
                      "pendingFastScans(",
                      "startMetadata = result[5] > 0",
                      "if (startMetadata) startManagedSession(true)"):
            self.assertIn(token, part)
        self.assertNotIn("LibraryReindexCoordinator(", part)
        self.assertNotIn("queueAllTagRefresh(", part)
        self.assertNotIn("deleteDatabase(", part)
        self.assertEqual(1, part.count("startManagedSession(true)"))

    def test_one_confirmed_full_action_starts_background_reader_after_queue(self):
        ui = src("app/src/main/java/app/enotone/android/MainActivity.kt")
        code = ui[ui.index("// Explicit full reindex is a DIFFERENT action"):
                  ui.index("if (showTechnicalTools) {", ui.index(
                      "// Explicit full reindex is a DIFFERENT action"))]
        for token in ("R.string.library_reindex_action",
                      "if (confirmLibraryReindex) {",
                      "AlertDialog(",
                      "R.string.library_reindex_confirm_title",
                      "R.string.library_reindex_confirm_message",
                      "LibraryReindexCoordinator(db).queue(",
                      "email, LibraryReindexScope.EntireLibrary",
                      "selected.artworkSources.map { item ->",
                      "ArtworkPreviewRepository(appContext)",
                      ".invalidate(email, targets)",
                      "busy = false",
                      "if (queued > 0) startManagedSession(true)"):
            self.assertIn(token, code)
        self.assertLess(code.index("busy = false"),
                        code.index("if (queued > 0) startManagedSession(true)"))
        self.assertIn("managedStatus.state != ManagedScanState.RUNNING", code)
        self.assertIn("!managedStatus.terminating", code)
        self.assertNotIn("Intent(appContext, IndexingForegroundService", code)
        self.assertNotIn("password =", code)
        self.assertNotIn("deleteDatabase(", code)

    def test_group_scope_matches_existing_album_and_performer_grouping(self):
        reindex = src("data/library/src/main/java/app/enotone/library/LibraryReindexCoordinator.kt")
        for token in ("data object EntireLibrary",
                      "data class Album(val groupKey: String)",
                      "data class Artist(val groupKey: String)",
                      "data class AlbumArtist(val groupKey: String)",
                      "OfflineLibraryGrouping.albums(tracks)",
                      "OfflineLibraryGrouping.artists(tracks)",
                      "OfflineLibraryGrouping.albumArtists(tracks)",
                      "LibrarySnapshotPlanner.accountKey(accountEmail)",
                      "dao.allForSource(sourceKey)",
                      "database.withTransaction {",
                      "MAX_SQL_IDS_PER_BATCH = 400",
                      ".chunked(MAX_SQL_IDS_PER_BATCH)",
                      "dao.queueTagRefreshByIds(sourceKey, ids)",
                      "dao.queueAllTagRefresh(sourceKey)",
                      "artworkSources = selected.mapNotNull"):
            self.assertIn(token, reindex)
        self.assertIn("item.missingScans == 0", reindex)
        self.assertIn("(item.remoteSize ?: 0L) > 0L", reindex)
        self.assertIn("item.sourceKey == sourceKey", reindex)
        self.assertNotIn("WebDavRangeReader(", reindex)
        self.assertNotIn("FlatLibraryWebDavClient(", reindex)
        self.assertNotIn("startForeground(", reindex)
        self.assertNotIn("deleteDatabase(", reindex)

    def test_old_tags_survive_failed_or_cancelled_forced_refresh(self):
        dao = src("core/database/src/main/java/app/enotone/database/RemoteTrackDao.kt")
        for token in ("SET tagScanCode = 'RETRY_REFRESH'",
                      "title = CASE WHEN :code IN",
                      "THEN :title ELSE title END",
                      "THEN :genre ELSE genre END",
                      "AND (tagScanCode IS NULL OR tagScanCode LIKE 'RETRY_%')",
                      "remoteEtag IS :etag", "remoteSize IS :size",
                      "remoteModified IS :modified"):
            self.assertIn(token, dao)
        session = src("data/library/src/main/java/app/enotone/library/ManagedTagIndexSession.kt")
        self.assertIn("MAX_CONTINUOUS_BATCHES = 80", session)
        self.assertIn("MAX_CONTINUOUS_NANOS", session)
        self.assertIn("currentCoroutineContext().ensureActive()", session)
        fgs = src("app/src/main/java/app/enotone/android/IndexingForegroundService.kt")
        self.assertIn("START_NOT_STICKY", fgs)
        self.assertIn("ACTION_PAUSE", fgs)
        self.assertIn("ACTION_STOP", fgs)
        self.assertNotIn("WIFI", fgs)
        self.assertNotIn("WorkManager(", fgs)

    def test_listing_detects_bad_etag_without_metadata_loss_on_unchanged_files(self):
        planner = src("data/library/src/main/java/app/enotone/library/LibrarySnapshotPlanner.kt")
        self.assertIn("old.remoteSize != new.sizeBytes", planner)
        self.assertIn("old.remoteModified != new.lastModified", planner)
        self.assertIn("return old.remoteEtag == new.etag", planner)
        self.assertIn("tagScanCode = if (sameVersion) old?.tagScanCode else null", planner)
        self.assertIn("genre = if (sameVersion) old?.genre else null", planner)
        self.assertIn("trackNumber = if (sameVersion) old?.trackNumber else null", planner)
        self.assertIn("discNumber = if (sameVersion) old?.discNumber else null", planner)

    def test_new_action_is_localized_without_schema_or_signing_changes(self):
        new_keys = {
            "library_reindex_action",
            "library_reindex_confirm_title",
            "library_reindex_confirm_message",
            "library_reindex_confirm_action",
            "library_reindex_queued",
            "library_reindex_empty",
            "library_reindex_failed",
        }
        for path in ("app/src/main/res/values/strings.xml",
                     "app/src/main/res/values-en/strings.xml"):
            xml = ET.fromstring(src(path))
            names = {item.attrib["name"] for item in xml.findall("string")}
            self.assertTrue(new_keys.issubset(names), names ^ new_keys)
        db = src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        gradle = src("app/build.gradle.kts")
        self.assertIn("version = 4", db)
        self.assertIn("AutoMigration(from = 3, to = 4)", db)
        self.assertNotIn("fallbackToDestructiveMigration", db)
        self.assertIn('applicationIdSuffix = ".preview"', gradle)


if __name__ == "__main__":
    unittest.main()
