package dev.jamie.uikit.ui

import dev.jamie.uikit.config.Config
import dev.jamie.uikit.config.Labeled
import dev.jamie.uikit.config.SettingHost
import dev.jamie.uikit.core.Kit

/**
 * Every colour, radius, size and motion constant the UI uses. Widgets and HUD panels read tokens from
 * here only — so a theme change restyles the whole mod consistently.
 */
object Theme : SettingHost() {
    enum class Preset(override val label: String, val bg: Int, val surface: Int, val surfaceHi: Int, val text: Int, val textDim: Int, val accent: Int) : Labeled {
        MIDNIGHT("Midnight", 0xFF0E0F13.toInt(), 0xFF16181E.toInt(), 0xFF1E2129.toInt(), 0xFFE8EAF0.toInt(), 0xFF9AA0AE.toInt(), 0xFF7C8CFF.toInt()),
        GRAPHITE("Graphite", 0xFF111111.toInt(), 0xFF1A1A1A.toInt(), 0xFF242424.toInt(), 0xFFEDEDED.toInt(), 0xFF9C9C9C.toInt(), 0xFF5EEAD4.toInt()),
        NORD("Nord", 0xFF1F2430.toInt(), 0xFF272E3B.toInt(), 0xFF303949.toInt(), 0xFFE5E9F0.toInt(), 0xFF9AA5B8.toInt(), 0xFF88C0D0.toInt()),
        ROSE("Rosé", 0xFF17131A.toInt(), 0xFF201A24.toInt(), 0xFF2A2230.toInt(), 0xFFF2E9F2.toInt(), 0xFFAE9FB2.toInt(), 0xFFF0A6CA.toInt()),
        OLED("OLED", 0xFF000000.toInt(), 0xFF0B0B0D.toInt(), 0xFF151519.toInt(), 0xFFF2F2F2.toInt(), 0xFF8E8E96.toInt(), 0xFF8B9DFF.toInt()),
        EMBER("Ember", 0xFF141110.toInt(), 0xFF1D1917.toInt(), 0xFF27211E.toInt(), 0xFFF3ECE7.toInt(), 0xFFAA9E96.toInt(), 0xFFFB923C.toInt()),
    }

    enum class HudFont(override val label: String) : Labeled { INTER("Inter (smooth)"), MINECRAFT("Minecraft") }

    val preset = choice("Theme", Preset.MIDNIGHT).desc("Base palette for menus and HUD panels").onChange { accentOverride.value = it.accent }
    val accentOverride = color("Accent colour", Preset.MIDNIGHT.accent, alpha = false, key = "accent").desc("Highlight colour used everywhere")
    val uiScale = slider("Menu scale", 1f, 0.6f, 1.6f, 0.05f, "×").desc("Size of ${Kit.displayName} menus, independent of Minecraft's GUI scale")
    val cornerScale = slider("Corner roundness", 1f, 0f, 2f, 0.05f, "×")
    val reduceMotion = toggle("Reduce motion").desc("Disable animations").onChange { Frame.motion = if (it) 0f else 1f }
    val blur = toggle("Background blur", true).desc("Blur the game behind ${Kit.displayName} menus")
    val hudGroup = group("HUD style", expanded = true)
    val hudFont = choice("HUD font", HudFont.INTER).under(hudGroup)
    val hudBackground = toggle("Panel backgrounds", true).desc("Rounded translucent backgrounds behind HUD elements").under(hudGroup)
    val hudOpacity = slider("Panel opacity", 0.55f, 0f, 1f, 0.05f).under(hudBackground)
    val hudTextShadow = toggle("Text shadow", true).under(hudGroup)

    fun init() = Config.bind("theme", "theme", this)

    // ── tokens ──────────────────────────────────────────────────────────────
    val bg get() = preset.value.bg
    val surface get() = preset.value.surface
    val surfaceHi get() = preset.value.surfaceHi
    val text get() = preset.value.text
    val textDim get() = preset.value.textDim
    val textFaint get() = Colors.lerp(preset.value.textDim, preset.value.surface, 0.45f)
    val border get() = Colors.withAlpha(preset.value.text, 0.07f)
    val borderStrong get() = Colors.withAlpha(preset.value.text, 0.14f)
    val accent get() = accentOverride.value
    val accentText get() = if (luminance(accent) > 0.55f) 0xFF0E0F13.toInt() else 0xFFFFFFFF.toInt()
    val success = 0xFF4ADE80.toInt()
    val warning = 0xFFFBBF24.toInt()
    val danger = 0xFFF87171.toInt()
    val info = 0xFF60A5FA.toInt()
    val shadow = 0x66000000

    fun radius(r: Float) = r * cornerScale.value
    val rSm get() = radius(5f)
    val rMd get() = radius(8f)
    val rLg get() = radius(12f)

    // Font sizes (logical px)
    const val CAPTION = 7.5f
    const val BODY = 8.5f
    const val TITLE = 9.5f
    const val HEADING = 12f

    private fun luminance(c: Int) = (0.299f * Colors.r(c) + 0.587f * Colors.g(c) + 0.114f * Colors.b(c)) / 255f
}
