package app.enotone.android

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.QueueEditResult
import app.enotone.playback.QueueEditStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueueActionMessageTest {
    private fun text(status: QueueEditStatus, count: Int = 0, next: Boolean = true, lang: String = "ru"): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(lang))
        }
        return QueueActionMessage(next, QueueEditResult(status, count))
            .text(context.createConfigurationContext(configuration).resources)
    }

    @Test fun singleAndPluralFeedbackUsesAndroidResources() {
        assertEquals("Будет следующей", text(QueueEditStatus.APPLIED, 1))
        assertEquals("Добавлено в очередь", text(QueueEditStatus.APPLIED, 1, next = false))
        assertEquals("Добавлено: 2 песни", text(QueueEditStatus.APPLIED, 2))
        assertEquals("Добавлено: 5 песен", text(QueueEditStatus.APPLIED, 5))
        assertEquals("Добавлено: 21 песня", text(QueueEditStatus.APPLIED, 21))
        assertEquals("Added: 2 songs", text(QueueEditStatus.APPLIED, 2, lang = "en"))
    }

    @Test fun rejectedAndFailedEditsNeverClaimSuccess() {
        assertEquals("Уже играет", text(QueueEditStatus.ALREADY_PLAYING))
        assertEquals("Очередь заполнена", text(QueueEditStatus.QUEUE_FULL, 3))
        assertEquals("Плеер недоступен", text(QueueEditStatus.UNAVAILABLE))
        assertEquals("Не удалось изменить очередь", text(QueueEditStatus.FAILED))
        assertEquals("Нет песен для добавления", text(QueueEditStatus.EMPTY_SELECTION))
    }
}
