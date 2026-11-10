"""M2 ergonomic cleanup: daily music views first, diagnostic features retained."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


class CleanLibraryUiContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_after_saved_login_open_library_and_keep_first_time_setup(self):
        main = self.src("app/src/main/java/app/enotone/android/MainActivity.kt")
        for text in (
            "vaultState = CredentialVaultState.RESTORED",
            "showConnection = false",
            "showingLibrary = true",
            "if (showingLibrary)",
            "OfflineLibraryScreen(accountEmail = email, appPassword = password,",
            "var showConnection by remember { mutableStateOf(true) }",
            "if (showConnection || email.isBlank() || password.isBlank())",
            "R.string.home_connection_settings",
        ):
            self.assertIn(text, main)
        # Saving and forgetting credentials remain explicit, never silently done.
        self.assertIn("LocalCredentialVault.save(appContext, email, password)", main)
        self.assertIn("LocalCredentialVault.clear(appContext)", main)

    def test_home_keeps_maintenance_and_diagnostics_but_collapses_pilots(self):
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

    def test_track_list_is_primary_and_player_does_not_take_full_height(self):
        ui = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        for text in (
            "var showPlayerDetails by remember { mutableStateOf(false) }",
            "if (showPlayerDetails && selectedTitle != null)",
            "AlertDialog(",
            "modifier = Modifier.heightIn(max = 480.dp)",
            ".verticalScroll(rememberScrollState())",
            "R.string.library_player_open",
            "R.string.library_player_close",
            "R.string.library_pages_short",
            "if (showDiagnostics)",
            "PlaybackSeekPolicy.positionForFraction(",
            "playback?.cycleRepeatMode()",
            "playback?.toggleShuffle()",
            "playback?.pause()",
            "playback?.resume()",
            "playback?.playNext()",
            "LibraryPage.ALBUM_ARTISTS -> OfflineLibraryGrouping.albumArtists(tracks)",
            "OfflineLibraryGrouping.albums(tracks, unknownAlbumName)",
        ):
            self.assertIn(text, ui)
        # The track/albums LazyColumn is laid out BEFORE the compact footer.
        # The huge playback controls must only render inside the dialog.
        self.assertLess(ui.index("if (showPlayerDetails && selectedTitle != null)"),
            ui.index("Column(\n        modifier = Modifier.fillMaxSize().padding(16.dp)"))
        self.assertLess(ui.index("items(visible, key = { it.id })"),
            ui.index("// Fixed-height mini player:"))
        self.assertNotIn("R.string.library_group_tags_only", ui)
        self.assertNotIn("deleteDatabase(", ui)

    def test_compact_accessible_icon_navigation(self):
        ui = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        for token in (
            "Modifier.fillMaxWidth().height(52.dp).selectableGroup()",
            "Modifier.weight(1f).height(48.dp)",
            "role = Role.Tab",
            "selected = active",
            "contentDescription = label",
            "painter = painterResource(icon)",
            "if (section != page)",
            "selectedGroupKey = null",
            'search = ""',
        ):
            self.assertIn(token, ui)
        self.assertNotIn("Row(modifier = Modifier.horizontalScroll(", ui)
        self.assertLess(ui.index("// Fixed-height mini player:"),
                        ui.index("// M2: a compact bottom rail"))
        import xml.etree.ElementTree as ET
        for name in ("tracks", "albums", "artists", "album_artists"):
            path = ROOT / f"app/src/main/res/drawable/ic_nav_{name}.xml"
            xml = ET.parse(path).getroot()
            self.assertEqual(xml.tag, "vector")
            self.assertEqual(xml.attrib.get(
                "{http://schemas.android.com/apk/res/android}height"), "24dp")

    def test_new_ux_strings_in_both_languages(self):
        needed = {
            "home_title", "home_hint", "home_connection_settings",
            "home_show_technical", "home_hide_technical",
            "home_diagnostics_title", "home_library_maintenance",
            "home_index_summary", "home_show_index_details",
            "home_hide_index_details", "library_pages_short",
            "library_player_title", "library_player_open",
            "library_player_close",
        }
        for path in ("app/src/main/res/values/strings.xml",
                     "app/src/main/res/values-en/strings.xml"):
            xml = ET.fromstring(self.src(path))
            names = {x.attrib["name"] for x in xml.findall("string")}
            self.assertTrue(needed.issubset(names), sorted(needed - names))

    def test_no_changes_to_stable_room_or_signing_path(self):
        db = self.src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        apk = self.src("app/build.gradle.kts")
        self.assertIn("version = 2", db)
        self.assertIn('applicationIdSuffix = ".preview"', apk)
        self.assertIn("getByName(\"stablePreview\")", apk)


if __name__ == "__main__":
    unittest.main()
