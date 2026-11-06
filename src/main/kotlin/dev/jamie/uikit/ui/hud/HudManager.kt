package dev.jamie.uikit.ui.hud

import dev.jamie.uikit.compat.Game
import dev.jamie.uikit.core.Kit

import dev.jamie.uikit.config.Config
import dev.jamie.uikit.core.guard
import dev.jamie.uikit.core.mc
import dev.jamie.uikit.ui.Canvas
import dev.jamie.uikit.ui.Frame
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Renders every [HudElement] in one vanilla HUD layer and stores their placement. */
object HudManager {
    private val elements = mutableListOf<HudElement>()
    val all: List<HudElement> get() = elements

    /** True while the HUD editor is open — elements are drawn by the editor instead. */
    var editing = false

    /** The editor opens with inactive elements shown when this says so (off the mod's usual world, say). */
    var showInactiveAtFirst: () -> Boolean = { false }

    /** Called each HUD frame, editor open or not, after the elements: for what draws on the HUD without being one. */
    val afterFrame = mutableListOf<(Canvas) -> Unit>()

    fun register(e: HudElement) {
        require(elements.none { it.id == e.id }) { "Duplicate HUD id ${e.id}" }
        elements += e
        Config.bind("hud", e.id, e)
    }

    /** [aboveChat]: draw over the chat rather than under it. */
    fun init(aboveChat: Boolean = true) {
        val id = Kit.id("hud")
        if (aboveChat) HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, id) { g, _ -> frame(g) }
        else HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id) { g, _ -> frame(g) }
    }

    private fun frame(g: GuiGraphicsExtractor) {
        if (Game.hideGui) return
        Frame.begin()
        val c = Canvas(g)
        if (!editing) {
            val shown = ArrayList<HudElement>()
            for (e in elements) if (e.shouldRender()) guard("hud ${e.id}") { if (drawElement(c, e, preview = false)) shown += e }
            settle(shown, c.guiWidth, c.guiHeight)
        }
        for (hook in afterFrame) guard("hud hook") { hook(c) }
    }

    /** Draws [e] at its placement; true if it drew anything. */
    fun drawElement(c: Canvas, e: HudElement, preview: Boolean): Boolean {
        val x = e.screenX(c.guiWidth)
        val y = e.screenY(c.guiHeight)
        var drew = false
        c.push {
            c.translate(x, y)
            c.scale(e.scale)
            val s = e.draw(c, preview)
            if (s.w > 0f && s.h > 0f) { e.lastSize = s; drew = true }
        }
        return drew
    }

    /**
     * Gives elements that were never placed a free spot the first time they show: an unplaced element that
     * overlaps another shown one slides past it (down, or up when bottom-anchored), spilling into the next
     * column inward when the current one is full, then remembers its place. Moved elements are never touched.
     */
    fun settle(shown: List<HudElement>, gw: Int, gh: Int) {
        for (e in shown) {
            if (!e.unplaced) continue
            val w = e.lastSize.w * e.scale; val h = e.lastSize.h * e.scale
            val up = e.anchor.fy == 1f
            val leftward = e.anchor.fx == 1f
            val y0 = e.screenY(gh)
            var x = e.screenX(gw); var y = y0
            var spot: Pair<Float, Float>? = null
            for (attempt in 0 until 200) {
                val hit = shown.firstOrNull { o ->
                    o !== e && x < o.screenX(gw) + o.lastSize.w * o.scale && o.screenX(gw) < x + w &&
                        y < o.screenY(gh) + o.lastSize.h * o.scale && o.screenY(gh) < y + h
                }
                if (hit == null) { spot = x to y; break }
                val ny = if (up) hit.screenY(gh) - h - 4f else hit.screenY(gh) + hit.lastSize.h * hit.scale + 4f
                if (ny >= 0f && ny + h <= gh - 40f) { y = ny; continue } // keep clear of the hotbar
                x = if (leftward) hit.screenX(gw) - w - 4f else hit.screenX(gw) + hit.lastSize.w * hit.scale + 4f
                y = y0
                if (x < 0f || x + w > gw) break
            }
            val (px, py) = spot ?: (e.screenX(gw) to y0)
            e.placeAt(px, py, gw, gh, reanchor = false)
        }
    }
}
