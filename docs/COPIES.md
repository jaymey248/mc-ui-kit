# The copies, and how they became one

Compared on 2026-11-06, after normalising package and mod names.

## What was already the same

`Canvas`, `Anim`, `Colors`, `Toasts` and `SdfFont` were identical in every copy, and so were the shaders apart from their
namespace.

## What each copy had that the others lacked

| From | What | In the kit as |
|---|---|---|
| UniversalVC | 26.3 render API (`renderpearl`), a shader set per generation | `compat/`, `src/shaders/{pre-26.3,26.3}` |
| UniversalVC | Key codes from `InputConstants`, not GLFW literals (26.3's SDL3 renumbered them) | `Keys`, `Game.keyName`, `Game.button`, `Game.isKeyDown` |
| UniversalVC | Voice and flag icons | `Icons` |
| Another mod | `BoundBool`, `BoundInt`: settings over a value someone else owns, never persisted | `Setting.kt` |
| Another mod | A slider value typed into its chip can differ from a drag's (`Slider.typed`) | `Controls.kt` |
| Another mod | Resizing keeps the element on screen, with a wider options popover | `HudEditorScreen` |
| VibeSB | A colour's default chroma, with `isDefault` and `reset` respecting it | `ColorSetting`, `SettingHost.color(chroma=)` |
| VibeSB | "Edit position" link in a settings list, with the element's settings under it | `HudLinkSetting`, `SettingsList(onEditHud=)` |
| VibeSB | A module's switch drawn as a mini card header | `SettingHost.headerToggle` |
| VibeSB | Keybinds that only fire while their owner is active (`ListenerOwner`) | `Keybinds.bind(active=)` |
| VibeSB | HUD elements tied to a feature and module, greyed when inactive, "Show inactive" | `HudElement.availableWhen`, `activeWhen`, `HudManager.showInactiveAtFirst` |
| VibeSB | HUD layer under the chat (UniversalVC draws over it) | `HudManager.init(aboveChat=)` |
| VibeSB | `RenderHudEvent` on every HUD frame | `HudManager.afterFrame` |
| VibeSB | A config format version of its own, with migrations | `Config.version`, set by the mod |

Nothing any copy fixed was dropped.

## Made generic

Hardcoded in each copy, now set once by `Kit.setup(modId, displayName)` or `uiKit { }`: the asset namespace and shader
imports, the font paths, the pipeline ids, the texture ids, the HUD layer id, the config folder, the log name, the config
thread's name, and the theme's descriptions.

## What stays out of the kit

Each of these needs a mod's own types, or only makes sense in that mod.

**VibeSB:**
- `feature/` (`Feature`, `Module`, `FeatureManager`), the event bus and `ListenerOwner`
- `skyblock/` and the per-element "Only on islands" setting, which `Feature.hud()` can register on the element
- `ConfigScreen`, `SectionPage`, `UiPrefs`, `ItemCells` and the profile viewer `screen/pv/`
- `compat/` beyond the kit's: `ChatLines`, `NameTagScale`, `ModMenuEntry`, `LateFeatures`, `MapTexture`, the world pipelines
  (`LINES_THROUGH_WALLS`, `FILLED_THROUGH_WALLS`), and `Game`'s camera, title, tab and dyed-item calls. They stay in a
  VibeSB object beside the kit's `Game`.
- `DevTools`, `ClickTest`, `Commands`, `Http`, `Notify`, `Scheduler`, `Migrations`

**UniversalVC:** `VoiceScreen`, `VoiceSettings`, `Hud`, `DevCheck`, its name-tag mixin, `font/voice.json` and `voice.png`.

**Worth adding once a second mod wants them:** `TrackerHud` (VibeSB; it uses only the kit), and the self-updater that VibeSB
has (`AutoUpdater`, `UpdateMath`, `UpdateBanner`, `SwapHelper`).

## Licence

The kit is MIT. The third-party parts are the fonts and icons named in the README.

VibeSB and UniversalVC stay GPL-3.0-or-later. MIT code can go in a GPL mod, and the kit's source ships in their sources jars.

## Order for the mods

1. **UniversalVC** first, on branch `kit/mc-ui-kit`. It gains the HUD editor.
2. **VibeSB** next. It has the most code on the kit (267 files import it). Its `Feature.hud()` sets `availableWhen` and
   `activeWhen`, so its elements don't change. Its `compat/Game.kt` splits into the kit's and its own.
