"""Permanent album art: one verified master, all pages, opt-in background."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class ArtworkCrossPageCacheContractTest(unittest.TestCase):
    def test_permanent_store_and_no_album_network_on_cache_hit(self):
        cache = read("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        for token in (
            "context.applicationContext.filesDir", "legacyDirectory",
            "migrateLegacy(key)", "const val MAX_SIDE = 1200",
            "fun peekAlbum(", "suspend fun cachedAlbumCover(", "suspend fun albumCover(",
            "suspend fun isAlbumIndexed(", "selectedSource(email, group)",
            "saveAlbumSelection(email, group, choice)",
            "saveAlbumSelection(email, group, NO_ART_DIGEST)",
            "private val NO_ART_DIGEST", "suspend fun clearAll()",
            "private val albumLocks = Array(32) { Mutex() }",
            "LruCache<String, Bitmap>(24 * 1024)",
        ):
            self.assertIn(token, cache)
        self.assertNotIn("CACHE_LIMIT_BYTES", cache)
        self.assertNotIn("trimCache()", cache)
        local = cache[cache.index("suspend fun cachedAlbumCover("):
                      cache.index("suspend fun isAlbumIndexed(")]
        self.assertNotIn("WebDavRangeReader(", local)
        self.assertNotIn("deleteDatabase(", cache)
        self.assertIn('File(directory, "art-$contentHash.webp")', cache)
        self.assertIn('File(directory, "ref-$sourceHash.txt")', cache)

    def test_auto_mode_resumes_after_tag_index_and_not_on_boot(self):
        planner = read("app/src/main/java/app/enotone/android/ArtworkIndexPlanner.kt")
        worker = read("app/src/main/java/app/enotone/android/ArtworkIndexingForegroundService.kt")
        preferences = read("app/src/main/java/app/enotone/android/ArtworkPreferences.kt")
        self.assertIn("ArtworkLoadMode.ON_DEMAND", preferences)
        self.assertIn("AUTO_AFTER_IMPORT", preferences)
        self.assertIn("ArtworkIndexPlanner.fromStoredLibrary(", worker)
        self.assertIn("repository.isAlbumIndexed(login.email, group)", worker)
        self.assertIn("repository.albumCover(login.email, login.password,", worker)
        self.assertIn("START_NOT_STICKY", worker)
        self.assertIn("MAX_SESSION_NANOS", worker)
        self.assertIn("ManagedScanState.RUNNING", worker)
        self.assertIn("ACTION_PAUSE", worker)
        self.assertIn("ACTION_STOP", worker)
        self.assertNotIn("WorkManager(", worker)
        self.assertNotIn("putExtra(\"password\"", worker)
        self.assertNotIn("WebDavRangeReader(", planner)
        self.assertNotIn("deleteDatabase(", planner)

    def test_settings_and_signing_are_stable(self):
        settings = read("app/src/main/java/app/enotone/android/ArtworkSettingsPanel.kt")
        db = read("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        gradle = read("app/build.gradle.kts")
        for token in (
            "artwork_mode_auto", "artwork_mode_visible",
            "R.string.artwork_cache_clear", "R.string.artwork_cache_confirm_title",
            "R.string.artwork_index_progress",
        ):
            self.assertIn(token, settings)
        self.assertIn("version = 4", db)
        self.assertIn('applicationIdSuffix = ".preview"', gradle)


if __name__ == "__main__":
    unittest.main()
