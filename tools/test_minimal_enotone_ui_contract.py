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
    def test_album_real_actions_and_shared_cover(self):
        header = source(BASE+"AlbumDetailHeader.kt")
        library = source(BASE+"OfflineLibraryScreen.kt")
        for token in ("repository.albumCover(", "onPlayAll", "onShuffleAll",
                      "onRefreshTags()", "DropdownMenuItem(", "R.string.ui_play"):
            self.assertIn(token,header)
        self.assertNotIn("ic_music_add",header)
        self.assertNotIn("albumBackdropArt",library)
        self.assertNotIn("Brush.verticalGradient(",library)
    def test_mini_native_surface_and_preserved_1200_master(self):
        library=source(BASE+"OfflineLibraryScreen.kt")
        mini=library[library.index("// Native Material 3 mini-player:"):
                     library.index("// Material 3 NavigationBar;")]
        for token in ("Surface(", "shadowElevation = 4.dp",
                      "R.drawable.ic_action_play", "R.drawable.ic_action_pause",
                      "R.drawable.ic_action_next", "showPlayerDetails = true"):
            self.assertIn(token,mini)
        self.assertNotIn("LinearProgressIndicator(",mini)
        self.assertNotIn("FloatingControlsIsland(",library)
        self.assertIn("requestedSide = 1200",library)
    def test_room_and_signing_unchanged(self):
        self.assertIn("version = 4",source("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt"))
        self.assertIn('applicationIdSuffix = ".preview"',source("app/build.gradle.kts"))
        art=source(BASE+"ArtworkPreviewRepository.kt")
        self.assertIn("const val MAX_SIDE = 1200",art)
        self.assertIn("context.applicationContext.filesDir",art)
if __name__ == "__main__":
    unittest.main()
