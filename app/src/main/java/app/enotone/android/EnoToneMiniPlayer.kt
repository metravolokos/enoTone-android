package app.enotone.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import app.enotone.android.core.ui.component.EnoMiniPlayer
import app.enotone.android.core.ui.component.EnoMiniPlayerLabels
import app.enotone.playback.PlaybackSeekPolicy

/**
 * Мини-плеер каркаса D3: состояние плеера → stateless `EnoMiniPlayer` из :core:ui.
 * Виден только при непустой очереди; в «Настройках» и «Диагностике» его нет (O-10).
 */
@Composable
internal fun EnoToneMiniPlayer(
    player: SharedPlayerUiState,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
) {
    val playback = player.playback
    if (playback.queueTotal <= 0) return
    val state = playback.playbackState
    val playing = state == "PLAYING" || state == "BUFFERING"
    // Ошибка видна сразу в мини-плеере, а не только на «Сейчас играет».
    val subtitle = when (state) {
        "ERROR" -> stringResource(R.string.playback_error, playback.errorCode)
        "MISSING_PASSWORD" -> stringResource(R.string.playback_missing_password)
        "MISSING_TRACK" -> stringResource(R.string.playback_missing_track)
        else -> playback.artist.orEmpty()
    }
    val artwork = player.artwork
    EnoMiniPlayer(
        title = playback.title ?: stringResource(R.string.ui_songs_title),
        subtitle = subtitle,
        isPlaying = playing,
        progress = PlaybackSeekPolicy.fraction(playback.positionMs, playback.durationMs),
        labels = EnoMiniPlayerLabels(
            open = stringResource(R.string.library_player_open),
            play = stringResource(R.string.playback_resume),
            pause = stringResource(R.string.playback_pause),
            next = stringResource(R.string.playback_next),
        ),
        onOpen = onOpen,
        onPlayPause = onPlayPause,
        onNext = onNext,
        modifier = modifier,
        artwork = artwork?.let { bitmap ->
            @Composable {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        playEnabled = playback.connected && !selecting,
        nextEnabled = playback.hasNext && !selecting,
    )
}
