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
        self.assertIn("setMediaSources(firstSources, plan.seedSelectedIndex, C.TIME_UNSET)", player)
        self.assertIn("player.hasNextMediaItem()", service)
        self.assertIn("player.hasPreviousMediaItem()", service)
        self.assertIn("player.seekToNextMediaItem()", service)
        self.assertIn("player.seekToPreviousMediaItem()", service)
        self.assertNotIn("Player.STATE_ENDED", service)
        self.assertNotIn("MediaSession.Callback", service)
        self.assertNotIn("credentials = Credentials(", service)
        self.assertIn("visible.mapNotNull", ui)
        self.assertIn("playQueue(accountEmail, appPassword", ui)

    def test_m4c_modes_use_native_media3_and_no_network_loop(self):
        service = (
            ROOT / "playback/src/main/java/app/enotone/playback/"
            "EnoTonePlaybackService.kt"
        ).read_text(encoding="utf-8")
        ui = (
            ROOT / "app/src/main/java/app/enotone/android/"
            "OfflineLibraryScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("PlaybackModes.nextRepeat(", service)
        self.assertIn("player.shuffleModeEnabled", service)
        self.assertIn("onRepeatModeChanged(", ui)
        self.assertIn("onShuffleModeEnabledChanged(", ui)
        self.assertIn("playback?.cycleRepeatMode()", ui)
        self.assertIn("playback?.toggleShuffle()", ui)
        self.assertNotIn("putExtra(", service)
        self.assertNotIn("Log.", service)

        self.assertNotIn("putExtra(", service)
        self.assertNotIn("Log.", service)


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

    def test_m5_latency_ab_uses_same_room_catalogue_without_network(self):
        ui = (
            ROOT / "app/src/main/java/app/enotone/android/"
            "OfflineLibraryScreen.kt"
        ).read_text(encoding="utf-8")
        player = (
            ROOT / "playback/src/main/java/app/enotone/playback/"
            "PreviewAudioPlayback.kt"
        ).read_text(encoding="utf-8")
        self.assertEqual(1, ui.count("allForSource(sourceKey)"))
        self.assertIn("preparedById[shown.id]", ui)
        self.assertIn("SystemClock.elapsedRealtime()", ui)
        self.assertIn("bufferingAfterTapMs", ui)
        self.assertIn("readyAfterTapMs", ui)
        self.assertIn("playingAfterTapMs", ui)
        self.assertIn("useCacheOnNextPlay", ui)
        self.assertIn("useTemporaryCache: Boolean", player)
        self.assertIn("if (useTemporaryCache)", player)
        self.assertIn("StagedPlaylistPlan.create(queue)", player)
        self.assertIn("stagePlaylistAfterReady()", player)
        self.assertIn("player.addMediaSources(0, sources)", player)
        self.assertIn("player.addMediaSources(sources)", player)
        self.assertIn("cancelPlaylistFill()", player)
        self.assertIn("PlaybackSetupTimings(", player)
        self.assertIn("playback_setup_breakdown", ui)
        self.assertIn("loadedQueueSources", ui)
        self.assertIn("else factory", player)
        self.assertIn("private var temporaryCache: TransientMediaCache? = null", player)
        self.assertNotIn("Log.", ui)


    def test_m5b_one_next_only_after_current_complete(self):
        module = ROOT / "playback/src/main/java/app/enotone/playback"
        cache = (module / "TransientMediaCache.kt").read_text(encoding="utf-8")
        player = (module / "PreviewAudioPlayback.kt").read_text(encoding="utf-8")
        service = (module / "EnoTonePlaybackService.kt").read_text(encoding="utf-8")
        ui = (
            ROOT / "app/src/main/java/app/enotone/android/"
            "OfflineLibraryScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("onComplete: (() -> Unit)?", cache)
        self.assertIn("CachePhase.COMPLETE", cache)
        self.assertIn("onComplete()", cache)
        self.assertIn("fun prefetchNext(", cache)
        self.assertIn("Executors.newSingleThreadExecutor", cache)
        self.assertIn("activeNextWriter?.cancel()", cache)
        self.assertIn("nextGeneration.incrementAndGet()", cache)
        self.assertIn("nextStatus", cache)
        self.assertIn("fullyCachedCurrentId = selectedId", player)
        self.assertIn("refreshNextPrefetch()", player)
        self.assertIn("player.nextMediaItemIndex", player)
        self.assertIn("player.mediaItemCount < expectedQueueSize", player)
        self.assertIn("NextPrefetchSelection.select(", player)
        self.assertIn("temporaryCache?.prefetchNext(", player)
        self.assertIn("onShuffleModeEnabledChanged(", player)
        self.assertIn("onRepeatModeChanged(", player)
        self.assertIn("nextCacheSnapshot()", service)
        self.assertIn("nextCacheInfo", ui)
        self.assertNotIn("putExtra(", service)
        self.assertNotIn("Log.", cache)
        self.assertNotIn("Log.", player)


    def test_m6_handoff_is_anonymous_and_debug_ui_is_collapsed(self):
        source = ROOT / "playback/src/main/java/app/enotone/playback"
        handoff = (source / "PlaybackHandoffTracker.kt").read_text(encoding="utf-8")
        player = (source / "PreviewAudioPlayback.kt").read_text(encoding="utf-8")
        service = (source / "EnoTonePlaybackService.kt").read_text(encoding="utf-8")
        ui = (
            ROOT / "app/src/main/java/app/enotone/android/"
            "OfflineLibraryScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("fun onItemChanged(", handoff)
        self.assertIn("fun onBuffering(", handoff)
        self.assertIn("fun onReady(", handoff)
        self.assertIn("fun onPlaying(", handoff)
        self.assertNotIn("encodedPath", handoff)
        self.assertNotIn("mediaId", handoff)
        self.assertNotIn("Log.", handoff)
        self.assertIn("addListener(handoffListener)", player)
        self.assertIn("addListener(cacheTransitionListener)", player)
        self.assertLess(player.index("addListener(handoffListener)"),
                        player.index("addListener(cacheTransitionListener)"))
        self.assertIn("CachePhase.COMPLETE", player)
        self.assertIn("fun handoffSnapshot()", service)
        self.assertIn("playback.noteNextButton()", service)
        self.assertIn("playback.notePreviousButton()", service)
        self.assertIn("var showDiagnostics", ui)
        self.assertIn("if (showDiagnostics)", ui)
        self.assertIn("useCacheOnNextPlay by remember { mutableStateOf(true) }", ui)
        self.assertIn("handoffInfo = playback?.handoffSnapshot()", ui)
        self.assertIn("playback_diagnostics_show", ui)
        self.assertIn("handoff_summary", ui)
        self.assertNotIn("Log.", ui)
        self.assertNotIn("putExtra(", service)


    def test_m6b_seek_uses_native_player_without_untrusted_remote_url(self):
        base = ROOT / "playback/src/main/java/app/enotone/playback"
        service = (base / "EnoTonePlaybackService.kt").read_text(encoding="utf-8")
        policy = (base / "PlaybackSeekPolicy.kt").read_text(encoding="utf-8")
        ui = (
            ROOT / "app/src/main/java/app/enotone/android/"
            "OfflineLibraryScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM", service)
        self.assertIn("fun seekToFraction(", service)
        self.assertIn("PlaybackSeekPolicy.positionForFraction(", service)
        self.assertIn("player.seekTo(target)", service)
        self.assertIn("fun isKnownDuration(", policy)
        self.assertIn("fraction.isFinite()", policy)
        self.assertIn("coerceIn(0L, durationMs)", policy)
        self.assertIn("fun formatTime(", policy)
        self.assertIn("Slider(", ui)
        self.assertIn("onValueChangeFinished", ui)
        self.assertIn("seekToFraction(scrubFraction)", ui)
        self.assertIn("canSeek = active?.canSeekCurrent == true", ui)
        self.assertIn("contentDescription = seekDescription", ui)
        self.assertIn("delay(500L)", ui)
        self.assertNotIn("allForSource(sourceKey)", service)
        self.assertNotIn("Log.", policy)
        self.assertNotIn("putExtra(", service)


if __name__ == "__main__":
    unittest.main()
