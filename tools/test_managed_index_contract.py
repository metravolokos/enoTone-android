"""Managed tag indexing: bounded, foreground service, no Wi-Fi policy."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class ManagedIndexContractTest(unittest.TestCase):
    def test_bounded_batches_and_preserved_room_checkpoint(self):
        runner = (ROOT / "data/library/src/main/java/app/enotone/library/"
                  "ManagedTagIndexSession.kt").read_text(encoding="utf-8")
        indexer = (ROOT / "data/library/src/main/java/app/enotone/library/"
                   "PilotTagIndexer.kt").read_text(encoding="utf-8")
        for needle in (
            "const val MAX_BATCHES = 4",
            "const val MAX_TOTAL_GETS = MAX_BATCHES * PilotBudget.MAX_BATCH_GETS",
            "const val MAX_TOTAL_BYTES = MAX_BATCHES * PilotBudget.MAX_BATCH_BYTES",
            "PilotBudget.RESUME_LIMIT",
            "ParallelTagIndexer(database).run(",
            "delay(ManagedIndexLimits.BETWEEN_BATCH_DELAY_MS)",
        ):
            self.assertIn(needle, runner)
        for needle in (
            "beforeFile: suspend () -> String? = { null }",
            'stop = "RETRY_BACKOFF"',
            "recordFastScanIfUnchanged(",
            "onProgress(report())",
        ):
            self.assertIn(needle, indexer)
        for forbidden in ("WorkManager", "GlobalScope", "PROPFIND", "while (true)", "Log."):
            self.assertNotIn(forbidden, runner)

    def test_parallel_batch_has_shared_budget_and_retries(self):
        reader = (ROOT / "data/library/src/main/java/app/enotone/library/"
                  "WebDavRangeReader.kt").read_text(encoding="utf-8")
        parallel = (ROOT / "data/library/src/main/java/app/enotone/library/"
                    "ParallelTagIndexer.kt").read_text(encoding="utf-8")
        for needle in (
            "const val WORKERS = 4",
            "const val MAX_RETRIES = 2",
            "SharedRangeBudget(",
            "workers.joinAll()",
            "launch(Dispatchers.IO)",
            "delay(pause)",
            "recordFastScanIfUnchanged(",
            'stop.set("AUTH_REJECTED")',
        ):
            self.assertIn(needle, parallel)
        for needle in (
            "class SharedRangeBudget(",
            "@Synchronized fun tryReserve(",
            "sharedBudget.tryReserve(n)",
        ):
            self.assertIn(needle, reader)
        for forbidden in ("GlobalScope", "while (true)", "PROPFIND", "Log."):
            self.assertNotIn(forbidden, parallel)
        self.assertNotIn("stopOnRetryableFailure = true",
                         (ROOT / "data/library/src/main/java/app/enotone/library/"
                          "ManagedTagIndexSession.kt").read_text(encoding="utf-8"))

    def test_terminated_session_does_not_leave_running_text_or_hide_failure(self):
        service = (ROOT / "app/src/main/java/app/enotone/android/"
                   "IndexingForegroundService.kt").read_text(encoding="utf-8")
        ui = (ROOT / "app/src/main/java/app/enotone/android/"
              "MainActivity.kt").read_text(encoding="utf-8")
        for needle in (
            'stopCode = "FAILED_INTERNAL"',
            'lastErrorStage = stage',
            'ManagedScanRuntime.fail(stage, failure)',
            'failureStage = if (state == last.state) last.failureStage else null',
            'status.value = ManagedScanStatus(',
            'catch (_: RuntimeException)',
        ):
            self.assertIn(needle, service)
        for needle in (
            "managedStatus.failureStage?.let { stage ->",
            "R.string.managed_index_internal_failure",
        ):
            self.assertIn(needle, ui)

    def test_single_room_collector_with_four_independent_network_workers(self):
        parallel = (ROOT / "data/library/src/main/java/app/enotone/library/"
                    "ParallelTagIndexer.kt").read_text(encoding="utf-8")
        service = (ROOT / "app/src/main/java/app/enotone/android/"
                   "IndexingForegroundService.kt").read_text(encoding="utf-8")
        # The only CAS write and report callback occur in ONE collector.
        collector_start = parallel.index("val collector = launch(Dispatchers.IO)")
        worker_start = parallel.index("val workers = (0 until ParallelTagPolicy.WORKERS)")
        assert collector_start < worker_start
        collector = parallel[collector_start:worker_start]
        workers = parallel[worker_start:]
        self.assertIn("for (file in completed)", collector)
        self.assertIn("recordFastScanIfUnchanged(", collector)
        self.assertIn('runStage("PROGRESS") { onProgress(report) }', collector)
        self.assertNotIn("recordFastScanIfUnchanged(", workers)
        self.assertNotIn("onProgress(", workers)
        self.assertIn("Channel<CompletedTagFile>(ParallelTagPolicy.WORKERS)", parallel)
        self.assertIn("completed.send(CompletedTagFile(", workers)
        self.assertIn("workers.joinAll()", workers)
        self.assertIn("completed.close()", workers)
        self.assertIn("collector.join()", workers)
        # Avoid a SQLite SELECT of all 3,500 pending rows after every file.
        self.assertNotIn('runStage("DB_COUNT")', collector)
        self.assertIn("var remainingEstimate = pending.size", parallel)
        self.assertIn("remaining = remainingEstimate", collector)
        # UI failure codes are fixed strings and unwrap no raw messages.
        self.assertIn("class TagBatchStageException(", parallel)
        self.assertIn("cause is TagBatchStageException", service)
        self.assertNotIn("cause.message", service)

    def test_foreground_service_survives_activity_stop_and_uses_any_network(self):
        main = (ROOT / "app/src/main/java/app/enotone/android/"
                "MainActivity.kt").read_text(encoding="utf-8")
        service = (ROOT / "app/src/main/java/app/enotone/android/"
                   "IndexingForegroundService.kt").read_text(encoding="utf-8")
        xml = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
        manifest = ET.fromstring(xml)
        permissions = [p.attrib.get(f"{ANDROID}name")
                       for p in manifest.findall("uses-permission")]
        self.assertIn("android.permission.FOREGROUND_SERVICE_DATA_SYNC", permissions)
        services = manifest.find("application").findall("service")
        self.assertTrue(any(s.attrib.get(f"{ANDROID}name") == ".IndexingForegroundService" and
                            s.attrib.get(f"{ANDROID}foregroundServiceType") == "dataSync" and
                            s.attrib.get(f"{ANDROID}exported") == "false" for s in services))
        for needle in (
            "appContext.startForegroundService(",
            "IndexingForegroundService.ACTION_START",
            "ManagedScanRuntime.status.collectAsState()",
            "IndexingForegroundService.ACTION_PAUSE",
            "IndexingForegroundService.ACTION_STOP",
            "onClick = { startManagedSession() }",
        ):
            self.assertIn(needle, main)
        for needle in (
            "class IndexingForegroundService : Service()",
            "ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC",
            "START_NOT_STICKY",
            "ManagedScanRuntime.claim()",
            "ManagedTagIndexSession(database).run(",
            "networkAllowed = { true }",
            "override fun onTimeout(startId: Int, fgsType: Int)",
            "stopForeground(STOP_FOREGROUND_REMOVE)",
            "ManagedScanRuntime.update(",
        ):
            self.assertIn(needle, service)
        for forbidden in (
            "override fun onStop()", "pauseManagedIndexOnStop", "TRANSPORT_WIFI",
            "allowMobileIndexing", "managedIndexNetworkAllowed",
            "putExtra(\"password\"", "Log.",
        ):
            self.assertNotIn(forbidden, main + service)
        self.assertIn('android:allowBackup="false"', xml)


if __name__ == "__main__":
    unittest.main()
