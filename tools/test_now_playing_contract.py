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
                  + self.src("feature/player/src/main/java/app/enotone/android/FeatureNowPlayingScreen.kt"))
        for token in (
            "remember(trackId) { mutableStateOf(false) }",
            "remember(trackId) { mutableFloatStateOf(0f) }",
            "PlaybackSeekPolicy.isKnownDuration(durationMs)",
            "PlaybackSeekPolicy.fraction(positionMs, durationMs)",
            "PlaybackSeekPolicy.positionForFraction(durationMs, scrubFraction)",
            "onSeekFraction(scrubFraction)",
            "contentDescription = seekContentDescription",
            "enabled = controlsEnabled && canSeek && knownDuration",
            "onPrevious",
            "onNext",
            "onPlayPause",
            "onCycleRepeat",
            "onToggleShuffle",
            "CoreUiR.drawable.ic_eno_play",
            "CoreUiR.drawable.ic_eno_pause",
            "CoreUiR.drawable.ic_eno_previous",
            "CoreUiR.drawable.ic_eno_next",
            "CoreUiR.drawable.ic_eno_more",
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
        # Transport and More icons come from approved UI Icons v1 (:core:ui).
        for name in ("previous", "next", "play", "pause", "more"):
            path = ROOT / f"core/ui/src/main/res/drawable/ic_eno_{name}.xml"
            xml = ET.parse(path).getroot()
            self.assertEqual(xml.tag, "vector")
            self.assertIn("UI Icons v1", path.read_text(encoding="utf-8"))
            self.assertEqual(
                xml.attrib.get("{http://schemas.android.com/apk/res/android}height"),
                "24dp",
            )

