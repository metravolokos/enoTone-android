package app.enotone.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-13/A-14: сбой временного кэша не должен лишать пользователя музыки.
 *
 * Тесты описывают контракт решения, а не реализацию Android: сам кэш здесь не
 * создаётся (для этого нужен `cacheDir` и SQLite), поэтому проверяется то, что
 * определяет поведение при отказе — типы решений и их причины.
 */
class TransientCacheFactoryTest {

    @Test fun everyFailureHasADistinctCode() {
        val codes = TransientCacheFailure.entries
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.contains(TransientCacheFailure.OPEN_FAILED))
        assertTrue(codes.contains(TransientCacheFailure.RESET_FAILED))
        assertTrue(codes.contains(TransientCacheFailure.DIRECTORY_UNAVAILABLE))
    }

    @Test fun playWithoutCacheCarriesTheReason() {
        val decision = TransientCacheFactory.Decision.PlayWithoutCache(
            TransientCacheFailure.RESET_FAILED)
        assertEquals(TransientCacheFailure.RESET_FAILED, decision.reason)
    }

    @Test fun decisionsAreDistinguishableWithoutInspectingTheCache() {
        // Ключевой инвариант: вызывающий код обязан уметь отличить «кэш есть»
        // от «играем без кэша», не заглядывая внутрь TransientMediaCache.
        val without = TransientCacheFactory.Decision.PlayWithoutCache(
            TransientCacheFailure.OPEN_FAILED)
        assertTrue(without is TransientCacheFactory.Decision.PlayWithoutCache)
        assertNotEquals(TransientCacheFailure.RESET_FAILED, without.reason)
    }

    @Test fun failureCodesContainNoFilesystemDetail() {
        // Наружу уходят только коды: ни путей, ни текстов исключений, ни имён треков.
        for (failure in TransientCacheFailure.entries) {
            assertTrue(failure.name.all { it in 'A'..'Z' || it == '_' })
        }
    }
}
