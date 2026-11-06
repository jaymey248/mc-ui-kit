package dev.jamie.uikit.config

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * A single configurable value. Settings are plain objects owned by a [SettingHost] (a Feature or a
 * HUD element); read them with `setting()` or `setting.value`.
 *
 * Every setting can:
 *  - own children ([parent]) which the UI nests and, for toggles, reveals only while the parent is on
 *  - be hidden conditionally ([visibleWhen])
 *  - be reset to its default, carry a [description] and search [tags]
 *  - notify listeners on change ([onChange])
 */
abstract class Setting<T>(
    val name: String,
    val default: T,
    key: String? = null,
) {
    /** Stable persistence key. Derived from the name unless given; give one explicitly before renaming. */
    val key: String = key ?: keyOf(name)

    var description: String = ""
    var tags: List<String> = emptyList()
    var parent: Setting<*>? = null
        internal set
    internal val children = mutableListOf<Setting<*>>()
    internal var visibility: (() -> Boolean)? = null
    internal val listeners = mutableListOf<(T) -> Unit>()
    lateinit var host: SettingHost
        internal set

    /** [host], or null for a setting nobody registered. */
    val hostOrNull: SettingHost? get() = if (::host.isInitialized) host else null

    open var value: T = default
        set(v) {
            val c = coerce(v)
            if (c == field) return
            if (UndoStack.recording) UndoStack.record(this, field)
            field = c
            listeners.forEach { it(c) }
            if (::host.isInitialized) host.markDirty()
        }

    operator fun invoke(): T = value

    open fun coerce(v: T): T = v
    open val isDefault: Boolean get() = value == default
    open fun reset() {
        value = default
    }

    /** This setting's own visibility condition, ignoring ancestors. */
    val ownVisible: Boolean get() = visibility?.invoke() != false

    /** Whether the UI should show this row right now (own condition and every ancestor visible). */
    val visible: Boolean
        get() {
            if (visibility?.invoke() == false) return false
            val p = parent ?: return true
            if (!p.visible) return false
            return p !is BoolSetting || p.value
        }

    val childSettings: List<Setting<*>> get() = children

    internal fun markHostDirty() {
        if (::host.isInitialized) host.markDirty()
    }

    abstract fun toJson(): JsonElement
    abstract fun fromJson(json: JsonElement)

    companion object {
        /** The persistence key derived from a setting name ("S+ message" → `s_plus_message`). */
        fun keyOf(name: String) = name.lowercase().replace("+", " plus").replace(Regex("[^a-z0-9]+"), "_").trim('_')
    }
}

// Fluent configuration (desc / tags / visibleWhen / onChange / under) lives on SettingHost as member
// extensions, so every host can chain them without imports and chains keep the concrete setting type.

/**
 * Undo history for settings changed in the menu. Rapid changes to the same setting (slider drags,
 * typing) merge into one step.
 */
object UndoStack {
    var recording = false
    private class Step(val setting: Setting<*>, val old: Any?, var at: Long)
    private val steps = ArrayDeque<Step>()

    fun record(s: Setting<*>, old: Any?) {
        if (!s.ownVisible) return // internal state (UI prefs, HUD positions) isn't user-undoable here
        val now = System.currentTimeMillis()
        val top = steps.lastOrNull()
        // Each list add/remove is its own step; other settings merge rapid changes.
        if (top != null && top.setting === s && s !is GroupListSetting<*> && now - top.at < 1500) { top.at = now; return }
        steps.addLast(Step(s, old, now))
        if (steps.size > 100) steps.removeFirst()
    }

    /** Reverts the last change; returns the setting's name or null if nothing to undo. */
    @Suppress("UNCHECKED_CAST")
    fun undo(): String? {
        val step = steps.removeLastOrNull() ?: return null
        val was = recording
        recording = false
        (step.setting as Setting<Any?>).value = step.old
        recording = was
        return step.setting.name
    }
}

/**
 * A switch over a value someone else owns (say, a field of the mod's own config). Reads and writes go straight
 * through, so it can never disagree with its owner, and Config does not persist it.
 */
class BoundBool(name: String, private val read: () -> Boolean, private val write: (Boolean) -> Unit) : BoolSetting(name, read()) {
    override var value: Boolean
        get() = read()
        set(v) { if (v != read()) write(v) }
}

/**
 * [BoundBool] for a slider. Dragging moves in [step]s; a value typed into the chip is kept exact
 * ([writeExact]), clamped to the range.
 */
class BoundInt(
    name: String, min: Int, max: Int, step: Int,
    private val read: () -> Int, private val write: (Int) -> Unit,
    val format: (Int) -> String,
) : IntSetting(name, read(), min, max, step) {
    override var value: Int
        get() = read()
        set(v) { val c = coerce(v); if (c != read()) write(c) }

    fun writeExact(v: Int) = write(v.coerceIn(min, max))
}

open class BoolSetting(name: String, default: Boolean, key: String? = null) : Setting<Boolean>(name, default, key) {
    fun toggle() {
        value = !value
    }

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        value = json.asBoolean
    }
}

open class IntSetting(name: String, default: Int, val min: Int, val max: Int, val step: Int = 1, val unit: String = "", key: String? = null) :
    Setting<Int>(name, default, key) {
    override fun coerce(v: Int) = (((v - min + step / 2) / step) * step + min).coerceIn(min, max)
    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        value = json.asInt
    }
}

class FloatSetting(name: String, default: Float, val min: Float, val max: Float, val step: Float = 0.01f, val unit: String = "", key: String? = null) :
    Setting<Float>(name, default, key) {
    override fun coerce(v: Float): Float {
        val stepped = if (step > 0f) Math.round((v - min) / step) * step + min else v
        return stepped.coerceIn(min, max)
    }

    val decimals: Int get() = when {
        step >= 1f -> 0
        step >= 0.1f -> 1
        else -> 2
    }

    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        value = json.asFloat
    }
}

/** Implement on enums used in choices for nice labels. */
interface Labeled {
    val label: String
}

/** Implement on enums of sounds: a choice of them previews each option in the menu (hover, pick, replay button). */
interface SoundOption {
    /** Plays this sound once as a preview, replacing any preview still playing (a silent option just stops it). */
    fun preview(volume: Float = 1f, pitch: Float = 1f)
}

fun Enum<*>.displayLabel(): String = (this as? Labeled)?.label
    ?: name.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

class ChoiceSetting<E : Enum<E>>(name: String, default: E, key: String? = null) : Setting<E>(name, default, key) {
    val options: List<E> = default.declaringJavaClass.enumConstants.toList()
    override fun toJson() = JsonPrimitive(value.name)
    override fun fromJson(json: JsonElement) {
        options.firstOrNull { it.name == json.asString }?.let { value = it }
    }
}

class MultiChoiceSetting<E : Enum<E>>(name: String, default: Set<E>, val options: List<E>, key: String? = null) :
    Setting<Set<E>>(name, default, key) {
    /** What the UI shows when nothing is selected ("None", "Everywhere", "Any floor"…). */
    var emptyLabel = "None"
    operator fun contains(e: E) = e in value
    fun toggle(e: E) {
        value = if (e in value) value - e else value + e
    }

    override fun toJson() = JsonArray().apply { value.forEach { add(it.name) } }
    override fun fromJson(json: JsonElement) {
        value = json.asJsonArray.mapNotNull { j -> options.firstOrNull { it.name == j.asString } }.toSet()
    }
}

/**
 * A set of ids picked from a list that is only known at runtime (registry entries, item names…). The UI is a
 * searchable checkbox list; [options] is asked each time it opens.
 */
class StringSetSetting(name: String, default: Set<String>, val options: () -> List<String>, val label: (String) -> String = { it }, key: String? = null) :
    Setting<Set<String>>(name, default, key) {
    /** What the UI shows when nothing is selected. */
    var emptyLabel = "None"
    operator fun contains(id: String) = id in value
    fun toggle(id: String) {
        value = if (id in value) value - id else value + id
    }

    override fun toJson() = JsonArray().apply { value.sorted().forEach { add(it) } }
    override fun fromJson(json: JsonElement) {
        value = json.asJsonArray.map { it.asString }.toSet()
    }
}

/** ARGB colour with optional animated chroma. Read [argb] for the colour to draw this frame. */
class ColorSetting(name: String, default: Int, val alpha: Boolean = true, key: String? = null, val defaultChroma: Boolean = false) : Setting<Int>(name, default, key) {
    var chroma = defaultChroma
        set(v) {
            field = v
            if (::host.isInitialized) host.markDirty()
        }

    override val isDefault: Boolean get() = value == default && chroma == defaultChroma
    override fun reset() {
        value = default
        chroma = defaultChroma
    }

    val argb: Int get() = if (chroma) dev.jamie.uikit.ui.Colors.chroma(value) else value

    override fun toJson() = JsonPrimitive(if (chroma) "chroma:%08X".format(value) else "%08X".format(value))
    override fun fromJson(json: JsonElement) {
        val s = json.asString
        chroma = s.startsWith("chroma:")
        value = s.removePrefix("chroma:").toLong(16).toInt()
    }
}

class TextSetting(name: String, default: String, val placeholder: String = "", val maxLength: Int = 256, key: String? = null) :
    Setting<String>(name, default, key) {
    override fun coerce(v: String) = v.take(maxLength)
    override fun toJson() = JsonPrimitive(value)
    override fun fromJson(json: JsonElement) {
        value = json.asString
    }
}

class StringListSetting(name: String, default: List<String>, val placeholder: String = "", key: String? = null) :
    Setting<List<String>>(name, default, key) {
    override fun toJson() = JsonArray().apply { value.forEach { add(it) } }
    override fun fromJson(json: JsonElement) {
        value = json.asJsonArray.map { it.asString }
    }
}

/** Key (GLFW key code, or mouse button as `-100 - button`) plus modifier mask. -1 = unbound. */
class KeySetting(name: String, default: Int = -1, key: String? = null) : Setting<Int>(name, default, key) {
    var modifiers = 0
    val bound: Boolean get() = value != -1

    override fun toJson() = JsonPrimitive("$value:$modifiers")
    override fun fromJson(json: JsonElement) {
        val parts = json.asString.split(':')
        value = parts[0].toInt()
        modifiers = parts.getOrNull(1)?.toInt() ?: 0
    }
}

/**
 * A list the user grows with "+": every entry is its own [SettingHost] built by [factory], so it can hold any
 * settings. The menu shows each entry as a collapsible subheading named by [entryName] with an optional dim
 * [summary]. Persisted as a JSON array of entry objects (settings by key); add and remove are undoable.
 */
class GroupListSetting<T : SettingHost>(
    name: String,
    val factory: () -> T,
    val entryName: (Int, T) -> String,
    val summary: (T) -> String = { "" },
    key: String? = null,
) : Setting<List<T>>(name, emptyList(), key) {
    /** Appends a fresh entry and returns it. */
    fun add(): T = entry().also { value = value + it }

    fun remove(entry: SettingHost) {
        value = value.filter { it !== entry }
    }

    // Entries of this list are always T (only [entry] builds them), so the casts below are safe.
    @Suppress("UNCHECKED_CAST")
    fun nameOf(entry: SettingHost, index: Int) = entryName(index, entry as T)

    @Suppress("UNCHECKED_CAST")
    fun summaryOf(entry: SettingHost) = summary(entry as T)

    private fun entry(): T = factory().also { it.listOwner = this }

    override fun toJson() = JsonArray().apply {
        for (e in value) add(JsonObject().apply { for (s in e.settings) if (s.persistent) add(s.key, s.toJson()) })
    }

    override fun fromJson(json: JsonElement) {
        value = json.asJsonArray.map { j ->
            val obj = j.asJsonObject
            entry().also { e -> for (s in e.settings) if (s.persistent) obj.get(s.key)?.let(s::fromJson) }
        }
    }
}

/** A button in the UI that runs [action]. Not persisted. */
class ActionSetting(name: String, val buttonLabel: String, val action: () -> Unit) : Setting<Unit>(name, Unit) {
    override fun toJson(): JsonElement = JsonPrimitive(0)
    override fun fromJson(json: JsonElement) {}
}

/** A collapsible header for grouping. Children are attached with [Setting.under]. Not persisted. */
class GroupSetting(name: String, val startExpanded: Boolean = false) : Setting<Unit>(name, Unit) {
    override fun toJson(): JsonElement = JsonPrimitive(0)
    override fun fromJson(json: JsonElement) {}
}

/** Read-only paragraph (hints, warnings). */
class InfoSetting(name: String, val text: () -> String) : Setting<Unit>(name, Unit) {
    override fun toJson(): JsonElement = JsonPrimitive(0)
    override fun fromJson(json: JsonElement) {}
}

/** Links a HUD element into a settings list: an "Edit position" button, with the element's own settings under it. */
class HudLinkSetting(val element: dev.jamie.uikit.ui.hud.HudElement) : Setting<Unit>(element.name, Unit, "hud_" + element.id) {
    override fun toJson(): JsonElement = JsonPrimitive(0)
    override fun fromJson(json: JsonElement) {}
}

val Setting<*>.persistent: Boolean
    get() = this !is ActionSetting && this !is GroupSetting && this !is InfoSetting && this !is HudLinkSetting && this !is BoundBool && this !is BoundInt
