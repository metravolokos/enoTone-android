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



if __name__ == "__main__":
    unittest.main()
