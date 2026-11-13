package app.enotone.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Нажатие на уведомление воспроизведения открывает приложение на экране «Сейчас играет».
 * Модуль воспроизведения не знает класс Activity, поэтому берёт launcher-интент пакета.
 * В интенте только флаг-признак: ни пути, ни идентификатора трека, ни учётных данных.
 */
object PlayerLaunchIntent {
    const val EXTRA_OPEN_NOW_PLAYING = "app.enotone.playback.OPEN_NOW_PLAYING"

    fun pendingIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.putExtra(EXTRA_OPEN_NOW_PLAYING, true)
        // Уже открытая Activity получает onNewIntent, а не пересоздаётся.
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
