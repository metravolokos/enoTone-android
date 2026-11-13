"""Graphite Material3 UI, not the superseded Apple Music clone."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
def source(path):
    return (ROOT / path).read_text()
BASE = "app/src/main/java/app/enotone/android/"
class MinimalEnoToneUiContractTest(unittest.TestCase):
    def test_player_has_only_real_controls(self):
        p = source(BASE+"NowPlayingScreen.kt") + source("feature/player/src/main/java/app/enotone/android/FeatureNowPlayingScreen.kt")
        for token in ("Brush.verticalGradient(", "Slider(", "FilledIconButton(",
                      "FilledTonalIconButton(",
                      "onCycleRepeat", "onToggleShuffle", "onPlayPause",
                      'playbackState == "PAUSED"', "0.84f", "coverScale"):
            self.assertIn(token,p)
        for token in ("artworkColors(", "Brush.radialGradient(", "Lossless",
                      "ic_music_star", "ic_music_lyrics", "ic_music_cast",
                      "ic_music_queue",
                      # Владелец 2026-11-12 (REQUIREMENTS O-12): регулятора громкости нет.
                      "AudioManager", "ic_volume_"):
            self.assertNotIn(token,p)
    def test_room_v5_migration_and_signing(self):
        database = source("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        self.assertIn("version = 5", database)
        self.assertIn("AutoMigration(from = 4, to = 5)", database)
        self.assertIn('applicationIdSuffix = ".preview"',source("app/build.gradle.kts"))
        art=source(BASE+"ArtworkPreviewRepository.kt")
        self.assertIn("const val MAX_SIDE = 1200",art)
        self.assertIn("context.applicationContext.filesDir",art)
if __name__ == "__main__":
    unittest.main()
