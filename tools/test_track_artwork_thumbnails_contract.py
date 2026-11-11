"""Track artwork contract: LazyColumn visible rows only; no library-wide network pass."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/app/enotone/android"


class TrackArtworkThumbnailContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_only_composed_visible_track_rows_request_thumbnails(self):
        screen = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        tile = self.src("app/src/main/java/app/enotone/android/TrackArtworkThumbnail.kt")
        self.assertIn("items(visible, key = { it.id }) { track ->", screen)
        row = screen[screen.index("items(visible, key = { it.id }) { track ->"):
                     screen.index("// Fixed-height mini player:")]
        for required in (
            "preparedById[track.id]",
            "PlayingArtworkTarget(",
            "TrackArtworkThumbnail(",
            "trackId = track.id",
            "target = artworkTarget",
            "repository = artworkRepository",
            'playbackState != "LOADING"',
            'playbackState != "BUFFERING"',
            "playTrack(track, visible)",
            "track.trackNumber",
            "track.discNumber",
            "track.title",
            "track.artist",
        ):
            self.assertIn(required, row)
        self.assertNotIn("openLibraryDatabase(", row)
        self.assertNotIn("WebDavRangeReader(", row)
        self.assertIn("LaunchedEffect(trackId, target, albumPreviewTarget, accountEmail,", tile)
        self.assertIn("repository.cover(accountEmail, appPassword, target, requestedSide = 192)", tile)
        self.assertIn("if (allowNetwork && target != null && appPassword.isNotBlank())", tile)
        self.assertIn("Modifier.size(44.dp)", tile)
        self.assertIn("R.drawable.ic_nav_tracks", tile)
        self.assertNotIn("LazyColumn(", tile)
        self.assertNotIn("openLibraryDatabase(", tile)
        self.assertNotIn("WebDavRangeReader(", tile)

    def test_reuses_existing_limits_and_never_changes_room_or_signing(self):
        artwork = self.src("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        self.assertIn("private val imageSlots = Semaphore(2)", artwork)
        self.assertIn("imageSlots.withPermit {", artwork)
        self.assertIn("enotone-artwork-v2", artwork)
        self.assertIn("CACHE_LIMIT_BYTES = 192L * 1024 * 1024", artwork)
        self.assertIn("const val MAX_SIDE = 1320", artwork)
        self.assertIn("maxDownloadedBytes = 8L * 1024 * 1024", artwork)
        db = self.src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        self.assertIn("version = 4", db)
        gradle = self.src("app/build.gradle.kts")
        self.assertIn('applicationIdSuffix = ".preview"', gradle)
        self.assertIn('getByName("stablePreview")', gradle)


if __name__ == "__main__":
    unittest.main()
