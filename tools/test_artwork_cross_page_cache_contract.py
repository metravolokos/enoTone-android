"""Cross-page artwork reuse without assuming every track shares album bytes."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class ArtworkCrossPageCacheContractTest(unittest.TestCase):
    def test_fast_memory_preview_and_disk_only_lookup_without_http(self):
        cache = read("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        for part in (
            "fun peek(email: String, target: PlayingArtworkTarget, requestedSide: Int): Bitmap?",
            "fun cachedSizedCover(key: String, side: Int): Bitmap?",
            "suspend fun cachedCover(",
            "memoryCache.get(\"$key|$side\")",
            "decodeFile(file, side)?.also",
            "file.setLastModified(System.currentTimeMillis())",
            "cachedSizedCover(key, side)?.let { return it }",
            "cachedSizedCover(key, side) ?: withContext(Dispatchers.IO)",
        ):
            self.assertIn(part, cache)
        local_only = cache[cache.index("suspend fun cachedCover("):
                           cache.index("suspend fun invalidate(")]
        self.assertNotIn("WebDavRangeReader(", local_only)
        self.assertNotIn("storeMaster(", local_only)
        self.assertNotIn("atomicWrite(", local_only)
        self.assertNotIn("referenceFile(key).delete()", local_only)
        self.assertNotIn("getExternalFilesDir(", cache)

    def test_album_tile_does_not_clear_successful_preview_when_recomposed(self):
        tile = read("app/src/main/java/app/enotone/android/AlbumArtworkTile.kt")
        header = read("app/src/main/java/app/enotone/android/AlbumDetailHeader.kt")
        for source, side in ((tile, 384), (header, 512)):
            self.assertIn("remember(group.key, target, accountEmail)", source)
            self.assertIn(f"repository.peek(accountEmail, it, requestedSide = {side})", source)
            self.assertIn(f"requestedSide = {side}) ?: cover", source)
            self.assertNotIn("cover = null", source)
        cache = read("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        self.assertIn("LruCache<String, Bitmap>(24 * 1024)", cache)
        self.assertIn("CACHE_LIMIT_BYTES = 192L * 1024 * 1024", cache)
        self.assertIn('enotone-artwork-v2', cache)
        self.assertNotIn("enotone-artwork-v3", cache)

    def test_track_uses_album_as_preview_only_and_validates_own_source(self):
        ui = read("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        track = read("app/src/main/java/app/enotone/android/TrackArtworkThumbnail.kt")
        self.assertIn("val albumPreviewByTrack = remember(tracks, preparedById,", ui)
        self.assertIn("OfflineLibraryGrouping.albums(tracks).flatMap { group ->", ui)
        self.assertIn('if (group.key == "album:missing") return@flatMap emptyList()', ui)
        self.assertIn("albumPreviewTarget = albumPreviewByTrack[track.id]", ui)
        self.assertIn("albumPreviewTarget: PlayingArtworkTarget?", track)
        self.assertIn("repository.peek(accountEmail, it, requestedSide = 192)", track)
        self.assertIn("repository.cachedCover(accountEmail, albumPreviewTarget, 192)", track)
        self.assertIn("albumPreviewTarget.id != target.id", track)
        self.assertIn("cover = repository.cover(accountEmail, appPassword, target, requestedSide = 192)", track)
        self.assertNotIn("storeMaster(", track)
        self.assertNotIn("WebDavRangeReader(", track)
        self.assertNotIn("queueTagRefreshByIds(", track)
        self.assertNotIn("deleteDatabase(", ui)

    def test_preserves_individual_source_identity_and_manual_invalidation(self):
        repo = read("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        self.assertIn('target.id + "|" + target.encodedPath', repo)
        self.assertIn("target.remoteEtag.orEmpty()", repo)
        self.assertIn('File(directory, "ref-$sourceHash.txt")', repo)
        self.assertIn('File(directory, "art-$contentHash.webp")', repo)
        self.assertIn("referenceFile(key).delete()", repo)
        self.assertIn("memoryCache.evictAll()", repo)
        self.assertIn("const val MAX_SIDE = 1320", repo)
        self.assertIn("private val imageSlots = Semaphore(2)", repo)
        self.assertIn("maxRequests = 128", repo)
        self.assertIn("maxDownloadedBytes = 8L * 1024 * 1024", repo)
        self.assertNotIn("albumKey", repo)

    def test_room_and_stable_preview_signing_unchanged(self):
        db = read("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        app = read("app/build.gradle.kts")
        self.assertIn("version = 4", db)
        self.assertIn('applicationIdSuffix = ".preview"', app)
        self.assertIn('getByName("stablePreview")', app)
        self.assertNotIn("fallbackToDestructiveMigration", db)


if __name__ == "__main__":
    unittest.main()
