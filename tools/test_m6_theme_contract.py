"""Guard the user's decision: retain standard Material Design colors in M6.

The previous design-project palette is NOT approved for the Android player.
Do not carry tokens across repositories until the owner accepts the UI design.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/app/enotone/android"


class ThemeContractTest(unittest.TestCase):
    def test_default_material_theme_is_used_in_android(self):
        activity = (UI / "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn("MaterialTheme { DiagnosticScreen() }", activity)
        self.assertNotIn("EnoToneTheme { DiagnosticScreen() }", activity)
        self.assertFalse((UI / "EnoToneTheme.kt").exists())

    def test_unapproved_palette_is_not_shipped(self):
        # No copy of the old project's colors in the current Compose screens.
        activity = (UI / "MainActivity.kt").read_text(encoding="utf-8")
        library = (UI / "OfflineLibraryScreen.kt").read_text(encoding="utf-8")
        for token in ("0xFFEBAE9F", "0xFFEDE9E5", "0xFF1B1A19"):
            self.assertNotIn(token, activity)
            self.assertNotIn(token, library)


if __name__ == "__main__":
    unittest.main()
