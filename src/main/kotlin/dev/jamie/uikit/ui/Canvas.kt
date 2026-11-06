package dev.jamie.uikit.ui

import dev.jamie.uikit.compat.render.GuiElements
import dev.jamie.uikit.compat.render.SdfFont
import dev.jamie.uikit.core.Kit
import dev.jamie.uikit.core.mc
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import kotlin.math.roundToInt

enum class Weight(val shift: Float) { REGULAR(0f), MEDIUM(0.035f), SEMIBOLD(0.07f), BOLD(0.11f) }
enum class Align { LEFT, CENTER, RIGHT }

object Fonts {
    val inter = SdfFont("/assets/${Kit.modId}/font/inter.ttf", "inter")
    val icons = SdfFont("/assets/${Kit.modId}/font/lucide.ttf", "lucide", warmAscii = false)
    /** Symbol fallback (✔ ❤ ⏣ ☠ ❄ ✪ …) for glyphs Inter doesn't have — DejaVu Sans. */
    val symbols = SdfFont("/assets/${Kit.modId}/font/symbols.ttf", "symbols", warmAscii = false)

    /** Picks the font that has [cp]: Inter, then the symbol font. Null means Minecraft's own font draws it (its Unifont fallback covers the whole BMP). */
    fun resolve(cp: Int): Pair<SdfFont, SdfFont.Glyph>? {
        inter.find(cp)?.let { return inter to it }
        symbols.find(cp)?.let { return symbols to it }
        return null
    }
}

/**
 * The single drawing API of the kit. Widgets, HUD elements and features draw through this — never
 * through GuiGraphics directly — so the look is consistent and version changes stay in `compat/`.
 *
 * Coordinates are floats in the current pose space. Text accepts § formatting codes.
 */
class Canvas(val g: GuiGraphicsExtractor) {
    /** Render-thread-only scratch buffer; glyph data is copied into the render state on submit, so one is enough. */
    private val batch get() = SHARED_BATCH
    private val fallbackBatch get() = SHARED_FALLBACK

    // ── transforms ──────────────────────────────────────────────────────────
    inline fun <T> push(block: () -> T): T {
        g.pose().pushMatrix()
        try {
            return block()
        } finally {
            g.pose().popMatrix()
        }
    }

    fun translate(x: Float, y: Float) {
        g.pose().translate(x, y)
    }

    fun scale(s: Float) {
        g.pose().scale(s, s)
    }

    fun scale(sx: Float, sy: Float) {
        g.pose().scale(sx, sy)
    }

    /** Rotates clockwise (y points down) by [radians]. */
    fun rotate(radians: Float) {
        g.pose().rotate(radians)
    }

    /** Scales around a pivot (used by pop-in animations). */
    fun scaleAround(s: Float, px: Float, py: Float) {
        translate(px, py); scale(s); translate(-px, -py)
    }

    /**
     * Clips drawing to a rectangle given in the current pose space (like every other Canvas coordinate): it follows
     * [translate]/[scale]/[scaleAround] into GUI space and nests, intersecting any enclosing clip.
     */
    inline fun <T> clip(x: Float, y: Float, w: Float, h: Float, block: () -> T): T {
        GuiElements.pushScissor(g, x, y, x + w, y + h)
        try {
            return block()
        } finally {
            GuiElements.popScissor(g)
        }
    }

    /** Starts a new layer: everything after draws on top of everything before (tooltips, popovers). */
    fun layer() = g.nextStratum()

    // ── shapes ──────────────────────────────────────────────────────────────
    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f) =
        GuiElements.shape(g, x, y, x + w, y + h, color, color, color, color, radius, radius, radius, radius, 0f)

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int, tl: Float, tr: Float, br: Float, bl: Float) =
        GuiElements.shape(g, x, y, x + w, y + h, color, color, color, color, tl, tr, br, bl, 0f)

    fun gradientH(x: Float, y: Float, w: Float, h: Float, left: Int, right: Int, radius: Float = 0f) =
        GuiElements.shape(g, x, y, x + w, y + h, left, right, right, left, radius, radius, radius, radius, 0f)

    fun gradientV(x: Float, y: Float, w: Float, h: Float, top: Int, bottom: Int, radius: Float = 0f) =
        GuiElements.shape(g, x, y, x + w, y + h, top, top, bottom, bottom, radius, radius, radius, radius, 0f)

    fun gradient4(x: Float, y: Float, w: Float, h: Float, tl: Int, tr: Int, br: Int, bl: Int, radius: Float = 0f) =
        GuiElements.shape(g, x, y, x + w, y + h, tl, tr, br, bl, radius, radius, radius, radius, 0f)

    fun outline(x: Float, y: Float, w: Float, h: Float, color: Int, radius: Float = 0f, width: Float = 1f) =
        GuiElements.shape(g, x, y, x + w, y + h, color, color, color, color, radius, radius, radius, radius, width)

    /** Fill + outline in one go (the common "card" look). */
    fun panel(x: Float, y: Float, w: Float, h: Float, fill: Int, border: Int, radius: Float, borderWidth: Float = 1f) {
        rect(x, y, w, h, fill, radius)
        if (Colors.a(border) > 0) outline(x, y, w, h, border, radius, borderWidth)
    }

    fun shadow(x: Float, y: Float, w: Float, h: Float, radius: Float, blur: Float = 12f, color: Int = Theme.shadow, dy: Float = 3f) =
        GuiElements.shape(g, x, y + dy, x + w, y + h + dy, color, color, color, color, radius, radius, radius, radius, blur, shadow = true)

    fun circle(cx: Float, cy: Float, r: Float, color: Int) = rect(cx - r, cy - r, r * 2, r * 2, color, r)
    fun ring(cx: Float, cy: Float, r: Float, color: Int, width: Float = 1f) = outline(cx - r, cy - r, r * 2, r * 2, color, r, width)

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float = 1f) {
        if (x0 == x1 || y0 == y1) rect(minOf(x0, x1) - if (x0 == x1) width / 2 else 0f, minOf(y0, y1) - if (y0 == y1) width / 2 else 0f,
            if (x0 == x1) width else kotlin.math.abs(x1 - x0), if (y0 == y1) width else kotlin.math.abs(y1 - y0), color)
        else {
            val dx = x1 - x0; val dy = y1 - y0
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            push {
                translate(x0, y0)
                g.pose().rotate(kotlin.math.atan2(dy, dx))
                rect(0f, -width / 2, len, width, color, width / 2)
            }
        }
    }

    // ── text ────────────────────────────────────────────────────────────────
    private val font get() = Fonts.inter
    private val vanillaFallback get() = font.failed

    /** Height of a text line at [size]. */
    fun lineHeight(size: Float) = size * 1.25f

    fun textWidth(text: String, size: Float = Theme.BODY, weight: Weight = Weight.REGULAR): Float = measure(text, size, weight)

    /**
     * Draws [text] with its top-left at ([x], [y]). § codes recolour; §l bolds. Returns the width drawn.
     * Uses the smooth SDF font, falling back to the vanilla font if the TTF failed to load. A non-zero [outline]
     * colour draws a thin outline (a fattened copy underneath) for text over busy backgrounds.
     */
    fun text(
        text: String, x: Float, y: Float,
        color: Int = Theme.text, size: Float = Theme.BODY, weight: Weight = Weight.REGULAR,
        align: Align = Align.LEFT, shadow: Boolean = false, outline: Int = 0,
    ): Float {
        if (text.isEmpty() || Colors.a(color) == 0) return 0f
        val width = textWidth(text, size, weight)
        val ox = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - width / 2
            Align.RIGHT -> x - width
        }
        if (vanillaFallback) {
            push {
                translate(ox, y); scale(size / 9f)
                g.text(mc.font, text, 0, 0, color, shadow)
            }
            return width
        }
        val setup = font.textureSetup() ?: return 0f
        if (shadow) {
            emit(text, ox + size * 0.07f, y + size * 0.09f, Colors.mulAlpha(0xFF000000.toInt(), Colors.a(color) / 255f * 0.55f), size, weight, true)
            submit(setup, 0.06f)
        }
        if (Colors.a(outline) > 0) {
            emit(text, ox, y, outline, size, weight, true, OUTLINE_SHIFT)
            submit(setup, 0f)
        }
        emit(text, ox, y, color, size, weight, false)
        submit(setup, 0f)
        return width
    }

    private fun submit(setup: net.minecraft.client.gui.render.TextureSetup, softness: Float) {
        GuiElements.glyphs(g, batch, setup, softness)
        if (fallbackBatch.count > 0) Fonts.symbols.textureSetup()?.let { GuiElements.glyphs(g, fallbackBatch, it, softness) }
        for (r in vanillaRuns) push {
            translate(r.x, r.y)
            scale(r.size / 9f)
            g.text(mc.font, r.text, 0, 0, r.color, false)
        }
    }

    private fun emit(text: String, x: Float, y: Float, baseColor: Int, size: Float, weight: Weight, isShadow: Boolean, boost: Float = 0f) {
        batch.count = 0
        fallbackBatch.count = 0
        vanillaRuns.clear()
        val s = size / SdfFont.BASE_PX
        val baseline = y + font.ascent * s
        val alpha = Colors.a(baseColor) / 255f
        var color = baseColor
        var pen = x
        var prev = -1
        var bold = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '§' && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
                val legacy = Colors.legacy(code)
                when {
                    legacy != null -> { if (!isShadow) color = Colors.mulAlpha(legacy, alpha); bold = false }
                    code == 'r' -> { color = baseColor; bold = false }
                    code == 'l' -> bold = true
                }
                i += 2; continue
            }
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp in 0xE000..0xF8FF) continue // Hypixel icon-font glyphs: not in our font
            val (gf, glyph) = Fonts.resolve(cp) ?: run {
                val str = String(Character.toChars(cp))
                vanillaRuns += VanillaRun(pen, y, size, str, color)
                pen += mc.font.width(str) * size / 9f
                prev = -1
                continue
            }
            if (prev >= 0 && gf === font) pen += font.kern(prev, cp) * s
            prev = if (gf === font) cp else -1
            if (glyph.w > 0f) {
                val gx = pen + glyph.x0 * s
                val gy = baseline + glyph.y0 * s
                val shift = (if (bold) maxOf(weight.shift, Weight.BOLD.shift) else weight.shift) + boost
                (if (gf === font) batch else fallbackBatch).add(gx, gy, gx + glyph.w * s, gy + glyph.h * s, glyph.u0, glyph.v0, glyph.u1, glyph.v1, color, shift)
            }
            pen += (glyph.advance + if (bold || weight.shift > 0.06f) 0.6f else 0f) * s
        }
    }

    /** Draws an [Icons] glyph centred in a [size]x[size] box at ([x],[y]). */
    fun icon(icon: String, x: Float, y: Float, size: Float, color: Int, weight: Weight = Weight.REGULAR) {
        if (Colors.a(color) == 0 || icon.isEmpty()) return
        val font = Fonts.icons
        val glyph = font.glyph(icon.codePointAt(0)) ?: return
        val setup = font.textureSetup() ?: return
        if (glyph.w <= 0f) return
        // Scale the em to the box and centre the ink.
        val s = size / SdfFont.BASE_PX * 1.15f
        val gw = glyph.w * s; val gh = glyph.h * s
        val gx = x + (size - gw) / 2; val gy = y + (size - gh) / 2
        batch.count = 0
        batch.add(gx, gy, gx + gw, gy + gh, glyph.u0, glyph.v0, glyph.u1, glyph.v1, color, weight.shift + 0.02f)
        GuiElements.glyphs(g, batch, setup, 0f)
    }

    /** Word-wraps [text] into [maxWidth]; returns total height. */
    fun paragraph(text: String, x: Float, y: Float, maxWidth: Float, color: Int = Theme.textDim, size: Float = Theme.CAPTION, weight: Weight = Weight.REGULAR): Float {
        val lines = wrap(text, maxWidth, size, weight)
        val lh = lineHeight(size)
        lines.forEachIndexed { i, l -> text(l, x, y + i * lh, color, size, weight) }
        return lines.size * lh
    }

    fun wrap(text: String, maxWidth: Float, size: Float, weight: Weight = Weight.REGULAR): List<String> {
        val out = mutableListOf<String>()
        for (para in text.split('\n')) {
            var line = StringBuilder()
            for (word in para.split(' ')) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (textWidth(candidate, size, weight) > maxWidth && line.isNotEmpty()) {
                    out += line.toString(); line = StringBuilder(word)
                } else line = StringBuilder(candidate)
            }
            out += line.toString()
        }
        return out
    }

    /** Vanilla-rendered component (keeps fancy formatting like obfuscation/hover fonts). */
    fun component(c: Component, x: Float, y: Float, color: Int = -1, shadow: Boolean = true, scale: Float = 1f) {
        push {
            translate(x, y); if (scale != 1f) scale(scale)
            g.text(mc.font, c, 0, 0, color, shadow)
        }
    }

    // ── items & textures ────────────────────────────────────────────────────
    fun item(stack: ItemStack, x: Float, y: Float, size: Float = 16f, decorations: Boolean = false) {
        push {
            translate(x, y)
            if (size != 16f) scale(size / 16f)
            g.item(stack, 0, 0)
            if (decorations) g.itemDecorations(mc.font, stack, 0, 0)
        }
    }

    /** Rounded textured quad. [pixelated] keeps the texture's own (nearest) sampler — for maps and pixel art. */
    fun texture(id: net.minecraft.resources.Identifier, x: Float, y: Float, w: Float, h: Float, radius: Float = 0f, tint: Int = -1, pixelated: Boolean = false) {
        val tex = mc.textureManager.getTexture(id)
        val setup = net.minecraft.client.gui.render.TextureSetup.singleTexture(tex.textureView, if (pixelated) tex.sampler else GuiElements.linearClamp)
        GuiElements.shape(g, x, y, x + w, y + h, tint, tint, tint, tint, radius, radius, radius, radius, 0f, texture = setup)
    }

    val guiWidth: Int get() = g.guiWidth()
    val guiHeight: Int get() = g.guiHeight()

    fun Float.px(): Float = (this * 2f).roundToInt() / 2f

    companion object {
        private val SHARED_BATCH = GuiElements.GlyphBatch(256)
        private val SHARED_FALLBACK = GuiElements.GlyphBatch(32)

        /** A character neither SDF font has, drawn by Minecraft's font at pen position [x] on the line starting at [y]. */
        private class VanillaRun(val x: Float, val y: Float, val size: Float, val text: String, val color: Int)

        private val vanillaRuns = ArrayList<VanillaRun>()
        private const val OUTLINE_SHIFT = 0.24f

        /** Text width without needing a Canvas (layout code, popover sizing). */
        fun measure(text: String, size: Float = Theme.BODY, weight: Weight = Weight.REGULAR): Float {
            val font = Fonts.inter
            if (font.failed) return mc.font.width(text) * size / 9f
            val s = size / SdfFont.BASE_PX
            var w = 0f
            var prev = -1
            var i = 0
            var bold = false
            while (i < text.length) {
                val ch = text[i]
                if (ch == '§' && i + 1 < text.length) {
                    val code = text[i + 1].lowercaseChar()
                    if (code == 'l') bold = true else if (code == 'r' || Colors.legacy(code) != null) bold = false
                    i += 2; continue
                }
                val cp = text.codePointAt(i)
                if (cp in 0xE000..0xF8FF) { i += 1; continue }
                val resolved = Fonts.resolve(cp)
                val glyph = resolved?.second
                if (resolved == null) w += mc.font.width(String(Character.toChars(cp))) * SdfFont.BASE_PX / 9f
                if (glyph != null) {
                    if (prev >= 0 && resolved.first === font) w += font.kern(prev, cp)
                    w += glyph.advance + if (bold || weight.shift > 0.06f) 0.6f else 0f
                }
                prev = cp
                i += Character.charCount(cp)
            }
            return w * s
        }
    }
}
