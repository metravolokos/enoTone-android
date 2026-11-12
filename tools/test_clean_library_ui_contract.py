"""M2 ergonomic cleanup: daily music views first, diagnostic features retained."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class CleanLibraryUiContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")


    def test_new_ux_strings_in_both_languages(self):
        needed = {
            "home_title", "home_hint",
            "home_index_summary", "library_pages_short",
            "library_player_title", "library_player_open",
            "library_player_close", "library_open_search",
            "library_close_search", "library_search_short",
            "nav_home", "settings_title", "home_music_title",
            "home_open_library", "home_connect_account",
        }
        for path in ("app/src/main/res/values/strings.xml",
                     "app/src/main/res/values-en/strings.xml"):
            xml = ET.fromstring(self.src(path))
            names = {x.attrib["name"] for x in xml.findall("string")}
            self.assertTrue(needed.issubset(names), sorted(needed - names))

    def test_no_changes_to_stable_room_or_signing_path(self):
        db = self.src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        apk = self.src("app/build.gradle.kts")
        self.assertIn("version = 4", db)
        self.assertIn('applicationIdSuffix = ".preview"', apk)
        self.assertIn("getByName(\"stablePreview\")", apk)


if __name__ == "__main__":
    unittest.main()
