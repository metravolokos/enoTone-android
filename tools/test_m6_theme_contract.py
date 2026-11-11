"""Guard current neutral palettes and one user-controlled accent (ADR-0069)."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/app/enotone/android"


class ThemeContractTest(unittest.TestCase):
    def test_approved_enotone_material_theme_is_used_in_android(self):
        activity = (UI / "MainActivity.kt").read_text(encoding="utf-8")
        theme = (UI / "EnoToneTheme.kt").read_text(encoding="utf-8")
        self.assertIn("EnoToneTheme { DiagnosticScreen() }", activity)
        self.assertIn("MaterialTheme(", theme)
        self.assertIn("lightColorScheme(", theme)
        self.assertIn("darkColorScheme(", theme)

    def test_approved_colors_have_one_central_source(self):
        # The latest Android UI decision supersedes the former pink palette.
        theme = (UI / "EnoToneTheme.kt").read_text(encoding="utf-8")
        activity = (UI / "MainActivity.kt").read_text(encoding="utf-8")
        library = (UI / "OfflineLibraryScreen.kt").read_text(encoding="utf-8")
        for token in ("background = Color.Black", "background = Color.White",
                      "surface = Color(0xFF242426)", "surface = Color(0xFFF0F0F2)"):
            self.assertIn(token, theme)
            self.assertNotIn(token, activity)
            self.assertNotIn(token, library)
        self.assertIn("UiAccent", theme)
        self.assertIn("UiAppearance", theme)
        self.assertNotIn("dynamicLightColorScheme", theme)
        self.assertNotIn("dynamicDarkColorScheme", theme)


if __name__ == "__main__":
    unittest.main()
