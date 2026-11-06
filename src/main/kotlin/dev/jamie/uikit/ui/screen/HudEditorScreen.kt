package dev.jamie.uikit.ui.screen

import dev.jamie.uikit.core.mc
import dev.jamie.uikit.ui.*
import dev.jamie.uikit.ui.hud.Anchor
import dev.jamie.uikit.ui.hud.HudElement
import dev.jamie.uikit.ui.hud.HudManager
import dev.jamie.uikit.ui.widget.*
import net.minecraft.client.gui.screens.Screen
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * HUD layout editor. Elements are shown with preview content; drag to move with snapping guides,
 * scroll or drag the corner handle to scale, arrows to nudge, right-click for element settings,
 * Ctrl+Z to undo. Elements work in GUI px; the toolbar uses the logical UI scale.
 */
class HudEditorScreen(parent: Screen?, private val focus: HudElement? = null) : VsbScreen("Edit HUD", parent) {
    override val dimBackground = false

    private data class Snapshot(val e: HudElement, val anchor: Anchor, val x: Float, val y: Float, val scale: Float)

    private val undo = ArrayDeque<List<Snapshot>>()
    private var selected: HudElement? = focus
    private var dragging: HudElement? = null
    private var scalingHandle = false
    private var dragOffX = 0f; private var dragOffY = 0f
    private var scaleStartDist = 1f; private var scaleStart = 1f
    private val guidesX = ArrayList<Float>(); private val guidesY = ArrayList<Float>()
    private var snap = true
    private var showInactive = HudManager.showInactiveAtFirst()
    private val hoverAnim = HashMap<HudElement, Ease>()
    private val focusPulse = Spring(if (focus != null) 1f else 0f, 80f, 0.5f)

    private val doneButton = Button("Done", ButtonStyle.PRIMARY) { onClose() }
    private val resetButton = Button("Reset selected", ButtonStyle.SUBTLE, Icons.ROTATE_CCW) { selected?.let { pushUndo(); it.resetPosition() } }
    private val resetAll: Button = Button("Reset all", ButtonStyle.DANGER) {
        val p = ConfirmPopover("Reset every HUD element?", "Reset") { pushUndo(); visibleElements().forEach { it.resetPosition() }; Toasts.show("HUD layout reset") }
        p.placeNear(ui, resetAll.x, resetAll.y - p.h - 8f, resetAll.w, 0f)
        ui.openPopover(p)
    }
    private val snapButton = Button("Snapping", ButtonStyle.SUBTLE, Icons.MAGNET) { snap = !snap }.also { it.active = { snap } }
    private val inactiveButton = Button("Show inactive", ButtonStyle.SUBTLE, Icons.EYE) { showInactive = !showInactive }.also { it.active = { showInactive } }

    private val arrangeButton = Button("Arrange", ButtonStyle.SUBTLE, Icons.LAYOUT_DASHBOARD) { pushUndo(); arrange(); Toasts.show("Arranged ${visibleElements().size} elements") }

    /** Packs elements into non-overlapping columns along the side they're closest to. */
    private fun arrange() {
        val gw = mc.window.guiScaledWidth; val gh = mc.window.guiScaledHeight
        val all = visibleElements()
        val (centered, sided) = all.partition { it.anchor.fx == 0.5f }
        for (right in listOf(false, true)) {
            val group = sided.filter { (it.screenX(gw) + it.lastSize.w * it.scale / 2 > gw / 2f) == right }.sortedBy { it.screenY(gh) }
            var x = 4f; var y = 4f; var colW = 0f
            for (e in group) {
                val w = e.lastSize.w * e.scale; val h = e.lastSize.h * e.scale
                if (y + h > gh - 40f && y > 4f) { x += colW + 6f; y = 4f; colW = 0f }
                e.placeAt(if (right) gw - x - w else x, y, gw, gh)
                y += h + 4f; colW = maxOf(colW, w)
            }
        }
        // Centred elements: rows of columns around the screen centre, wrapping sideways when full.
        var cy = gh / 2f + 30f; var col = 0; val colW = centered.maxOfOrNull { it.lastSize.w * it.scale } ?: 0f
        for (e in centered) {
            val w = e.lastSize.w * e.scale; val h = e.lastSize.h * e.scale
            if (cy + h > gh - 40f) { col++; cy = gh / 2f + 30f }
            val offset = if (col == 0) 0f else ((col + 1) / 2) * (colW + 6f) * if (col % 2 == 1) 1f else -1f
            e.placeAt((gw - w) / 2 + offset, cy, gw, gh); cy += h + 4f
        }
    }

    private val gs get() = scale // logical → gui

    init {
        HudManager.editing = true
    }

    override fun removed() {
        HudManager.editing = false
        dev.jamie.uikit.config.Config.markDirty()
    }

    private fun visibleElements() = HudManager.all.filter { it.available && (showInactive || it.activeNow || it === focus) }

    /** The toolbar; the inactive switch only appears when something is inactive. */
    private fun toolbar() = buildList {
        add(snapButton)
        if (HudManager.all.any { it.available && !it.activeNow }) add(inactiveButton)
        add(arrangeButton); add(resetButton); add(resetAll); add(doneButton)
    }

    private fun bounds(e: HudElement): FloatArray {
        val gw = mc.window.guiScaledWidth; val gh = mc.window.guiScaledHeight
        val x = e.screenX(gw); val y = e.screenY(gh)
        return floatArrayOf(x, y, e.lastSize.w * e.scale, e.lastSize.h * e.scale)
    }

    private val mouseGx get() = ui.mouseX * gs
    private val mouseGy get() = ui.mouseY * gs

    override fun draw(c: Canvas) {
        val gw = mc.window.guiScaledWidth.toFloat(); val gh = mc.window.guiScaledHeight.toFloat()
        // Work in GUI px for elements: undo the logical scale.
        c.push {
            c.scale(1f / gs)
            c.rect(0f, 0f, gw, gh, Colors.withAlpha(Theme.bg, 0.35f))
            // centre lines (faint)
            c.rect(gw / 2 - 0.25f, 0f, 0.5f, gh, Colors.withAlpha(Theme.text, 0.05f))
            c.rect(0f, gh / 2 - 0.25f, gw, 0.5f, Colors.withAlpha(Theme.text, 0.05f))
            val pulse = focusPulse.update()
            for (e in visibleElements()) {
                dev.jamie.uikit.core.guard("hud preview ${e.id}") { HudManager.drawElement(c, e, preview = true) }
                val (x, y, w, h) = bounds(e)
                val hovered = mouseGx in x..x + w && mouseGy in y..y + h
                val hv = hoverAnim.getOrPut(e) { Ease(0f) }.apply { target = if (hovered || e === dragging) 1f else 0f }.update()
                val sel = e === selected
                val col = when {
                    sel -> Theme.accent
                    !e.activeNow -> Colors.withAlpha(Theme.textDim, 0.35f + 0.4f * hv)
                    else -> Colors.withAlpha(Theme.text, 0.18f + 0.45f * hv)
                }
                c.outline(x - 1.5f, y - 1.5f, w + 3f, h + 3f, col, 3f, if (sel) 1.2f else 0.8f)
                if (sel && pulse > 0.01f && e === focus) c.outline(x - 4f, y - 4f, w + 8f, h + 8f, Colors.mulAlpha(Theme.accent, pulse), 5f, 1.5f)
                if (sel || hv > 0.05f) {
                    val label = "${e.name}  ${"%.2f".format(e.scale)}×"
                    val lw = c.textWidth(label, 6.5f, Weight.MEDIUM) + 8f
                    val ly = if (y > 12f) y - 11f else y + h + 3f
                    c.rect(x - 1.5f, ly, lw, 9f, if (sel) Theme.accent else Colors.withAlpha(Theme.surfaceHi, 0.95f), 2.5f)
                    c.text(label, x + 2.5f, ly + 1.4f, if (sel) Theme.accentText else Theme.text, 6.5f, Weight.MEDIUM)
                }
                if (sel) {
                    // scale handle
                    c.circle(x + w + 1.5f, y + h + 1.5f, 2.6f, Theme.accent)
                    c.circle(x + w + 1.5f, y + h + 1.5f, 1.2f, Theme.accentText)
                }
            }
            HudManager.settle(visibleElements(), gw.toInt(), gh.toInt())
            for (gx in guidesX) c.rect(gx - 0.25f, 0f, 0.5f, gh, Colors.withAlpha(Theme.accent, 0.8f))
            for (gy in guidesY) c.rect(0f, gy - 0.25f, gw, 0.5f, Colors.withAlpha(Theme.accent, 0.8f))
        }
        if (visibleElements().isEmpty()) {
            val msg = if (showInactive || HudManager.all.none { it.available }) "No HUD elements are switched on."
                else "Nothing is active here. Click \"Show inactive\" to arrange them all."
            c.text(msg, ui.screenW / 2, ui.screenH / 2 - 6f, Theme.textDim, Theme.BODY, Weight.MEDIUM, Align.CENTER)
        }
        drawToolbar(c)
        if (focusPulse.target == 1f && focusPulse.value > 0.95f) focusPulse.target = 0f
        Toasts.render(c, ui.screenW, ui.screenH)
    }

    private fun drawToolbar(c: Canvas) {
        val buttons = toolbar()
        buttons.forEach { it.w = it.measure(c) }
        val total = buttons.sumOf { it.w.toDouble() }.toFloat() + 6f * (buttons.size - 1) + 16f
        val x0 = (ui.screenW - total) / 2
        val y0 = ui.screenH - 34f
        c.shadow(x0, y0, total, 24f, Theme.rMd, 16f, 0x80000000.toInt())
        c.panel(x0, y0, total, 24f, Colors.withAlpha(Theme.surface, 0.96f), Theme.borderStrong, Theme.rMd)
        var x = x0 + 8f
        for (b in buttons) { b.layout(x, y0 + 5f, b.w); b.render(c, ui); x += b.w + 6f }
        val hint = "Drag to move · Scroll to scale · Right-click for options · Arrows nudge · Ctrl+Z undo"
        c.text(hint, ui.screenW / 2, y0 - 12f, Colors.withAlpha(Theme.text, 0.55f), Theme.CAPTION, align = Align.CENTER, shadow = true)
    }

    private fun pushUndo() {
        undo.addLast(visibleElements().map { Snapshot(it, it.anchor, it.offsetX, it.offsetY, it.scale) })
        if (undo.size > 50) undo.removeFirst()
    }

    private fun elementAt(gx: Float, gy: Float): HudElement? = visibleElements().lastOrNull { e ->
        val (x, y, w, h) = bounds(e); gx in x - 2..x + w + 2 && gy in y - 2..y + h + 2
    }

    override fun onMouseDown(button: Int, double: Boolean): Boolean {
        toolbar().forEach { if (it.mouseDown(ui, button)) return true }
        val gx = mouseGx; val gy = mouseGy
        selected?.let { s ->
            val (x, y, w, h) = bounds(s)
            if (button == 0 && abs(gx - (x + w + 1.5f)) < 4f && abs(gy - (y + h + 1.5f)) < 4f) {
                pushUndo(); dragging = s; scalingHandle = true
                scaleStartDist = maxOf(4f, kotlin.math.hypot(gx - x, gy - y)); scaleStart = s.scale
                return true
            }
        }
        val e = elementAt(gx, gy)
        selected = e
        if (e == null) return false
        if (button == 1) {
            openElementPopover(e); return true
        }
        if (button == 0) {
            pushUndo()
            dragging = e; scalingHandle = false
            val (x, y) = bounds(e)
            dragOffX = gx - x; dragOffY = gy - y
        }
        return true
    }

    private fun openElementPopover(e: HudElement) {
        val list = SettingsList({ e.rootSettings })
        val p = object : Popover() {
            init { w = 270f }
            override fun render(c: Canvas, ui: Ui) {
                val t = appear.update()
                h = list.layout(x + 10f, y + 24f, w - 20f) + 32f
                c.push {
                    c.scaleAround(0.95f + 0.05f * t, x + w / 2, y)
                    c.shadow(x, y, w, h, Theme.rMd, 16f, Colors.mulAlpha(0x80000000.toInt(), t))
                    c.panel(x, y, w, h, Theme.surface, Theme.borderStrong, Theme.rMd)
                    c.text(e.name, x + 10f, y + 8f, Theme.text, Theme.TITLE, Weight.SEMIBOLD)
                    list.render(c, ui)
                }
            }
            override fun mouseDown(ui: Ui, button: Int) = list.mouseDown(ui, button) || contains(ui.mouseX, ui.mouseY)
            override fun key(ui: Ui, key: Int, mods: Int) = list.key(ui, key, mods)
            override fun char(ui: Ui, cp: Int) = list.char(ui, cp)
        }
        p.h = 120f
        p.x = (ui.mouseX + 8f).coerceAtMost(ui.screenW - p.w - 6f)
        p.y = (ui.mouseY).coerceAtMost(ui.screenH - 160f)
        ui.openPopover(p)
    }

    override fun onMouseDrag(button: Int) {
        val e = dragging ?: return
        val gw = mc.window.guiScaledWidth; val gh = mc.window.guiScaledHeight
        val gx = mouseGx; val gy = mouseGy
        guidesX.clear(); guidesY.clear()
        if (scalingHandle) {
            val (x, y) = bounds(e)
            val d = maxOf(4f, kotlin.math.hypot(gx - x, gy - y))
            var s = (scaleStart * d / scaleStartDist).coerceIn(0.3f, 4f)
            if (snap) s = (s * 20f).roundToInt() / 20f
            // keep top-left fixed while scaling, and the element on screen
            e.scale = s
            e.placeAt(x.coerceIn(0f, maxOf(0f, gw - e.lastSize.w * s)), y.coerceIn(0f, maxOf(0f, gh - e.lastSize.h * s)), gw, gh, reanchor = false)
            return
        }
        var nx = gx - dragOffX; var ny = gy - dragOffY
        val w = e.lastSize.w * e.scale; val h = e.lastSize.h * e.scale
        if (snap) {
            val threshold = 4f
            val xs = mutableListOf(2f, gw / 2f, gw - 2f)
            val ys = mutableListOf(2f, gh / 2f, gh - 2f)
            for (o in visibleElements()) if (o !== e) {
                val (ox, oy, ow, oh) = bounds(o)
                xs += listOf(ox, ox + ow, ox + ow / 2); ys += listOf(oy, oy + oh, oy + oh / 2)
            }
            fun snapAxis(pos: Float, size: Float, targets: List<Float>, guides: MutableList<Float>): Float {
                var best = pos; var bestD = threshold
                for (t in targets) for ((edge, off) in listOf(pos to 0f, pos + size / 2 to size / 2, pos + size to size)) {
                    val d = abs(edge - t)
                    if (d < bestD) { bestD = d; best = t - off; guides.clear(); guides += t }
                }
                return best
            }
            nx = snapAxis(nx, w, xs, guidesX)
            ny = snapAxis(ny, h, ys, guidesY)
        }
        nx = nx.coerceIn(0f, gw - w); ny = ny.coerceIn(0f, gh - h)
        e.placeAt(nx, ny, gw, gh)
    }

    override fun onMouseUp(button: Int) {
        dragging = null; scalingHandle = false
        guidesX.clear(); guidesY.clear()
    }

    override fun onScroll(amount: Float): Boolean {
        val e = elementAt(mouseGx, mouseGy) ?: selected ?: return false
        val (x, y) = bounds(e)
        pushUndo()
        val step = if (hasShift()) 0.01f else 0.05f
        e.scale = (e.scale + amount * step).coerceIn(0.3f, 4f)
        val gw = mc.window.guiScaledWidth; val gh = mc.window.guiScaledHeight
        e.placeAt(x.coerceIn(0f, maxOf(0f, gw - e.lastSize.w * e.scale)), y.coerceIn(0f, maxOf(0f, gh - e.lastSize.h * e.scale)), gw, gh, reanchor = false)
        selected = e
        return true
    }

    private fun hasShift() = dev.jamie.uikit.compat.Game.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT) ||
        dev.jamie.uikit.compat.Game.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT)

    override fun onKey(key: Int, mods: Int): Boolean {
        if (key == Keys.Z && mods and Keys.CTRL != 0) {
            undo.removeLastOrNull()?.forEach { it.e.anchor = it.anchor; it.e.offsetX = it.x; it.e.offsetY = it.y; it.e.scale = it.scale }
            return true
        }
        val e = selected ?: return false
        val step = if (mods and Keys.SHIFT != 0) 10f else 1f
        when (key) {
            Keys.LEFT -> e.offsetX -= step
            Keys.RIGHT -> e.offsetX += step
            Keys.UP -> e.offsetY -= step
            Keys.DOWN -> e.offsetY += step
            Keys.DELETE, Keys.BACKSPACE -> { pushUndo(); e.resetPosition() }
            else -> return false
        }
        return true
    }
}
