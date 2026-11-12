"""M2 persistent page preferences: no Room changes or hidden-page traps."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class LibraryPageSettingsContractTest(unittest.TestCase):
    def read(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_settings_reuse_stable_ids_and_store_private_preferences(self):
        settings = self.read(
            "app/src/main/java/app/enotone/android/LibraryPagePreferences.kt")
        model = self.read(
            "data/library/src/main/java/app/enotone/library/LibraryPageVisibility.kt")
        for needle in (
            'getSharedPreferences(', 'Context.MODE_PRIVATE',
            '"hidden_pages_v1"', 'prefs.getStringSet(', 'prefs.edit().putStringSet(',
            'LibraryPageVisibility.parseHidden(', 'LibraryPageVisibility.persistedHidden(',
        ):
            self.assertIn(needle, settings)
        for needle in (
            'enum class LibraryPage { TRACKS, ALBUMS, ARTISTS, ALBUM_ARTISTS }',
            'fun withVisibility(', 'fun parseHidden(', 'fun persistedHidden(',
            'fun selectedOrFallback(',
        ):
            self.assertIn(needle, model)
        for forbidden in ("WebDav", "RemoteTrackDao", "Room.databaseBuilder",
                          "password", "accountEmail", "onPlay"):
            self.assertNotIn(forbidden, settings)

    def test_labels_are_translated_and_room_schema_untouched(self):
        names = (
            "library_pages_settings_button",
            "library_pages_settings_title",
            "library_pages_settings_explanation",
            "library_pages_settings_last_visible",
            "library_pages_settings_done",
        )
        for path in ("app/src/main/res/values/strings.xml",
                     "app/src/main/res/values-en/strings.xml"):
            root = ET.fromstring(self.read(path))
            present = {x.attrib["name"] for x in root.findall("string")}
            for name in names:
                self.assertIn(name, present)
        schema = self.read(
            "core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        self.assertIn("version = 4", schema)


if __name__ == "__main__":
    unittest.main()
