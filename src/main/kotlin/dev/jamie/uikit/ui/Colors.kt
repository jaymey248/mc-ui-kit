package dev.jamie.uikit.ui

import dev.jamie.uikit.core.Clock
import kotlin.math.roundToInt

/** ARGB int helpers. All UI colours are ARGB ints. */
object Colors {
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(hex: Int) = hex or (0xFF shl 24)

    fun a(c: Int) = c ushr 24 and 0xFF
    fun r(c: Int) = c shr 16 and 0xFF
    fun g(c: Int) = c shr 8 and 0xFF
    fun b(c: Int) = c and 0xFF

    fun withAlpha(c: Int, alpha: Float) = (c and 0xFFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)
    fun mulAlpha(c: Int, f: Float) = (c and 0xFFFFFF) or (((a(c) * f.coerceIn(0f, 1f)).roundToInt()) shl 24)

    fun lerp(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(s: Int) = ((from shr s and 0xFF) + ((to shr s and 0xFF) - (from shr s and 0xFF)) * k).roundToInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Mixes towards white (amount > 0) or black (< 0). */
    fun shade(c: Int, amount: Float) = if (amount >= 0) lerp(c, c or 0xFFFFFF, amount) else lerp(c, c and 0xFF000000.toInt(), -amount)

    fun hsv(h: Float, s: Float, v: Float, alpha: Int = 255): Int = (java.awt.Color.HSBtoRGB(h, s, v) and 0xFFFFFF) or (alpha shl 24)

    fun toHsv(c: Int): FloatArray = java.awt.Color.RGBtoHSB(r(c), g(c), b(c), null)

    /** Rainbow cycling colour keeping [base]'s saturation/brightness/alpha. */
    fun chroma(base: Int, speed: Float = 0.25f, offset: Float = 0f): Int {
        val hsv = toHsv(base)
        val s = if (hsv[1] < 0.2f) 0.75f else hsv[1]
        val v = if (hsv[2] < 0.2f) 1f else hsv[2]
        return hsv(((Clock.now * speed + offset) % 1.0).toFloat(), s, v, a(base))
    }

    /** Minecraft § colour codes → ARGB. */
    val LEGACY = intArrayOf(
        0xFF000000.toInt(), 0xFF0000AA.toInt(), 0xFF00AA00.toInt(), 0xFF00AAAA.toInt(),
        0xFFAA0000.toInt(), 0xFFAA00AA.toInt(), 0xFFFFAA00.toInt(), 0xFFAAAAAA.toInt(),
        0xFF555555.toInt(), 0xFF5555FF.toInt(), 0xFF55FF55.toInt(), 0xFF55FFFF.toInt(),
        0xFFFF5555.toInt(), 0xFFFF55FF.toInt(), 0xFFFFFF55.toInt(), 0xFFFFFFFF.toInt(),
    )

    fun legacy(code: Char): Int? = "0123456789abcdef".indexOf(code.lowercaseChar()).takeIf { it >= 0 }?.let { LEGACY[it] }

    fun hex(c: Int, alpha: Boolean = true) = if (alpha) "%08X".format(c) else "%06X".format(c and 0xFFFFFF)
    fun parseHex(s: String): Int? {
        val t = s.removePrefix("#").trim()
        return when (t.length) {
            6 -> t.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())
            8 -> t.toLongOrNull(16)?.toInt()
            else -> null
        }
    }
}
