package dev.jamie.uikit.ui.screen

import dev.jamie.uikit.config.*
import dev.jamie.uikit.ui.*
import dev.jamie.uikit.ui.widget.*

/**
 * Renders a tree of settings as rows: label + description on the left, control on the right,
 * children indented under their parent (revealed with animation when a parent toggle turns on).
 * A [GroupListSetting] renders as a header with "+ Add" followed by one collapsible subheading per entry.
 * Used by settings pages and the HUD editor's element popover.
 */
class SettingsList(
    private val roots: () -> List<Setting<*>>,
    private val highlight: (Setting<*>) -> Boolean = { false },
    private val onEditHud: (dev.jamie.uikit.ui.hud.HudElement) -> Unit = {},
) : Widget() {
    private val controls = HashMap<Setting<*>, Widget>()
    /** Open/closed reveal per parent setting or list entry. */
    private val reveal = HashMap<Any, Spring>()
    private val groupOpen = HashMap<GroupSetting, Boolean>()
    private val entryOpen = HashMap<SettingHost, Boolean>()
    private val moduleOpen = HashMap<Setting<*>, Boolean>()
    /** Appear/disappear spring per list entry, and the entries each list currently draws (removed ones until collapsed). */
    private val appear = HashMap<SettingHost, Spring>()
    private val shown = HashMap<GroupListSetting<*>, List<SettingHost>>()
    private val rowHover = HashMap<Any, Ease>()
    private val rows = ArrayList<Row>()

    /** One row: a setting, or (when [entry] is set) the subheading of a list entry. [alpha] is the product of ancestor reveals. */
    private class Row(val s: Setting<*>?, val entry: SettingHost?, val depth: Int, val alpha: Float) {
        var y = 0f; var h = 0f
    }

    companion object {
        const val ROW_PAD = 5f
        const val INDENT = 11f
        const val CONTROL_W = 112f
        const val ENTRY_H = 18f
    }

    private fun control(s: Setting<*>): Widget? = controls.getOrPut(s) { createControl(s) ?: return null }

    @Suppress("UNCHECKED_CAST")
    private fun createControl(s: Setting<*>): Widget? = when (s) {
        is BoolSetting -> Switch({ s.value }, { s.value = it })
        is BoundInt -> Slider(s.min.toFloat(), s.max.toFloat(), s.step.toFloat(), { s.value.toFloat() }, { s.value = Math.round(it) }, { s.writeExact(Math.round(it)) }) { s.format(it.toInt()) }
        is IntSetting -> Slider(s.min.toFloat(), s.max.toFloat(), s.step.toFloat(), { s.value.toFloat() }, { s.value = Math.round(it) }) { "${it.toInt()}${s.unit}" }
        is FloatSetting -> Slider(s.min, s.max, s.step, { s.value }, { s.value = it }) { "%.${s.decimals}f".format(it) + s.unit }
        // Casting to a concrete placeholder enum is safe: generics are erased and nothing checkcasts to it.
        is ChoiceSetting<*> -> choiceDropdown(s as ChoiceSetting<AnyEnum>)
        is MultiChoiceSetting<*> -> MultiButton(s as MultiChoiceSetting<AnyEnum>)
        is StringSetSetting -> StringSetButton(s)
        is ColorSetting -> ColorSwatch({ s.value }, { s.value = it }, s.alpha, { s.chroma }, { s.chroma = it })
        is KeySetting -> KeyButton({ s.value }, { s.value = it })
        is TextSetting -> TextField(s.placeholder, s.value, s.maxLength) { s.value = it }
        is ActionSetting -> Button(s.buttonLabel, ButtonStyle.SUBTLE) { s.action() }
        is HudLinkSetting -> Button("Edit position", ButtonStyle.SUBTLE, Icons.MOVE) { onEditHud(s.element) }
        is StringListSetting -> StringListEditor(s)
        is GroupListSetting<*> -> Button("Add", ButtonStyle.SUBTLE, Icons.PLUS) { entryOpen[s.add()] = true }
        else -> null
    }

    private enum class AnyEnum

    private fun <E : Enum<E>> choiceDropdown(s: ChoiceSetting<E>): Widget {
        if (s.options.firstOrNull() !is SoundOption) return Dropdown({ s.options }, { it.displayLabel() }, { s.value }, { s.value = it })
        // A "Volume" / "Pitch" slider next to the sound (same parent) shapes its preview too.
        fun sibling(word: String) = s.host.settings.firstOrNull { it is FloatSetting && it.parent === s.parent && word in it.name.lowercase() } as FloatSetting?
        val volume = sibling("volume"); val pitch = sibling("pitch")
        return SoundChoice(s) { (it as SoundOption).preview(volume?.value ?: 1f, pitch?.value ?: 1f) }
    }

    /** A sound choice: the dropdown previews options as you rest on and pick them; the speaker button left of it replays the current one. */
    private class SoundChoice<E : Enum<E>>(val s: ChoiceSetting<E>, val play: (E) -> Unit) : Widget() {
        private val drop = Dropdown({ s.options }, { it.displayLabel() }, { s.value }, { s.value = it }, play)
        private val hover = Ease(0f)
        private val press = Spring(1f, 700f, 0.7f)
        init { w = CONTROL_W; h = 14f }

        override fun layout(x: Float, y: Float, width: Float): Float {
            this.x = x; this.y = y
            drop.w = w - 18f; drop.layout(x + 18f, y, drop.w)
            return h
        }

        override fun render(c: Canvas, ui: Ui) {
            val over = ui.over(x, y, 14f, h)
            val hv = hover.apply { target = if (over) 1f else 0f }.update()
            c.push {
                c.scaleAround(press.update(), x + 7f, y + h / 2)
                c.rect(x, y, 14f, h, Colors.shade(Theme.surfaceHi, hv * 0.05f), Theme.rSm)
                c.outline(x, y, 14f, h, Colors.lerp(Theme.border, Colors.withAlpha(Theme.accent, 0.6f), hv), Theme.rSm)
                c.icon(Icons.VOLUME_2, x + 2.75f, y + 2.75f, 8.5f, Colors.lerp(Theme.textDim, Theme.accent, hv))
            }
            if (over) ui.tooltip(this, "Play ${s.value.displayLabel()}")
            drop.render(c, ui)
        }

        override fun mouseDown(ui: Ui, button: Int): Boolean {
            if (button == 0 && ui.over(x, y, 14f, h)) { press.value = 0.86f; play(s.value); return true }
            return drop.mouseDown(ui, button)
        }
    }

    /** Rows that should show right now, flattened with depth; [alpha] is how far the enclosing parents are revealed. */
    private fun collect(list: List<Setting<*>>, depth: Int, alpha: Float, out: MutableList<Row>) {
        for (s in list) {
            if (!s.visibleSelf()) continue
            out += Row(s, null, depth, alpha)
            if (s is GroupListSetting<*>) {
                collectEntries(s, depth + 1, alpha, out); continue
            }
            val kids = when (s) {
                is HudLinkSetting -> s.element.rootSettings
                else -> s.childSettings
            }
            if (kids.isEmpty()) continue
            val open = when (s) {
                is BoolSetting -> (if (s.isModule()) moduleOpen[s] == true else s.value) || kids.any { it.leadsToHighlight() }
                is GroupSetting -> groupOpen.getOrPut(s) { s.startExpanded } || kids.any { it.leadsToHighlight() }
                is HudLinkSetting -> true
                else -> true
            }
            val sp = reveal.getOrPut(s) { Spring(if (open) 1f else 0f, 420f, 0.9f) }
            sp.target = if (open) 1f else 0f
            if (open || sp.value > 0.01f) collect(kids, depth + 1, alpha * sp.value, out)
        }
    }

    /** One subheading per entry (sliding in when added, out when removed), each followed by its settings while open. */
    private fun collectEntries(l: GroupListSetting<*>, depth: Int, alpha: Float, out: MutableList<Row>) {
        for (e in shownEntries(l)) {
            val ap = appear.getOrPut(e) { Spring(0f, 420f, 0.9f) }
            ap.target = if (e in l.value) 1f else 0f
            val a = alpha * ap.value
            out += Row(null, e, depth, a)
            val open = entryOpen[e] == true || e.allSettings.any(highlight)
            val sp = reveal.getOrPut(e) { Spring(if (open) 1f else 0f, 420f, 0.9f) }
            sp.target = if (open) 1f else 0f
            if (open || sp.value > 0.01f) collect(e.rootSettings, depth + 1, a * sp.value, out)
        }
    }

    /** [l]'s entries plus removed ones still collapsing, in their old places; entries present when first drawn don't animate in. */
    private fun shownEntries(l: GroupListSetting<*>): List<SettingHost> {
        val cur = l.value
        val prev = shown[l] ?: cur.onEach { appear[it] = Spring(1f, 420f, 0.9f) }
        val out = cur.toMutableList<SettingHost>()
        prev.forEachIndexed { i, e ->
            if (e in cur) return@forEachIndexed
            if ((appear[e]?.value ?: 0f) < 0.01f) { forget(e); return@forEachIndexed }
            val before = prev.subList(0, i).lastOrNull { it in out }
            out.add(if (before == null) 0 else out.indexOf(before) + 1, e)
        }
        shown[l] = out
        return out
    }

    /** Drops the UI state of an entry that finished collapsing (undo can bring it back; it then starts fresh). */
    private fun forget(e: SettingHost) {
        e.allSettings.forEach { controls.remove(it); reveal.remove(it); rowHover.remove(it) }
        appear.remove(e); reveal.remove(e); entryOpen.remove(e); rowHover.remove(e)
    }

    /** This row or one nested under it is highlighted (a search hit), so its parents open to show it. */
    private fun Setting<*>.leadsToHighlight(): Boolean = highlight(this) || childSettings.any { it.leadsToHighlight() }

    /** Its host's [SettingHost.headerToggle]: drawn like a mini card header whose settings expand on click (the switch toggles it). */
    private fun Setting<*>.isModule() = hostOrNull?.headerToggle === this

    /** Own visibility condition only — parent state is handled by the tree walk (for animation). */
    private fun Setting<*>.visibleSelf(): Boolean = this is HudLinkSetting || ownVisible

    /** Shows the hover "↺"; never on lists, where one stray click would wipe every entry. */
    private val Setting<*>.resettable: Boolean get() = persistent && !isDefault && this !is GroupListSetting<*>

    private fun rowHeight(c: Canvas?, s: Setting<*>, width: Float): Float {
        val labelW = width - CONTROL_W - 14f
        val desc = s.description
        val descH = if (desc.isNotEmpty()) wrapCount(desc, labelW) * Theme.CAPTION * 1.25f else 0f
        return when (s) {
            is InfoSetting -> wrapCount(s.text(), width) * Theme.CAPTION * 1.25f + ROW_PAD * 2
            is GroupSetting -> 18f
            is StringListSetting -> 20f + (controls[s] as? StringListEditor)?.contentHeight() .let { it ?: 18f } + descH
            is GroupListSetting<*> -> maxOf(14f, Theme.BODY * 1.25f + descH + if (s.value.isEmpty()) Theme.CAPTION * 1.25f else 0f) + ROW_PAD * 2
            else -> maxOf(14f, Theme.BODY * 1.25f + descH) + ROW_PAD * 2
        }
    }

    private fun Row.baseHeight(width: Float) = if (entry != null) ENTRY_H else rowHeight(null, s!!, width)

    private fun wrapCount(text: String, width: Float): Int {
        var lines = 0
        for (para in text.split('\n')) {
            var line = ""
            lines++
            for (word in para.split(' ')) {
                val cand = if (line.isEmpty()) word else "$line $word"
                if (Canvas.measure(cand, Theme.CAPTION) > width && line.isNotEmpty()) { lines++; line = word } else line = cand
            }
        }
        return lines
    }

    override fun layout(x: Float, y: Float, width: Float): Float {
        this.x = x; this.y = y; this.w = width
        rows.clear()
        collect(roots(), 0, 1f, rows)
        var cy = y
        for (r in rows) {
            r.y = cy
            r.h = r.baseHeight(width - r.depth * INDENT) * r.alpha
            cy += r.h
        }
        h = cy - y
        return h
    }

    /** Top and bottom of the first highlighted setting row (from the last [layout]). */
    fun firstHighlight(): Pair<Float, Float>? = rows.firstOrNull { it.h > 0.5f && it.s?.let(highlight) == true }?.let { it.y to it.y + it.h }

    override fun render(c: Canvas, ui: Ui) {
        reveal.values.forEach { it.update() }
        appear.values.forEach { it.update() }
        for (r in rows) {
            if (r.h < 0.5f) continue
            val indent = r.depth * INDENT
            val rx = x + indent
            val rw = w - indent
            c.clip(x - 2f, r.y, w + 4f, r.h) { if (r.entry != null) drawEntry(c, ui, r.entry, rx, r.y, rw) else drawRow(c, ui, r.s!!, rx, r.y, rw) }
            if (r.depth > 0) c.rect(rx - INDENT / 2 - 0.5f, r.y, 1f, r.h, Colors.withAlpha(Theme.text, 0.05f))
        }
    }

    private fun listFor(e: SettingHost) = e.listOwner as GroupListSetting<*>

    /** An entry's subheading: chevron, name, dim summary, remove button. */
    private fun drawEntry(c: Canvas, ui: Ui, e: SettingHost, rx: Float, ry: Float, rw: Float) {
        val l = listFor(e)
        val hovered = ui.over(rx - 4f, ry, rw + 4f, ENTRY_H)
        val hv = rowHover.getOrPut(e) { Ease(0f) }.apply { target = if (hovered) 1f else 0f }.update()
        val open = reveal[e]?.value ?: 0f
        val hl = e.allSettings.any(highlight)
        c.rect(rx - 4f, ry + 1.5f, rw + 4f, ENTRY_H - 3f, if (hl) Colors.withAlpha(Theme.accent, 0.1f) else Colors.withAlpha(Theme.text, 0.035f + 0.03f * hv), Theme.rSm)
        c.push {
            c.translate(rx + 3f, ry + ENTRY_H / 2)
            c.g.pose().rotate((Math.PI / 2 * open).toFloat())
            c.icon(Icons.CHEVRON_RIGHT, -4f, -4f, 8f, Colors.lerp(Theme.textFaint, Theme.textDim, maxOf(hv, open)))
        }
        val name = l.nameOf(e, shown[l]?.indexOf(e) ?: 0)
        c.text(name, rx + 11f, ry + 5f, if (hl) Theme.accent else Theme.text, Theme.BODY, Weight.MEDIUM)
        val summary = l.summaryOf(e)
        val sx = rx + 17f + c.textWidth(name, Theme.BODY, Weight.MEDIUM)
        if (summary.isNotEmpty()) c.clip(sx, ry, (rx + rw - 18f - sx).coerceAtLeast(0f), ENTRY_H) { c.text(summary, sx, ry + 5.5f, Theme.textDim, Theme.CAPTION + 0.5f) }
        val over = ui.over(rx + rw - 15f, ry + 2f, 14f, ENTRY_H - 4f)
        c.icon(Icons.TRASH_2, rx + rw - 12f, ry + 4.5f, 9f, if (over) Theme.danger else Colors.lerp(Theme.textFaint, Theme.textDim, hv))
        if (over) ui.tooltip(e, "Remove")
    }

    private fun drawRow(c: Canvas, ui: Ui, s: Setting<*>, rx: Float, ry: Float, rw: Float) {
        val hov = rowHover.getOrPut(s) { Ease(0f) }
        val hovered = ui.over(rx, ry, rw, rowHeight(null, s, rw))
        hov.target = if (hovered) 1f else 0f
        val hv = hov.update()
        val hl = highlight(s)
        if (hv > 0.01f || hl) c.rect(rx - 4f, ry + 1f, rw + 8f, rowHeight(null, s, rw) - 2f,
            if (hl) Colors.withAlpha(Theme.accent, 0.08f + 0.04f * hv) else Colors.withAlpha(Theme.text, 0.025f * hv), Theme.rSm)
        when (s) {
            is GroupSetting -> {
                val open = groupOpen.getOrPut(s) { s.startExpanded }
                c.icon(if (open) Icons.CHEVRON_DOWN else Icons.CHEVRON_RIGHT, rx - 1f, ry + 4.5f, 8f, Theme.textDim)
                c.text(s.name.uppercase(), rx + 9f, ry + 5f, Theme.textDim, Theme.CAPTION, Weight.SEMIBOLD)
                c.rect(rx + 12f + c.textWidth(s.name.uppercase(), Theme.CAPTION, Weight.SEMIBOLD), ry + 9f, rw - 16f - c.textWidth(s.name.uppercase(), Theme.CAPTION, Weight.SEMIBOLD), 1f, Theme.border)
                return
            }
            is InfoSetting -> {
                c.paragraph(s.text(), rx, ry + ROW_PAD, rw, Theme.textDim, Theme.CAPTION)
                return
            }
            else -> {}
        }
        val labelW = rw - CONTROL_W - 14f
        val nameColor = if (hl) Theme.accent else Theme.text
        val isModule = s.isModule()
        c.text(s.name, rx, ry + ROW_PAD, nameColor, Theme.BODY, if (isModule) Weight.SEMIBOLD else Weight.MEDIUM)
        if (s.description.isNotEmpty()) c.paragraph(s.description, rx, ry + ROW_PAD + Theme.BODY * 1.25f, labelW, Theme.textDim, Theme.CAPTION)
        if (s is GroupListSetting<*> && s.value.isEmpty()) {
            val dy = if (s.description.isNotEmpty()) wrapCount(s.description, labelW) * Theme.CAPTION * 1.25f else 0f
            c.text("Nothing here yet · click Add to create one", rx, ry + ROW_PAD + Theme.BODY * 1.25f + dy, Theme.textFaint, Theme.CAPTION)
        }
        // reset button
        if (s.resettable) {
            val bx = rx + rw - CONTROL_W - 12f
            val over = ui.over(bx - 2f, ry + ROW_PAD - 1f, 10f, 12f)
            if (hovered) c.icon(Icons.ROTATE_CCW, bx - 1f, ry + ROW_PAD + 0.5f, 9f, if (over) Theme.accent else Theme.textFaint)
            if (over) ui.tooltip(s, "Reset to default")
        }
        val ctl = control(s) ?: return
        val cw = when (ctl) {
            is Switch -> ctl.w
            is ColorSwatch -> ctl.w
            is Button -> ctl.measure(c)
            is StringListEditor -> rw
            else -> CONTROL_W
        }
        if (ctl is StringListEditor) {
            ctl.layout(rx, ry + ROW_PAD + Theme.BODY * 1.25f + 2f + if (s.description.isNotEmpty()) wrapCount(s.description, labelW) * Theme.CAPTION * 1.25f else 0f, rw)
        } else {
            ctl.w = cw
            ctl.layout(rx + rw - cw, ry + ROW_PAD - 1f, cw)
        }
        ctl.render(c, ui)
        if (isModule && s.childSettings.isNotEmpty()) c.push {
            c.translate(rx + rw - cw - 12f, ry + ROW_PAD + 6f)
            c.g.pose().rotate((Math.PI * (reveal[s]?.value ?: 0f)).toFloat())
            c.icon(Icons.CHEVRON_DOWN, -4f, -4f, 8f, if (hovered) Theme.textDim else Theme.textFaint)
        }
    }

    override fun mouseDown(ui: Ui, button: Int): Boolean {
        for (r in rows) {
            if (r.h < 1f || ui.mouseY < r.y || ui.mouseY >= r.y + r.h) continue
            val rx = x + r.depth * INDENT
            val rw = w - r.depth * INDENT
            val e = r.entry
            if (e != null) {
                val l = listFor(e)
                if (e !in l.value || button != 0) return true // still collapsing after removal
                if (ui.over(rx + rw - 15f, r.y + 2f, 14f, ENTRY_H - 4f)) {
                    val name = l.nameOf(e, l.value.indexOf(e))
                    l.remove(e)
                    Toasts.show("Removed $name" + if (UndoStack.recording) " · Ctrl+Z to undo" else "", Theme.textDim)
                } else entryOpen[e] = entryOpen[e] != true
                return true
            }
            val s = r.s!!
            if (s is GroupSetting) {
                groupOpen[s] = !(groupOpen[s] ?: s.startExpanded); return true
            }
            if (s.resettable && ui.over(rx + rw - CONTROL_W - 14f, r.y + ROW_PAD - 1f, 10f, 12f)) {
                s.reset(); (controls[s] as? TextField)?.setText((s as? TextSetting)?.value ?: "", notify = false); return true
            }
            val ctl = controls[s] ?: continue
            if (ctl.mouseDown(ui, button)) return true
            // Clicking anywhere on a toggle row flips it — big hit targets feel better.
            if (s is BoolSetting && button == 0 && ui.mouseX >= rx && ui.mouseX < rx + rw) {
                if (s.isModule()) moduleOpen[s] = moduleOpen[s] != true else s.toggle()
                return true
            }
        }
        return false
    }

    override fun key(ui: Ui, key: Int, mods: Int): Boolean = controls.values.any { it.key(ui, key, mods) }
    override fun char(ui: Ui, cp: Int): Boolean = controls.values.any { it.char(ui, cp) }

    /** Multi-choice: shows a summary, opens a checkbox list. */
    private class MultiButton<E : Enum<E>>(val s: MultiChoiceSetting<E>) : Widget() {
        private val hover = Ease(0f)
        init { w = CONTROL_W; h = 14f }

        override fun layout(x: Float, y: Float, width: Float): Float { this.x = x; this.y = y; return h }

        override fun render(c: Canvas, ui: Ui) {
            val hv = hover.apply { target = if (hovered(ui)) 1f else 0f }.update()
            c.rect(x, y, w, h, Colors.shade(Theme.surfaceHi, hv * 0.05f), Theme.rSm)
            c.outline(x, y, w, h, Theme.border, Theme.rSm)
            val label = when (s.value.size) {
                0 -> s.emptyLabel
                s.options.size -> "All"
                1 -> s.value.first().displayLabel()
                else -> "${s.value.size} selected"
            }
            c.clip(x, y, w - 14f, h) { c.text(label, x + 6f, y + 3.4f, Theme.text, Theme.CAPTION + 0.5f) }
            c.icon(Icons.CHEVRON_DOWN, x + w - 12f, y + 3f, 8f, Theme.textDim)
        }

        override fun mouseDown(ui: Ui, button: Int): Boolean {
            if (!hovered(ui)) return false
            val p = ListPopover(s.options, { it.displayLabel() }, { it in s.value }, { s.toggle(it) }, w, multi = true)
            p.measure(); p.placeNear(ui, x, y, w, h); ui.openPopover(p)
            return true
        }
    }

    /** Searchable checkbox list over runtime options; the summary shows how many are picked. */
    private class StringSetButton(val s: StringSetSetting) : Widget() {
        private val hover = Ease(0f)
        init { w = CONTROL_W; h = 14f }

        override fun layout(x: Float, y: Float, width: Float): Float { this.x = x; this.y = y; return h }

        override fun render(c: Canvas, ui: Ui) {
            val hv = hover.apply { target = if (hovered(ui)) 1f else 0f }.update()
            c.rect(x, y, w, h, Colors.shade(Theme.surfaceHi, hv * 0.05f), Theme.rSm)
            c.outline(x, y, w, h, Theme.border, Theme.rSm)
            val label = when (s.value.size) { 0 -> s.emptyLabel; 1 -> s.label(s.value.first()); else -> "${s.value.size} selected" }
            c.clip(x, y, w - 14f, h) { c.text(label, x + 6f, y + 3.4f, Theme.text, Theme.CAPTION + 0.5f) }
            c.icon(Icons.CHEVRON_DOWN, x + w - 12f, y + 3f, 8f, Theme.textDim)
        }

        override fun mouseDown(ui: Ui, button: Int): Boolean {
            if (!hovered(ui)) return false
            // Picked entries first, then the rest alphabetically by label.
            val options = s.options().sortedWith(compareBy<String>({ it !in s.value }, { s.label(it) }))
            val p = ListPopover(options, s.label, { it in s.value }, { s.toggle(it) }, maxOf(w, 150f), multi = true)
            p.measure(); p.placeNear(ui, x, y, w, h); ui.openPopover(p)
            return true
        }
    }

    /** Editable list of strings with remove buttons and an add field. */
    private class StringListEditor(val s: StringListSetting) : Widget() {
        private val add = TextField(s.placeholder.ifEmpty { "Add entry…" }, icon = Icons.PLUS)

        init {
            add.onSubmit = {
                if (add.text.isNotBlank()) { s.value = s.value + add.text.trim(); add.setText("", notify = false) }
            }
        }

        fun contentHeight() = s.value.size * 15f + 16f

        override fun layout(x: Float, y: Float, width: Float): Float { this.x = x; this.y = y; this.w = width; h = contentHeight(); return h }

        override fun render(c: Canvas, ui: Ui) {
            s.value.forEachIndexed { i, entry ->
                val ry = y + i * 15f
                c.rect(x, ry, w, 13f, Theme.surfaceHi, Theme.rSm)
                c.text(entry, x + 5f, ry + 3f, Theme.text, Theme.CAPTION + 0.5f)
                val over = ui.over(x + w - 13f, ry, 13f, 13f)
                c.icon(Icons.X, x + w - 11f, ry + 2.5f, 8f, if (over) Theme.danger else Theme.textFaint)
            }
            add.layout(x, y + s.value.size * 15f, w); add.render(c, ui)
        }

        override fun mouseDown(ui: Ui, button: Int): Boolean {
            s.value.forEachIndexed { i, _ ->
                if (ui.over(x + w - 13f, y + i * 15f, 13f, 13f)) { s.value = s.value.toMutableList().also { it.removeAt(i) }; return true }
            }
            return add.mouseDown(ui, button)
        }

        override fun key(ui: Ui, key: Int, mods: Int) = add.key(ui, key, mods)
        override fun char(ui: Ui, cp: Int) = add.char(ui, cp)
    }
}
