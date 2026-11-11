package app.enotone.android

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max

/** Album colors from the already decoded bitmap: no network and no new files.
 *
 * A hue-weighted histogram avoids the old algorithm's habit of selecting
 * almost-black and nearly-white pixels as the "dominant album colors".
 * We return muted but *chromatic* swatches for the two broad soft spotlights;
 * the caller supplies a darkening overlay for white foreground contrast.
 */
internal fun artworkColors(artwork: Bitmap?): Pair<Color, Color> {
    val fallback = Color(0xFF535358) to Color(0xFF36363B)
    if (artwork == null || artwork.isRecycled) return fallback
    return runCatching {
        val tiny = Bitmap.createScaledBitmap(artwork, 32, 32, true)
        data class HueBucket(
            var score: Float = 0f,
            var sumS: Float = 0f,
            var sumV: Float = 0f,
            var sumHueX: Float = 0f,
            var sumHueY: Float = 0f,
        )
        val bins = Array(24) { HueBucket() }
        val hsv = FloatArray(3)
        for (y in 0 until tiny.height) for (x in 0 until tiny.width) {
            val pixel = tiny.getPixel(x, y)
            if (AndroidColor.alpha(pixel) < 180) continue
            AndroidColor.colorToHSV(pixel, hsv)
            val hue = hsv[0]
            val saturation = hsv[1]
            val value = hsv[2]
            // Black, gray and near-white areas may occupy most of a cover,
            // but provide poor gradients when used alone.
            if (value < .13f || value > .975f || saturation < .17f) continue
            val index = ((hue / 15f).toInt()).coerceIn(0, 23)
            val weight = (.25f + saturation * 1.30f) *
                (.35f + value * .70f)
            val b = bins[index]
            b.score += weight
            b.sumS += saturation * weight
            b.sumV += value * weight
            val angle = Math.toRadians(hue.toDouble())
            b.sumHueX += kotlin.math.cos(angle).toFloat() * weight
            b.sumHueY += kotlin.math.sin(angle).toFloat() * weight
        }
        val ranked = bins.indices.sortedByDescending { bins[it].score }
            .filter { bins[it].score > 1f }
        if (ranked.isEmpty()) return@runCatching fallback
        val first = ranked.first()
        fun hueDistance(a: Int, b: Int): Int {
            val d = abs(a - b)
            return minOf(d, 24 - d)
        }
        val second = ranked.firstOrNull {
            it != first && hueDistance(first, it) >= 3 &&
                bins[it].score > bins[first].score * .10f
        } ?: ranked.firstOrNull { it != first } ?: first
        fun color(index: Int, light: Boolean): Color {
            val b = bins[index]
            val hue = (
                Math.toDegrees(
                    kotlin.math.atan2(b.sumHueY.toDouble(), b.sumHueX.toDouble()),
                ).toFloat() + 360f
            ) % 360f
            val s = (b.sumS / max(b.score, .0001f)).coerceIn(.24f, .92f)
            val v = (b.sumV / max(b.score, .0001f)).coerceIn(.28f, .90f)
            // Saturation survives dark backgrounds; luminance remains low
            // enough to keep white captions, controls and system icons clear.
            val outS = (s * .74f).coerceIn(.38f, .72f)
            val outV = if (light) (.43f + v * .20f).coerceIn(.49f, .64f)
                else (.25f + v * .21f).coerceIn(.31f, .46f)
            return Color(AndroidColor.HSVToColor(floatArrayOf(hue, outS, outV)))
        }
        color(first, true) to color(second, false)
    }.getOrDefault(fallback)
}
