package app.enotone.playback

import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class WebDavMedia3DataSourceFactoryTest {
    private fun id(n: Int) = n.toString(16).padStart(64, '0')
    private fun track(n: Int) = QueueTrack(id(n), "Fixture $n", "/AllMusic/$n.mp3", 1_024L, null)

    @Test fun evictedRoutesLiveThroughMediaPeriodReleaseAndDoNotAccumulate() {
        val initial = (1..100).map(::track)
        val factory = WebDavMedia3DataSource.Factory("test@example.invalid", "fixture-password", initial)
        factory.addTracks(listOf(track(1))) // Re-adding the same route is safe and idempotent.
        assertEquals(100, factory.routeCount)

        val period = anyProxy(MediaPeriod::class.java)
        val delegate = mediaSource(period)
        val tracked = factory.trackMediaSource(id(1), delegate)
        val created = tracked.createPeriod(
            MediaSource.MediaPeriodId("period-1"),
            anyProxy(Allocator::class.java),
            0L,
        )
        assertEquals(100, factory.routeCount)

        factory.addTracks(listOf(track(101)))
        factory.setActiveTrackIds((2..100).map(::id) + id(101))
        assertEquals("an evicted route remains while its MediaPeriod is alive", 101, factory.routeCount)
        factory.addTracks(listOf(track(1)))
        assertEquals("the same live route can be re-added", 101, factory.routeCount)
        tracked.releasePeriod(created)
        assertEquals(100, factory.routeCount)

        var active = ((2..100).map(::id) + id(101)).toMutableList()
        repeat(1_000) { offset ->
            val next = track(102 + offset)
            factory.addTracks(listOf(next))
            active = (active.drop(1) + next.id).toMutableList()
            factory.setActiveTrackIds(active)
            assertEquals("routes stay bounded by the active queue", 100, factory.routeCount)
        }
    }

    @Test fun truncatedRoutePeriodIsIgnoredWithoutChangingOtherReferences() {
        val initial = (1..100).map(::track)
        val factory = WebDavMedia3DataSource.Factory("test@example.invalid", "fixture-password", initial)
        val allocator = anyProxy(Allocator::class.java)
        val truncated = factory.trackMediaSource(id(1), mediaSource(anyProxy(MediaPeriod::class.java)))

        factory.setActiveTrackIds((2..100).map(::id))
        assertEquals(99, factory.routeCount)
        val uncountedPeriod = truncated.createPeriod(
            MediaSource.MediaPeriodId("truncated"), allocator, 0L,
        )
        assertEquals("a truncated route is not retained", 99, factory.routeCount)

        factory.addTracks(listOf(track(1)))
        val retained = factory.trackMediaSource(id(1), mediaSource(anyProxy(MediaPeriod::class.java)))
        val countedPeriod = retained.createPeriod(
            MediaSource.MediaPeriodId("retained"), allocator, 0L,
        )
        truncated.releasePeriod(uncountedPeriod)
        factory.setActiveTrackIds((2..100).map(::id))
        assertEquals("releasing an uncounted period preserves another period's reference", 100, factory.routeCount)

        retained.releasePeriod(countedPeriod)
        assertEquals(99, factory.routeCount)
    }

    private fun mediaSource(period: MediaPeriod): MediaSource = Proxy.newProxyInstance(
        MediaSource::class.java.classLoader,
        arrayOf(MediaSource::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "createPeriod" -> period
            else -> null
        }
    } as MediaSource

    private fun <T> anyProxy(type: Class<T>): T = Proxy.newProxyInstance(
        type.classLoader,
        arrayOf(type),
    ) { _, method, _ ->
        when (method.returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Void.TYPE -> null
            else -> null
        }
    } as T
}
