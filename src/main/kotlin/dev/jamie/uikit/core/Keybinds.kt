package dev.jamie.uikit.core

import dev.jamie.uikit.compat.Game

import dev.jamie.uikit.config.KeySetting
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents

/**
 * Binds [KeySetting]s to actions. Polled every client tick with edge detection, so it needs no
 * vanilla KeyMapping registration (and no keybind-menu clutter).
 */
object Keybinds {
    private class Binding<T>(val entries: () -> List<T>, val key: (T) -> KeySetting, val active: (() -> Boolean)?, val inScreens: Boolean, val action: (T) -> Unit) {
        private val held = HashSet<KeySetting>()

        fun poll() {
            val allowed = (inScreens || Game.screen == null) && active?.invoke() != false
            for (e in entries()) {
                val k = key(e)
                if (!(k.bound && isDown(k.value))) held.remove(k)
                else if (held.add(k) && allowed) guard("keybind ${k.name}") { action(e) }
            }
        }
    }

    private val bindings = mutableListOf<Binding<*>>()

    /** [active], when given, is asked at each press (a feature that is switched off, say). */
    fun bind(key: KeySetting, active: (() -> Boolean)? = null, inScreens: Boolean = false, action: () -> Unit) {
        val single = listOf(key)
        bindings += Binding({ single }, { it }, active, inScreens) { action() }
    }

    /** Like [bind] for a list whose entries come and go (e.g. a GroupListSetting's); [entries] is read every tick. */
    fun <T> bindEach(entries: () -> List<T>, key: (T) -> KeySetting, active: (() -> Boolean)? = null, inScreens: Boolean = false, action: (T) -> Unit) {
        bindings += Binding(entries, key, active, inScreens, action)
    }

    fun isDown(code: Int): Boolean {
        if (code == -1) return false
        return if (code <= -100) Game.isMouseDown(-100 - code) else Game.isKeyDown(code)
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            for (b in bindings) b.poll()
        }
    }
}
