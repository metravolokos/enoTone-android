"""Manual per-album metadata refresh: no unrelated WebDAV GET or data loss."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class SelectiveAlbumTagRefreshContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_only_ten_local_ids_are_queued_and_read(self):
        album = self.src("data/library/src/main/java/app/enotone/library/AlbumTagRefresher.kt")
        dao = self.src("core/database/src/main/java/app/enotone/database/RemoteTrackDao.kt")
        pilot = self.src("data/library/src/main/java/app/enotone/library/PilotTagIndexer.kt")
        for token in (
            "MAX_FILES_PER_TAP = 10",
            "require(trackIds.size in 1..MAX_FILES_PER_TAP)",
            "require(trackIds.distinct().size == trackIds.size)",
            "queueTagRefreshByIds(sourceKey, trackIds)",
            "count = 10",
            "targetIds = trackIds.toSet()",
            "stopOnRetryableFailure = true",
            "onFileCompleted = { id -> attempted.add(id) }",
            "onFileOutcome = { id, finished ->",
            "if (finished) completed.add(id)",
            "return AlbumTagRefreshReport(",
            "completed.toSet(), attempted.toSet(), progress",
        ):
            self.assertIn(token, album)
        for token in (
            "WHERE sourceKey = :sourceKey AND id IN (:ids) AND missingScans = 0",
            "remoteSize > 0",
            "suspend fun queueTagRefreshByIds(sourceKey: String, ids: List<String>): Int",
        ):
            self.assertIn(token, dao)
        self.assertIn("if (targetIds == null) pending else pending.filter { it.id in targetIds }", pilot)
        self.assertIn("onFileCompleted(item.id)", pilot)
        self.assertIn("onFileOutcome(", pilot)
        self.assertIn('!recordCode.startsWith("RETRY_")', pilot)
        self.assertIn('!recordCode.startsWith("FAILED_")', pilot)
        self.assertIn("saved && !recordCode.startsWith", pilot)
        self.assertNotIn("queueAllTagRefresh", album)
        self.assertNotIn("ManagedTagIndexSession(", album)
        self.assertNotIn("deleteDatabase(", album)

    def test_single_track_pilot_persists_just_added_numeric_tags(self):
        pilot = self.src("data/library/src/main/java/app/enotone/library/PilotTagIndexer.kt")
        for token in (
            "year = tags?.year",
            "trackNumber = tags?.trackNumber",
            "discNumber = tags?.discNumber",
            "dao.recordFastScanIfUnchanged(",
            "maxRequests = minOf(6, remainingGets)",
            "maxDownloadedBytes = minOf(6L * 65536, remainingBytes)",
        ):
            self.assertIn(token, pilot)

    def test_user_confirms_selected_album_and_local_result_reloads(self):
        ui = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        header = self.src("app/src/main/java/app/enotone/android/AlbumDetailHeader.kt")
        for token in (
            "val albumRefreshSampleIds = remember(",
            "AlbumTagRefreshSelection.nextBatch(",
            "albumAttemptedByGroup[selectedGroup?.key].orEmpty()",
            "result.attemptedIds",
            "result.checkedIds",
            "unfinished = ids.count { it !in result.checkedIds }",
            "R.string.album_refresh_retry_needed",
            "eligibleIds,",
            "confirmAlbumRefresh = selectedGroup.key to",
            "AlertDialog(",
            "R.string.album_refresh_confirm_title",
            "R.string.album_refresh_confirm_text",
            "AlbumTagRefresher(db).refresh(",
            "managedIndexStatus.state != ManagedScanState.RUNNING",
            'playbackState !in setOf("PLAYING", "BUFFERING", "LOADING")',
            "libraryRefreshEpoch++",
            "LaunchedEffect(accountEmail, libraryRefreshEpoch)",
            "albumCheckedByGroup = albumCheckedByGroup +",
            "R.string.album_refresh_done",
            "onPlayAll = {",
            "playTrack(first, albumTracks)",
        ):
            self.assertIn(token, ui)
        for token in (
            "OutlinedButton(",
            "enabled = canRefreshTags && !refreshingTags",
            "onClick = onRefreshTags",
            "R.string.album_refresh_tags",
        ):
            self.assertIn(token, header)
        policy = self.src(
            "data/library/src/main/java/app/enotone/library/AlbumTagRefreshSelection.kt")
        self.assertIn("waiting = albumIds.distinct().filterNot { it in completedIds }", policy)
        self.assertIn("val fresh = waiting.filterNot { it in attemptedIds }", policy)
        self.assertIn("val retried = waiting.filter { it in attemptedIds }", policy)
        self.assertNotIn("queueAllForRefresh(", ui)
        self.assertNotIn("WebDavRangeReader(", header)
        self.assertNotIn("deleteDatabase(", ui)

    def test_only_ui_strings_added_and_room_v3_stable(self):
        names = {
            "album_refresh_tags", "album_refresh_busy",
            "album_refresh_confirm_title", "album_refresh_confirm_text",
            "album_refresh_confirm_action", "album_refresh_cancel",
            "album_refresh_done", "album_refresh_failed",
            "album_refresh_no_more", "album_refresh_audio_active",
            "album_refresh_retry_needed", "album_refresh_backoff",
            "album_refresh_budget",
        }
        for path in ("app/src/main/res/values/strings.xml",
                     "app/src/main/res/values-en/strings.xml"):
            root = ET.fromstring(self.src(path))
            actual = {x.attrib["name"] for x in root.findall("string")}
            self.assertTrue(names.issubset(actual), names - actual)
        db = self.src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        self.assertIn("version = 4", db)
        self.assertIn("AutoMigration(from = 2, to = 3)", db)
        self.assertIn("AutoMigration(from = 3, to = 4)", db)
        self.assertNotIn("fallbackToDestructiveMigration", db)


if __name__ == "__main__":
    unittest.main()
