"""ADR-0072 screenshot parity superseded by Graphite Material 3 ADR-0073."""
from pathlib import Path
import unittest
BASE=Path(__file__).resolve().parents[1]/"app/src/main/java/app/enotone/android"
def src(name): return (BASE/name).read_text()
class ScreenshotParityContractTest(unittest.TestCase):
    def test_real_local_search_and_grid(self):
        lib=src("OfflineLibraryScreen.kt")
        actions=src("MusicVisualControls.kt")
        for token in ("BasicTextField(", "GridCells.Fixed(2)", "MusicListAction(",
                      "R.string.ui_search_songs", "R.string.ui_search_albums",
                      "R.string.ui_search_artists"):
            self.assertIn(token,lib)
        self.assertIn("FilledTonalButton(",actions)
        self.assertIn("maxLines = 1",actions)
    def test_no_artwork_gradient_or_network_added_to_album(self):
        lib=src("OfflineLibraryScreen.kt")
        hdr=src("AlbumDetailHeader.kt")
        for token in ("repository.peekAlbum(", "repository.cachedAlbumCover(",
                      "repository.albumCover(", "onShuffleAll", "onRefreshTags()"):
            self.assertIn(token,hdr)
        for token in ("albumBackdropArt", "onArtworkLoaded", "ic_music_add"):
            self.assertNotIn(token,lib+hdr)
    def test_no_fake_actions_or_quality_banners(self):
        player=src("NowPlayingScreen.kt")
        for token in ("Slider(", "onSeekFraction(scrubFraction)",
                      "AudioManager.STREAM_MUSIC", "onToggleShuffle",
                      "onCycleRepeat"):
            self.assertIn(token,player)
        for token in ("ic_music_lyrics","ic_music_cast","ic_music_queue",
                      "ic_music_star","Lossless"):
            self.assertNotIn(token,player)
    def test_no_legacy_island_or_custom_accent(self):
        self.assertNotIn("FloatingControlsIsland(",src("OfflineLibraryScreen.kt")+src("EnoToneHomeScreen.kt"))
        self.assertNotIn("UiAccent",src("EnoToneTheme.kt"))
if __name__ == "__main__":
    unittest.main()
