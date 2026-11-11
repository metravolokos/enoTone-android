"""Track artwork: visible-only album covers shared between every library page."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class TrackArtworkThumbnailContractTest(unittest.TestCase):
    def text(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_only_composed_rows_resolve_shared_album_cover(self):
        screen = self.text("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        tile = self.text("app/src/main/java/app/enotone/android/TrackArtworkThumbnail.kt")
        row = screen[screen.index("items(visible, key = { it.id }) { track ->"):
                     screen.index("// Fixed-height mini player:")]
        for token in ("TrackArtworkThumbnail(", "trackId = track.id",
                      "artworkGroup = artworkCatalog.byTrackId[track.id]",
                      "repository = artworkRepository",
                      'playbackState != "LOADING"',
                      'playbackState != "BUFFERING"',
                      "playTrack(track, visible)", "track.title", "track.artist"):
            self.assertIn(token, row)
        self.assertIn("LaunchedEffect(trackId, artworkGroup, accountEmail, appPassword, allowNetwork)", tile)
        self.assertIn("repository.peekAlbum(accountEmail, it, requestedSide = 192)", tile)
        self.assertIn("repository.cachedAlbumCover(accountEmail, artworkGroup, 192)", tile)
        self.assertIn("repository.albumCover(accountEmail, appPassword,", tile)
        self.assertIn("requestedSide = 192", tile)
        self.assertIn("if (allowNetwork && appPassword.isNotBlank())", tile)
        self.assertIn("Modifier.size(44.dp)", tile)
        self.assertNotIn("WebDavRangeReader(", tile)
        self.assertNotIn("openLibraryDatabase(", tile)

    def test_storage_is_permanent_with_bounded_ram_and_http(self):
        repo = self.text("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        for token in ("context.applicationContext.filesDir", "enotone-artwork-v2",
                      "const val MAX_SIDE = 1200", "LruCache<String, Bitmap>(24 * 1024)",
                      "private val imageSlots = Semaphore(2)",
                      "maxRequests = 128",
                      "maxDownloadedBytes = 8L * 1024 * 1024",
                      "suspend fun albumCover(", "suspend fun clearAll()"):
            self.assertIn(token, repo)
        self.assertNotIn("CACHE_LIMIT_BYTES", repo)
        db = self.text("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        self.assertIn("version = 4", db)


if __name__ == "__main__":
    unittest.main()
