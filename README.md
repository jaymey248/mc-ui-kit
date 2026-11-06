# mc-ui-kit

The UI framework of our Fabric mods, for Minecraft 26.1.2, 26.2 and 26.3: drawing through `Canvas` (SDF rounded shapes,
Inter, Lucide icons), colours from `Theme`, motion with `Spring` and `Ease`, settings through the `SettingHost` DSL, a
widget set, and HUD elements players can move and resize in `HudEditorScreen`.

It started in VibeSB and was copied into other mods. [docs/COPIES.md](docs/COPIES.md) records what each copy
had, what stayed out, and the order the mods move over. `sample/` is the smallest mod on the kit: copy it to start one.

## Adding it to a mod

A player installs one jar. The kit's classes and assets go inside the mod's jar under the mod's own package and namespace,
so two mods can carry different kit versions and never touch each other.

1. Add the submodule, which pins the mod to one kit commit:

   ```bash
   git submodule add https://github.com/jaymey248/mc-ui-kit.git kit
   ```

2. In `settings.gradle.kts`:

   ```kotlin
   pluginManagement {
       includeBuild("kit/consumer-plugin")
       // ...repositories as usual
   }

   includeBuild("kit") {
       dependencySubstitution {
           for (mc in listOf("26.1.2", "26.2", "26.3")) substitute(module("dev.jamie:mc-ui-kit-$mc")).using(project(":$mc"))
       }
   }
   ```

3. In `build.gradle.kts`:

   ```kotlin
   plugins {
       id("net.fabricmc.fabric-loom") version "1.17.20"
       kotlin("jvm") version "2.4.10"
       id("dev.jamie.uikit.consumer")
   }

   uiKit {
       modId = "mymod"                      // assets move to assets/mymod, settings go to config/mymod
       relocateTo = "dev.me.mymod.uikit"    // unique to this mod
       minecraft = sc.current.version
   }
   ```

   The mod needs `implementation` of Fabric API and Fabric Language Kotlin. The kit brings neither.

4. First thing in the client entrypoint:

   ```kotlin
   Kit.setup("mymod", "My Mod")   // same id as uiKit { modId }
   HudManager.register(myElement) // each element, then:
   HudManager.init()
   Config.load()                  // after everything is bound
   ```

Write code against the real package, `dev.jamie.uikit`. `gradlew build` renames it in the finished jar, and a dev client
runs it unrenamed. After cloning a mod, fetch the kit with `git submodule update --init`.

## HUD elements

```kotlin
val score = TextHud("score", "Score", Anchor.TOP_RIGHT, -6f, 6f, title = "Score") { listOf("§a$points") }

val bar = DrawHud("bar", "Health bar", Anchor.BOTTOM, 0f, -46f) { c, preview ->
    c.rect(0f, 0f, 80f, 6f, Theme.accent, 3f)
    Size(80f, 6f)
}

score.availableWhen = { settings.showScore.value } // off: not drawn and not in the editor
score.activeWhen = { inDungeon() }                 // inactive: greyed in the editor, behind "Show inactive"
```

Open the editor with `Game.open(HudEditorScreen(parent, focusedElement))`. It has dragging with snap guides, scroll or the
corner handle to resize, arrow keys to nudge, right-click for the element's own settings, Arrange, Reset and Ctrl+Z. To put
an "Edit position" button in a settings list, `register(HudLinkSetting(element))` and give `SettingsList` an `onEditHud`.
Placement is saved to `config/<modId>/hud.json` as anchor, offset and scale, so it survives resolution and GUI-scale changes.

The kit knows nothing of a mod's features, worlds or commands. `availableWhen` and `activeWhen` are how a mod says when an
element counts, and `HudManager.afterFrame` draws anything on the HUD that isn't an element, such as toasts.

## Working on it

Edit the kit in a mod's `kit/` folder or here. A mod's composite build recompiles it, so a dev client shows the change at
once. Then commit to the kit, push, and commit the new submodule commit in each mod that should have it. A mod gets a new
kit only when its own commit says so, and that commit is where to read the kit changes that came with it.

```bash
./gradlew build
```

```bash
cd consumer-plugin && ../gradlew test
```

```bash
cd sample && ../gradlew :26.2:runClient -Dsample.dev=check
```

The last one drives the kit through a world, the settings screen and the HUD editor with real input events, and saves
screenshots in `sample/run/26.2/screenshots`.

To try finished jars together, put them in `run/<version>/mods` and start the bare client, which has no mod of its own:

```bash
./gradlew :26.2:runClient
```

Set `onboardAccessibility:false` in a new `run/<version>/options.txt`, or a scripted run stalls on Minecraft's first-run
accessibility screen.

**Compiling isn't enough: run the game on every version.** 26.3 replaced GLFW with SDL3, which renumbered every key and
mouse button without breaking the build, so never write key numbers as literals. Its new shader compiler needs different
GLSL, so `src/shaders/` has one set per generation. Version differences go in `compat/`, in `//?` blocks, and the tree in
git is always 26.1.2's.

## Why each mod has its own copy

Jar-in-jar would be the usual way to share a library. Fabric Loader would then load only the newest kit among the installed
mods, so one mod's update could change the UI code under another mod that was never tested with it, and a breaking kit
change would crash the older mod.

Each mod therefore carries a private, renamed copy. The cost: each mod has its own HUD editor and theme, and a fix reaches a
mod when that mod updates its submodule.

How the copy is made:

- `processResources` merges the kit's classes and assets into the mod's output, with `assets/uikit` moved to
  `assets/<modId>` and shader imports rewritten.
- `jar` renames `dev.jamie.uikit` to `relocateTo` in every class of the jar, the mod's own included, along with the class
  names in Kotlin's `@Metadata`. Nested jars pass through untouched.
- The kit's only leftover strings are in `SourceDebugExtension`, a debugger's line map. Nothing loads them.
- `sourcesJar` gets the kit's source under `uikit-source/`, which a GPL mod needs.

The kit has no mixins, and no `fabric.mod.json` because it is not a mod. It relies on Fabric API's transitive access wideners
for `GuiGraphicsExtractor`, which every mod on the kit has. A mixin or access widener added to the kit would have to be
declared by each mod, so keep them out.

## Licence

MIT, see [LICENSE](LICENSE). Inter (OFL-1.1), DejaVu Sans (Bitstream Vera) and Lucide (ISC) keep their own licences, with
the notices in `src/main/resources/assets/uikit/licenses`. The plugin carries those and the kit's own licence into every
mod's jar under `assets/<modId>/licenses`, so a mod needs to do nothing more to meet them.
