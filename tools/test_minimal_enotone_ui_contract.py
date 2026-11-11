"""Graphite Material3 UI, not the superseded Apple Music clone."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
def source(path):
    return (ROOT / path).read_text()
BASE = "app/src/main/java/app/enotone/android/"
class MinimalEnoToneUiContractTest(unittest.TestCase):
    def test_native_navigation_and_actions(self):
        nav = source(BASE+"CompactLibraryNavigation.kt")
        action = source(BASE+"MusicVisualControls.kt")
        theme = source(BASE+"EnoToneTheme.kt")
        self.assertIn("NavigationBar(", nav)
        self.assertIn("NavigationBarItem(", nav)
        self.assertIn("NavigationBarItemDefaults.colors(", nav)
        self.assertIn("FilledTonalButton(", action)
        self.assertNotIn("UiAccent", theme)
    def test_player_has_only_real_controls(self):
        p = source(BASE+"NowPlayingScreen.kt")
        for token in ("Brush.verticalGradient(", "Slider(", "FilledIconButton(",
                      "FilledTonalIconButton(", "AudioManager.STREAM_MUSIC",
                      "onCycleRepeat", "onToggleShuffle", "onPlayPause",
                      'playbackState == "PAUSED"', "0.84f", "coverScale"):
            self.assertIn(token,p)
        for token in ("artworkColors(", "Brush.radialGradient(", "Lossless",
                      "ic_music_star", "ic_music_lyrics", "ic_music_cast",
                      "ic_music_queue"):
            self.assertNotIn(token,p)
    def test_room_and_signing_unchanged(self):
        self.assertIn("version = 4",source("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt"))
        self.assertIn('applicationIdSuffix = ".preview"',source("app/build.gradle.kts"))
        art=source(BASE+"ArtworkPreviewRepository.kt")
        self.assertIn("const val MAX_SIDE = 1200",art)
        self.assertIn("context.applicationContext.filesDir",art)
if __name__ == "__main__":
    unittest.main()
