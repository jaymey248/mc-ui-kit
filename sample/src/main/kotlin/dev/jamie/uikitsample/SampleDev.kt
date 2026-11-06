package dev.jamie.uikitsample

import com.mojang.blaze3d.platform.InputConstants
import dev.jamie.uikit.compat.Game
import dev.jamie.uikit.config.Config
import dev.jamie.uikit.core.log
import dev.jamie.uikit.core.mc
import dev.jamie.uikit.ui.Canvas
import dev.jamie.uikit.ui.Theme
import dev.jamie.uikit.ui.screen.HudEditorScreen
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.levelgen.presets.WorldPresets
import java.nio.file.Files

/**
 * `-Dsample.dev=check`: a scripted pass through the kit with real input events, screenshots in
 * run/<version>/screenshots. Does nothing in a normal game.
 */
object SampleDev {
    fun init() {
        if (System.getProperty("sample.dev") == null) return
        log.info("[dev] kit classes: ${Canvas::class.java.name}, accent ${"%08X".format(Theme.accent)}, config ${Config.dir}")
        val e = Sample.position
        var step = -1
        var wait = 0
        fun placed() = e.run { "anchor $anchor x ${"%.1f".format(offsetX)} y ${"%.1f".format(offsetY)} scale ${"%.2f".format(scale)}" }
        ClientTickEvents.END_CLIENT_TICK.register {
            if (step == -1) {
                if (!Game.loading && Game.screen != null) { log.info("[dev] position at start: ${placed()}"); step = 0; wait = 20 }
                return@register
            }
            if (--wait > 0) return@register
            when (step) {
                0 -> openWorld()
                1 -> if (mc.level == null || Game.screen != null) return@register else wait = 60
                2 -> shot("1-world")
                3 -> Game.open(SampleScreen())
                4 -> shot("2-screen")
                5 -> Game.open(HudEditorScreen(null, e))
                6 -> shot("3-editor")
                7 -> {
                    val screen = Game.screen as HudEditorScreen
                    val w = mc.window.guiScaledWidth; val h = mc.window.guiScaledHeight
                    val x = (e.screenX(w) + e.lastSize.w * e.scale / 2).toDouble()
                    val y = (e.screenY(h) + e.lastSize.h * e.scale / 2).toDouble()
                    val left = MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)
                    screen.mouseClicked(MouseButtonEvent(x, y, left), false)
                    for (i in 1..10) screen.mouseDragged(MouseButtonEvent(x - i * 30, y + i * 12, left), -30.0, 12.0)
                    screen.mouseReleased(MouseButtonEvent(x - 300, y + 120, left))
                    log.info("[dev] dragged -300,120: ${placed()}")
                    repeat(4) { screen.mouseScrolled(x - 300, y + 120, 0.0, 1.0) }
                    log.info("[dev] scrolled 4: ${placed()}")
                }
                8 -> shot("4-moved")
                9 -> Game.open(null)
                10 -> { Config.saveNow(); log.info("[dev] hud.json: " + Files.readString(Config.dir.resolve("hud.json")).replace('\n', ' ')) }
                11 -> shot("5-world-after")
                12 -> { log.info("[dev] sample check done"); mc.stop() }
            }
            step++
            wait = maxOf(wait, 20)
        }
    }

    private fun openWorld() {
        val id = "uikit-dev-" + net.minecraft.SharedConstants.getCurrentVersion().name()
        val flows = mc.createWorldOpenFlows()
        if (mc.levelSource.levelExists(id)) return flows.openWorld(id) {}
        val settings = LevelSettings("uikit dev", GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT)
        flows.createFreshLevel(id, settings, SelectWorldScreen.TEST_OPTIONS, WorldPresets::createNormalWorldDimensions, Game.screen ?: return)
    }

    private fun shot(name: String) {
        Screenshot.grab(mc.gameDirectory, "sample-$name.png", Game.renderTarget, 1) { log.info("[dev] screenshot $name: ${it.string}") }
    }

}
