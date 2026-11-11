"""Graphite Material 3: native dark/light/system and no custom accents."""
from pathlib import Path
import unittest
UI = Path(__file__).resolve().parents[1] / "app/src/main/java/app/enotone/android"
class ThemeContractTest(unittest.TestCase):
    def test_graphite_tokens_and_theme(self):
        theme = (UI / "EnoToneTheme.kt").read_text()
        for token in ("MaterialTheme(", "darkColorScheme(", "lightColorScheme(",
                      "Color(0xFF101113)", "Color(0xFFF7F7F8)",
                      "primaryContainer = Color(0xFF3A3B40)",
                      "primaryContainer = Color(0xFFE1E2E6)",
                      "UiMode.SYSTEM", "isSystemInDarkTheme()"):
            self.assertIn(token, theme)
        self.assertNotIn("dynamicDarkColorScheme", theme)
        self.assertNotIn("dynamicLightColorScheme", theme)
    def test_appearance_has_only_mode_not_accent(self):
        theme = (UI / "EnoToneTheme.kt").read_text()
        settings = (UI / "AppearanceSettings.kt").read_text()
        self.assertIn('putString("mode", value.name)', theme)
        self.assertIn("UiAppearance.chooseMode(context, mode)", settings)
        self.assertIn("RadioButton(", settings)
        self.assertNotIn("UiAccent", theme + settings)
        self.assertNotIn("chooseAccent", theme + settings)
        self.assertNotIn("appearance_accent", settings)
if __name__ == "__main__":
    unittest.main()
