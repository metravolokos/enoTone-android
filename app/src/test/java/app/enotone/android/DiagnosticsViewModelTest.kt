package app.enotone.android

import app.enotone.database.RemoteTrackEntity
import app.enotone.network.ProbeReport
import app.enotone.network.ProbeStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private class Gateway : DiagnosticsGateway {
        var calls = 0
        var queues = 0
        var wait: CompletableDeferred<Unit>? = null
        override suspend fun inspect(login: StoredWebDavLogin, file: String, infinity: Boolean): ProbeReport {
            calls++
            return ProbeReport(listOf(
                ProbeStep("${login.email} ${login.password} $file", 207, 10, true, false,
                    "Authorization: ${login.password} $file ${login.email}"),
                ProbeStep("PROPFIND /AllMusic", 207, 12, false, true, "Multi-Status получен")))
        }
        override suspend fun run(command: DiagnosticCommand, login: StoredWebDavLogin, file: String): String {
            calls++
            wait?.await()
            return command.name
        }
        override suspend fun audit(query: AuditQuery, email: String, search: String): List<RemoteTrackEntity> = emptyList()
        override suspend fun queue(email: String, id: String?): Int { queues++; return 3 }
        override suspend fun pilot(login: StoredWebDavLogin, count: Int, progress: (String) -> Unit): String {
            progress("PROGRESS")
            wait?.await()
            return "DONE"
        }
    }

    @Test fun `fake webdav response cannot leak credentials paths or arbitrary notes`() = settingsTest {
        val session = SettingsWorkSession()
        val gateway = Gateway()
        val model = DiagnosticsViewModel(gateway, FakeBackgroundWorkLauncher(), session, testSettingsText)
        session.updateLogin(testLogin)
        advanceUntilIdle()
        model.changeFile("/AllMusic/private-track.flac")
        model.inspect()
        advanceUntilIdle()
        val report = model.state.value.report
        assertFalse(report.contains(testLogin.email))
        assertFalse(report.contains(testLogin.password))
        assertFalse(report.contains("/AllMusic/private-track.flac"))
        assertFalse(report.contains("Authorization"))
        assertTrue(report.contains("HTTP 207"))
        assertTrue(report.contains("PROPFIND /AllMusic"))
        assertTrue(report.contains("Multi-Status получен"))
    }

    @Test fun `shared busy gate rejects overlapping tools and no login prevents probe`() = settingsTest {
        val session = SettingsWorkSession()
        val gateway = Gateway()
        val model = DiagnosticsViewModel(gateway, FakeBackgroundWorkLauncher(), session, testSettingsText)
        model.inspect()
        advanceUntilIdle()
        assertEquals(0, gateway.calls)
        session.updateLogin(testLogin)
        advanceUntilIdle()
        gateway.wait = CompletableDeferred()
        model.run(DiagnosticCommand.COVERAGE)
        runCurrent()
        model.inspect()
        assertEquals(1, gateway.calls)
        gateway.wait?.complete(Unit)
        advanceUntilIdle()
        assertEquals("COVERAGE", model.state.value.report)
        assertFalse(model.state.value.busy)
    }

    @Test fun `audit queue requires confirmation and respects active service`() = settingsTest {
        val session = SettingsWorkSession()
        val gateway = Gateway()
        val launcher = FakeBackgroundWorkLauncher()
        val model = DiagnosticsViewModel(gateway, launcher, session, testSettingsText)
        session.updateLogin(testLogin)
        advanceUntilIdle()
        model.queueAll()
        assertEquals(0, gateway.queues)
        model.confirmRefreshAll(true)
        launcher.indexing.value = ManagedScanStatus(ManagedScanState.RUNNING)
        model.queueAll()
        assertEquals(0, gateway.queues)
        launcher.indexing.value = ManagedScanStatus(ManagedScanState.COMPLETE)
        model.queueAll()
        advanceUntilIdle()
        assertEquals(1, gateway.queues)
        assertFalse(model.state.value.confirmRefreshAll)
        model.audit(AuditQuery.MISSING_ALBUM)
        advanceUntilIdle()
        assertEquals(testSettingsText.get(R.string.tag_audit_no_missing), model.state.value.message)
    }

    @Test fun `pilot cancellation releases busy gate and sign out clears private input`() = settingsTest {
        val session = SettingsWorkSession()
        val gateway = Gateway()
        val model = DiagnosticsViewModel(gateway, FakeBackgroundWorkLauncher(), session, testSettingsText)
        session.updateLogin(testLogin)
        advanceUntilIdle()
        gateway.wait = CompletableDeferred()
        model.pilot(10)
        runCurrent()
        assertTrue(model.state.value.pilotRunning)
        model.cancelPilot()
        advanceUntilIdle()
        assertFalse(model.state.value.busy)
        assertFalse(model.state.value.pilotRunning)
        model.changeSearch("private track")
        model.changeFile("/AllMusic/private.flac")
        session.updateLogin(null)
        advanceUntilIdle()
        assertEquals("", model.state.value.search)
        assertEquals("", model.state.value.file)
        assertEquals("", model.state.value.report)
    }
}
