"""Managed tag indexing guard: explicit foreground user action and hard caps."""
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
            "stopOnRetryableFailure = true",
            "delay(ManagedIndexLimits.BETWEEN_BATCH_DELAY_MS)",
            "if (!networkAllowed())",
            'if (networkAllowed()) null else "NETWORK_POLICY"',
        ):
            self.assertIn(needle, runner)
        for needle in (
            "beforeFile: suspend () -> String? = { null }",
            "val policyStop = beforeFile()",
            'stop = "RETRY_BACKOFF"',
            "recordFastScanIfUnchanged(",
        ):
            self.assertIn(needle, indexer)
        for forbidden in ("WorkManager", "GlobalScope", "PROPFIND", "while (true)", "Log."):
            self.assertNotIn(forbidden, runner)

    def test_wifi_default_and_background_stop(self):
        main = (ROOT / "app/src/main/java/app/enotone/android/"
                "MainActivity.kt").read_text(encoding="utf-8")
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        permissions = [p.attrib.get(f"{ANDROID}name")
                       for p in manifest.findall("uses-permission")]
        self.assertIn("android.permission.ACCESS_NETWORK_STATE", permissions)
        for needle in (
            "var allowMobileIndexing by remember { mutableStateOf(false) }",
            "NetworkCapabilities.TRANSPORT_WIFI",
            "override fun onStop()",
            "pauseManagedIndexOnStop?.invoke()",
            "managedJob?.cancel()",
            "onClick = { startManagedSession() }",
            "ManagedTagIndexSession(database).run(",
            "R.string.managed_index_allow_mobile",
            "ManagedScanState.PAUSED",
            "ManagedScanState.STOPPED",
        ):
            self.assertIn(needle, main)
        self.assertNotIn("Log.", main)
        self.assertNotIn("WorkManager", main)
        self.assertIn('android:allowBackup="false"', (ROOT / "app/src/main/AndroidManifest.xml").read_text())


if __name__ == "__main__":
    unittest.main()
