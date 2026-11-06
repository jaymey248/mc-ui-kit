package dev.jamie.uikit.ui.hud

import dev.jamie.uikit.config.SettingHost
import dev.jamie.uikit.ui.Canvas
import dev.jamie.uikit.ui.Colors
import dev.jamie.uikit.ui.Theme
import dev.jamie.uikit.ui.Weight

/** 9-point anchor. Both the screen point and the element's own point use the same fractions. */
enum class Anchor(val fx: Float, val fy: Float) {
    TOP_LEFT(0f, 0f), TOP(0.5f, 0f), TOP_RIGHT(1f, 0f),
    LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

    companion object {
        fun nearest(fx: Float, fy: Float): Anchor {
            val col = if (fx < 1f / 3) 0 else if (fx < 2f / 3) 1 else 2
            val row = if (fy < 1f / 3) 0 else if (fy < 2f / 3) 1 else 2
            return entries[row * 3 + col]
        }
    }
}

data class Size(val w: Float, val h: Float)

/**
 * Something drawn on the HUD that the user can move and scale in the HUD editor.
 *
 * Positions are stored as anchor + offset (GUI px) + scale, so layouts survive resolution and GUI-scale
 * changes. Subclasses implement [draw] at local origin (0,0) and return the size they used; [preview]
 * is true in the editor so elements can show example content when they have nothing live to show.
 *
 * A mod says when an element counts by overriding [available] and [activeNow]; the kit knows nothing
 * of the mod's features or worlds. Register with [HudManager.register], open [dev.jamie.uikit.ui.screen.HudEditorScreen].
 */
abstract class HudElement(
    val id: String,
    val name: String,
    defaultAnchor: Anchor = Anchor.TOP_LEFT,
    defaultX: Float = 10f,
    defaultY: Float = 10f,
    defaultScale: Float = 1f,
) : SettingHost() {
    // Persisted placement — hidden from the settings UI; edited only in the HUD editor.
    internal val anchorSetting = choice("Anchor", defaultAnchor, key = "anchor").visibleWhen { false }
    internal val xSetting = slider("X", defaultX, -10000f, 10000f, 0f, key = "x").visibleWhen { false }
    internal val ySetting = slider("Y", defaultY, -10000f, 10000f, 0f, key = "y").visibleWhen { false }
    internal val scaleSetting = slider("Scale", defaultScale, 0.3f, 4f, 0.01f, key = "scale").visibleWhen { false }
    private val placedSetting = toggle("Placed", false, key = "placed").visibleWhen { false }

    /** Still at its default spot and never placed by the user or [HudManager.settle]. */
    val unplaced get() = !placedSetting.value && anchorSetting.isDefault && xSetting.isDefault && ySetting.isDefault

    /** Draw the themed panel behind the content. Users can turn it off per element. */
    val background = toggle("Background", true).desc("Rounded panel behind this element")

    var anchor: Anchor
        get() = anchorSetting.value
        set(v) { anchorSetting.value = v }
    var offsetX: Float
        get() = xSetting.value
        set(v) { xSetting.value = v }
    var offsetY: Float
        get() = ySetting.value
        set(v) { ySetting.value = v }
    var scale: Float
        get() = scaleSetting.value
        set(v) { scaleSetting.value = v }

    /** Size from the last frame (unscaled). Used for anchoring and the editor's hitbox. */
    var lastSize = Size(60f, 14f)
        internal set

    /** Switched on at all, whatever is going on in the game (its feature's toggle, say). Off: never drawn, and not in the editor. */
    open val available: Boolean get() = true

    /** Should show right now (the player is where it works, say). Not active: the editor greys it and hides it behind "Show inactive". */
    open val activeNow: Boolean get() = true

    /** Should this render in normal gameplay? */
    open fun shouldRender(): Boolean = available && activeNow

    abstract fun draw(c: Canvas, preview: Boolean): Size

    // ── geometry in GUI space ──
    fun screenX(guiW: Int) = guiW * anchor.fx + offsetX - lastSize.w * scale * anchor.fx
    fun screenY(guiH: Int) = guiH * anchor.fy + offsetY - lastSize.h * scale * anchor.fy

    /** Moves to the absolute top-left [x],[y] keeping the visual position, re-anchoring to the nearest side. */
    fun placeAt(x: Float, y: Float, guiW: Int, guiH: Int, reanchor: Boolean = true) {
        val w = lastSize.w * scale; val h = lastSize.h * scale
        if (reanchor) anchor = Anchor.nearest((x + w / 2) / guiW, (y + h / 2) / guiH)
        offsetX = x - guiW * anchor.fx + w * anchor.fx
        offsetY = y - guiH * anchor.fy + h * anchor.fy
        placedSetting.value = true
    }

    fun resetPosition() {
        anchorSetting.reset(); xSetting.reset(); ySetting.reset(); scaleSetting.reset(); placedSetting.reset()
    }

    companion object {
        const val PAD_X = 5f
        const val PAD_Y = 4f

        /** The shared HUD panel look. Every element uses this so the HUD is visually uniform. */
        fun panel(c: Canvas, w: Float, h: Float, enabled: Boolean) {
            if (!enabled || !Theme.hudBackground.value) return
            c.rect(0f, 0f, w, h, Colors.withAlpha(Theme.bg, Theme.hudOpacity.value), Theme.radius(5f))
            c.outline(0f, 0f, w, h, Colors.withAlpha(Theme.text, 0.06f * Theme.hudOpacity.value / 0.55f), Theme.radius(5f))
        }
    }
}

/** A HUD element from a lambda, for anything drawn by hand: [draw] gets the canvas at (0,0) and whether it is a preview. */
open class DrawHud(
    id: String,
    name: String,
    anchor: Anchor = Anchor.TOP_LEFT, x: Float = 10f, y: Float = 10f,
    private val body: (Canvas, Boolean) -> Size,
) : HudElement(id, name, anchor, x, y) {
    override fun draw(c: Canvas, preview: Boolean): Size = body(c, preview)
}

/**
 * The most common HUD element: a titled list of text lines (§ codes allowed).
 * ```
 * hud(TextHud("score", "Score") { listOf("§aScore: §f${score}") })
 * ```
 */
open class TextHud(
    id: String,
    name: String,
    anchor: Anchor = Anchor.TOP_LEFT, x: Float = 10f, y: Float = 10f,
    val title: String? = null,
    val preview: () -> List<String> = { listOf("§7$name") },
    val lines: () -> List<String>,
) : HudElement(id, name, anchor, x, y) {
    val align = choice("Text align", TextAlign.LEFT)

    enum class TextAlign { LEFT, CENTER, RIGHT }

    override fun draw(c: Canvas, preview: Boolean): Size {
        val content = lines().ifEmpty { if (preview) preview() else emptyList() }
        if (content.isEmpty()) return Size(0f, 0f)
        val size = Theme.BODY
        val lh = c.lineHeight(size)
        val titleH = if (title != null) lh + 1f else 0f
        var w = content.maxOf { HudText.width(c, it, size) }
        if (title != null) w = maxOf(w, HudText.width(c, title, size, Weight.SEMIBOLD))
        val totalW = w + PAD_X * 2
        val totalH = content.size * lh + titleH + PAD_Y * 2 - (lh - size)
        panel(c, totalW, totalH, background.value)
        var y = PAD_Y
        if (title != null) {
            HudText.draw(c, title, PAD_X, y, Theme.accent, size, Weight.SEMIBOLD)
            y += titleH
        }
        for (line in content) {
            val lw = HudText.width(c, line, size)
            val x = when (align.value) {
                TextAlign.LEFT -> PAD_X
                TextAlign.CENTER -> (totalW - lw) / 2
                TextAlign.RIGHT -> totalW - PAD_X - lw
            }
            HudText.draw(c, line, x, y, Theme.text, size)
            y += lh
        }
        return Size(totalW, totalH)
    }
}

/** HUD text honouring the user's HUD font + shadow preferences. */
object HudText {
    fun width(c: Canvas, s: String, size: Float, weight: Weight = Weight.REGULAR): Float =
        if (Theme.hudFont.value == Theme.HudFont.MINECRAFT) dev.jamie.uikit.core.mc.font.width(s).toFloat() * size / 8.5f
        else c.textWidth(s, size, weight)

    fun draw(c: Canvas, s: String, x: Float, y: Float, color: Int, size: Float, weight: Weight = Weight.REGULAR) {
        if (Theme.hudFont.value == Theme.HudFont.MINECRAFT) {
            c.push {
                c.translate(x, y); c.scale(size / 8.5f)
                c.g.text(dev.jamie.uikit.core.mc.font, s, 0, 0, color, Theme.hudTextShadow.value)
            }
        } else c.text(s, x, y, color, size, weight, shadow = Theme.hudTextShadow.value)
    }
}
