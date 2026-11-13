"""M3 Now Playing uses the existing Media3 session and shared cover master."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/app/enotone/android"


class NowPlayingContractTest(unittest.TestCase):
    def src(self, path):
        return (ROOT / path).read_text(encoding="utf-8")

    def test_track_sensitive_seek_and_transport_buttons_have_accessible_labels(self):
        player = (self.src("app/src/main/java/app/enotone/android/NowPlayingScreen.kt")
                  + self.src("feature/player/src/main/java/app/enotone/android/NowPlayingScreen.kt"))
        for token in (
            "remember(trackId) { mutableStateOf(false) }",
            "remember(trackId) { mutableFloatStateOf(0f) }",
            "PlaybackSeekPolicy.isKnownDuration(durationMs)",
            "PlaybackSeekPolicy.fraction(positionMs, durationMs)",
            "PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction)",
            "onSeekFraction(scrubFraction)",
            "enabled = controlsEnabled && canSeek && knownDuration",
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

