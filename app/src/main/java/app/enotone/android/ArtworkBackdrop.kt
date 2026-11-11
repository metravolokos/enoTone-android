package app.enotone.android

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Sample the already-loaded album bitmap in memory, never request another
 * artwork file. The small quantized palette is stable and inexpensive.
 */
internal fun artworkColors(artwork: Bitmap?): Pair<Color, Color> {
    if (artwork == null || artwork.isRecycled) {
        return Color(0xFF535358) to Color(0xFF36363B)
    }
    return runCatching {
        val tiny = Bitmap.createScaledBitmap(artwork, 20, 20, true)
        val buckets = mutableMapOf<Int, Int>()
        for (y in 0 until tiny.height) {
            for (x in 0 until tiny.width) {
                val pixel = tiny.getPixel(x, y)
                if (AndroidColor.alpha(pixel) < 200) continue
                val red = AndroidColor.red(pixel)
                val green = AndroidColor.green(pixel)
                val blue = AndroidColor.blue(pixel)
                val max = maxOf(red, green, blue)
                val min = minOf(red, green, blue)
                if (max < 32 || min > 232) continue
                val key = ((red / 32) shl 16) or
                    ((green / 32) shl 8) or (blue / 32)
                val weight = if (max - min >= 40) 3 else 1
                buckets[key] = (buckets[key] ?: 0) + weight
            }
        }
        val ordered = buckets.entries.sortedByDescending { it.value }.map { it.key }
        fun channel(key: Int, shift: Int): Int = ((key shr shift) and 7) * 32 + 16
        fun color(key: Int): Color = Color(
            channel(key, 16), channel(key, 8), channel(key, 0),
        )
        val first = ordered.firstOrNull()
        if (first == null) {
            Color(0xFF535358) to Color(0xFF36363B)
        } else {
            val second = ordered.drop(1).firstOrNull { next ->
                abs(channel(next, 16) - channel(first, 16)) +
                    abs(channel(next, 8) - channel(first, 8)) +
                    abs(channel(next, 0) - channel(first, 0)) > 110
            } ?: first
            color(first) to color(second)
        }
    }.getOrDefault(Color(0xFF535358) to Color(0xFF36363B))
}
