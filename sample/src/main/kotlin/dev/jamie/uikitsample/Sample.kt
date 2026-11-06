package dev.jamie.uikitsample

import dev.jamie.uikit.compat.Game
import dev.jamie.uikit.config.Config
import dev.jamie.uikit.config.HudLinkSetting
import dev.jamie.uikit.config.SettingHost
import dev.jamie.uikit.core.Kit
import dev.jamie.uikit.core.mc
import dev.jamie.uikit.ui.Canvas
import dev.jamie.uikit.ui.Colors
import dev.jamie.uikit.ui.Theme
import dev.jamie.uikit.ui.hud.Anchor
import dev.jamie.uikit.ui.hud.DrawHud
import dev.jamie.uikit.ui.hud.HudElement
import dev.jamie.uikit.ui.hud.HudManager
import dev.jamie.uikit.ui.hud.Size
import dev.jamie.uikit.ui.hud.TextHud
import dev.jamie.uikit.ui.screen.HudEditorScreen
import dev.jamie.uikit.ui.screen.SettingsList
import dev.jamie.uikit.ui.screen.VsbScreen
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/** The smallest mod on the kit: three movable, resizable HUD elements and a settings screen. Copy it to start a mod. */
object Sample : ClientModInitializer {
    private var openMenu = false

    /** A titled list of lines. */
    val position = TextHud("position", "Position", Anchor.TOP_RIGHT, -6f, 6f, title = "Position", preview = { listOf("§7x 120  y 64  z -30") }) {
        val p = mc.player ?: return@TextHud emptyList()
        listOf("§7x ${p.blockX}  y ${p.blockY}  z ${p.blockZ}")
    }

    /** Drawn by hand. */
    val health = DrawHud("health", "Health bar", Anchor.BOTTOM, 0f, -46f) { c: Canvas, _ ->
        val p = mc.player
        val share = if (p == null) 0.7f else (p.health / p.maxHealth).coerceIn(0f, 1f)
        c.rect(0f, 0f, 80f, 6f, Colors.withAlpha(Theme.bg, 0.7f), 3f)
        c.rect(0f, 0f, 80f * share, 6f, Theme.accent, 3f)
        Size(80f, 6f)
    }

    /** Only shows at times: the editor greys it, and hides it behind "Show inactive" when it is not showing. */
    val sprint = object : HudElement("sprint", "Sprinting", Anchor.LEFT, 6f, 0f) {
        override val activeNow get() = mc.player?.isSprinting == true
        override fun draw(c: Canvas, preview: Boolean): Size {
            panel(c, 50f, 13f, background.value)
            c.text("Sprinting", PAD_X, 3f, Theme.success)
            return Size(50f, 13f)
        }
    }

    override fun onInitializeClient() {
        Kit.setup("uikitsample", "UI Kit Sample")
        HudManager.register(position)
        HudManager.register(health)
        HudManager.register(sprint)
        HudManager.init()
        SampleSettings.init()
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(ClientCommands.literal("uikitsample").executes { openMenu = true; 1 })
        }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (openMenu) { openMenu = false; Game.open(SampleScreen()) }
        }
        Config.load()
        ClientLifecycleEvents.CLIENT_STOPPING.register { Config.shutdown() }
        SampleDev.init()
    }
}

object SampleSettings : SettingHost() {
    val greeting = text("Greeting", "Hello", placeholder = "Say something").desc("Any setting the kit has, saved in config/uikitsample")
    val hud = group("HUD", expanded = true)

    fun init() {
        listOf(Sample.position, Sample.health, Sample.sprint).forEach { register(HudLinkSetting(it)).under(hud) }
        Config.bind("settings", "sample", this)
    }
}

class SampleScreen : VsbScreen("UI Kit Sample") {
    private val list = SettingsList({ SampleSettings.rootSettings + Theme.rootSettings }, onEditHud = { Game.open(HudEditorScreen(this, it)) })

    override fun draw(c: Canvas) {
        val w = 340f
        val x = (ui.screenW - w) / 2
        c.panel(x, 20f, w, ui.screenH - 40f, Theme.surface, Theme.border, Theme.rLg)
        list.layout(x + 12f, 32f, w - 24f)
        list.render(c, ui)
    }

    override fun onMouseDown(button: Int, double: Boolean) = list.mouseDown(ui, button)
    override fun onKey(key: Int, mods: Int) = list.key(ui, key, mods)
    override fun onChar(cp: Int) = list.char(ui, cp)
}
