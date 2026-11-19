package app.enotone.android

import android.content.Context

/**
 * Non-personal onboarding flags share the no-backup account-state preferences with SignOutMarker.
 * The credential vault remains a separate encrypted store.
 */
internal class FirstRunPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        ACCOUNT_STATE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun firstRunCompleted(): Boolean? =
        if (preferences.contains(FIRST_RUN_COMPLETED)) preferences.getBoolean(FIRST_RUN_COMPLETED, false)
        else null

    fun setFirstRunCompleted(value: Boolean) {
        check(preferences.edit().putBoolean(FIRST_RUN_COMPLETED, value).commit()) {
            "Unable to persist first-run state"
        }
    }

    fun reauthenticationRequired(): Boolean = preferences.getBoolean(REAUTHENTICATION_REQUIRED, false)

    fun setReauthenticationRequired(value: Boolean) {
        check(preferences.edit().putBoolean(REAUTHENTICATION_REQUIRED, value).commit()) {
            "Unable to persist account verification state"
        }
    }

    fun resetAfterSignOut() {
        setFirstRunCompleted(false)
        setReauthenticationRequired(false)
    }

    private companion object {
        const val FIRST_RUN_COMPLETED = "firstRunCompleted"
        const val REAUTHENTICATION_REQUIRED = "reauthentication_required"
    }
}

/** Shared writer used by both first-run actions so the route changes only after durable storage. */
internal fun completeFirstRun(preferences: FirstRunPreferences, onCompleted: (Boolean) -> Unit) {
    preferences.setFirstRunCompleted(true)
    onCompleted(true)
}

internal const val ACCOUNT_STATE_PREFERENCES = "enotone_account_state"
