"""M2 ergonomic cleanup: daily music views first, diagnostic features retained."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class CleanLibraryUiContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_saved_login_opens_home_and_first_time_setup_remains(self):
        main = self.src("app/src/main/java/app/enotone/android/MainActivity.kt")
        for text in (
            "vaultState = CredentialVaultState.RESTORED",
            "showConnection = false",
            "var showingSettings by remember { mutableStateOf(false) }",
            "if (showingLibrary)",
            "OfflineLibraryScreen(",
            "initialPage = initialLibraryPage",
            "if (!showingSettings)",
            "EnoToneHomeScreen(",
            "var showConnection by remember { mutableStateOf(true) }",
            "if (showConnection || email.isBlank() || password.isBlank())",
            "R.string.home_connection_settings",
        ):
            self.assertIn(text, main)
        self.assertNotIn("showingLibrary = true", main[
            main.index("LaunchedEffect(appContext)"):main.index("val importReport")])
        self.assertIn("LibraryVisibilitySettings()", main)
        # Saving and forgetting credentials remain explicit, never silently done.
        self.assertIn("LocalCredentialVault.save(appContext, email, password)", main)
        self.assertIn("LocalCredentialVault.clear(appContext)", main)

    def test_settings_keeps_maintenance_and_diagnostics(self):
        main = self.src("app/src/main/java/app/enotone/android/MainActivity.kt")
        for text in (
            "var showTechnicalTools by remember { mutableStateOf(false) }",
            "if (showTechnicalTools) {",
            "R.string.home_show_technical",
            "R.string.home_diagnostics_title",
            "R.string.home_library_maintenance",
            "R.string.save_local_library",
            "R.string.managed_index_all",
            "onClick = { startManagedSession(true) }",
            "onClick = { startManagedSession(false) }",
            "R.string.home_index_summary",
            "R.string.home_show_index_details",
            "R.string.read_stored_tags_offline",
            "R.string.pilot_resume_bounded",
            "R.string.tag_audit_queue_all",
        ):
            self.assertIn(text, main)
        # Full index and sync remain reachable outside the diagnostic panel.
        self.assertLess(main.index("R.string.home_library_maintenance"),
            main.index("R.string.save_local_library"))
        self.assertLess(main.index("R.string.save_local_library"),
            main.index("R.string.managed_index_all"))
        self.assertNotIn("deleteDatabase(", main)

    def test_new_ux_strings_in_both_languages(self):
        needed = {
            "home_title", "home_hint", "home_connection_settings",
            "home_show_technical", "home_hide_technical",
            "home_diagnostics_title", "home_library_maintenance",
            "home_index_summary", "home_show_index_details",
            "home_hide_index_details", "library_pages_short",
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
