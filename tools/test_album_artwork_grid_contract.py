"""Album-grid contract: visible-only artwork, bounded concurrency, and stable app identity."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/app/enotone/android"


class AlbumArtworkGridContractTest(unittest.TestCase):
    def text(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_one_shared_two_request_throttle_and_existing_cache(self):
        repo = self.text("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        for needle in (
            "private val imageSlots = Semaphore(2)",
            "imageSlots.withPermit {",
            "enotone-artwork-v2",
            "maxRequests = 128",
            "maxDownloadedBytes = 8L * 1024 * 1024",
            "MAX_DISK_FILE_BYTES",
            "context.applicationContext.filesDir",
            "WebDavRangeReader(",
            "EmbeddedArtworkPayloadReader().read(",
        ):
            self.assertIn(needle, repo)
        self.assertNotIn("deleteDatabase(", repo)

    def test_no_new_permissions_or_preview_signing_change(self):
        db = self.text(
            "core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        gradle = self.text("app/build.gradle.kts")
        self.assertIn("version = 5", db)
        self.assertIn("AutoMigration(from = 4, to = 5)", db)
        self.assertIn('applicationIdSuffix = ".preview"', gradle)
        self.assertIn('getByName("stablePreview")', gradle)


if __name__ == "__main__":
    unittest.main()
