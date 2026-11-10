"""M3 Now Playing uses the existing Media3 session and shared cover master."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/app/enotone/android"


class NowPlayingContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_fullscreen_not_alert_dialog_or_secondary_player(self):
        screen = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        player = self.src("app/src/main/java/app/enotone/android/NowPlayingScreen.kt")
        for token in (
            "if (showPlayerDetails && selectedTitle != null) {",
            "NowPlayingScreen(",
            "BackHandler(enabled = showPlayerDetails) { showPlayerDetails = false }",
            "onClose = { showPlayerDetails = false }",
            "} else {\n    Column(",
            "showPlayerDetails = true",
            "playback?.seekToFraction(fraction)",
            "playback?.pause()",
            "playback?.resume()",
            "playback?.playNext()",
            "playback?.playPrevious()",
            "playback?.cycleRepeatMode()",
            "playback?.toggleShuffle()",
        ):
            self.assertIn(token, screen)
        self.assertIn("BoxWithConstraints(", player)
        self.assertIn("Modifier.fillMaxSize().verticalScroll(rememberScrollState())", player)
        self.assertIn("Modifier.size(coverSide)", player)
        self.assertIn("onClose: () -> Unit", player)
        self.assertNotIn("AlertDialog(", player)
        self.assertNotIn("ExoPlayer.Builder(", player)
        self.assertNotIn("MediaSession(", player)
        self.assertNotIn("bindService(", player)
        self.assertNotIn("openLibraryDatabase(", player)
        self.assertNotIn("WebDavRangeReader(", player)

    def test_large_artwork_is_only_fetched_while_visible_and_audio_ready(self):
        screen = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        artwork = self.src("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        player = self.src("app/src/main/java/app/enotone/android/NowPlayingScreen.kt")
        for token in (
            "LaunchedEffect(showPlayerDetails, currentTrackId, preparedById, appPassword,",
            "if (!showPlayerDetails || appPassword.isBlank()) return@LaunchedEffect",
            'state == "PLAYING" || state == "PAUSED"',
            "fullArtwork = artworkRepository.cover(",
            "requestedSide = 1320",
            "artwork = fullArtwork ?: currentArtwork",
            "remember(currentTrackId, accountEmail)",
        ):
            self.assertIn(token, screen)
        self.assertIn("const val MAX_SIDE = 1320", artwork)
        self.assertIn("enotone-artwork-v2", artwork)
        self.assertIn("Bitmap", player)
        self.assertIn("artwork.asImageBitmap()", player)
        self.assertNotIn("repository.cover(", player)

    def test_track_sensitive_seek_and_transport_buttons_have_accessible_labels(self):
        player = self.src("app/src/main/java/app/enotone/android/NowPlayingScreen.kt")
        for token in (
            "remember(trackId) { mutableStateOf(false) }",
            "remember(trackId) { mutableStateOf(0f) }",
            "PlaybackSeekPolicy.isKnownDuration(durationMs)",
            "PlaybackSeekPolicy.fraction(positionMs, durationMs)",
            "PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction)",
            "onSeekFraction(scrubFraction)",
            "enabled = canSeek && controlsEnabled && hasDuration",
            "contentDescription = seekLabel",
            "onPrevious",
            "onNext",
            "onPlayPause",
            "onCycleRepeat",
            "onToggleShuffle",
            "R.drawable.ic_action_play",
            "R.drawable.ic_action_pause",
            "R.drawable.ic_action_previous",
            "R.drawable.ic_action_next",
            "R.drawable.ic_action_repeat",
            "R.drawable.ic_action_shuffle",
            "R.string.playback_previous",
            "R.string.playback_next",
            "R.string.playback_pause",
            "R.string.playback_resume",
            "R.string.playback_repeat_control",
            "R.string.playback_shuffle_on",
            "R.string.playback_shuffle_off",
            "R.string.artwork_description",
            "R.string.now_playing_no_artwork",
            "R.string.now_playing_queue_short",
        ):
            self.assertIn(token, player)

    def test_ru_en_strings_and_vector_icons(self):
        strings = {"now_playing_title", "now_playing_queue_short",
                   "now_playing_no_artwork"}
        for relative in ("app/src/main/res/values/strings.xml",
                         "app/src/main/res/values-en/strings.xml"):
            xml = ET.fromstring(self.src(relative))
            names = {node.attrib["name"] for node in xml.findall("string")}
            self.assertTrue(strings.issubset(names), strings - names)
        for name in ("previous", "repeat", "shuffle"):
            path = ROOT / f"app/src/main/res/drawable/ic_action_{name}.xml"
            xml = ET.parse(path).getroot()
            self.assertEqual(xml.tag, "vector")
            self.assertEqual(
                xml.attrib.get("{http://schemas.android.com/apk/res/android}height"),
                "24dp",
            )

    def test_settings_keeps_anonymous_playback_diagnostics(self):
        main = self.src("app/src/main/java/app/enotone/android/MainActivity.kt")
        screen = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        panel = self.src("app/src/main/java/app/enotone/android/PlaybackDiagnosticsPanel.kt")
        self.assertIn("if (showTechnicalTools) {", main)
        self.assertIn("PlaybackDiagnosticsPanel()", main)
        self.assertIn("PlaybackDiagnosticsRuntime.publish(", screen)
        self.assertIn("PlaybackDiagnosticSnapshot(", screen)
        self.assertIn("PlaybackDiagnosticsRuntime.useCacheOnNextPlay", screen)
        for token in ("playback_startup_timing", "playback_setup_breakdown",
                      "handoff_summary", "playback_cache_downloading",
                      "playback_next_cache_downloading", "playback_error_stage"):
            self.assertIn(token, panel)
        self.assertNotIn("WebDavRangeReader(", panel)
        self.assertNotIn("allForSource(", panel)
        self.assertNotIn("Log.", panel)
        self.assertNotIn("encodedPath", screen[screen.index(
            "PlaybackDiagnosticsRuntime.publish("):screen.index("delay(500L)",
            screen.index("PlaybackDiagnosticsRuntime.publish("))])

    def test_room_and_signed_preview_are_unchanged(self):
        db = self.src("core/database/src/main/java/app/enotone/database/EnoToneDatabase.kt")
        gradle = self.src("app/build.gradle.kts")
        screen = self.src("app/src/main/java/app/enotone/android/OfflineLibraryScreen.kt")
        self.assertIn("version = 4", db)
        self.assertIn('applicationIdSuffix = ".preview"', gradle)
        self.assertIn('getByName("stablePreview")', gradle)
        self.assertNotIn("deleteDatabase(", screen)


if __name__ == "__main__":
    unittest.main()
