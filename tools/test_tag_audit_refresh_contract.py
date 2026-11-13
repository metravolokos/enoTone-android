"""Local-only incomplete-tag audit and explicit refresh contract."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
DAO = ROOT / "core/database/src/main/java/app/enotone/database/RemoteTrackDao.kt"
STORE = ROOT / "data/library/src/main/java/app/enotone/library/LibraryTagStore.kt"
PLANNER = ROOT / "data/library/src/main/java/app/enotone/library/LibrarySnapshotPlanner.kt"
PILOT = ROOT / "data/library/src/main/java/app/enotone/library/PilotTagIndexer.kt"
LANGS = [
    ROOT / "app/src/main/res/values/strings.xml",
    ROOT / "app/src/main/res/values-en/strings.xml",
]


class AuditRefreshContractTest(unittest.TestCase):
    def test_album_audit_and_search_use_local_room_only(self):
        dao = DAO.read_text()
        store = STORE.read_text()
        for needle in ("suspend fun tracksWithoutAlbum(", "album IS NULL",
                       "suspend fun tracksWithoutAlbumArtist(",
                       "albumArtist IS NULL",
                       "ORDER BY fileName COLLATE NOCASE", "LIMIT :limit"):
            self.assertIn(needle, dao)
        for needle in ("missingAlbum(accountEmail:", "missingAlbumArtist(accountEmail:"):
            self.assertIn(needle, store)
        diagnostics = (ROOT / 'app/src/main/java/app/enotone/android/DiagnosticsGateway.kt').read_text()
        diagnostics += (ROOT / 'app/src/main/java/app/enotone/android/DiagnosticsViewModel.kt').read_text()
        self.assertNotIn("Log.", diagnostics)
        self.assertNotIn("println(", diagnostics)

    def test_queue_and_scan_preserve_old_tags_on_failed_refresh(self):
        dao = DAO.read_text()
        pilot = PILOT.read_text()
        for needle in (
            "SET tagScanCode = 'RETRY_REFRESH'",
            "suspend fun queueTagRefreshById(",
            "suspend fun queueAllTagRefresh(",
            "tagScanCode LIKE 'RETRY_%'",
            "tagScanCode != 'RETRY_REFRESH'",
            "CASE WHEN :code IN ('MP3_ID3V23', 'MP3_ID3V24'",
            "THEN :album ELSE album END",
            "THEN :albumArtist ELSE albumArtist END",
            "remoteEtag IS :etag",
            "remoteSize IS :size",
            "remoteModified IS :modified",
        ):
            self.assertIn(needle, dao)
        self.assertIn('it.tagScanCode?.startsWith("RETRY_") == true', pilot)
        self.assertIn("it.title == null && it.artist == null", pilot)
        # A normal completed record stays excluded until an explicit queue action.
        self.assertIn('it.tagScanCode?.startsWith("RETRY_") != false', pilot)

    def test_incremental_listing_preserves_unchanged_versions(self):
        planner = PLANNER.read_text()
        for needle in (
            "title = if (sameVersion) old?.title else null",
            "album = if (sameVersion) old?.album else null",
            "albumArtist = if (sameVersion) old?.albumArtist else null",
            "tagScanCode = if (sameVersion) old?.tagScanCode else null",
        ):
            self.assertIn(needle, planner)

    def test_confirmed_full_reindex_and_localization(self):
        for p in LANGS:
            xml = p.read_text()
            for key in ("tag_audit_title", "tag_audit_missing_album",
                        "tag_audit_missing_album_artist", "library_reindex_action",
                        "library_reindex_confirm_title"):
                self.assertIn(f'name="{key}"', xml)
        launcher = (ROOT / 'app/src/main/java/app/enotone/android/BackgroundWorkLauncher.kt').read_text()
        self.assertNotIn('putExtra("password"', launcher)


if __name__ == "__main__":
    unittest.main()
