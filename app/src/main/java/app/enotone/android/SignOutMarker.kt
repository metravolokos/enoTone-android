package app.enotone.android

import android.content.Context

/**
 * Флаг «выход начат и не завершён» без личных данных. Переживает гибель процесса:
 * после перезапуска сохранённый вход не используется для медиатеки, плеера и фоновых работ,
 * пока пользователь не повторит «Выйти» (ADR-0080, внешний аудит 2026-11-13, п. 8).
 */
internal object SignOutMarker {
    private const val PREFS = "enotone_account_state"
    private const val PENDING = "sign_out_pending"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pending(context: Context): Boolean = prefs(context).getBoolean(PENDING, false)
    fun begin(context: Context) { prefs(context).edit().putBoolean(PENDING, true).commit() }
    fun finish(context: Context) { prefs(context).edit().remove(PENDING).commit() }
}
