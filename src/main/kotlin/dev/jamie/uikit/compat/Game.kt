package dev.jamie.uikit.compat

import com.mojang.blaze3d.platform.InputConstants
import dev.jamie.uikit.core.mc
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * Version facade for the Minecraft calls that move between releases. Everything outside `compat/`
 * goes through here, so a Minecraft update is fixed in this file (Stonecutter `//?` blocks).
 */
object Game {
    /** The open screen, or null in-game. */
    //? if <26.2 {
    val screen: Screen? get() = mc.screen
    fun open(screen: Screen?) = mc.setScreen(screen)
    /** The resource-loading overlay is up. */
    val loading: Boolean get() = mc.overlay != null
    val hideGui: Boolean get() = mc.options.hideGui
    fun chat(c: Component) = mc.gui.chat.addClientSystemMessage(c)
    val renderTarget get() = mc.mainRenderTarget
    //?} else {
    /*val screen: Screen? get() = mc.gui.screen()
    fun open(screen: Screen?) = mc.gui.setScreen(screen)
    /^* The resource-loading overlay is up. ^/
    val loading: Boolean get() = mc.gui.overlay() != null
    val hideGui: Boolean get() = mc.gui.hud.isHidden()
    fun chat(c: Component) = mc.gui.hud.chat.addClientSystemMessage(c)
    val renderTarget get() = mc.gameRenderer.mainRenderTarget()
    *///?}

    // ── input ───────────────────────────────────────────────────────────────
    // 26.3 replaced GLFW with SDL3. Key numbers changed (use InputConstants.KEY_*, never literals),
    // and so did mouse buttons: everything in the kit uses the old numbering (0 left, 1 right,
    // 2 middle), converted once at the edge by [button].
    //? if <26.3 {
    fun isKeyDown(code: Int) = InputConstants.isKeyDown(mc.window, code)
    fun isMouseDown(button: Int) = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.window.handle(), button) == org.lwjgl.glfw.GLFW.GLFW_PRESS
    fun button(raw: Int) = raw
    fun keyName(code: Int): String = InputConstants.Type.KEYSYM.getOrCreate(code).displayName.string
    /** The config key for a saved key binding: the setting's own, as the numbering hasn't changed. */
    fun keyId(@Suppress("UNUSED_PARAMETER") id: String): String? = null
    //?} else {
    /*fun isKeyDown(code: Int) = InputConstants.isKeyDown(code)
    fun isMouseDown(button: Int): Boolean {
        val raw = when (button) { 0 -> 1; 1 -> 3; 2 -> 2; else -> button + 1 }
        return org.lwjgl.sdl.SDLMouse.nSDL_GetMouseState(0L, 0L) and (1 shl (raw - 1)) != 0
    }
    fun button(raw: Int) = when (raw) { 1 -> 0; 3 -> 1; 2 -> 2; else -> raw - 1 }
    fun keyName(code: Int): String = InputConstants.Type.KEYBOARD.getOrCreate(code).displayName.string
    /^* Saved apart, because the config folder is shared across game versions and 86 means another key here. ^/
    fun keyId(id: String): String? = "sdl_$id"
    *///?}
}
