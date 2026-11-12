package app.enotone.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {
    private class Fake : AccountSettingsGateway {
        var persisted: StoredWebDavLogin? = null
        var accepted = false
        var saves = 0
        var verifyCalls = 0
        var tracks = 12
        var exitCalls = 0
        var failExit = false
        var pending = false
        override suspend fun restore(): StoredWebDavLogin? = persisted
        override suspend fun signOutPending(): Boolean = pending
        override suspend fun verify(email: String, password: String): Boolean {
            verifyCalls++
            return accepted
        }
        override suspend fun save(email: String, password: String) {
            saves++
            persisted = StoredWebDavLogin(email, password)
        }
        override suspend fun countTracks(email: String) = tracks
        override suspend fun signOut(email: String) {
            exitCalls++
            if (failExit) throw AccountSignOutFailure(AccountSignOutFailure.Step.TRACKS)
            persisted = null
        }
    }

    @Test fun `successful verification persists and failed verification does not`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            val model = AccountSettingsViewModel(fake)
            advanceUntilIdle()
            model.onEmailChanged("example@invalid.test")
            model.onPasswordChanged("secret")
            model.verifyConnection()
            advanceUntilIdle()
            assertEquals(0, fake.saves)
            assertFalse(model.state.value.loggedIn)
            fake.accepted = true
            model.verifyConnection()
            advanceUntilIdle()
            assertEquals(1, fake.saves)
            assertTrue(model.state.value.loggedIn)
            assertEquals(1, model.state.value.verifiedVersion)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `saved login restores on startup`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            fake.persisted = StoredWebDavLogin("example@invalid.test", "secret")
            val model = AccountSettingsViewModel(fake)
            advanceUntilIdle()
            assertEquals("example@invalid.test", model.state.value.email)
            assertEquals("secret", model.state.value.password)
            assertTrue(model.state.value.loggedIn)
            assertFalse(model.state.value.restoring)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `confirmation cancel keeps data and failed sign out can retry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            fake.persisted = StoredWebDavLogin("example@invalid.test", "secret")
            val model = AccountSettingsViewModel(fake)
            advanceUntilIdle()
            model.requestSignOut()
            advanceUntilIdle()
            assertEquals(12, model.state.value.trackCount)
            model.cancelSignOut()
            assertEquals(0, fake.exitCalls)
            fake.failExit = true
            model.requestSignOut()
            advanceUntilIdle()
            model.confirmSignOut()
            advanceUntilIdle()
            assertEquals(1, fake.exitCalls)
            assertTrue(model.state.value.loggedIn)
            fake.failExit = false
            model.requestSignOut()
            advanceUntilIdle()
            model.confirmSignOut()
            advanceUntilIdle()
            assertFalse(model.state.value.loggedIn)
            assertEquals("", model.state.value.password)
            assertEquals(1, model.state.value.signedOutVersion)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `working login is only the verified one and form edits never reach it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            fake.persisted = StoredWebDavLogin("saved@invalid.test", "saved")
            val model = AccountSettingsViewModel(fake)
            // Правка во время восстановления не становится рабочим входом.
            model.onEmailChanged("typed-without-at")
            model.onPasswordChanged("typed")
            advanceUntilIdle()
            assertEquals(StoredWebDavLogin("saved@invalid.test", "saved"), model.state.value.workingLogin)
            assertEquals("saved@invalid.test", model.state.value.email)
            // После входа поля не редактируются вовсе.
            model.onEmailChanged("other@invalid.test")
            model.onPasswordChanged("wrong")
            assertEquals("saved@invalid.test", model.state.value.email)
            assertEquals(StoredWebDavLogin("saved@invalid.test", "saved"), model.state.value.workingLogin)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `unverified form never becomes working login`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            val model = AccountSettingsViewModel(fake)
            advanceUntilIdle()
            model.onEmailChanged("new@invalid.test")
            model.onPasswordChanged("secret")
            assertNull(model.state.value.workingLogin)
            model.verifyConnection()
            advanceUntilIdle()
            assertNull(model.state.value.workingLogin)
            fake.accepted = true
            model.verifyConnection()
            advanceUntilIdle()
            assertEquals(StoredWebDavLogin("new@invalid.test", "secret"), model.state.value.workingLogin)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `failed sign out blocks working and library login until retry succeeds`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            fake.persisted = StoredWebDavLogin("example@invalid.test", "secret")
            fake.failExit = true
            val model = AccountSettingsViewModel(fake)
            advanceUntilIdle()
            model.requestSignOut()
            advanceUntilIdle()
            model.confirmSignOut()
            advanceUntilIdle()
            assertTrue(model.state.value.loggedIn)
            assertTrue(model.state.value.signOutFailed)
            assertNull(model.state.value.workingLogin)
            assertNull(model.state.value.libraryLogin)
            fake.failExit = false
            model.requestSignOut()
            advanceUntilIdle()
            model.confirmSignOut()
            advanceUntilIdle()
            assertFalse(model.state.value.signOutFailed)
            assertFalse(model.state.value.loggedIn)
            assertNull(model.state.value.active)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun `pending sign out from previous process blocks restored login`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake()
            fake.persisted = StoredWebDavLogin("example@invalid.test", "secret")
            fake.pending = true
            val model = AccountSettingsViewModel(fake)
            advanceUntilIdle()
            assertTrue(model.state.value.loggedIn)
            assertTrue(model.state.value.signOutFailed)
            assertEquals(AccountError.SIGN_OUT, model.state.value.error)
            assertNull(model.state.value.workingLogin)
            model.requestSignOut()
            advanceUntilIdle()
            assertTrue(model.state.value.showConfirmation)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
