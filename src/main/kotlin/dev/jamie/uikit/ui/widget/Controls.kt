package dev.jamie.uikit.ui.widget

import dev.jamie.uikit.core.mc
import dev.jamie.uikit.ui.Align
import dev.jamie.uikit.ui.Canvas
import dev.jamie.uikit.ui.Colors
import dev.jamie.uikit.ui.Ease
import dev.jamie.uikit.ui.Spring
import dev.jamie.uikit.ui.Theme
import dev.jamie.uikit.ui.Weight
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pill switch. Knob springs across, track cross-fades to the accent. */
class Switch(val get: () -> Boolean, val set: (Boolean) -> Unit) : Widget() {
    private val knob = Spring(if (get()) 1f else 0f, 500f, 0.78f)
    private val hover = Ease(0f)

    init { w = 22f; h = 12f }

    override fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; return h
    }

    override fun render(c: Canvas, ui: Ui) {
        knob.target = if (get()) 1f else 0f
        val t = knob.update()
        val hv = hover.apply { target = if (hovered(ui)) 1f else 0f }.update()
        val off = Colors.shade(Theme.surfaceHi, 0.06f + hv * 0.05f)
        c.rect(x, y, w, h, Colors.lerp(off, Theme.accent, t.coerceIn(0f, 1f)), h / 2)
        val kr = h / 2 - 2f
        val kx = x + 2f + kr + (w - 4f - kr * 2) * t
        c.circle(kx, y + h / 2 + 0.4f, kr + 0.4f, 0x33000000)
        c.circle(kx, y + h / 2, kr, Colors.lerp(Theme.textDim, 0xFFFFFFFF.toInt(), t.coerceIn(0f, 1f)))
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (!hovered(ui) || button != 0) return false
        set(!get()); return true
    }
}

enum class ButtonStyle { PRIMARY, GHOST, SUBTLE, DANGER }

class Button(var label: String, val style: ButtonStyle = ButtonStyle.SUBTLE, var icon: String? = null, val onClick: () -> Unit) : Widget() {
    private val hover = Ease(0f)
    private val press = Spring(1f, 700f, 0.7f)
    var fixedWidth: Float? = null
    var active: () -> Boolean = { false }

    init { h = 14f }

    fun measure(c: Canvas): Float = fixedWidth ?: (c.textWidth(label, Theme.CAPTION + 0.5f, Weight.MEDIUM) + 14f + if (icon != null) 12f else 0f)

    override fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; this.w = width; return h
    }

    override fun render(c: Canvas, ui: Ui) {
        if (w == 0f) w = measure(c)
        val hv = hover.apply { target = if (hovered(ui) || active()) 1f else 0f }.update()
        val s = press.update()
        val (bg, fg) = when (style) {
            ButtonStyle.PRIMARY -> Colors.shade(Theme.accent, hv * 0.08f) to Theme.accentText
            ButtonStyle.GHOST -> Colors.withAlpha(Theme.text, 0.04f + hv * 0.06f) to Theme.text
            ButtonStyle.SUBTLE -> Colors.shade(Theme.surfaceHi, hv * 0.05f) to Colors.lerp(Theme.textDim, Theme.text, hv)
            ButtonStyle.DANGER -> Colors.withAlpha(Theme.danger, 0.14f + hv * 0.1f) to Theme.danger
        }
        c.push {
            c.scaleAround(s, x + w / 2, y + h / 2)
            c.rect(x, y, w, h, bg, Theme.rSm)
            if (style == ButtonStyle.SUBTLE) c.outline(x, y, w, h, Theme.border, Theme.rSm)
            val tw = c.textWidth(label, Theme.CAPTION + 0.5f, Weight.MEDIUM) + if (icon != null) 12f else 0f
            var tx = x + (w - tw) / 2
            icon?.let { c.icon(it, tx - 0.5f, y + (h - 8.5f) / 2, 8.5f, fg); tx += 12f }
            c.text(label, tx, y + 3.4f, fg, Theme.CAPTION + 0.5f, Weight.MEDIUM)
        }
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (!hovered(ui) || button != 0) return false
        press.value = 0.94f
        onClick(); return true
    }
}

/**
 * Slider for int/float values. Drag or click the track; shift = fine; double-click value chip to type.
 */
class Slider(
    val min: Float, val max: Float, val step: Float,
    val get: () -> Float, val set: (Float) -> Unit,
    /** What a value typed into the chip goes through, when it differs from a drag's [set]. */
    val typed: ((Float) -> Unit)? = null,
    val format: (Float) -> String,
) : Widget() {
    private val hover = Ease(0f)
    private val shown = Spring((get() - min) / (max - min), 600f, 0.9f)
    private var dragging = false
    private var editing: TextField? = null

    init { w = 120f; h = 12f }

    private val trackW get() = w - 34f

    override fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; return h
    }

    override fun render(c: Canvas, ui: Ui) {
        val frac = ((get() - min) / (max - min)).coerceIn(0f, 1f)
        shown.target = frac
        val t = if (dragging) frac.also { shown.snap(it) } else shown.update()
        val hv = hover.apply { target = if (hovered(ui) || dragging) 1f else 0f }.update()
        val ty = y + h / 2
        c.rect(x, ty - 1.5f, trackW, 3f, Theme.surfaceHi, 1.5f)
        c.rect(x, ty - 1.5f, trackW * t, 3f, Theme.accent, 1.5f)
        val kx = x + trackW * t
        val kr = 3.6f + hv * 1.1f
        c.circle(kx, ty + 0.4f, kr + 0.6f, 0x40000000)
        c.circle(kx, ty, kr, 0xFFFFFFFF.toInt())
        val ed = editing
        if (ed != null) {
            ed.layout(x + trackW + 4f, y - 1f, 30f)
            ed.render(c, ui)
        } else {
            c.rect(x + trackW + 4f, y, 30f, h, Theme.surfaceHi, Theme.rSm)
            c.text(format(get()), x + trackW + 19f, y + 2.4f, Theme.text, Theme.CAPTION, Weight.MEDIUM, Align.CENTER)
        }
    }

    private fun setFromMouse(ui: Ui, fine: Boolean) {
        var frac = ((ui.mouseX - x) / trackW).coerceIn(0f, 1f)
        if (fine) frac = (get() - min) / (max - min) + (frac - (get() - min) / (max - min)) * 0.15f
        set(min + (max - min) * frac)
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (button != 0) return false
        editing?.let { if (it.contains(ui.mouseX, ui.mouseY)) return it.mouseDown(ui, button) }
        if (ui.over(x + trackW + 4f, y, 30f, h)) {
            val tf = TextField(placeholder = "", initial = format(get()).filter { it.isDigit() || it == '.' || it == '-' }) { text ->
                text.toFloatOrNull()?.let(typed ?: set)
            }
            tf.onSubmit = { editing = null; ui.focus(null) }
            tf.onBlurAction = { tf.submit(); editing = null }
            editing = tf
            ui.focus(tf)
            tf.selectAll()
            return true
        }
        if (!ui.over(x - 4f, y - 3f, trackW + 8f, h + 6f)) return false
        dragging = true
        ui.captured = this
        setFromMouse(ui, false)
        return true
    }

    override fun mouseDrag(ui: Ui, button: Int) {
        if (dragging) setFromMouse(ui, false)
    }

    override fun mouseUp(ui: Ui, button: Int) {
        dragging = false
    }

    override fun scroll(ui: Ui, amount: Float): Boolean = false
}

/**
 * Single-line text field: caret, selection, clipboard, word-delete, horizontal scrolling.
 * [onChange] fires on every edit; [onSubmit] on Enter.
 */
class TextField(
    val placeholder: String = "",
    initial: String = "",
    val maxLength: Int = 256,
    val icon: String? = null,
    val onChange: (String) -> Unit = {},
) : Widget() {
    var text = initial
        private set
    private var caret = initial.length
    private var anchor = caret
    private var scrollX = 0f
    private var focusedNow = false
    private val focusAnim = Ease(0f)
    var onSubmit: (() -> Unit)? = null
    var onBlurAction: (() -> Unit)? = null
    var onKey: ((Int, Int) -> Boolean)? = null
    var size = Theme.CAPTION + 0.5f
    var filled = true

    init { h = 14f }

    fun setText(t: String, notify: Boolean = true) {
        text = t.take(maxLength); caret = text.length; anchor = caret
        if (notify) onChange(text)
    }

    fun selectAll() {
        anchor = 0; caret = text.length
    }

    fun submit() {
        onChange(text)
    }

    override fun onFocus() {
        focusedNow = true
    }

    override fun onBlur() {
        focusedNow = false
        onBlurAction?.invoke()
    }

    override fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; this.w = width; return h
    }

    private val pad get() = if (icon != null) 17f else 5f

    override fun render(c: Canvas, ui: Ui) {
        val f = focusAnim.apply { target = if (focusedNow) 1f else 0f }.update()
        if (filled) {
            c.rect(x, y, w, h, Colors.shade(Theme.bg, 0.02f), Theme.rSm)
            c.outline(x, y, w, h, Colors.lerp(Theme.border, Colors.withAlpha(Theme.accent, 0.8f), f), Theme.rSm)
        }
        icon?.let { c.icon(it, x + 5f, y + (h - 8.5f) / 2, 8.5f, Colors.lerp(Theme.textFaint, Theme.accent, f)) }
        val innerW = w - pad - 5f
        val ty = y + (h - size) / 2 - 0.5f
        val caretX = c.textWidth(text.substring(0, caret), size)
        if (caretX - scrollX > innerW) scrollX = caretX - innerW
        if (caretX - scrollX < 0) scrollX = caretX
        c.clip(x + pad - 1f, y, innerW + 2f, h) {
            if (text.isEmpty() && !focusedNow) c.text(placeholder, x + pad, ty, Theme.textFaint, size)
            else if (text.isEmpty()) c.text(placeholder, x + pad, ty, Colors.mulAlpha(Theme.textFaint, 0.6f), size)
            if (caret != anchor) {
                val a = c.textWidth(text.substring(0, minOf(caret, anchor)), size)
                val b = c.textWidth(text.substring(0, maxOf(caret, anchor)), size)
                c.rect(x + pad + a - scrollX, ty - 0.5f, b - a, size + 2f, Colors.withAlpha(Theme.accent, 0.35f), 1.5f)
            }
            c.text(text, x + pad - scrollX, ty, Theme.text, size)
            if (focusedNow && ((dev.jamie.uikit.core.Clock.now * 2).toInt() % 2 == 0 || caret != anchor)) {
                c.rect(x + pad + caretX - scrollX, ty - 0.5f, 0.8f, size + 2f, Theme.accent)
            }
        }
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (!hovered(ui)) return false
        ui.focus(this)
        // Caret from click position (approximate by scanning).
        val local = ui.mouseX - x - pad + scrollX
        caret = text.indices.firstOrNull { measureCache(it + 1) > local }?.let { if (local - measureCache(it) < (measureCache(it + 1) - measureCache(it)) / 2) it else it + 1 } ?: text.length
        anchor = caret
        return true
    }

    // Approximate width via glyph advances without a Canvas (uses the same font).
    private fun measureCache(n: Int): Float = Canvas.measure(text.substring(0, n), size)

    private fun hasSel() = caret != anchor
    private fun deleteSel() {
        val a = minOf(caret, anchor); val b = maxOf(caret, anchor)
        text = text.removeRange(a, b); caret = a; anchor = a
    }

    private fun insert(s: String) {
        if (hasSel()) deleteSel()
        val room = maxLength - text.length
        val ins = s.take(room.coerceAtLeast(0))
        text = text.substring(0, caret) + ins + text.substring(caret)
        caret += ins.length; anchor = caret
        onChange(text)
    }

    private fun wordLeft(from: Int): Int {
        var i = from
        while (i > 0 && text[i - 1] == ' ') i--
        while (i > 0 && text[i - 1] != ' ') i--
        return i
    }

    private fun wordRight(from: Int): Int {
        var i = from
        while (i < text.length && text[i] == ' ') i++
        while (i < text.length && text[i] != ' ') i++
        return i
    }

    override fun key(ui: Ui, key: Int, mods: Int): Boolean {
        if (!focusedNow) return false
        if (onKey?.invoke(key, mods) == true) return true
        val shift = mods and Keys.SHIFT != 0
        val ctrl = mods and Keys.CTRL != 0
        fun move(to: Int) {
            caret = to.coerceIn(0, text.length); if (!shift) anchor = caret
        }
        when (key) {
            Keys.LEFT -> if (!shift && hasSel()) move(minOf(caret, anchor)) else move(if (ctrl) wordLeft(caret) else caret - 1)
            Keys.RIGHT -> if (!shift && hasSel()) move(maxOf(caret, anchor)) else move(if (ctrl) wordRight(caret) else caret + 1)
            Keys.HOME -> move(0)
            Keys.END -> move(text.length)
            Keys.BACKSPACE -> {
                if (hasSel()) deleteSel() else if (caret > 0) {
                    val from = if (ctrl) wordLeft(caret) else caret - 1
                    text = text.removeRange(from, caret); caret = from; anchor = from
                }
                onChange(text)
            }
            Keys.DELETE -> {
                if (hasSel()) deleteSel() else if (caret < text.length) {
                    val to = if (ctrl) wordRight(caret) else caret + 1
                    text = text.removeRange(caret, to)
                }
                onChange(text)
            }
            Keys.A -> if (ctrl) selectAll() else return false
            Keys.C -> if (ctrl && hasSel()) mc.keyboardHandler.setClipboard(text.substring(minOf(caret, anchor), maxOf(caret, anchor))) else return ctrl
            Keys.X -> if (ctrl && hasSel()) {
                mc.keyboardHandler.setClipboard(text.substring(minOf(caret, anchor), maxOf(caret, anchor))); deleteSel(); onChange(text)
            } else return ctrl
            Keys.V -> if (ctrl) insert(mc.keyboardHandler.clipboard.replace("\n", " ")) else return false
            Keys.ENTER, Keys.KP_ENTER -> { onChange(text); onSubmit?.invoke() }
            Keys.ESCAPE -> { ui.focus(null); return true }
            else -> return key in 32..126 || key in 290..314 // swallow printable keys so they don't trigger shortcuts
        }
        return true
    }

    override fun char(ui: Ui, cp: Int): Boolean {
        if (!focusedNow) return false
        if (cp < 32 || cp == 127 || cp == '§'.code) return true
        insert(String(Character.toChars(cp)))
        return true
    }
}

/** Dropdown button showing the current choice; opens a [ListPopover]. */
class Dropdown<T>(
    val options: () -> List<T>,
    val label: (T) -> String,
    val get: () -> T,
    val set: (T) -> Unit,
    /** Plays an option (sound choices): on a debounced hover in the open list and when one is picked. */
    val preview: ((T) -> Unit)? = null,
) : Widget() {
    private val hover = Ease(0f)

    init { w = 110f; h = 14f }

    override fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; return h
    }

    override fun render(c: Canvas, ui: Ui) {
        val hv = hover.apply { target = if (hovered(ui)) 1f else 0f }.update()
        c.rect(x, y, w, h, Colors.shade(Theme.surfaceHi, hv * 0.05f), Theme.rSm)
        c.outline(x, y, w, h, Theme.border, Theme.rSm)
        c.clip(x, y, w - 14f, h) { c.text(label(get()), x + 6f, y + 3.4f, Theme.text, Theme.CAPTION + 0.5f) }
        c.icon(dev.jamie.uikit.ui.Icons.CHEVRON_DOWN, x + w - 12f, y + 3f, 8f, Theme.textDim)
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (!hovered(ui) || button != 0) return false
        val p = ListPopover(options(), label, { it == get() }, { set(it); preview?.invoke(it); ui.closePopover() }, minWidth = w, preview = preview)
        p.measure()
        p.placeNear(ui, x, y, w, h)
        ui.openPopover(p)
        return true
    }
}

/** Vertical list of options with hover highlight, keyboard nav and a filter box for long lists. */
class ListPopover<T>(
    val options: List<T>,
    val label: (T) -> String,
    val selected: (T) -> Boolean,
    val choose: (T) -> Unit,
    val minWidth: Float = 100f,
    val multi: Boolean = false,
    /** Called once for the option the highlight rests on (mouse or arrow keys) for [PREVIEW_DELAY] s, never for the one shown at open. */
    val preview: ((T) -> Unit)? = null,
) : Popover() {
    private val rowH = 14f
    private var filter = ""
    private val search = if (options.size > 8) TextField("Filter…", icon = dev.jamie.uikit.ui.Icons.SEARCH) { filter = it } else null
    private var scroll = 0f
    private var highlighted = options.indexOfFirst(selected).coerceAtLeast(0)
    private val maxRows = 10
    private var resting: T? = options.firstOrNull(selected)
    private var restingSince = 0.0
    private var previewed: T? = resting

    private val visible get() = if (filter.isBlank()) options else options.filter { label(it).contains(filter, true) }

    fun measure() {
        w = maxOf(minWidth, (options.maxOfOrNull { Canvas.measure(label(it), Theme.CAPTION + 0.5f) } ?: 60f) + 26f)
        h = minOf(options.size, maxRows) * rowH + 6f + if (search != null) 18f else 0f
    }

    override fun open() {
        super.open()
    }

    override fun render(c: Canvas, ui: Ui) {
        val t = appear.update()
        c.push {
            c.scaleAround(0.95f + 0.05f * t, x + w / 2, y)
            c.shadow(x, y, w, h, Theme.rMd, 14f, Colors.mulAlpha(0x80000000.toInt(), t))
            c.panel(x, y, w, h, Colors.mulAlpha(Theme.surface, t), Colors.mulAlpha(Theme.borderStrong, t), Theme.rMd)
            var top = y + 3f
            search?.let {
                it.layout(x + 3f, top, w - 6f); it.render(c, ui); top += 18f
                if (ui.focused == null) ui.focus(it)
            }
            val list = visible
            val maxScroll = ((list.size - maxRows) * rowH).coerceAtLeast(0f)
            scroll = scroll.coerceIn(0f, maxScroll)
            c.clip(x, top, w, h - (top - y) - 3f) {
                list.forEachIndexed { i, opt ->
                    val ry = top + i * rowH - scroll
                    if (ry + rowH < top || ry > y + h) return@forEachIndexed
                    val hovered = ui.over(x + 3f, ry, w - 6f, rowH)
                    if (hovered) highlighted = i
                    if (i == highlighted) c.rect(x + 3f, ry, w - 6f, rowH, Colors.withAlpha(Theme.accent, 0.16f), Theme.rSm)
                    val sel = selected(opt)
                    if (multi) {
                        c.rect(x + 7f, ry + 3.5f, 7f, 7f, if (sel) Theme.accent else Theme.surfaceHi, 2f)
                        if (!sel) c.outline(x + 7f, ry + 3.5f, 7f, 7f, Theme.borderStrong, 2f)
                        if (sel) c.icon(dev.jamie.uikit.ui.Icons.CHECK, x + 7.5f, ry + 4f, 6f, Theme.accentText, Weight.BOLD)
                    } else if (sel) c.circle(x + 10f, ry + rowH / 2, 2f, Theme.accent)
                    c.text(label(opt), x + 17f, ry + 3.4f, if (sel) Theme.text else Theme.textDim, Theme.CAPTION + 0.5f, if (sel) Weight.MEDIUM else Weight.REGULAR)
                }
            }
            preview?.let { previewRest(list.getOrNull(highlighted), it) }
        }
    }

    /** Debounce: sweeping the mouse over the list plays nothing; resting on an option plays it once. */
    private fun previewRest(opt: T?, play: (T) -> Unit) {
        val now = dev.jamie.uikit.core.Clock.now
        if (opt != resting) { resting = opt; restingSince = now }
        if (opt != null && opt != previewed && now - restingSince >= PREVIEW_DELAY) { previewed = opt; play(opt) }
    }

    companion object {
        const val PREVIEW_DELAY = 0.22
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (!contains(ui.mouseX, ui.mouseY)) return false
        search?.let { if (it.contains(ui.mouseX, ui.mouseY)) return it.mouseDown(ui, button) }
        val top = y + 3f + if (search != null) 18f else 0f
        val i = ((ui.mouseY - top + scroll) / rowH).toInt()
        visible.getOrNull(i)?.let(choose)
        return true
    }

    override fun scroll(ui: Ui, amount: Float): Boolean {
        scroll -= amount * rowH * 2; return true
    }

    override fun key(ui: Ui, key: Int, mods: Int): Boolean {
        val list = visible
        when (key) {
            Keys.DOWN -> highlighted = (highlighted + 1).coerceAtMost(list.lastIndex)
            Keys.UP -> highlighted = (highlighted - 1).coerceAtLeast(0)
            Keys.ENTER, Keys.KP_ENTER -> list.getOrNull(highlighted)?.let(choose)
            else -> return search?.key(ui, key, mods) ?: false
        }
        if (highlighted * rowH < scroll) scroll = highlighted * rowH
        if ((highlighted + 1) * rowH > scroll + maxRows * rowH) scroll = (highlighted + 1 - maxRows) * rowH
        return true
    }

    override fun char(ui: Ui, cp: Int): Boolean = search?.char(ui, cp) ?: false
}

/** Key-binding capture button. */
class KeyButton(val get: () -> Int, val set: (Int) -> Unit) : Widget() {
    private var listening = false
    private val hover = Ease(0f)

    init { w = 80f; h = 14f }

    override fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; return h
    }

    override fun onBlur() {
        listening = false
    }

    override fun render(c: Canvas, ui: Ui) {
        val hv = hover.apply { target = if (hovered(ui) || listening) 1f else 0f }.update()
        val pulse = if (listening) (0.5f + 0.5f * kotlin.math.sin(dev.jamie.uikit.core.Clock.now * 6).toFloat()) else 0f
        c.rect(x, y, w, h, Colors.shade(Theme.surfaceHi, hv * 0.05f), Theme.rSm)
        c.outline(x, y, w, h, if (listening) Colors.withAlpha(Theme.accent, 0.5f + pulse * 0.5f) else Theme.border, Theme.rSm)
        val text = if (listening) "Press a key…" else keyName(get())
        c.text(text, x + w / 2, y + 3.4f, if (listening) Theme.accent else Theme.text, Theme.CAPTION + 0.5f, Weight.MEDIUM, Align.CENTER)
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (listening) {
            set(-100 - button); listening = false; ui.focus(null); return true
        }
        if (!hovered(ui)) return false
        if (button == 0) {
            listening = true; ui.focus(this)
        } else if (button == 1) set(-1)
        return true
    }

    override fun key(ui: Ui, key: Int, mods: Int): Boolean {
        if (!listening) return false
        set(if (key == Keys.ESCAPE || key == Keys.BACKSPACE) -1 else key)
        listening = false
        ui.focus(null)
        return true
    }

    companion object {
        fun keyName(code: Int): String = when {
            code == -1 -> "None"
            code <= -100 -> when (-100 - code) {
                0 -> "Left Click"; 1 -> "Right Click"; 2 -> "Middle Click"; else -> "Mouse ${-100 - code + 1}"
            }
            else -> dev.jamie.uikit.compat.Game.keyName(code)
        }
    }
}

/** Colour swatch that opens a [ColorPopover]. */
class ColorSwatch(val get: () -> Int, val set: (Int) -> Unit, val alpha: Boolean, val chroma: (() -> Boolean)?, val setChroma: ((Boolean) -> Unit)?) : Widget() {
    init { w = 34f; h = 14f }

    override fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; return h
    }

    override fun render(c: Canvas, ui: Ui) {
        val col = if (chroma?.invoke() == true) Colors.chroma(get()) else get()
        if (Colors.a(col) < 255) Checker.draw(c, x, y, w, h, Theme.rSm)
        c.rect(x, y, w, h, col, Theme.rSm)
        c.outline(x, y, w, h, if (hovered(ui)) Theme.borderStrong else Theme.border, Theme.rSm)
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (!hovered(ui) || button != 0) return false
        val p = ColorPopover(get, set, alpha, chroma, setChroma)
        p.placeNear(ui, x, y, w, h)
        ui.openPopover(p)
        return true
    }
}

object Checker {
    fun draw(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float) {
        c.rect(x, y, w, h, 0xFFE0E0E0.toInt(), r)
        c.clip(x, y, w, h) {
            var i = 0
            var yy = y
            while (yy < y + h) {
                var xx = x + if ((i % 2) == 0) 0f else 3f
                while (xx < x + w) { c.rect(xx, yy, 3f, 3f, 0xFFB0B0B0.toInt()); xx += 6f }
                yy += 3f; i++
            }
        }
    }
}

/** SV square + hue strip + alpha strip + hex field + chroma toggle. */
class ColorPopover(val get: () -> Int, val set: (Int) -> Unit, val alpha: Boolean, val chroma: (() -> Boolean)?, val setChroma: ((Boolean) -> Unit)?) : Popover() {
    private var hsv = Colors.toHsv(get())
    private var a = Colors.a(get()) / 255f
    private var drag = 0 // 1 sv, 2 hue, 3 alpha
    private val hex = TextField("RRGGBBAA", Colors.hex(get(), alpha), 8) { s -> Colors.parseHex(s)?.let { v -> apply(v, updateHex = false) } }
    private val chromaSwitch = if (chroma != null && setChroma != null) Switch(chroma, setChroma) else null

    init { w = 132f; h = 138f + if (chromaSwitch != null) 16f else 0f }

    private fun apply(v: Int, updateHex: Boolean = true) {
        hsv = Colors.toHsv(v); a = Colors.a(v) / 255f
        set(v)
        if (updateHex) hex.setText(Colors.hex(v, alpha), notify = false)
    }

    private fun current() = Colors.hsv(hsv[0], hsv[1], hsv[2], if (alpha) (a * 255).roundToInt() else 255)

    private val sq get() = floatArrayOf(x + 6f, y + 6f, w - 12f, 82f)
    private val hueY get() = y + 94f
    private val alphaY get() = y + 104f

    override fun render(c: Canvas, ui: Ui) {
        val t = appear.update()
        c.push {
            c.scaleAround(0.95f + 0.05f * t, x + w / 2, y)
            c.shadow(x, y, w, h, Theme.rMd, 16f, Colors.mulAlpha(0x80000000.toInt(), t))
            c.panel(x, y, w, h, Theme.surface, Theme.borderStrong, Theme.rMd)
            val (sx, sy, sw, sh) = sq
            val pure = Colors.hsv(hsv[0], 1f, 1f)
            c.gradientH(sx, sy, sw, sh, 0xFFFFFFFF.toInt(), pure, Theme.rSm)
            c.gradientV(sx, sy, sw, sh, 0x00000000, 0xFF000000.toInt(), Theme.rSm)
            val px = sx + hsv[1] * sw; val py = sy + (1 - hsv[2]) * sh
            c.ring(px, py, 3.5f, 0xFFFFFFFF.toInt(), 1.5f)
            // hue strip: 6 gradient segments
            val segW = sw / 6f
            for (i in 0 until 6) {
                val c0 = Colors.hsv(i / 6f, 1f, 1f); val c1 = Colors.hsv((i + 1) / 6f, 1f, 1f)
                val rl = if (i == 0) 3f else 0f; val rr = if (i == 5) 3f else 0f
                dev.jamie.uikit.compat.render.GuiElements.shape(c.g, sx + i * segW, hueY, sx + (i + 1) * segW + 0.5f, hueY + 6f, c0, c1, c1, c0, rl, rr, rr, rl, 0f)
            }
            c.ring(sx + hsv[0] * sw, hueY + 3f, 3.5f, 0xFFFFFFFF.toInt(), 1.5f)
            if (alpha) {
                Checker.draw(c, sx, alphaY, sw, 6f, 3f)
                val opaque = Colors.hsv(hsv[0], hsv[1], hsv[2])
                c.gradientH(sx, alphaY, sw, 6f, opaque and 0xFFFFFF, opaque, 3f)
                c.ring(sx + a * sw, alphaY + 3f, 3.5f, 0xFFFFFFFF.toInt(), 1.5f)
            }
            hex.layout(sx, y + 116f, sw - 20f); hex.render(c, ui)
            c.rect(sx + sw - 16f, y + 116f, 16f, 14f, current(), Theme.rSm)
            chromaSwitch?.let {
                c.text("Chroma", sx, y + 136f, Theme.textDim, Theme.CAPTION + 0.5f)
                it.layout(sx + sw - it.w, y + 135f, 0f); it.render(c, ui)
            }
        }
    }

    private fun updateDrag(ui: Ui) {
        val (sx, sy, sw, sh) = sq
        when (drag) {
            1 -> { hsv[1] = ((ui.mouseX - sx) / sw).coerceIn(0f, 1f); hsv[2] = 1f - ((ui.mouseY - sy) / sh).coerceIn(0f, 1f) }
            2 -> hsv[0] = ((ui.mouseX - sx) / sw).coerceIn(0f, 0.999f)
            3 -> a = ((ui.mouseX - sx) / sw).coerceIn(0f, 1f)
        }
        val v = current()
        set(v)
        hex.setText(Colors.hex(v, alpha), notify = false)
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        if (!contains(ui.mouseX, ui.mouseY)) return false
        val (sx, sy, sw, sh) = sq
        drag = when {
            ui.mouseX in sx..sx + sw && ui.mouseY in sy..sy + sh -> 1
            ui.mouseX in sx - 3..sx + sw + 3 && ui.mouseY in hueY - 2..hueY + 8 -> 2
            alpha && ui.mouseX in sx - 3..sx + sw + 3 && ui.mouseY in alphaY - 2..alphaY + 8 -> 3
            else -> 0
        }
        if (drag != 0) {
            ui.captured = this; updateDrag(ui); return true
        }
        if (hex.contains(ui.mouseX, ui.mouseY)) return hex.mouseDown(ui, button)
        chromaSwitch?.let { if (it.mouseDown(ui, button)) return true }
        return true
    }

    override fun mouseDrag(ui: Ui, button: Int) {
        if (drag != 0) updateDrag(ui)
    }

    override fun mouseUp(ui: Ui, button: Int) {
        drag = 0
    }

    override fun key(ui: Ui, key: Int, mods: Int) = hex.key(ui, key, mods)
    override fun char(ui: Ui, cp: Int) = hex.char(ui, cp)
}


/** "Are you sure?" popover for destructive actions. */
class ConfirmPopover(val message: String, val confirmLabel: String, val onConfirm: () -> Unit) : Popover() {
    private val yes = Button(confirmLabel, ButtonStyle.DANGER) { onConfirm(); close?.invoke() }
    private val no = Button("Cancel", ButtonStyle.SUBTLE) { close?.invoke() }
    var close: (() -> Unit)? = null

    init { w = 170f; h = 52f }

    override fun render(c: Canvas, ui: Ui) {
        close = { ui.closePopover() }
        val t = appear.update()
        c.push {
            c.scaleAround(0.95f + 0.05f * t, x + w / 2, y + h)
            c.shadow(x, y, w, h, Theme.rMd, 16f, Colors.mulAlpha(0x80000000.toInt(), t))
            c.panel(x, y, w, h, Theme.surface, Theme.borderStrong, Theme.rMd)
            c.text(message, x + 9f, y + 8f, Theme.text, Theme.CAPTION + 0.5f, Weight.MEDIUM)
            yes.w = yes.measure(c); no.w = no.measure(c)
            yes.layout(x + w - yes.w - 8f, y + h - 22f, yes.w); no.layout(x + w - yes.w - no.w - 14f, y + h - 22f, no.w)
            yes.render(c, ui); no.render(c, ui)
        }
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        close = { ui.closePopover() }
        return yes.mouseDown(ui, button) || no.mouseDown(ui, button) || contains(ui.mouseX, ui.mouseY)
    }
}

/** Smooth scrolling container state: wheel sets a target, a spring follows, content is clamped. */
class ScrollState {
    val spring = Spring(0f, 300f, 1f)
    var contentH = 0f
    var viewH = 0f
    val max get() = (contentH - viewH).coerceAtLeast(0f)
    val offset get() = spring.value

    fun update() {
        spring.target = spring.target.coerceIn(0f, max)
        spring.update()
    }

    fun scrollBy(px: Float) {
        spring.target = (spring.target + px).coerceIn(0f, max)
    }

    fun ensureVisible(top: Float, bottom: Float) {
        if (top < spring.target) spring.target = top
        else if (bottom > spring.target + viewH) spring.target = bottom - viewH
    }

    fun drawBar(c: Canvas, x: Float, y: Float, alpha: Float = 1f) {
        if (max <= 0f) return
        val barH = (viewH * viewH / contentH).coerceAtLeast(16f)
        val by = y + (viewH - barH) * (offset / max)
        c.rect(x, by, 2.5f, barH, Colors.withAlpha(Theme.text, 0.16f * alpha), 1.25f)
    }
}
