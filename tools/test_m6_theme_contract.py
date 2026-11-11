"""Guard the user's decision: retain standard Material Design colors in M6.

The previous design-project palette is NOT approved for the Android player.
Do not carry tokens across repositories until the owner accepts the UI design.
"""
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
        # The owner accepted Graphite/Porcelain in enotone-design v1.3.
        theme = (UI / "EnoToneTheme.kt").read_text(encoding="utf-8")
        activity = (UI / "MainActivity.kt").read_text(encoding="utf-8")
        library = (UI / "OfflineLibraryScreen.kt").read_text(encoding="utf-8")
        for token in ("0xFFEBAE9F", "0xFFEDE9E5", "0xFF1B1A19"):
            self.assertIn(token, theme)
            self.assertNotIn(token, activity)
            self.assertNotIn(token, library)
        self.assertNotIn("dynamicLightColorScheme", theme)
        self.assertNotIn("dynamicDarkColorScheme", theme)


if __name__ == "__main__":
    unittest.main()
