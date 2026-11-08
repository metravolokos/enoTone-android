"""M3 static security and lifecycle contract tests run in existing Python CI.

The on-device behavior still requires Huawei smoke testing. These tests protect
the explicit media service permissions, non-exported binder, and RAM-only auth.
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class MediaSessionContractTest(unittest.TestCase):
    def test_foreground_playback_permissions_and_private_service(self):
        tree = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
        root = tree.getroot()
        permissions = {
            node.attrib[f"{ANDROID}name"]
            for node in root.findall("uses-permission")
        }
        self.assertIn("android.permission.FOREGROUND_SERVICE", permissions)
        self.assertIn(
            "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", permissions)
        services = root.findall("./application/service")
        playback = [
            node for node in services
            if node.attrib.get(f"{ANDROID}name") ==
            "app.enotone.playback.EnoTonePlaybackService"
        ]
        self.assertEqual(1, len(playback))
        service = playback[0]
        self.assertEqual("false", service.attrib[f"{ANDROID}exported"])
        self.assertEqual("mediaPlayback",
                         service.attrib[f"{ANDROID}foregroundServiceType"])
        actions = {
            node.attrib[f"{ANDROID}name"]
            for node in service.findall("./intent-filter/action")
        }
        self.assertIn("androidx.media3.session.MediaSessionService", actions)

    def test_sensitive_playback_input_never_enters_android_intent(self):
        service = (
            ROOT / "playback/src/main/java/app/enotone/playback/"
            "EnoTonePlaybackService.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("MediaSession.Builder(", service)
        self.assertIn("addSession(session)", service)
        self.assertIn("super.onBind(intent)", service)
        self.assertIn("ACTION_LOCAL_BIND", service)
        self.assertIn("session.release()", service)
        self.assertNotIn("putExtra(", service)
        self.assertNotIn("getStringExtra(", service)
        self.assertNotIn("START_STICKY", service)
        self.assertNotIn("Log.", service)

    def test_screen_unbinds_without_stopping_playback(self):
        ui = (
            ROOT / "app/src/main/java/app/enotone/android/"
            "OfflineLibraryScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("bindService(", ui)
        self.assertIn("unbindService(", ui)
        self.assertIn("EnoTonePlaybackService", ui)
        self.assertNotIn("LifecycleEventObserver", ui)
        self.assertNotIn("active?.close()", ui)
        self.assertNotIn("active?.pause()", ui)


    def test_m4_native_playlist_exposes_standard_skip_and_stays_private(self):
        service = (
            ROOT / "playback/src/main/java/app/enotone/playback/"
            "EnoTonePlaybackService.kt"
        ).read_text(encoding="utf-8")
        ui = (
            ROOT / "app/src/main/java/app/enotone/android/"
            "OfflineLibraryScreen.kt"
        ).read_text(encoding="utf-8")
        player = (
            ROOT / "playback/src/main/java/app/enotone/playback/"
            "PreviewAudioPlayback.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("PlaybackQueue.create(tracks, selectedId)", player)
        self.assertIn("setMediaSources(sources, queue.position, C.TIME_UNSET)", player)
        self.assertIn("player.hasNextMediaItem()", service)
        self.assertIn("player.hasPreviousMediaItem()", service)
        self.assertIn("player.seekToNextMediaItem()", service)
        self.assertIn("player.seekToPreviousMediaItem()", service)
        self.assertNotIn("Player.STATE_ENDED", service)
        self.assertNotIn("MediaSession.Callback", service)
        self.assertNotIn("credentials = Credentials(", service)
        self.assertIn("visible.mapNotNull", ui)
        self.assertIn("playQueue(accountEmail, appPassword", ui)
        self.assertNotIn("putExtra(", service)
        self.assertNotIn("Log.", service)


if __name__ == "__main__":
    unittest.main()
