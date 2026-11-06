package dev.jamie.uikit.core

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** Shorthand for the client instance. Everything client-side goes through this. */
val mc: Minecraft get() = Minecraft.getInstance()

/** Logs under the mod's name; available once [Kit.setup] has run. */
val log: Logger by lazy { LoggerFactory.getLogger(Kit.displayName) }

/** Runs [block], logging instead of crashing. Features must never take the game down. */
inline fun guard(what: String, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        log.error("[$what] failed", t)
    }
}

private val FORMATTING = Regex("§.")

/** Strips Minecraft § formatting codes. */
val String.clean: String get() = if (indexOf('§') < 0) this else FORMATTING.replace(this, "")

val Component.clean: String get() = string.clean

/** Monotonic seconds since an arbitrary origin, with sub-ms precision. Used by all animation. */
object Clock {
    private val origin = System.nanoTime()
    val now: Double get() = (System.nanoTime() - origin) / 1_000_000_000.0
    val millis: Long get() = System.currentTimeMillis()
}

fun Double.format(decimals: Int = 1): String = "%.${decimals}f".format(this)
fun Float.format(decimals: Int = 1): String = "%.${decimals}f".format(this)

private val SUFFIXES = arrayOf("", "k", "M", "B", "T")

/** 1234567 → "1.23M". */
fun Number.shortFormat(): String {
    var v = toDouble()
    var i = 0
    while (kotlin.math.abs(v) >= 1000 && i < SUFFIXES.lastIndex) { v /= 1000; i++ }
    return if (i == 0) "%,.0f".format(v) else "%.${if (v >= 100) 1 else 2}f${SUFFIXES[i]}".format(v)
}

/** 1234567 → "1,234,567". */
fun Number.commas(): String = "%,d".format(toLong())

/** Milliseconds → "1m 05s" / "12.3s". */
fun Long.durationFormat(): String {
    val s = this / 1000.0
    return when {
        s < 60 -> "${s.format(1)}s"
        s < 3600 -> "${(s / 60).toInt()}m ${"%02d".format((s % 60).toInt())}s"
        else -> "${(s / 3600).toInt()}h ${"%02d".format(((s % 3600) / 60).toInt())}m"
    }
}

private val LEGACY_BY_RGB: Map<Int, Char> by lazy {
    "0123456789abcdef".withIndex().associate { (i, ch) -> (dev.jamie.uikit.ui.Colors.LEGACY[i] and 0xFFFFFF) to ch }
}

/**
 * Component → legacy "§" string, keeping colours (hex colours snap to the nearest legacy colour) and
 * bold. Lets components render through the kit's SDF text, which speaks § codes.
 */
fun Component.toLegacy(): String {
    val sb = StringBuilder()
    visit({ style, text ->
        if (text.isNotEmpty()) {
            val rgb = style.color?.value
            if (rgb != null) sb.append('§').append(LEGACY_BY_RGB[rgb] ?: nearestLegacy(rgb))
            if (style.isBold) sb.append("§l")
            sb.append(text)
        }
        java.util.Optional.empty<Unit>()
    }, net.minecraft.network.chat.Style.EMPTY)
    return sb.toString()
}

private fun nearestLegacy(rgb: Int): Char = LEGACY_BY_RGB.minBy { (c, _) ->
    val dr = (c shr 16 and 255) - (rgb shr 16 and 255); val dg = (c shr 8 and 255) - (rgb shr 8 and 255); val db = (c and 255) - (rgb and 255)
    dr * dr + dg * dg + db * db
}.value
