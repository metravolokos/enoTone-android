package app.enotone.android

import android.content.Context

/**
 * Флаг «выход начат и не завершён» без личных данных. Переживает гибель процесса:
 * после перезапуска сохранённый вход не используется для медиатеки, плеера и фоновых работ,
 * пока пользователь не повторит «Выйти» (ADR-0080, внешний аудит 2026-11-13, п. 8).
 */
internal object SignOutMarker {
    private const val PENDING = "sign_out_pending"
    private const val CLEANUP_CONFIRMED = "sign_out_cleanup_confirmed"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(ACCOUNT_STATE_PREFERENCES, Context.MODE_PRIVATE)

    fun pending(context: Context): Boolean = prefs(context).getBoolean(PENDING, false)

    /** Durable proof that Room, artwork and checkpoints completed before vault cleanup. */
    fun cleanupConfirmed(context: Context): Boolean =
        pending(context) && prefs(context).getBoolean(CLEANUP_CONFIRMED, false)

    fun begin(context: Context) {
        check(prefs(context).edit()
            .putBoolean(PENDING, true)
            .remove(CLEANUP_CONFIRMED)
            .commit()) { "Unable to persist sign-out marker" }
    }

    fun markCleanupComplete(context: Context) {
        check(prefs(context).edit().putBoolean(CLEANUP_CONFIRMED, true).commit()) {
            "Unable to persist sign-out cleanup state"
        }
    }

    fun finish(context: Context) {
        check(prefs(context).edit()
            .remove(PENDING)
            .remove(CLEANUP_CONFIRMED)
            .commit()) { "Unable to clear sign-out marker" }
    }
}
