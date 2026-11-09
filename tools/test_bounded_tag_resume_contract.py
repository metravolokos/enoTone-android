"""M1 bounded resume: explicit opt-in and unchanged per-session GET/byte caps."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class BoundedTagResumeContractTest(unittest.TestCase):
    def test_caps_and_atomic_checkpoints(self):
        indexer = (ROOT / "data/library/src/main/java/app/enotone/library/"
                   "PilotTagIndexer.kt").read_text(encoding="utf-8")
        assert "const val RESUME_LIMIT = 100" in indexer
        assert "const val MAX_BATCH_GETS = 60" in indexer
        assert "const val MAX_BATCH_BYTES = 4L * 1024 * 1024" in indexer
        assert "const val MAX_BATCH_NANOS = 180L * 1_000_000_000L" in indexer
        assert "fun requestCap(count: Int) = minOf(MAX_BATCH_GETS, count * 3)" in indexer
        assert "fun byteCap(count: Int) = minOf(MAX_BATCH_BYTES, count * 256L * 1024)" in indexer
        assert "PilotBudget.mayStartFile(gets, bytes, count)" in indexer
        assert "recordFastScanIfUnchanged(" in indexer
        assert "onProgress(report())" in indexer
        assert "maxRequests = minOf(6, remainingGets)" in indexer
        assert "maxDownloadedBytes = minOf(6L * 65536, remainingBytes)" in indexer
        for forbidden in ("Log.", "WorkManager", "GlobalScope", "PROPFIND", "while (true)"):
            assert forbidden not in indexer

    def test_user_interaction_still_cancelable_and_not_background(self):
        ui = (ROOT / "app/src/main/java/app/enotone/android/"
              "MainActivity.kt").read_text(encoding="utf-8")
        assert "onClick = { runPilot(100) }" in ui
        assert "onClick = { runPilot(10) }" in ui
        assert "onClick = { runPilot(20) }" in ui
        assert "pilotJob?.cancel()" in ui
        assert "PilotTagIndexer(database).run(email, password, count)" in ui
        assert "LocalCredentialVault.restore(appContext)" in ui
        assert "R.string.pilot_resume_explanation" in ui
        assert "onClick = { runPilot(3590) }" not in ui
        assert "WorkManager" not in ui


if __name__ == "__main__":
    unittest.main()
