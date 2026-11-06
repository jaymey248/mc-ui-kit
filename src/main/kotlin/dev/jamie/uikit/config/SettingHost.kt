package dev.jamie.uikit.config

/**
 * Anything that owns settings (features, HUD elements, the theme). Provides the setting DSL so every
 * owner declares settings identically:
 *
 * ```
 * val sound = toggle("Play sound", true).desc("Ping when it spawns")
 * val volume = slider("Volume", 1f, 0f, 2f).under(sound)
 * ```
 */
abstract class SettingHost {
    val settings = mutableListOf<Setting<*>>()

    /** The [GroupListSetting] this host is an entry of, if any; its changes then dirty that setting's host. */
    var listOwner: Setting<*>? = null
        internal set

    /** A toggle of this host that a settings list draws as a mini card header, whose click opens its children. */
    open val headerToggle: Setting<*>? get() = null

    open fun markDirty() {
        val o = listOwner
        if (o != null) o.markHostDirty() else Config.markDirty()
    }

    open fun <S : Setting<*>> register(s: S): S {
        require(settings.none { it.key == s.key }) { "Duplicate setting key '${s.key}' in $this" }
        s.host = this
        settings += s
        return s
    }

    /** The persistence key for a DSL-declared setting. */
    @PublishedApi internal open fun keyFor(name: String, key: String?): String? = key

    /** Settings that are not nested under another — the UI renders these and recurses into children. */
    val rootSettings: List<Setting<*>> get() = settings.filter { it.parent == null }

    /** [settings] plus, recursively, the settings inside every [GroupListSetting] entry (for search). */
    val allSettings: List<Setting<*>>
        get() = settings + settings.filterIsInstance<GroupListSetting<*>>().flatMap { l -> l.value.flatMap { it.allSettings } }

    // ── fluent configuration (type-preserving) ──
    fun <S : Setting<*>> S.desc(text: String): S = apply { description = text }
    fun <S : Setting<*>> S.tags(vararg t: String): S = apply { tags = t.toList() }
    fun <S : Setting<*>> S.visibleWhen(cond: () -> Boolean): S = apply { visibility = cond }
    fun <T, S : Setting<T>> S.onChange(fn: (T) -> Unit): S = apply { listeners += fn }

    /** Nests this setting under [p]. If [p] is a toggle, this row is only shown while it is on. */
    fun <S : Setting<*>> S.under(p: Setting<*>): S = apply {
        parent?.children?.remove(this)
        parent = p
        p.children += this
    }

    fun toggle(name: String, default: Boolean = false, key: String? = null) = register(BoolSetting(name, default, keyFor(name, key)))
    fun slider(name: String, default: Int, min: Int, max: Int, step: Int = 1, unit: String = "", key: String? = null) =
        register(IntSetting(name, default, min, max, step, unit, keyFor(name, key)))

    fun slider(name: String, default: Float, min: Float, max: Float, step: Float = 0.01f, unit: String = "", key: String? = null) =
        register(FloatSetting(name, default, min, max, step, unit, keyFor(name, key)))

    fun <E : Enum<E>> choice(name: String, default: E, key: String? = null) = register(ChoiceSetting(name, default, keyFor(name, key)))
    inline fun <reified E : Enum<E>> multiChoice(name: String, default: Set<E>, key: String? = null) =
        register(MultiChoiceSetting(name, default, enumValues<E>().toList(), keyFor(name, key)))

    /** Pick any number of ids from [options] (asked when the list opens) in a searchable checkbox list. */
    fun stringSet(name: String, default: Set<String>, options: () -> List<String>, label: (String) -> String = { it }, key: String? = null) =
        register(StringSetSetting(name, default, options, label, keyFor(name, key)))

    fun color(name: String, default: Int, alpha: Boolean = true, key: String? = null, chroma: Boolean = false) =
        register(ColorSetting(name, default, alpha, keyFor(name, key), chroma))
    fun text(name: String, default: String = "", placeholder: String = "", key: String? = null) =
        register(TextSetting(name, default, placeholder, key = keyFor(name, key)))

    fun stringList(name: String, default: List<String> = emptyList(), placeholder: String = "", key: String? = null) =
        register(StringListSetting(name, default, placeholder, keyFor(name, key)))

    fun keybind(name: String, default: Int = -1, key: String? = null) = register(KeySetting(name, default, keyFor(name, key)))

    /** A list the user adds entries to with "+"; each entry is a [T] from [factory] holding its own settings. */
    fun <T : SettingHost> groupList(
        name: String,
        entryName: (Int, T) -> String = { i, _ -> "$name ${i + 1}" },
        summary: (T) -> String = { "" },
        key: String? = null,
        factory: () -> T,
    ) = register(GroupListSetting(name, factory, entryName, summary, keyFor(name, key)))

    fun button(name: String, label: String, action: () -> Unit) = register(ActionSetting(name, label, action))
    fun group(name: String, expanded: Boolean = false) = register(GroupSetting(name, expanded))
    fun info(name: String, text: () -> String) = register(InfoSetting(name, text))
}
