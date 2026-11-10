"""First actual artwork feature: one playing-track request, no mass indexing."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/app/enotone/android"
LIB = ROOT / "data/library/src/main/java/app/enotone/library"


class OnDemandArtworkContractTest(unittest.TestCase):
    def test_only_current_media_item_can_start_artwork_request(self):
        ui = (APP / "OfflineLibraryScreen.kt").read_text()
        for token in (
            "currentTrackId = mediaItem?.mediaId",
            "currentTrackId = active.player.currentMediaItem?.mediaId",
            "LaunchedEffect(currentTrackId, preparedById, appPassword, accountEmail)",
            "val target = preparedById[id] ?: return@LaunchedEffect",
            "PlayingArtworkTarget(",
            "currentArtwork = artworkRepository.cover(",
            "Image(", "R.string.artwork_description",
            "bitmap = currentArtwork!!.asImageBitmap()",
        ):
            self.assertIn(token, ui)
        # No trigger on scroll, opening an album, artist or tab switch.
        self.assertNotIn("LaunchedEffect(visible", ui)
        self.assertNotIn("LaunchedEffect(shownGroups", ui)
        self.assertNotIn("deleteDatabase(", ui)

    def test_range_and_cache_limits_are_private(self):
        code = (APP / "ArtworkPreviewRepository.kt").read_text()
        payload = (LIB / "EmbeddedArtworkPayloadReader.kt").read_text()
        for token in (
            "context.applicationContext.cacheDir",
            "enotone-artwork-v1",
            "MessageDigest.getInstance(\"SHA-256\")",
            "WebDavRangeReader(",
            "EmbeddedArtworkPayloadReader().read(",
            "maxRequests = 128",
            "maxDownloadedBytes = 8L * 1024 * 1024",
            "const val MAX_SIDE = 512",
            "const val CACHE_LIMIT_BYTES = 32L * 1024 * 1024",
            "BitmapFactory.Options().apply { inJustDecodeBounds = true }",
            "coroutineContext",
        ):
            self.assertIn(token, code)
        for token in (
            "EmbeddedArtworkLocator().locate",
            "image.length !in 1..MAX_PAYLOAD_BYTES",
            "const val MAX_PAYLOAD_BYTES = 4 * 1024 * 1024",
            "output.fill(0)",
        ):
            self.assertIn(token, payload)
        for forbidden in ("Log.", "println(", "SharedPreferences",
                          "RemoteTrackDao", "Room.", "MediaSession",
                          "getExternalFilesDir"):
            self.assertNotIn(forbidden, code)

    def test_strings_and_stable_room(self):
        for path in ("app/src/main/res/values/strings.xml",
                     "app/src/main/res/values-en/strings.xml"):
            root = ET.parse(ROOT / path).getroot()
            self.assertIn("artwork_description", {
                item.attrib["name"] for item in root.findall("string")
            })
        db = (ROOT / "core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt").read_text()
        self.assertIn("version = 2", db)


if __name__ == "__main__":
    unittest.main()
