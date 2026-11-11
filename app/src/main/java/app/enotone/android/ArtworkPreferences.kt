package app.enotone.android

import android.content.Context

/** Separate from tag indexing; default preserves the existing lazy behavior. */
internal enum class ArtworkLoadMode { ON_DEMAND, AUTO_AFTER_IMPORT }

internal class ArtworkPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "enotone_artwork_preferences_v1", Context.MODE_PRIVATE)

    fun mode(): ArtworkLoadMode =
        if (preferences.getString("load_mode", null) ==
            ArtworkLoadMode.AUTO_AFTER_IMPORT.name) ArtworkLoadMode.AUTO_AFTER_IMPORT
        else ArtworkLoadMode.ON_DEMAND

    fun setMode(value: ArtworkLoadMode) {
        check(preferences.edit().putString("load_mode", value.name).commit()) {
            "Unable to store artwork mode"
        }
    }
}
