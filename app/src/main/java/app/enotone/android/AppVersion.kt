package app.enotone.android

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Human-readable version, e.g. "0.6.0-preview.425" (ADR-0078). Read from the
 * installed package so it always matches the APK the owner is testing; no
 * BuildConfig generation is needed.
 */
internal fun appVersionName(context: Context): String = try {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    info.versionName.orEmpty()
} catch (_: PackageManager.NameNotFoundException) {
    ""
}
