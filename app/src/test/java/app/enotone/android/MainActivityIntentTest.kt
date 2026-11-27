package app.enotone.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import app.enotone.playback.PlayerLaunchIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ensures notification requests are one-shot on both cold and existing activities. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityIntentTest {
    /**
     * Guards the manifest half of the #274 contract: without `singleTop` an external
     * `am start` builds a second instance instead of delivering the extra to `onNewIntent`.
     *
     * This is a configuration guard, not a dispatch test — Robolectric calls `onNewIntent`
     * directly (see the test below) and would pass either way. The dispatch path itself is
     * covered by the consumed-extra test; this one fails the moment the manifest attribute is
     * dropped, which no other test observes.
     */
    @Test fun activityDeclaresSingleTopSoExternalRequestsReachOnNewIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java), 0)
        assertEquals(
            "external OPEN_NOW_PLAYING needs one instance: onNewIntent handles the extra",
            ActivityInfo.LAUNCH_SINGLE_TOP, info.launchMode,
        )
    }

    @Test fun notificationRequestIsConsumedOnLaunchAndNewIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun request() = Intent(context, MainActivity::class.java)
            .putExtra(PlayerLaunchIntent.EXTRA_OPEN_NOW_PLAYING, true)
        val cold = request()
        val controller = Robolectric.buildActivity(MainActivity::class.java, cold).setup()
        try {
            assertFalse(cold.hasExtra(PlayerLaunchIntent.EXTRA_OPEN_NOW_PLAYING))
            val warm = request()
            controller.newIntent(warm)
            assertFalse(warm.hasExtra(PlayerLaunchIntent.EXTRA_OPEN_NOW_PLAYING))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
