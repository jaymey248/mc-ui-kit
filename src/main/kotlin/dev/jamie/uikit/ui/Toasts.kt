package dev.jamie.uikit.ui

import dev.jamie.uikit.core.Clock

/** Small bottom-right notifications ("Config copied"). Drawn by screens and the HUD. */
object Toasts {
    private class Toast(val text: String, val color: Int, val born: Double, val anim: Spring = Spring(0f, 320f, 0.8f))

    private val toasts = ArrayList<Toast>()
    private const val LIFE = 2.6

    fun show(text: String, color: Int = Theme.accent) {
        toasts += Toast(text, color, Clock.now).also { it.anim.target = 1f }
        if (toasts.size > 4) toasts.removeAt(0)
    }

    fun render(c: Canvas, screenW: Float, screenH: Float) {
        if (toasts.isEmpty()) return
        val now = Clock.now
        toasts.removeAll { now - it.born > LIFE + 0.4 }
        var y = screenH - 12f
        c.layer()
        for (t in toasts.asReversed()) {
            t.anim.target = if (now - t.born > LIFE) 0f else 1f
            val a = t.anim.update().coerceIn(0f, 1f)
            val w = c.textWidth(t.text, Theme.CAPTION + 0.5f, Weight.MEDIUM) + 22f
            val h = 18f
            val x = screenW - w - 12f + (1f - a) * 24f
            y -= h * a + 4f * a
            c.shadow(x, y, w, h, Theme.rMd, 12f, Colors.mulAlpha(0x70000000, a))
            c.panel(x, y, w, h, Colors.mulAlpha(Theme.surfaceHi, a), Colors.mulAlpha(Theme.borderStrong, a), Theme.rMd)
            c.circle(x + 8f, y + h / 2, 2.2f, Colors.mulAlpha(t.color, a))
            c.text(t.text, x + 14f, y + 5f, Colors.mulAlpha(Theme.text, a), Theme.CAPTION + 0.5f, Weight.MEDIUM)
        }
    }
}
