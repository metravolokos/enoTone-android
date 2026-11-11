"""UI contracts for screenshots supplied from the same Huawei (ADR-0072)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/app/enotone/android"


def source(name):
    return (UI / name).read_text(encoding="utf-8")


class ScreenshotParityContractTest(unittest.TestCase):
    def test_real_search_and_single_line_actions(self):
        library = source("OfflineLibraryScreen.kt")
        controls = source("MusicVisualControls.kt")
        for token in (
            "BasicTextField(", "R.string.ui_songs_title",
            "R.string.ui_search_songs", "R.string.ui_search_albums",
            "R.string.ui_search_artists", "MusicListAction(",
            "GridCells.Fixed(2)", "contentPadding = PaddingValues(",
        ):
            self.assertIn(token, library)
        self.assertIn("Modifier.weight(1f)", library)
        self.assertIn("maxLines = 1", controls)
        self.assertIn("overflow = TextOverflow.Ellipsis", controls)
        self.assertIn("height(48.dp)", controls)
        self.assertNotIn("R.string.album_play_all", library[
            library.index("if ((section == LibraryPage.TRACKS"):
            library.index("when {", library.index("if ((section == LibraryPage.TRACKS"))
        ])

    def test_album_hero_palette_does_not_download_art_twice(self):
        library = source("OfflineLibraryScreen.kt")
        header = source("AlbumDetailHeader.kt")
        for token in (
            "albumPalette = remember(albumBackdropArt)",
            "Brush.verticalGradient(", "onArtworkLoaded = { albumBackdropArt = it }",
            ".systemBarsPadding()", ".navigationBarsPadding()",
        ):
            self.assertIn(token, library)
        self.assertIn("repository.peekAlbum(", library)
        self.assertIn("onArtworkLoaded(cover)", header)
        self.assertIn("modifier = Modifier.size(242.dp)", header)
        self.assertIn("R.string.ui_play", header)
        self.assertIn("R.drawable.ic_music_add", header)
        self.assertIn("onShuffleAll", header)
        self.assertNotIn("WebDavRangeReader(", header)

    def test_player_flat_slider_and_necessary_controls(self):
        player = source("NowPlayingScreen.kt")
        visuals = source("MusicVisualControls.kt")
        for token in (
            "artworkColors(artwork)", "Brush.radialGradient(",
            'playbackState == "PAUSED"', "0.84f", "coverScale",
            "MusicThinSlider(", "onSeekFraction(scrubFraction)",
            "R.drawable.ic_music_star", "R.drawable.ic_music_lyrics",
            "R.drawable.ic_music_cast", "R.drawable.ic_music_queue",
            "R.string.now_playing_queue_short", "AudioManager.STREAM_MUSIC",
        ):
            self.assertIn(token, player)
        self.assertIn("Canvas(", visuals)
        self.assertIn(".height(4.dp)", visuals)
        self.assertIn("setProgress", visuals)
        self.assertNotIn("WebDav", player)
        self.assertNotIn("MediaSession(", player)

    def test_theme_icons_and_blur_not_faked(self):
        theme = source("EnoToneTheme.kt")
        island = source("FloatingControlsIsland.kt")
        self.assertIn("!UiAppearance.albumPageVisible", theme)
        self.assertIn("!UiAppearance.playerVisible", theme)
        self.assertIn("RoundedCornerShape(if (compact) 26.dp else 34.dp)", island)
        self.assertIn("border(.7.dp", island)
        self.assertNotIn("Modifier.blur(", island)
        art = source("ArtworkBackdrop.kt")
        self.assertIn("Bitmap.createScaledBitmap(artwork, 32, 32, true)", art)
        self.assertNotIn("WebDav", art)


if __name__ == "__main__":
    unittest.main()
