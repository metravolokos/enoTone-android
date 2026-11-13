package app.enotone.android

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import app.enotone.playback.PlayerLaunchIntent
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
