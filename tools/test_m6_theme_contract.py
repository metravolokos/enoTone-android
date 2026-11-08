"""M6 approved enoTone design tokens contract.

Source (approved visual concept, not generated assets):
metravolokos/enotone-design/tokens/colors.json v1.3.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]


class ThemeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.theme = (ROOT / "app/src/main/java/app/enotone/android/"
                     "EnoToneTheme.kt").read_text(encoding="utf-8")
        cls.activity = (ROOT / "app/src/main/java/app/enotone/android/"
                        "MainActivity.kt").read_text(encoding="utf-8")

    def test_themes_use_exact_approved_background_surface_and_accent(self):
        # Color tokens are case-insensitive hexadecimal literals in Compose.
        text = self.theme.upper()
        expected = {
            "EDE9E5", "F8F5F1", "E2DCD6", "211E1C", "5C544E",
            "EBAE9F", "A24F3E", "1B1A19", "252321", "2F2C2A",
            "F4ECE4", "B9AEA4", "8FB0C2",
        }
        actual = set(re.findall(r"COLOR\(0XFF([0-9A-F]{6})\)", text))
        self.assertTrue(expected.issubset(actual), expected - actual)

    def test_pink_buttons_have_dark_labels_in_both_themes(self):
        self.assertIn("primary = Color(0xFFEBAE9F)", self.theme)
        self.assertIn("onPrimary = Color(0xFF211E1C)", self.theme)
        self.assertIn("onPrimary = Color(0xFF1B1A19)", self.theme)
        self.assertIn("isSystemInDarkTheme()", self.theme)
        self.assertNotIn("dynamicLightColorScheme", self.theme)
        self.assertNotIn("dynamicDarkColorScheme", self.theme)
        self.assertIn("EnoToneTheme { DiagnosticScreen() }", self.activity)


if __name__ == "__main__":
    unittest.main()
