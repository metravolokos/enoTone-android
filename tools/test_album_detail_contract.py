"""M2 full album page: local header, artwork, and existing Media3 queue."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/app/enotone/android"


class AlbumDetailContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_album_header_reuses_cached_artwork_and_never_invents_year(self):
        header = self.src("app/src/main/java/app/enotone/android/AlbumDetailHeader.kt")
        for required in (
            "LaunchedEffect(group.key, artworkGroup, accountEmail, appPassword)",
            "repository.albumCover(accountEmail, appPassword,",
            "group.subtitle?.takeIf",
            "R.drawable.ic_nav_albums",
            "R.string.album_cover_description",
            "R.string.ui_play",
            "Button(",
            "enabled = canPlay",
            "onClick = onPlayAll",
            "modifier = Modifier.size(232.dp)",
        ):
            self.assertIn(required, header)
        self.assertNotIn("WebDavRangeReader(", header)
        self.assertNotIn("openLibraryDatabase(", header)
        self.assertNotIn("year = ", header)
        # Release year and disc/track numbers are nullable, never fabricated.
        entity = self.src("core/database/src/main/java/app/enotone/database/RemoteTrackEntity.kt")
        self.assertIn("val year: Int? = null", entity)
        self.assertIn("val trackNumber: Int? = null", entity)
        self.assertIn("val discNumber: Int? = null", entity)

    def test_localization_and_stable_data(self):
        for path in ("app/src/main/res/values/strings.xml",
                     "app/src/main/res/values-en/strings.xml"):
            root = ET.fromstring(self.src(path))
            keys = {item.attrib["name"] for item in root.findall("string")}
            self.assertIn("album_cover_description", keys)
        database = self.src(
            "core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        self.assertIn("AutoMigration(from = 4, to = 5)", database)


if __name__ == "__main__":
    unittest.main()
