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
        # Temporary security guard at the code's new owner, pending Kotlin instrumentation.
        connection = (
            ROOT / "app/src/main/java/app/enotone/android/"
            "PlaybackConnection.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("bindService(", connection)
        self.assertIn("unbindService(", connection)
        self.assertIn("EnoTonePlaybackService", connection)
        self.assertNotIn("stopPlayback(", connection)
        self.assertNotIn("putExtra(", connection)

    def test_m5_current_cache_is_transient_and_pause_independent(self):
        module = ROOT / "playback/src/main/java/app/enotone/playback"
        cache = (module / "TransientMediaCache.kt").read_text(encoding="utf-8")
        player = (module / "PreviewAudioPlayback.kt").read_text(encoding="utf-8")
        service = (module / "EnoTonePlaybackService.kt").read_text(encoding="utf-8")
        self.assertIn("context.cacheDir", cache)
        self.assertIn("directory.deleteRecursively()", cache)
        self.assertIn("CacheWriter(", cache)
        self.assertIn("writer.cache()", cache)
        self.assertIn("setCacheWriteDataSinkFactory(null)", cache)
        self.assertIn("TransientCachePolicy.canDownload(", cache)
        self.assertIn("onMediaItemTransition(", player)
        self.assertIn("setCustomCacheKey(TransientCachePolicy.key(item))", player)
        self.assertIn("temporaryCache?.prefetchCurrent(", player)
        self.assertIn("scheduleAfterFirstBuffer()", player)
        self.assertIn("CacheStartPolicy.mayStart(", player)
        self.assertIn("Player.STATE_READY", player)
        self.assertIn("Player.STATE_BUFFERING", player)
        self.assertIn("clearScheduledWriter()", player)
        self.assertIn("fun cacheSnapshot()", service)
        self.assertNotIn("putExtra(", service)
        self.assertNotIn("Log.", cache)
        self.assertNotIn("Log.", player)

    def test_m6b_eof_boundary_matches_media3_and_never_calls_webdav(self):
        base = ROOT / "playback/src/main/java/app/enotone/playback"
        source = (base / "WebDavMedia3DataSource.kt").read_text(encoding="utf-8")
        window = (base / "PlaybackReadWindow.kt").read_text(encoding="utf-8")
        tests = (ROOT / "playback/src/test/java/app/enotone/playback/"
                 "PlaybackReadWindowTest.kt").read_text(encoding="utf-8")
        self.assertIn("PlaybackReadWindow.resolve(", source)
        self.assertIn("if (window.endInclusive == null)", source)
        self.assertLess(source.index("if (window.endInclusive == null)"),
                        source.index("conn = URL(safeUrl).openConnection()"))
        self.assertIn("bytesLeft = 0L", source)
        self.assertIn("return window.openReturnLength", source)
        self.assertIn("PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE", source)
        self.assertIn("if (position == size)", window)
        self.assertIn("endInclusive = null", window)
        self.assertIn("exactEofIsLegalWithoutAnyNetworkRange", tests)
        self.assertIn("beyondEofRemainsRejectedAndNumbersAreValidated", tests)

