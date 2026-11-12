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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private class Gateway : DiagnosticsGateway {
        var calls = 0
        var wait: CompletableDeferred<Unit>? = null
        var rows: List<RemoteTrackEntity> = emptyList()
        override suspend fun inspect(login: StoredWebDavLogin): ProbeReport {
            calls++
            wait?.await()
            return ProbeReport(listOf(
                ProbeStep("${login.email} ${login.password}", 207, 10, true, false,
                    "Authorization: ${login.password} ${login.email}"),
                ProbeStep("PROPFIND /AllMusic", 207, 12, false, true, "Multi-Status получен"),
                ProbeStep("PROPFIND Depth infinity", null, 0, false, false, "Не запрошено")))
        }
        override suspend fun audit(query: AuditQuery, email: String): List<RemoteTrackEntity> {
            calls++
            return rows
        }
    }

    private val row = RemoteTrackEntity("1", "source", "/AllMusic/a.flac", "a.flac", null, 1L, null,
        lastSeenEpoch = 0)

    @Test fun `fake webdav response cannot leak credentials or arbitrary notes and skipped steps are hidden`() =
        settingsTest {
            val session = SettingsWorkSession()
            val model = DiagnosticsViewModel(Gateway(), session, testSettingsText)
            session.updateLogin(testLogin)
            advanceUntilIdle()
            model.inspect()
            advanceUntilIdle()
            val report = model.state.value.probe
            assertFalse(report.contains(testLogin.email))
            assertFalse(report.contains(testLogin.password))
            assertFalse(report.contains("Authorization"))
            assertFalse(report.contains("Depth infinity"))
            assertTrue(report.contains("HTTP 207"))
            assertTrue(report.contains("PROPFIND /AllMusic"))
            assertTrue(report.contains("Multi-Status получен"))
            assertEquals("", session.state.value.report)
        }

    @Test fun `shared busy gate rejects overlapping work and no login prevents probe`() = settingsTest {
        val session = SettingsWorkSession()
        val gateway = Gateway()
        val model = DiagnosticsViewModel(gateway, session, testSettingsText)
        model.inspect()
        advanceUntilIdle()
        assertEquals(0, gateway.calls)
        assertEquals(testSettingsText.get(R.string.missing_credentials), model.state.value.message)
        session.updateLogin(testLogin)
        advanceUntilIdle()
        gateway.wait = CompletableDeferred()
        model.inspect()
        runCurrent()
        model.audit(AuditQuery.MISSING_ALBUM)
        assertEquals(1, gateway.calls)
        gateway.wait?.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.state.value.busy)
    }

    @Test fun `audit is view only and reports empty result`() = settingsTest {
        val session = SettingsWorkSession()
        val gateway = Gateway()
        val model = DiagnosticsViewModel(gateway, session, testSettingsText)
        session.updateLogin(testLogin)
        advanceUntilIdle()
        model.audit(AuditQuery.MISSING_ALBUM)
        advanceUntilIdle()
        assertEquals(testSettingsText.get(R.string.tag_audit_no_missing), model.state.value.message)
        gateway.rows = listOf(row)
        model.audit(AuditQuery.MISSING_ALBUM_ARTIST)
        advanceUntilIdle()
        assertEquals(AuditQuery.MISSING_ALBUM_ARTIST, model.state.value.auditQuery)
        assertEquals(listOf(row), model.state.value.rows)
        assertEquals("", model.state.value.message)
    }

    @Test fun `sign out clears private rows and report`() = settingsTest {
        val session = SettingsWorkSession()
        val gateway = Gateway().apply { rows = listOf(row) }
        val model = DiagnosticsViewModel(gateway, session, testSettingsText)
        session.updateLogin(testLogin)
        advanceUntilIdle()
        model.inspect()
        model.audit(AuditQuery.MISSING_ALBUM)
        advanceUntilIdle()
        session.updateLogin(null)
        advanceUntilIdle()
        assertEquals("", model.state.value.probe)
        assertTrue(model.state.value.rows.isEmpty())
        assertNull(model.state.value.auditQuery)
    }
}
