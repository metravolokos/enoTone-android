"""Album-grid contract: visible-only artwork, bounded concurrency, no DB changes."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/app/enotone/android"


class AlbumArtworkGridContractTest(unittest.TestCase):
    def text(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_lazy_grid_only_in_album_root_and_click_opens_group(self):
        ui = self.text("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        tile = self.text("app/src/main/java/app/enotone/android/AlbumArtworkTile.kt")
        for needle in (
            "section == LibraryPage.ALBUMS && selectedGroup == null",
            "LazyVerticalGrid(",
            "GridCells.Adaptive(minSize = 148.dp)",
            "gridItems(shownGroups, key = { it.key })",
            "AlbumArtworkTile(",
            "onOpen = {",
            "selectedGroupKey = group.key",
            'search = ""',
            "val albumArtworkTargets = remember(groups, preparedById, section,",
            'if (group.key == "album:missing") return@mapNotNull null',
            "preparedById[it]",
        ):
            self.assertIn(needle, ui)
        self.assertIn("LaunchedEffect(group.key, target, accountEmail, appPassword)", tile)
        self.assertIn("repository.cover(accountEmail, appPassword, target)", tile)
        self.assertIn("modifier = Modifier.fillMaxWidth().aspectRatio(1f)", tile)
        self.assertIn("onClick = onOpen", tile)
        self.assertNotIn("WebDavRangeReader(", tile)
        self.assertNotIn("openLibraryDatabase(", tile)
        # Album grid occurs before artist list and track list, both preserved.
        self.assertLess(ui.index("section == LibraryPage.ALBUMS && selectedGroup == null"),
                        ui.index("section != LibraryPage.TRACKS && selectedGroup == null -> {"))
        self.assertIn("items(visible, key = { it.id })", ui)

    def test_one_shared_two_request_throttle_and_existing_cache(self):
        repo = self.text("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        for needle in (
            "private val imageSlots = Semaphore(2)",
            "imageSlots.withPermit {",
            "enotone-artwork-v2",
            "maxRequests = 128",
            "maxDownloadedBytes = 8L * 1024 * 1024",
            "MAX_DISK_FILE_BYTES",
            "CACHE_LIMIT_BYTES = 64L * 1024 * 1024",
            "WebDavRangeReader(",
            "EmbeddedArtworkPayloadReader().read(",
        ):
            self.assertIn(needle, repo)
        self.assertNotIn("deleteDatabase(", repo)

    def test_no_new_permissions_database_or_signing(self):
        db = self.text(
            "core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        gradle = self.text("app/build.gradle.kts")
        self.assertIn("version = 4", db)
        self.assertIn('applicationIdSuffix = ".preview"', gradle)
        self.assertIn('getByName("stablePreview")', gradle)


if __name__ == "__main__":
    unittest.main()
