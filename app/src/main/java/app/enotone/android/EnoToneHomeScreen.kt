package app.enotone.android

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

/** Строки и значок настроек «Домой» принадлежат :app; экран живёт в :feature:library (ADR-0083). */
private val homeScreenIds = HomeScreenIds(
    navHome = R.string.nav_home,
    icActionSettings = R.drawable.ic_action_settings,
    settingsTitle = R.string.settings_title,
    homeConnectAccount = R.string.home_connect_account,
    homeShuffleAll = R.string.home_shuffle_all,
    homeSongCount = R.plurals.home_song_count,
)

@Composable
internal fun EnoToneHomeScreen(
    connectedAccount: Boolean,
    songCount: Int?,
    onSettings: () -> Unit,
    onShuffleAll: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    shuffleEnabled: Boolean = true,
) {
    FeatureEnoToneHomeScreen(
        connectedAccount = connectedAccount,
        songCount = songCount,
        onSettings = onSettings,
        onShuffleAll = onShuffleAll,
        ids = homeScreenIds,
        contentPadding = contentPadding,
        shuffleEnabled = shuffleEnabled,
    )
}
