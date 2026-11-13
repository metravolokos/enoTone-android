"""Поведение скриптов качества CI (не проверки текста исходников)."""
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import check_adr_index  # noqa: E402
import check_detekt_baseline  # noqa: E402


class AdrIndexTest(unittest.TestCase):
    def test_single_numbers_and_ranges_are_covered(self):
        index = "| ADR | Тема |\n|---|---|\n| 0001 | A |\n| 0044–0049 | B |\n| 0050-0051 | C |\n"
        self.assertEqual({1, 44, 45, 46, 47, 48, 49, 50, 51}, check_adr_index.covered(index))

    def test_text_mentions_are_not_rows(self):
        self.assertEqual(set(), check_adr_index.covered("см. | 0083 в тексте\nADR 0084\n"))


class DetektBaselineTest(unittest.TestCase):
    def test_growth_rules(self):
        self.assertFalse(check_detekt_baseline.grew(None, 429))  # baseline вводится впервые
        self.assertFalse(check_detekt_baseline.grew(429, 429))
        self.assertFalse(check_detekt_baseline.grew(429, 400))
        self.assertTrue(check_detekt_baseline.grew(429, 430))

    def test_counts_ids_in_tree_but_not_in_build_outputs(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            (root / "app").mkdir()
            (root / "app" / "detekt-baseline.xml").write_text(
                "<SmellBaseline><CurrentIssues><ID>a</ID><ID>b</ID></CurrentIssues></SmellBaseline>")
            (root / "app" / "build").mkdir()
            (root / "app" / "build" / "detekt-baseline.xml").write_text("<ID>x</ID>")
            self.assertEqual(2, check_detekt_baseline.count_tree(root))


if __name__ == "__main__":
    unittest.main()
