package app.enotone.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-1 (ревью PR #160): режим кэша фиксируется один раз при сборке очереди.
 *
 * До исправления `cacheEnabledForQueue` выставлялся по запросу пользователя, а не
 * по фактической готовности кэша. Очередь, собранная до готовности
 * `TransientMediaCache`, оставляла флаг включённым: `prefetchCurrent`/
 * `prefetchNext` срабатывали позже, когда кэш появлялся, но `activeRoutes` и
 * `upstreamFactory` этой очереди были пусты — предзагрузка молча не работала.
 *
 * Этот тест — контракт решения: очередь без кэша не включает предзагрузку, даже
 * если кэш станет доступен позже. Проверяется чистая функция, которой пользуются
 * оба пути (`playQueue` и `restoreQueue`); поведение службы покрыто
 * `CacheModeServiceTest`.
 */
class CacheModeDecisionTest {

    @Test fun requestedAndAvailableEnablesCache() {
        assertTrue(CacheModeDecision.cacheEnabledForQueue(requested = true, cacheAvailable = true))
    }

    @Test fun requestedButNotReadyDisablesCacheForThisQueue() {
        // Главный случай R-1: кэш ещё готовится в фоне — очередь собирается без него.
        assertFalse(CacheModeDecision.cacheEnabledForQueue(requested = true, cacheAvailable = false))
    }

    @Test fun notRequestedDisablesCacheEvenWhenReady() {
        // Пользователь выключил кэш: готовый кэш не включает предзагрузку сам.
        assertFalse(CacheModeDecision.cacheEnabledForQueue(requested = false, cacheAvailable = true))
    }

    @Test fun neitherRequestedNorReadyStaysDisabled() {
        assertFalse(CacheModeDecision.cacheEnabledForQueue(requested = false, cacheAvailable = false))
    }

    @Test fun decisionIsStableAndDoesNotDependOnLaterReadiness() {
        // Решение — чистая функция от двух входов: «кэш появился позже» не может
        // его изменить, потому что повторный вызов с теми же входами даёт тот же ответ.
        val first = CacheModeDecision.cacheEnabledForQueue(requested = true, cacheAvailable = false)
        val again = CacheModeDecision.cacheEnabledForQueue(requested = true, cacheAvailable = false)
        assertFalse(first)
        assertFalse(again)
    }
}
