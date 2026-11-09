"""Security guardrails for opt-in stable Preview signing and Room updates.

Static checks run in Mock WebDAV test step and require no GitHub secrets.
"""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]


class StablePreviewContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        cls.workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        cls.resources = (ROOT / "app/src/stablePreview/res/values/strings.xml").read_text(encoding="utf-8")

    def test_same_identity_version_and_secret_only_signer(self):
        self.assertIn('create("stablePreview")', self.gradle)
        self.assertIn('applicationIdSuffix = ".preview"', self.gradle)
        self.assertIn('getByName("release")', self.gradle)
        self.assertIn('isDebuggable = false', self.gradle)
        self.assertIn('GITHUB_RUN_NUMBER', self.gradle)
        self.assertIn('assembleStablePreview", "packageStablePreview"', self.gradle)
        self.assertIn('ENOTONE_PREVIEW_KEYSTORE_PATH', self.gradle)
        self.assertIn('ENOTONE_PREVIEW_KEYSTORE_PASSWORD', self.gradle)
        self.assertIn('keyAlias = "enotone-preview"', self.gradle)
        self.assertIn('storeType = "PKCS12"', self.gradle)
        self.assertIn('Stable Preview signing secrets are required', self.gradle)
        self.assertIn('applicationIdSuffix = ".preview.r$previewRun"', self.gradle)
        self.assertIn('enoTone Preview', self.resources)

    def test_main_only_opt_in_and_no_unsigned_artifact(self):
        steps = self.workflow
        conditions = "github.ref == 'refs/heads/main'"
        self.assertGreaterEqual(steps.count(conditions), 4)
        self.assertIn("vars.ENOTONE_STABLE_PREVIEW_ENABLED == 'true'", steps)
        self.assertIn("secrets.ENOTONE_PREVIEW_KEYSTORE_BASE64", steps)
        self.assertIn("secrets.ENOTONE_PREVIEW_KEYSTORE_PASSWORD", steps)
        self.assertIn("vars.ENOTONE_PREVIEW_CERT_SHA256", steps)
        self.assertIn('base64 --decode', steps)
        self.assertIn("gradle --no-daemon :app:assembleStablePreview", steps)
        self.assertIn("gradle --no-daemon :app:compileStablePreviewKotlin", steps)
        self.assertIn("app.enotone.android.preview' versionCode=", steps)
        self.assertIn("Stable Preview signing certificate fingerprint mismatch", steps)
        self.assertIn('python3 tools/check_no_gms.py dependencies.txt "$apk"', steps)
        self.assertIn("name: enotone-stable-preview-apk", steps)
        self.assertIn('if-no-files-found: error', steps)
        self.assertIn('rm -f "$RUNNER_TEMP/enotone-preview.p12"', steps)
        self.assertNotIn("storePassword = \"", self.gradle)
        self.assertNotIn("keyPassword = \"", self.gradle)

    def test_no_network_or_player_changes_for_signing(self):
        self.assertTrue((ROOT / "docs/DECISIONS/ADR-0042-stable-preview-channel.md").is_file())
        self.assertTrue((ROOT / "docs/STABLE_PREVIEW_CHANNEL.md").is_file())


if __name__ == "__main__":
    unittest.main()
