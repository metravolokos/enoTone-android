"""Master artwork cache: one content-addressed 1320px disk object, local view sizes."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/app/enotone/android"


class ArtworkMasterCacheContractTest(unittest.TestCase):
    def code(self, relative):
        return (ROOT / relative).read_text(encoding="utf-8")

    def test_one_master_and_source_version_references_not_three_disk_sizes(self):
        art = self.code("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        for token in (
            "enotone-artwork-v2",
            "const val MAX_SIDE = 1320",
            "const val CACHE_LIMIT_BYTES = 192L * 1024 * 1024",
            'File(directory, "ref-$sourceHash.txt")',
            'File(directory, "art-$contentHash.webp")',
            "val digest = sha256(encoded)",
            "storeMaster(key, master)",
            "private fun decodeFile(file: File, requestedSide: Int)",
            "BitmapFactory.decodeFile(file.absolutePath, options)",
            "val side = requestedSide.coerceIn(1, MAX_SIDE)",
            "private fun scaleToSide(bitmap: Bitmap, requestedSide: Int)",
            "LruCache<String, Bitmap>(24 * 1024)",
            "refreshEpoch only triggers UI recomposition",
        ):
            self.assertIn(token, art)
        self.assertNotIn("enotone-artwork-v1", art)
        self.assertNotIn('art-$contentHash-512.webp', art)
        self.assertNotIn('art-$contentHash-192.webp', art)

    def test_dedup_concurrency_and_bounded_webdav(self):
        art = self.code("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        for token in (
            "private val imageSlots = Semaphore(2)",
            "imageSlots.withPermit {",
            "private val sourceLocks = Array(32) { Mutex() }",
            "lock.lock()",
            "lock.unlock()",
            "memoryCache.get(memoryKey)",
            "WebDavRangeReader(",
            "maxRequests = 128",
            "maxDownloadedBytes = 8L * 1024 * 1024",
            "EmbeddedArtworkPayloadReader().read(",
            "requestContext.ensureActive()",
            "raw.fill(0)",
            'target.remoteEtag.orEmpty()',
        ):
            self.assertIn(token, art)
        for forbidden in ("deleteDatabase(", "SharedPreferences", "getExternalFilesDir",
                          "println(", "Log.", "RemoteTrackDao"):
            self.assertNotIn(forbidden, art)
        # Key is per source version; content hash deduplicates *verified* bytes
        # after first bounded read. Never infer that all tracks in an album
        # share one cover without reading/validating each source.
        self.assertIn('target.id + "|" + target.encodedPath', art)

    def test_local_view_sizes_and_etag_independent_manual_invalidation(self):
        screen = self.code("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        track = self.code("app/src/main/java/app/enotone/android/TrackArtworkThumbnail.kt")
        art = self.code("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        self.assertIn("requestedSide = 192", track)
        self.assertIn("requestedSide: Int = 512", art)
        self.assertIn("val refreshEpoch: Int = 0", art)
        self.assertIn("suspend fun invalidate(email: String,", art)
        self.assertIn("referenceFile(key).delete()", art)
        self.assertIn("memoryCache.evictAll()", art)
        self.assertIn("artworkRepository.invalidate(accountEmail, refreshedPictures)", screen)
        self.assertIn("result.checkedIds.mapNotNull { id ->", screen)
        self.assertIn("listOfNotNull(albumArtworkTargets[key])", screen)
        self.assertLess(screen.index("artworkRepository.invalidate(accountEmail, refreshedPictures)"),
                        screen.index("libraryRefreshEpoch++"))
        self.assertIn("refreshEpoch = libraryRefreshEpoch", screen)
        # Invalidating a reference must not remove a shared content-addressed
        # blob, as other tracks may still refer to the identical image.
        invalidate = art[art.index("suspend fun invalidate("):
                         art.index("private suspend fun loadOrFetch")]
        self.assertNotIn("masterFile(", invalidate)
        self.assertNotIn("deleteDatabase(", invalidate)

    def test_no_unapproved_changes_to_database_or_preview_package(self):
        database = self.code("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        gradle = self.code("app/build.gradle.kts")
        self.assertIn("version = 4", database)
        self.assertIn('applicationIdSuffix = ".preview"', gradle)
        self.assertIn('getByName("stablePreview")', gradle)


if __name__ == "__main__":
    unittest.main()
