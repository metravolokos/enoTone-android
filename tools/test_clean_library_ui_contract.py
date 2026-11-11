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

    def test_track_list_is_primary_and_full_player_has_own_screen(self):
        ui = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        player = self.src("app/src/main/java/app/enotone/android/NowPlayingScreen.kt")
        for token in (
            "var showPlayerDetails by remember { mutableStateOf(false) }",
            "if (showPlayerDetails && selectedTitle != null)",
            "NowPlayingScreen(",
            "BackHandler(enabled = showPlayerDetails)",
            "R.string.library_player_open",
            "onClose = { showPlayerDetails = false }",
            "playback?.seekToFraction(fraction)",
            "playback?.cycleRepeatMode()",
            "playback?.toggleShuffle()",
            "playback?.pause()",
            "playback?.resume()",
            "playback?.playNext()",
            "LibraryPage.ALBUM_ARTISTS -> OfflineLibraryGrouping.albumArtists(tracks)",
            "OfflineLibraryGrouping.albums(tracks, unknownAlbumName)",
        ):
            self.assertIn(token, ui)
        self.assertIn("PlaybackSeekPolicy.positionForFraction(", player)
        self.assertIn(".verticalScroll(rememberScrollState())", player)
        self.assertIn("R.string.library_player_close", player)
        self.assertNotIn("AlertDialog(", ui[
            ui.index("if (showPlayerDetails && selectedTitle != null)"):
            ui.index("Column(\n        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)")
        ])
        self.assertLess(ui.index("if (showPlayerDetails && selectedTitle != null)"),
            ui.index("Column(\n        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)"))
        self.assertLess(ui.index("items(visible, key = { it.id })"),
            ui.index("// Floating mini player:"))
        self.assertNotIn("R.string.library_group_tags_only", ui)
        self.assertNotIn("deleteDatabase(", ui)

    def test_five_position_navigation_and_visibility_settings(self):
        nav = self.src("app/src/main/java/app/enotone/android/CompactLibraryNavigation.kt")
        ui = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        home = self.src("app/src/main/java/app/enotone/android/EnoToneHomeScreen.kt")
        settings = self.src("app/src/main/java/app/enotone/android/LibraryVisibilitySettings.kt")
        for token in (
            "Modifier.fillMaxWidth().height(52.dp).selectableGroup()",
            "Modifier.weight(1f).height(48.dp)",
            "role = Role.Tab",
            "selected = active",
            "contentDescription = label",
            "painter = painterResource(icon)",
            "listOf<LibraryPage?>(null) + visiblePages",
            "null -> R.drawable.ic_nav_home",
            "null -> R.string.nav_home",
        ):
            self.assertIn(token, nav)
        self.assertIn("CompactLibraryNavigation(", home)
        self.assertIn("CompactLibraryNavigation(", ui)
        self.assertIn("onHome = onBack", ui)
        self.assertIn("if (page != section)", ui)
        self.assertIn("selectedGroupKey = null", ui)
        self.assertIn('search = ""', ui)
        self.assertNotIn("showPageSettings", ui)
        self.assertNotIn("R.drawable.ic_action_pages", ui)
        self.assertIn("LibraryPageVisibility.withVisibility(", settings)
        self.assertIn("preferences.saveHiddenPages(next)", settings)
        self.assertIn("visible.size > 1", settings)
        self.assertNotIn("Row(modifier = Modifier.horizontalScroll(", ui)
        self.assertLess(ui.index("// Floating mini player:"),
                        ui.index("// Five-slot navigation"))
        for name in ("home", "tracks", "albums", "artists", "album_artists"):
            path = ROOT / f"app/src/main/res/drawable/ic_nav_{name}.xml"
            xml = ET.parse(path).getroot()
            self.assertEqual(xml.tag, "vector")
            self.assertEqual(xml.attrib.get(
                "{http://schemas.android.com/apk/res/android}height"), "24dp")

    def test_compact_app_bar_search_and_mini_player(self):
        ui = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        self.assertIn("modifier = Modifier.fillMaxWidth().height(48.dp)", ui)
        self.assertIn("R.drawable.ic_action_back", ui)
        self.assertNotIn("R.drawable.ic_action_pages", ui)
        self.assertIn("if (searchExpanded) {", ui)
        self.assertIn("if (search.isNotBlank()) {", ui)
        self.assertIn("R.drawable.ic_action_search", ui)
        self.assertIn("R.string.library_close_search", ui)
        self.assertIn("R.string.library_open_search", ui)
        self.assertIn("selectedGroupKey = null", ui)
        self.assertIn("selectedArtist = mediaMetadata.artist?.toString()", ui)
        self.assertIn("selectedArtist = track.artist", ui)
        mini = ui[ui.index("// Floating mini player:"):
                  ui.index("// Five-slot navigation")]
        self.assertIn("Modifier.fillMaxWidth().height(56.dp)", mini)
        self.assertNotIn("LinearProgressIndicator(", mini)
        self.assertIn("FloatingControlsIsland(", ui)
        self.assertIn("PaddingValues(bottom =", ui)
        self.assertIn("R.drawable.ic_action_play", mini)
        self.assertIn("R.drawable.ic_action_pause", mini)
        self.assertIn("R.drawable.ic_action_next", mini)
        self.assertNotIn("R.drawable.ic_action_expand", mini)
        self.assertIn("onClickLabel = stringResource(R.string.library_player_open)", mini)
        self.assertIn("contentDescription = stringResource", mini)
        self.assertNotIn("TextButton(", mini)
        # The app bar and footer use platform text descriptions with 48dp targets.
        for name in ("back", "search", "close", "pages", "play", "pause",
                     "next", "expand"):
            path = ROOT / f"app/src/main/res/drawable/ic_action_{name}.xml"
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
