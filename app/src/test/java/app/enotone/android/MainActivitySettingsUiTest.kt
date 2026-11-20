package app.enotone.android

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreException
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.util.Collections
import java.util.Date
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Fresh-install app shell: checks that login gates regular app navigation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivitySettingsUiTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)

    private val signedInFixture = TestRule { base, description ->
        object : Statement() {
            override fun evaluate() {
                val previousProvider = installTestKeyStore()
                try {
                    clearFixture()
                    if (description.methodName == SETTINGS_TEST) {
                        FirstRunPreferences(context).setFirstRunCompleted(true)
                        LocalCredentialVault.save(context, FIXTURE_EMAIL, FIXTURE_PASSWORD)
                    }
                    base.evaluate()
                } finally {
                    clearFixture()
                    restoreKeyStoreProvider(previousProvider)
                }
            }
        }
    }

    @get:Rule val rule = RuleChain.outerRule(signedInFixture).around(composeRule)

    private fun clearFixture() {
        context.getSharedPreferences(ACCOUNT_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit().clear().commit()
        TestAndroidKeyStore.keys.clear()
        LocalCredentialVault.clear(context)
    }

    private fun installTestKeyStore(): Provider? {
        val previous = Security.getProvider(KEYSTORE_PROVIDER)
        Security.removeProvider(KEYSTORE_PROVIDER)
        Security.insertProviderAt(MainActivityTestKeyStoreProvider(), 1)
        return previous
    }

    private fun restoreKeyStoreProvider(previous: Provider?) {
        Security.removeProvider(KEYSTORE_PROVIDER)
        previous?.let { Security.insertProviderAt(it, 1) }
    }

    @Test fun freshInstallShowsLoginBeforeRegularAppNavigation() {
        composeRule.onNodeWithText(label(R.string.first_run_login_title)).assertExists()
        assertTrue(composeRule.onAllNodesWithContentDescription(label(R.string.settings_title))
            .fetchSemanticsNodes().isEmpty())
    }

    @Test fun settingsGroupsAreOrderedAndDiagnosticsHasBackNavigation() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription(label(R.string.settings_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(label(R.string.settings_title)).performClick()
        val titles = listOf(
            R.string.settings_group_cloud,
            R.string.settings_group_library,
            R.string.appearance_title,
            R.string.settings_group_app_library,
            R.string.library_pages_settings_title,
            R.string.artwork_settings_title,
            R.string.diagnostics_title,
        )
        // Off-screen coordinates can be clipped: use the rendered semantics order.
        val screenSemantics = composeRule.onRoot(useUnmergedTree = true).printToString()
        val offsets = titles.map { screenSemantics.indexOf(label(it)) }
        assertTrue("Settings groups must remain in their accepted order",
            offsets.all { it >= 0 } &&
                offsets.zipWithNext().all { (before, after) -> before < after })
        composeRule.onNodeWithContentDescription(label(R.string.settings_title)).assertExists()
        composeRule.onNodeWithText("О приложении").assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.diagnostics_title)).performScrollTo().performClick()
        composeRule.onNodeWithText(label(R.string.diagnostics_intro)).assertExists()
        composeRule.onNodeWithContentDescription(label(R.string.library_back)).performClick()
        composeRule.onNodeWithText(label(R.string.settings_group_cloud)).assertExists()
        pressBack()
        composeRule.waitForIdle()
        // D3: «Домой» C2 без заголовка «Моя музыка»; признак «Домой» — вход в «Настройки» в заголовке.
        composeRule.onNodeWithText(label(R.string.settings_group_cloud)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(label(R.string.settings_title)).assertExists()
    }

    private companion object {
        const val SETTINGS_TEST = "settingsGroupsAreOrderedAndDiagnosticsHasBackNavigation"
        const val FIXTURE_EMAIL = "settings-fixture@example.invalid"
        const val FIXTURE_PASSWORD = "settings-fixture-only"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }
}

private object TestAndroidKeyStore {
    val keys = mutableMapOf<String, SecretKey>()
}

class MainActivityTestKeyStoreProvider : Provider(
    "AndroidKeyStore",
    1.0,
    "In-memory AndroidKeyStore for Robolectric",
) {
    init {
        put("KeyStore.AndroidKeyStore", MainActivityTestKeyStoreSpi::class.java.name)
        put("KeyGenerator.AES", MainActivityTestAesKeyGenerator::class.java.name)
    }
}

class MainActivityTestKeyStoreSpi : KeyStoreSpi() {
    override fun engineGetKey(alias: String, password: CharArray?): Key? = TestAndroidKeyStore.keys[alias]
    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null
    override fun engineGetCertificate(alias: String): Certificate? = null
    override fun engineGetCreationDate(alias: String): Date? = null
    override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) {
        val secretKey = key as? SecretKey ?: throw KeyStoreException("Fixture only accepts secret keys")
        TestAndroidKeyStore.keys[alias] = secretKey
    }
    override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) {
        TestAndroidKeyStore.keys[alias] = SecretKeySpec(key, "AES")
    }
    override fun engineSetCertificateEntry(alias: String, certificate: Certificate) = Unit
    override fun engineDeleteEntry(alias: String) {
        TestAndroidKeyStore.keys.remove(alias)
    }
    override fun engineAliases() = Collections.enumeration(TestAndroidKeyStore.keys.keys)
    override fun engineContainsAlias(alias: String): Boolean = alias in TestAndroidKeyStore.keys
    override fun engineSize(): Int = TestAndroidKeyStore.keys.size
    override fun engineIsKeyEntry(alias: String): Boolean = alias in TestAndroidKeyStore.keys
    override fun engineIsCertificateEntry(alias: String): Boolean = false
    override fun engineGetCertificateAlias(certificate: Certificate): String? = null
    override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
    override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
}

class MainActivityTestAesKeyGenerator : KeyGeneratorSpi() {
    private var random = SecureRandom()

    override fun engineInit(random: SecureRandom?) {
        this.random = random ?: SecureRandom()
    }

    override fun engineInit(params: java.security.spec.AlgorithmParameterSpec?, random: SecureRandom?) {
        this.random = random ?: SecureRandom()
    }

    override fun engineInit(keysize: Int, random: SecureRandom?) {
        this.random = random ?: SecureRandom()
    }

    override fun engineGenerateKey(): SecretKey {
        val bytes = ByteArray(32).also(random::nextBytes)
        return SecretKeySpec(bytes, "AES").also {
            TestAndroidKeyStore.keys[FIXTURE_KEY_ALIAS] = it
        }
    }

    private companion object {
        const val FIXTURE_KEY_ALIAS = "enotone_webdav_credentials_v1"
    }
}
