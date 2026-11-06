package dev.jamie.uikit.core

import dev.jamie.uikit.compat.render.VsbPipelines
import dev.jamie.uikit.ui.Theme
import net.minecraft.resources.Identifier

/**
 * Who this copy of the kit belongs to. Call [setup] first thing in the mod's client entrypoint: the asset
 * namespace, the config folder and the log name all come from here. Each mod carries its own renamed copy
 * of the kit, so this is per mod even with several loaded.
 */
object Kit {
    private var id: String? = null
    private var name: String? = null

    /** The mod's id: its asset namespace and its `config/<id>` folder. Must match what the build's `uiKit { modId }` says. */
    val modId: String get() = id ?: error("Call Kit.setup(modId, displayName) first thing in the mod's client entrypoint")

    /** The name players see in the kit's own settings ("Size of X menus"). */
    val displayName: String get() = name ?: modId

    /** Also registers what every mod needs before the first resource reload: the render pipelines, the theme and key polling. Then bind the mod's own settings and call [dev.jamie.uikit.config.Config.load]. */
    fun setup(modId: String, displayName: String = modId) {
        id = modId
        name = displayName
        VsbPipelines.init()
        Keybinds.init()
        Theme.init()
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(modId, path)
}
