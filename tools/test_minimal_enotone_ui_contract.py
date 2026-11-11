"""Minimalist first visual pass without altering music/cache functionality."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def src(path):
    return (ROOT / path).read_text(encoding="utf-8")


class MinimalEnoToneUiContractTest(unittest.TestCase):
    def test_neutral_backgrounds_and_persisted_accent(self):
        theme = src("app/src/main/java/app/enotone/android/EnoToneTheme.kt")
        settings = src("app/src/main/java/app/enotone/android/AppearanceSettings.kt")
        main = src("app/src/main/java/app/enotone/android/MainActivity.kt")
        for token in ("darkColorScheme(", "lightColorScheme(", "Color.Black",
                      "Color.White", "UiAccent", "UiMode", "getSharedPreferences(",
                      "isSystemInDarkTheme()", "primaryContainer = accent",
                      "background = Color.Black", "background = Color.White"):
            self.assertIn(token, theme)
        self.assertNotIn("dynamicDarkColorScheme", theme)
        self.assertNotIn("dynamicLightColorScheme", theme)
        self.assertIn("UiAppearance.chooseAccent(", settings)
        self.assertIn("UiAppearance.chooseMode(", settings)
        self.assertIn("AppearanceSettings()", main)

    def test_gradient_and_paused_album_shadow(self):
        player = src("app/src/main/java/app/enotone/android/NowPlayingScreen.kt")
        backdrop = src("app/src/main/java/app/enotone/android/ArtworkBackdrop.kt")
        for token in ("artworkColors(artwork)", "Brush.verticalGradient(",
                      "Color.Black", "Color.White", "animateColorAsState(",
                      "animateFloatAsState(", 'playbackState == "PAUSED"',
                      "elevation = 32.dp", "coverScale"):
            self.assertIn(token, player)
        self.assertIn("Bitmap.createScaledBitmap(", backdrop)
        self.assertNotIn("WebDav", backdrop)

    def test_album_header_keeps_play_and_hides_technical_action_in_overflow(self):
        header = src("app/src/main/java/app/enotone/android/AlbumDetailHeader.kt")
        for token in (
            "ButtonDefaults.buttonColors(",
            "MaterialTheme.colorScheme.primaryContainer",
            "MaterialTheme.colorScheme.onPrimaryContainer",
            "R.string.album_play_all",
            "R.drawable.ic_action_more",
            "DropdownMenu(", "DropdownMenuItem(", "onRefreshTags()",
            "R.string.album_refresh_tags",
            "year?.toString()", "genre?.takeIf",
            "group.trackIds.size", "repository.albumCover(",
        ):
            self.assertIn(token, header)
        self.assertNotIn("OutlinedButton(", header)
        self.assertLess(header.index("R.string.album_play_all"),
                        header.index("R.drawable.ic_action_more"))
        for f in ("app/src/main/res/values/strings.xml",
                  "app/src/main/res/values-en/strings.xml"):
            tree = ET.fromstring(src(f))
            self.assertIn("album_actions",
                {el.attrib.get("name") for el in tree.findall("string")})
        icon = ET.fromstring(src("app/src/main/res/drawable/ic_action_more.xml"))
        self.assertEqual(icon.tag, "vector")
        self.assertEqual(icon.attrib[
            "{http://schemas.android.com/apk/res/android}height"], "24dp")

    def test_mini_player_has_only_two_visible_transport_buttons(self):
        ui = src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        mini = ui[ui.index("// Floating mini player:"):
                  ui.index("// Five-slot navigation")]
        self.assertIn('Modifier.fillMaxWidth().height(56.dp)', mini)
        self.assertIn("R.drawable.ic_action_play", mini)
        self.assertIn("R.drawable.ic_action_pause", mini)
        self.assertIn("R.drawable.ic_action_next", mini)
        self.assertNotIn("R.drawable.ic_action_expand", mini)
        self.assertNotIn("LinearProgressIndicator(", mini)
        self.assertIn("FloatingControlsIsland(", ui)
        self.assertIn("Modifier.size(48.dp).clickable(", mini)
        self.assertIn("onClickLabel = stringResource(R.string.library_player_open)", mini)
        self.assertIn("showPlayerDetails = true", mini)
        # Same live playback session; Now Playing still uses its 1200px cover.
        self.assertIn("if (currentArtwork != null) {", mini)
        self.assertIn("NowPlayingScreen(", ui)
        self.assertIn("requestedSide = 1200", ui)

    def test_accepted_persistent_cover_and_room_signing_unchanged(self):
        artwork = src("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        db = src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        gradle = src("app/build.gradle.kts")
        self.assertIn("const val MAX_SIDE = 1200", artwork)
        self.assertIn("context.applicationContext.filesDir", artwork)
        self.assertNotIn("const val CACHE_LIMIT_BYTES", artwork)
        self.assertIn("version = 4", db)
        self.assertIn('applicationIdSuffix = ".preview"', gradle)


if __name__ == "__main__":
    unittest.main()
