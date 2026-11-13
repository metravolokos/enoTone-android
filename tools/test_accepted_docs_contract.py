"""Guard the accepted enoTone M1/M2 documentation baseline against stale ADRs.

Historical records are intentionally kept; tests inspect their supersession
markers rather than banning old numbers from archive content.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


class AcceptedDocumentationContractTest(unittest.TestCase):
    def test_current_decisions_match_shipped_1200px_persistent_repository(self):
        accepted = read("docs/CURRENT_DECISIONS.md")
        cache = read("app/src/main/java/app/enotone/android/ArtworkPreviewRepository.kt")
        prefs = read("feature/library/src/main/java/app/enotone/android/ArtworkPreferences.kt")
        for token in ("2026-11-11", "1200 px", "filesDir/enotone-artwork-v2",
                      "ON_DEMAND", "AUTO_AFTER_IMPORT", "ADR-0067",
                      "Stable Preview #301", "Неизвестный альбом"):
            self.assertIn(token, accepted)
        self.assertIn("const val MAX_SIDE = 1200", cache)
        self.assertIn('File(context.applicationContext.filesDir, "enotone-artwork-v2")', cache)
        self.assertNotIn("const val CACHE_LIMIT_BYTES", cache)
        self.assertIn("AUTO_AFTER_IMPORT", prefs)
        self.assertIn("ON_DEMAND", prefs)

    def test_current_entrypoints_point_to_canonical_decisions(self):
        for path in ("README.md", "AGENTS.md", "docs/ROADMAP.md",
                     "docs/ARCHITECTURE.md", "docs/DATA_MODEL.md",
                     "docs/OPEN_QUESTIONS.md", "docs/KNOWN_ISSUES.md",
                     "docs/RELEASE.md", "docs/TESTING.md"):
            text = read(path)
            self.assertIn("CURRENT_DECISIONS", text, path)
        for path in ("README.md", "docs/ROADMAP.md", "docs/ARCHITECTURE.md"):
            self.assertIn("1200 px", read(path), path)
            self.assertIn("filesDir", read(path), path)

    def test_historical_1320px_adr_is_explicitly_superseded(self):
        old = read("docs/DECISIONS/ADR-0064-single-master-artwork-cache.md")
        new = read("docs/DECISIONS/ADR-0067-permanent-album-artwork.md")
        source = read("docs/DECISIONS/ADR-0062-unified-import-genre-artwork.md")
        self.assertIn("ЗАМЕНЁН", old)
        self.assertIn("ADR-0067", old)
        self.assertIn("1200 px", new)
        self.assertIn("filesDir", new)
        self.assertIn("Нет автоматического", read("docs/CURRENT_DECISIONS.md"))
        self.assertIn("история", source)
        for p in ("docs/DECISIONS/ADR-0006-caches.md",
                  "docs/DECISIONS/ADR-0054-embedded-artwork-on-demand.md",
                  "docs/DECISIONS/ADR-0056-on-demand-now-playing-artwork.md",
                  "docs/DECISIONS/ADR-0057-lazy-album-artwork-grid.md",
                  "docs/DECISIONS/ADR-0063-track-row-artwork.md",
                  "docs/DECISIONS/ADR-0065-now-playing-screen.md"):
            self.assertIn("ADR-0067", read(p), p)

    def test_device_acceptance_does_not_fabricate_unperformed_tests(self):
        for path in ("docs/CURRENT_DECISIONS.md", "docs/STATUS.md",
                     "docs/TESTING.md", "docs/DECISIONS/ADR-0067-permanent-album-artwork.md",
                     "CHANGELOG.md"):
            t = read(path)
            self.assertIn("#301", t, path)
            self.assertIn("Работает", t, path)
        accepted = read("docs/CURRENT_DECISIONS.md")
        self.assertIn("не", accepted.lower())
        self.assertIn("offline", accepted)
        self.assertIn("плейлист", accepted.lower())
        self.assertIn("не готов", accepted.lower())


if __name__ == "__main__":
    unittest.main()
