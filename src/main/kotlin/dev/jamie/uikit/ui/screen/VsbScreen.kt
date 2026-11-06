package dev.jamie.uikit.ui.screen

import dev.jamie.uikit.compat.Game

import dev.jamie.uikit.core.Clock
import dev.jamie.uikit.core.mc
import dev.jamie.uikit.ui.*
import dev.jamie.uikit.ui.widget.Keys
import dev.jamie.uikit.ui.widget.Ui
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Base for all kit screens. Provides:
 *  - a logical coordinate space independent of Minecraft's GUI scale ([scale])
 *  - backdrop blur + dim with open animation
 *  - a popover layer, tooltip layer and keyboard focus, routed in the right order
 * Subclasses implement [draw] and the `on*` input hooks in logical px.
 */
abstract class VsbScreen(title: String, private val parent: Screen? = null) : Screen(Component.literal(title)) {
    protected val ui = Ui()
    protected val openAnim = Spring(0f, 260f, 0.82f)
    private var lastMods = 0

    /** Logical px → GUI px. 1 logical px = 2 physical px × menu scale. */
    val scale: Float get() = 2f * Theme.uiScale.value / mc.window.guiScale.toFloat()

    open val dimBackground = true

    /** Record setting changes made through this screen for Ctrl+Z. */
    open val recordUndo = false

    private inline fun <T> recorded(block: () -> T): T {
        if (!recordUndo) return block()
        dev.jamie.uikit.config.UndoStack.recording = true
        try { return block() } finally { dev.jamie.uikit.config.UndoStack.recording = false }
    }

    override fun init() {
        if (openAnim.target == 0f) openAnim.target = 1f
    }

    override fun isPauseScreen() = false

    override fun extractBackground(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        if (!dimBackground) return
        if (Theme.blur.value && mc.level != null) g.blurBeforeThisStratum()
        val t = openAnim.value.coerceIn(0f, 1f)
        g.fillGradient(0, 0, width, height, Colors.withAlpha(Theme.bg, 0.45f * t), Colors.withAlpha(Theme.bg, 0.62f * t))
    }

    final override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        Frame.begin()
        openAnim.update()
        val c = Canvas(g)
        val s = scale
        ui.mouseX = mouseX / s
        ui.mouseY = mouseY / s
        ui.screenW = width / s
        ui.screenH = height / s
        ui.tooltipText = null
        c.push {
            c.scale(s)
            draw(c)
            ui.popover?.let { p ->
                c.layer()
                ui.asPopover { p.render(c, ui) }
            }
            drawTooltip(c)
        }
    }

    abstract fun draw(c: Canvas)

    private fun drawTooltip(c: Canvas) {
        val text = ui.tooltipText ?: return
        if (Clock.now - ui.tooltipSince < 0.35) return
        val t = ((Clock.now - ui.tooltipSince - 0.35) / 0.12).toFloat().coerceIn(0f, 1f)
        c.layer()
        val maxW = 180f
        val lines = c.wrap(text, maxW, Theme.CAPTION + 0.5f)
        val w = lines.maxOf { c.textWidth(it, Theme.CAPTION + 0.5f) } + 12f
        val h = lines.size * c.lineHeight(Theme.CAPTION + 0.5f) + 8f
        var x = ui.mouseX + 8f
        var y = ui.mouseY + 10f - (1f - t) * 4f
        if (x + w > ui.screenW - 4f) x = ui.mouseX - w - 6f
        if (y + h > ui.screenH - 4f) y = ui.mouseY - h - 6f
        c.shadow(x, y, w, h, Theme.rSm, 10f, Colors.mulAlpha(0x70000000, t))
        c.panel(x, y, w, h, Colors.mulAlpha(Theme.surfaceHi, t), Colors.mulAlpha(Theme.borderStrong, t), Theme.rSm)
        lines.forEachIndexed { i, l -> c.text(l, x + 6f, y + 4f + i * c.lineHeight(Theme.CAPTION + 0.5f), Colors.mulAlpha(Theme.text, t), Theme.CAPTION + 0.5f) }
    }

    // ── input routing: captured → popover → focused → screen ───────────────
    open fun onMouseDown(button: Int, double: Boolean): Boolean = false
    open fun onMouseUp(button: Int) {}
    open fun onMouseDrag(button: Int) {}
    open fun onScroll(amount: Float): Boolean = false
    open fun onKey(key: Int, mods: Int): Boolean = false
    open fun onChar(cp: Int): Boolean = false

    private fun updateMouse(x: Double, y: Double) {
        ui.mouseX = (x / scale).toFloat(); ui.mouseY = (y / scale).toFloat()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean = recorded { clicked(event, doubleClick) }

    private fun clicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        updateMouse(event.x(), event.y())
        val b = Game.button(event.button())
        val p = ui.popover
        if (p != null) {
            if (p.contains(ui.mouseX, ui.mouseY)) { ui.asPopover { p.mouseDown(ui, b) }; return true }
            ui.closePopover()
            ui.focus(null)
            return true
        }
        val focusedBefore = ui.focused
        val handled = onMouseDown(b, doubleClick)
        if (ui.focused === focusedBefore && focusedBefore != null && !focusedBefore.contains(ui.mouseX, ui.mouseY)) ui.focus(null)
        return handled
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        updateMouse(event.x(), event.y())
        val b = Game.button(event.button())
        ui.captured?.mouseUp(ui, b)
        ui.captured = null
        ui.popover?.let { p -> ui.asPopover { p.mouseUp(ui, b) } }
        onMouseUp(b)
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean = recorded { dragged(event) }

    private fun dragged(event: MouseButtonEvent): Boolean {
        updateMouse(event.x(), event.y())
        val b = Game.button(event.button())
        ui.captured?.let { it.mouseDrag(ui, b); return true }
        onMouseDrag(b)
        return true
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        updateMouse(x, y)
        val p = ui.popover
        if (p != null && p.contains(ui.mouseX, ui.mouseY)) return ui.asPopover { p.scroll(ui, scrollY.toFloat()) }
        return onScroll(scrollY.toFloat())
    }

    override fun keyPressed(event: KeyEvent): Boolean = recorded { pressed(event) }

    /** Set when a key button captured this key press: the character event GLFW sends next belongs to it, not to search. */
    private var swallowChar = false

    private fun pressed(event: KeyEvent): Boolean {
        val key = event.key(); val mods = event.modifiers()
        lastMods = mods
        swallowChar = false
        ui.popover?.let { p ->
            if (key == Keys.ESCAPE) { ui.closePopover(); ui.focus(null); return true }
            if (ui.asPopover { p.key(ui, key, mods) }) return true
        }
        ui.focused?.let { if (it.key(ui, key, mods)) { swallowChar = it is dev.jamie.uikit.ui.widget.KeyButton; return true } }
        if (onKey(key, mods)) return true
        if (key == Keys.ESCAPE) { onClose(); return true }
        return false
    }

    override fun charTyped(event: CharacterEvent): Boolean = recorded { typed(event) }

    private fun typed(event: CharacterEvent): Boolean {
        val cp = event.codepoint()
        if (swallowChar) { swallowChar = false; return true }
        ui.popover?.let { p -> if (ui.asPopover { p.char(ui, cp) }) return true }
        ui.focused?.let { if (it.char(ui, cp)) return true }
        return onChar(cp)
    }

    override fun onClose() {
        dev.jamie.uikit.config.Config.markDirty()
        Game.open(parent)
    }
}
