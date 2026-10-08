package com.chernuga.spge.wear

import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Colour helpers shared by the schedule UI.
 *
 * <p>Lesson colours arrive as hex strings from the web app. They are tuned for
 * a dark background there, so on a light background they are brightened to keep
 * contrast against the card, mirroring the web app's own adjustment.
 */
object Palette {

    val Background = ComposeColor(0xFF07090F)
    val CardBase = ComposeColor(0xFF141821)
    val TextPrimary = ComposeColor(0xFFF2F5FA)
    val TextMuted = ComposeColor(0xFF97A1B2)
    val Accent = ComposeColor(0xFF60A5FA)
    val Current = ComposeColor(0xFFFBBF24)

    /**
     * Parses {@code #rgb}, {@code #rrggbb} or {@code #aarrggbb}.
     *
     * <p>Results are memoised: the colour maths below runs for every visible
     * card, and during a scroll that is many calls per frame for a handful of
     * distinct colours.
     */
    private val cache = HashMap<String, ComposeColor>()

    fun parse(hex: String?): ComposeColor {
        if (hex.isNullOrBlank()) return Fallback
        cache[hex]?.let { return it }
        val c = try {
            val parsed = Color.parseColor(if (hex.startsWith("#")) hex else "#$hex")
            ComposeColor(parsed)
        } catch (e: IllegalArgumentException) {
            Fallback
        }
        cache[hex] = c
        return c
    }

    /**
     * Raises very dark lesson colours so they stay visible as a border accent
     * on the watch's small, dark screen. Memoised for the same reason as
     * [parse].
     */
    private val readableCache = HashMap<String, ComposeColor>()

    fun readable(hex: String?): ComposeColor {
        if (hex.isNullOrBlank()) return Fallback
        readableCache[hex]?.let { return it }

        val c = parse(hex)
        val luminance = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        val out = if (luminance >= 0.35f) {
            c
        } else {
            val t = (0.35f - luminance) / (1f - luminance).coerceAtLeast(0.0001f)
            ComposeColor(
                red = c.red + (1f - c.red) * t,
                green = c.green + (1f - c.green) * t,
                blue = c.blue + (1f - c.blue) * t,
                alpha = c.alpha
            )
        }
        readableCache[hex] = out
        return out
    }

    private val Fallback = ComposeColor(0xFF888888)
}
