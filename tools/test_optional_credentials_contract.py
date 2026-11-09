"""M0-K security contract: explicit opt-in, non-exportable Keystore AES-GCM.

This static test complements Android build/JUnit and Huawei device tests; it
cannot itself prove that an Android device accepts a Keystore key.
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class OptionalWebDavCredentialVaultContractTest(unittest.TestCase):
    def test_aes_gcm_and_private_ciphertext_only(self):
        vault = (ROOT / "app/src/main/java/app/enotone/android/"
                 "LocalCredentialVault.kt").read_text(encoding="utf-8")
        for requirement in (
            'KeyStore.getInstance(KEYSTORE)', '"AndroidKeyStore"',
            'KeyProperties.KEY_ALGORITHM_AES', '.setKeySize(256)',
            'KeyProperties.BLOCK_MODE_GCM', 'AES/GCM/NoPadding',
            'GCMParameterSpec(TAG_BITS', 'cipher.updateAAD(associatedData)',
            'Cipher.ENCRYPT_MODE', 'Cipher.DECRYPT_MODE',
            'Context.MODE_PRIVATE', 'Base64.encodeToString(blob',
            '.putString(ENTRY, encoded)', '.remove(ENTRY)', 'deleteEntry(KEY_ALIAS)',
        ):
            self.assertIn(requirement, vault)
        for forbidden in ('Log.', 'println(', 'putExtra(', 'getStringExtra(',
                          'Room.', 'androidx.security.crypto', 'KeyProperties.PURPOSE_SIGN'):
            self.assertNotIn(forbidden, vault)
        self.assertNotIn('.putString(ENTRY, password)', vault)
        self.assertNotIn('putString(ENTRY, email)', vault)

    def test_explicit_save_and_delete_and_safe_restore(self):
        main = (ROOT / "app/src/main/java/app/enotone/android/"
                "MainActivity.kt").read_text(encoding="utf-8")
        self.assertIn('LocalCredentialVault.restore(appContext)', main)
        self.assertIn('LocalCredentialVault.save(appContext, email, password)', main)
        self.assertIn('LocalCredentialVault.clear(appContext)', main)
        self.assertIn('withContext(Dispatchers.IO)', main)
        self.assertIn('CredentialVaultState.FAILED', main)
        self.assertIn('R.string.credential_vault_save', main)
        self.assertIn('R.string.credential_vault_clear', main)
        self.assertIn('Column(verticalArrangement = Arrangement.spacedBy(8.dp))', main)
        self.assertIn('Button(modifier = Modifier.fillMaxWidth()', main)
        self.assertNotIn('Log.', main)
        self.assertNotIn('androidx.security.crypto', main)

    def test_backup_remains_disabled(self):
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        app = manifest.find("application")
        self.assertEqual("false", app.attrib[f"{ANDROID}allowBackup"])
        for rel in ("app/src/main/res/xml/backup_rules.xml",
                    "app/src/main/res/xml/data_extraction_rules.xml"):
            xml = (ROOT / rel).read_text(encoding="utf-8")
            self.assertIn('domain="sharedpref" path="."', xml)
        self.assertTrue((ROOT / "docs/DECISIONS/"
                         "ADR-0043-m0k-optional-credential-vault.md").is_file())


if __name__ == "__main__":
    unittest.main()
