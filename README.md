# S3 Progression Mod

A Forge mod + KubeJS scripts implementing the tech-tier progression system for **CivTFG
Season 3**. Teams (via FTB Teams/Chunks) research their way through a fixed sequence of
tiers - Bronze, Iron, Steel, Steam, LV, HV, EV, IV - by crafting "science" items in a
custom **Laboratory** block, and each tier unlocks a specific gating machine needed to
reach the next one (e.g. completing Bronze unlocks the bloomery needed to make Iron).

## How it works

- **Laboratory block** - insert up to 5 distinct science items from the same age; any
  1-5 *different* categories is a valid craft, worth `2^(n-1)` research toward that age's
  running total (so 5 distinct items is worth far more than one at a time). The lab
  requires its chunk to be claimed by an FTB Team - an unclaimed lab will sit at 0
  progress forever (the GUI shows a warning if so).
- **Tiers unlock in order** - each tier has a research threshold; crossing it grants
  every online team member a GameStage (e.g. `bronze_unlocked`), and a login-sync keeps
  offline members caught up. A team can't start progressing tier N+1 until tier N is
  unlocked.
- **Gated machines** - one (multi-)block per tier transition is locked until the team
  holds that tier's stage: interaction gates (can't right-click/use it), placement gates
  (can't place it), or possession gates (Java-side inventory scan strips the item outright
  - used where placement/interaction alone could be bypassed by automation).
- **Single source of truth** - `config/s3_progression_mod/progression.json` defines tier
  order, thresholds, gamestage ids, and the gated-machine list. Both the Java mod and
  every KubeJS script read this one file at runtime; rebalancing tiers/thresholds/gates
  needs no code changes on either side.
- **Science-item recipes** - `kubejs_scripts/server_scripts/science_recipes.js` turns a
  data list of recipes (age, category, machine, inputs) into real crafting-table, GTCEU
  machine, or Create machine recipes. See "Editing science recipes" below for how to add
  to that list without hand-writing KubeJS.

## Repository layout

- `java_mod/` - the Forge mod (Gradle project). Build with `./gradlew build` from inside
  `java_mod/`; the jar lands in `java_mod/build/libs/`.
- `kubejs_scripts/` - `startup_scripts/` and `server_scripts/` subfolders, mirroring
  KubeJS's own layout.
- `config_files/s3_progression_mod/progression.json` - the progression config described
  above.
- `tools/` - standalone Python developer tooling for editing science recipes (not part of
  the mod itself, nothing here ships to the instance). See "Editing science recipes" below.

## Installing into the TFG instance

Copy each piece into the matching folder of your TerraFirmaGreg-Modern (TFG) instance -
these are plain file copies, not symlinks, so re-copy after every change:

| Repo path | Instance destination |
|---|---|
| `java_mod/build/libs/S3_progression_mod-<version>.jar` | `mods/` |
| `kubejs_scripts/startup_scripts/*` | `kubejs/startup_scripts/s3_progression_mod/` |
| `kubejs_scripts/server_scripts/*` | `kubejs/server_scripts/s3_progression_mod/` |
| `config_files/s3_progression_mod/progression.json` | `config/s3_progression_mod/progression.json` |

Game Stages must be downloaded and moved to `mods/` [Direct Download Link](https://www.curseforge.com/minecraft/mc-mods/game-stages/download/5790361)

A full game restart is required after updating the jar or `progression.json` - both are
read once into static fields at startup, so a KubeJS-only reload (`/kubejs
reload_server`) won't pick up jar or config changes, only edits to the `.js` files
themselves.

### Other mods required

Hard dependencies (the mod won't load without these - see `mods.toml`):

- Forge (1.20.1)
- KubeJS
- FTB Library
- FTB Teams
- FTB Chunks
- GameStages (`net.darkhax.gamestages`, 15.0.2+)
- TerraFirmaCraft (TFC), 3.2.23+ - `FireStartGateEnforcer` subscribes directly to TFC's own
  `StartFireEvent` (at `EventPriority.HIGHEST`, so it always runs before TFC's own handler
  regardless of mod load order) to close a real gate-bypass: TFC's fire-starting tools
  (e.g. the Firestarter) ignite blocks via this event after a multi-second "charge", not
  through a normal right-click interaction - `blocked_blocks.js`'s interaction gates never
  see it. This is the one place this mod links against TFC's actual Java classes rather
  than just referencing block ids as strings.

Referenced by id in the KubeJS gating scripts (not a hard dependency otherwise, but the
pack this mod targets includes them - without them the corresponding gate simply never
triggers):

- TerraFirmaCraft (TFC) - bloomery, blast furnace
- GregTech CEu (GTCEU) - steam boilers, LV generators, High Temp Precision Fabricator
- Ad Astra - Moon/Mars rockets

Also referenced by id in `science_recipes.js` (science-item crafting recipes only - not
required for the progression-gating feature above):

- TerraFirmaCraft (TFC) and GregTech CEu (GTCEU) - most recipe inputs
- Create - the recipe generator can target Create machine types (milling, mixing,
  pressing, etc.); requires the `kubejs-create` addon for its KubeJS recipe schemas

## Editing science recipes

`kubejs_scripts/server_scripts/science_recipes.js` holds the recipe list as a JSON array
(between `// ===RECIPES-JSON-START/END===` markers) plus a generator that turns each entry
into a real crafting-table, GTCEU machine, or Create machine recipe. You can edit that
array by hand, or use the GUI in `tools/`:

- Requires Python 3 with tkinter (both come standard with a normal Python install - nothing
  to install for this repo itself).
- One-time (and after any mod update), build the searchable item index used by the "Search"
  button on each recipe input:
  ```
  python tools/build_item_index.py
  ```
  This scans every jar in the TFG instance's `mods/` folder, then backfills whatever it
  couldn't find there (mostly GTCEU materials, which are generated purely at runtime with
  no static model or lang entry to scan) from three places already sitting on disk: the
  instance's FTB Quests chapters, EMI's own `emi.json` (every item you've ever looked up or
  favorited there), and - the big one - **ProbeJS**'s dumped live registry.

  ProbeJS (https://github.com/Prunoideae/ProbeJS) is installed in the instance's `mods/`
  folder specifically for this. Run `/probejs dump` in-game (needs cheats enabled in that
  world) and **wait at the pause/world screen until it's actually done** before quitting -
  with ~650+ GTCEU materials to process it can take a couple of minutes, and quitting too
  early leaves `kubejs/probe/` empty. Once it finishes, it writes
  `kubejs/probe/generated/globals.d.ts`, which happens to contain a single TypeScript union
  type listing literally every item id in the live registry - the actual authoritative
  list, not a guess. Re-run `/probejs dump` (and this script) after major mod updates to
  refresh it; everything still works without it, just with more unfound items.

  None of the four sources need a running game at index-build time - by the time you run
  `build_item_index.py`, it's all static data already on disk. If an item still isn't found
  after all four (should be rare now), the recipe input field still accepts a hand-typed id
  even when search finds nothing.
- Then run the editor itself:
  ```
  python tools/recipe_editor.py
  ```
  Add/edit/delete recipes with the on-screen buttons; "Save" writes back into
  `science_recipes.js`, and "Save && deploy to instance" also copies the file straight into
  the TFG instance's `kubejs/server_scripts/` folder. Either way, run `/kubejs
  reload_server` in-game afterward - recipes are plain server_scripts, not the jar or
  `progression.json`, so no full restart is needed.

All the code was generated by Claude.
