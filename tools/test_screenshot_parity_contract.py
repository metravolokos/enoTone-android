"""ADR-0072 screenshot parity superseded by Graphite Material 3 ADR-0073."""
from pathlib import Path
import unittest
BASE=Path(__file__).resolve().parents[1]/"app/src/main/java/app/enotone/android"
def src(name): return (BASE/name).read_text()
class ScreenshotParityContractTest(unittest.TestCase):
    def test_no_fake_actions_or_quality_banners(self):
        player=src("NowPlayingScreen.kt")
        for token in ("Slider(", "onSeekFraction(scrubFraction)",
                      "AudioManager.STREAM_MUSIC", "onToggleShuffle",
                      "onCycleRepeat"):
            self.assertIn(token,player)
        for token in ("ic_music_lyrics","ic_music_cast","ic_music_queue",
                      "ic_music_star","Lossless"):
            self.assertNotIn(token,player)
