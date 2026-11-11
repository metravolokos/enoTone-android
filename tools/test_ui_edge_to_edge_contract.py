"""Huawei #314 visual regression contracts (ADR-0070)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = "app/src/main/java/app/enotone/android/"


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class UiEdgeToEdgeContractTest(unittest.TestCase):
    def test_window_draws_through_bars_with_correct_icon_theme(self):
        activity = read(BASE + "MainActivity.kt")
        theme = read(BASE + "EnoToneTheme.kt")
        self.assertIn("enableEdgeToEdge()", activity)
        self.assertIn("window.isNavigationBarContrastEnforced = false", activity)
        self.assertIn("window.statusBarColor = android.graphics.Color.TRANSPARENT", theme)
        self.assertIn("window.navigationBarColor = android.graphics.Color.TRANSPARENT", theme)
        self.assertIn("controller.isAppearanceLightStatusBars = !dark", theme)
        self.assertIn("controller.isAppearanceLightNavigationBars = !dark", theme)

    def test_safe_insets_and_full_bleed_player_background(self):
        activity = read(BASE + "MainActivity.kt")
        home = read(BASE + "EnoToneHomeScreen.kt")
        library = read(BASE + "OfflineLibraryScreen.kt")
        player = read(BASE + "NowPlayingScreen.kt")
        self.assertIn("Modifier.fillMaxSize().systemBarsPadding()", home)
        self.assertIn(".systemBarsPadding()", library)
        self.assertIn("Modifier.fillMaxSize().systemBarsPadding()", activity)
        self.assertIn(".windowInsetsPadding(WindowInsets.systemBars)", player)
        self.assertLess(player.index(".drawBehind {"),
                        player.index(".windowInsetsPadding(WindowInsets.systemBars)"))

    def test_album_palette_and_stable_control_layout(self):
        player = read(BASE + "NowPlayingScreen.kt")
        self.assertIn("primary.copy(alpha = .78f)", player)
        self.assertIn("secondary.copy(alpha = .61f)", player)
        self.assertNotIn("Color.Black, 0.59f", player)
        self.assertNotIn("Color.Black, 0.88f", player)
        self.assertIn("artworkColors(artwork)", player)
        self.assertIn("stateDescription = status", player)
        self.assertNotIn("if (stateText.isNotBlank())", player)
        self.assertIn("elevation = 28.dp", player)
        self.assertIn("Brush.radialGradient(", player)
        self.assertNotIn("WebDavRangeReader(", player)

    def test_media_and_persistent_artwork_unchanged(self):
        artwork = read(BASE + "ArtworkPreviewRepository.kt")
        db = read("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        self.assertIn("const val MAX_SIDE = 1200", artwork)
        self.assertIn("enotone-artwork-v2", artwork)
        self.assertIn("version = 4", db)


if __name__ == "__main__":
    unittest.main()
