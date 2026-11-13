"""Security regression: non-exportable Keystore AES-GCM and disabled backup.

This static test complements Android build/JUnit and Huawei device tests; it
cannot itself prove that an Android device accepts a Keystore key.
"""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"


class WebDavCredentialVaultSecurityTest(unittest.TestCase):
    def test_aes_gcm_and_private_ciphertext_only(self):
        # Keystore and storage live in LocalCredentialVault; the AES-GCM envelope
        # format moved to CredentialEnvelope (behaviour: CredentialEnvelopeTest).
        vault = "".join((ROOT / "app/src/main/java/app/enotone/android" / name)
                        .read_text(encoding="utf-8")
                        for name in ("LocalCredentialVault.kt", "CredentialEnvelope.kt"))
        for requirement in (
            'KeyStore.getInstance(KEYSTORE)', '"AndroidKeyStore"',
            'KeyProperties.KEY_ALGORITHM_AES', '.setKeySize(256)',
            'KeyProperties.BLOCK_MODE_GCM', 'AES/GCM/NoPadding',
            'GCMParameterSpec(TAG_BITS', 'cipher.updateAAD(associatedData)',
            'Cipher.ENCRYPT_MODE', 'Cipher.DECRYPT_MODE',
            'Context.MODE_PRIVATE', 'Base64.encodeToString(',
            '.putString(ENTRY, encoded)', '.remove(ENTRY)', 'deleteEntry(KEY_ALIAS)',
        ):
            self.assertIn(requirement, vault)
        for forbidden in ('Log.', 'println(', 'putExtra(', 'getStringExtra(',
                          'Room.', 'androidx.security.crypto', 'KeyProperties.PURPOSE_SIGN'):
            self.assertNotIn(forbidden, vault)
        self.assertNotIn('.putString(ENTRY, password)', vault)
        self.assertNotIn('putString(ENTRY, email)', vault)

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
