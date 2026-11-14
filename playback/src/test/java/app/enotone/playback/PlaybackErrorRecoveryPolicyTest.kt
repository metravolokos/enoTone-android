package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Владелец 2026-11-15: Play после ошибки идёт к следующему треку. Эти тесты
 * фиксируют решение вместе с граничными случаями, из которых иначе получился бы
 * новый тупик: последний трек очереди и включённый повтор одного.
 */
class PlaybackErrorRecoveryPolicyTest {

    @Test fun withoutErrorPlayResumesTheSameTrack() {
        assertEquals(
            PlaybackErrorAction.PLAY,
            PlaybackErrorRecoveryPolicy.actionFor(
                hasError = false, hasNext = true, repeatOne = false),
        )
    }

    @Test fun withoutErrorAndLastTrackStillResumes() {
        // Ошибки нет — даже на последнем треке Play не должен ничего переключать.
        assertEquals(
            PlaybackErrorAction.PLAY,
            PlaybackErrorRecoveryPolicy.actionFor(
                hasError = false, hasNext = false, repeatOne = true),
        )
    }

    @Test fun errorWithNextTrackContinuesToTheNext() {
        assertEquals(
            PlaybackErrorAction.NEXT,
            PlaybackErrorRecoveryPolicy.actionFor(
                hasError = true, hasNext = true, repeatOne = false),
        )
    }

    @Test fun errorOnLastTrackRestartsCurrentInsteadOfGettingStuck() {
        assertEquals(
            PlaybackErrorAction.RESTART_CURRENT,
            PlaybackErrorRecoveryPolicy.actionFor(
                hasError = true, hasNext = false, repeatOne = false),
        )
    }

    @Test fun repeatOneWinsOverNextTrack() {
        // Решение владельца не отменяет repeat-one: повтор одного важнее перехода.
        assertEquals(
            PlaybackErrorAction.RESTART_CURRENT,
            PlaybackErrorRecoveryPolicy.actionFor(
                hasError = true, hasNext = true, repeatOne = true),
        )
    }

    @Test fun everyErrorCaseHasAnActionThatLeavesPlay() {
        // Ни одна комбинация не должна вернуть PLAY: иначе Play останется в тупике.
        for (hasNext in listOf(true, false)) {
            for (repeatOne in listOf(true, false)) {
                val action = PlaybackErrorRecoveryPolicy.actionFor(
                    hasError = true, hasNext = hasNext, repeatOne = repeatOne)
                if (action == PlaybackErrorAction.PLAY) {
                    throw AssertionError(
                        "hasNext=$hasNext repeatOne=$repeatOne must not stay on PLAY")
                }
            }
        }
    }
}
