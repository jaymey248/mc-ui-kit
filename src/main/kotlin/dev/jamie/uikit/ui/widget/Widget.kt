package dev.jamie.uikit.ui.widget

import com.mojang.blaze3d.platform.InputConstants
import dev.jamie.uikit.ui.Canvas

/**
 * Key codes and modifier bits we use, from Minecraft's own constants: their values change between
 * releases (GLFW before 26.3, SDL3 after), so literals here would silently mean other keys.
 */
object Keys {
    const val ESCAPE = InputConstants.KEY_ESCAPE; const val ENTER = InputConstants.KEY_RETURN; const val TAB = InputConstants.KEY_TAB
    const val BACKSPACE = InputConstants.KEY_BACKSPACE; const val DELETE = InputConstants.KEY_DELETE
    const val RIGHT = InputConstants.KEY_RIGHT; const val LEFT = InputConstants.KEY_LEFT; const val DOWN = InputConstants.KEY_DOWN; const val UP = InputConstants.KEY_UP
    const val HOME = InputConstants.KEY_HOME; const val END = InputConstants.KEY_END; const val KP_ENTER = InputConstants.KEY_NUMPADENTER
    const val A = InputConstants.KEY_A; const val C = InputConstants.KEY_C; const val F = InputConstants.KEY_F; const val V = InputConstants.KEY_V
    const val X = InputConstants.KEY_X; const val Z = InputConstants.KEY_Z; const val SLASH = InputConstants.KEY_SLASH
    const val SHIFT = InputConstants.MOD_SHIFT; const val CTRL = InputConstants.MOD_CONTROL; const val ALT = InputConstants.MOD_ALT
}

/**
 * Shared per-frame UI state passed to every widget: logical mouse position, focus, the popover layer
 * and the tooltip slot. One instance per [dev.jamie.uikit.ui.screen.VsbScreen].
 */
class Ui {
    var mouseX = 0f
    var mouseY = 0f
    var focused: Widget? = null
        private set
    var popover: Popover? = null
        private set
    /** Mouse is captured by a widget (e.g. dragging a slider) until release. */
    var captured: Widget? = null

    internal var tooltipText: String? = null
    internal var tooltipOwner: Any? = null
    internal var tooltipSince = 0.0

    var screenW = 0f
    var screenH = 0f

    fun focus(w: Widget?) {
        if (focused === w) return
        focused?.onBlur()
        focused = w
        w?.onFocus()
    }

    fun openPopover(p: Popover) {
        popover?.onClose()
        popover = p
        p.open()
    }

    fun closePopover() {
        popover?.onClose()
        popover = null
    }

    /** Called by widgets during render when hovered; the screen draws it after a short delay. */
    fun tooltip(owner: Any, text: String) {
        if (text.isBlank()) return
        if (tooltipOwner !== owner) {
            tooltipOwner = owner
            tooltipSince = dev.jamie.uikit.core.Clock.now
        }
        tooltipText = text
    }

    /** True while the popover itself is drawn or handles input, so its own rows and buttons can be hovered. */
    private var inPopover = false

    /** Runs [block] as the popover (see [over]). */
    inline fun <T> asPopover(block: () -> T): T {
        enterPopover(true)
        try { return block() } finally { enterPopover(false) }
    }

    @PublishedApi internal fun enterPopover(on: Boolean) { inPopover = on }

    /** Hit-testing helper that respects the popover (things under an open popover aren't hovered, except by the popover itself). */
    fun over(x: Float, y: Float, w: Float, h: Float): Boolean {
        val p = popover
        if (!inPopover && p != null && p.contains(mouseX, mouseY)) return false
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
    }
}

/**
 * Base of every widget. Layout is width-constrained top-down: a parent calls [layout] with a position
 * and available width, the widget returns its height. Everything is in logical px.
 */
abstract class Widget {
    var x = 0f; var y = 0f; var w = 0f; var h = 0f

    open fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; this.w = width
        return h
    }

    abstract fun render(c: Canvas, ui: Ui)

    open fun mouseDown(ui: Ui, button: Int): Boolean = false
    open fun mouseUp(ui: Ui, button: Int) {}
    open fun mouseDrag(ui: Ui, button: Int) {}
    open fun scroll(ui: Ui, amount: Float): Boolean = false
    open fun key(ui: Ui, key: Int, mods: Int): Boolean = false
    open fun char(ui: Ui, cp: Int): Boolean = false
    open fun onFocus() {}
    open fun onBlur() {}

    fun contains(mx: Float, my: Float) = mx >= x && mx < x + w && my >= y && my < y + h
    fun hovered(ui: Ui) = ui.over(x, y, w, h)
}

/** Floating layer anchored near a widget (dropdown lists, colour picker). */
abstract class Popover : Widget() {
    val appear = dev.jamie.uikit.ui.Spring(0f, 520f, 0.8f)
    open fun open() {
        appear.snap(0f); appear.target = 1f
    }

    open fun onClose() {}

    /** Places the popover below (or above, if no room) the anchor rect, kept on screen. */
    fun placeNear(ui: Ui, ax: Float, ay: Float, aw: Float, ah: Float) {
        x = (ax + aw - w).coerceIn(4f, ui.screenW - w - 4f)
        y = if (ay + ah + 4f + h < ui.screenH - 4f) ay + ah + 4f else (ay - h - 4f).coerceAtLeast(4f)
    }
}
