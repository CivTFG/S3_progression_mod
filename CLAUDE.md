# S3 Progression Mod — Handoff Notes

This file is project context for whichever Claude instance works on this repo next. It's
written from direct experience across many prior sessions — every pitfall here was a real
bug that actually happened, not a hypothetical. Read this before touching anything, especially
the "Pitfalls" section.

## What this is

A Forge mod (+ companion KubeJS scripts) implementing the tech-tier progression system for
**CivTFG Season 3**, a heavily-modded TerraFirmaGreg-Modern (TFG) 1.20.1 pack. Teams (via FTB
Teams/Chunks) research through a fixed tier sequence by crafting "science" items in a custom
**Laboratory** block; each tier unlocks a gating machine needed to reach the next one.

- Minecraft 1.20.1, Forge 47.4.13, KubeJS 2001.6.5.
- Mod id `s3_progression_mod`, Java package `com.civtfg.progression`.
- Everything in this repo was built collaboratively with Claude across many sessions — the
  user (goes by CivTFG) does the in-game testing and reports back; verify claims against
  actual game/log behavior rather than assuming, per the pitfalls below.

## Repository layout

```
java_mod/                        Forge mod (Gradle project)
  src/main/java/com/civtfg/progression/
    ProgressionMod.java          mod entry point, registers everything
    block/LaboratoryBlock.java   the block: ACTIVE blockstate, placement logic, GUI gate
    blockentity/LaboratoryBlockEntity.java   5-slot inventory, recipe matching, team resolution
    menu/LaboratoryMenu.java, client/LaboratoryScreen.java   GUI
    event/ProgressionEvent.java  Forge event fired on a successful craft (KubeJS listens)
    registry/ModBlocks.java, ModItems.java, ModScienceItems.java, ModBlockEntities.java,
             ModMenuTypes.java, ModCreativeModeTabs.java
    stage/ProgressionTiers.java  loads config/s3_progression_mod/progression.json; the
                                 shared source of truth for tiers/gates/team-lab-tracking
    stage/GatedItemEnforcer.java tick-based inventory scan enforcing "possession" gates
  src/main/resources/
    META-INF/mods.toml           dependency version ranges — see Pitfall #1
    assets/s3_progression_mod/   blockstates, models, textures, lang
    data/s3_progression_mod/     loot table + STATIC per-item crafting recipes — see
                                 Pitfall #2, this is important and easy to miss
kubejs_scripts/
  startup_scripts/progression_listener.js   ForgeEvents.onEvent(ProgressionEvent) listener
  server_scripts/blocked_blocks.js          interaction/placement gates (KubeJS BlockEvents)
  server_scripts/progression_commands.js    /progression admin commands
  server_scripts/science_recipes.js         data-driven KubeJS recipe generator — see below
config_files/s3_progression_mod/progression.json   repo copy of the single-source-of-truth config
tools/
  recipe_editor.py               Tkinter GUI for editing science_recipes.js's recipe list
  build_item_index.py            builds tools/item_index.json (search index for the GUI)
  item_index.json                generated, gitignored — regenerate, don't hand-edit
  PROBEJS_REFERENCE.md           (German) notes on ProbeJS's dump format, for reuse
README.md                        user-facing install/build instructions — keep in sync
```

## How the mod actually works

- **Laboratory block**: insert up to 5 distinct science items from the same age into its 5
  slots. Any 1–5 *different* categories is a valid craft, worth `2^(n-1)` toward that age's
  running research total (so 5 distinct items is worth far more than crafting 5 times with
  1 each). Progress resets if slot contents change mid-craft. On success, fires
  `ProgressionEvent` (a Forge event) which `progression_listener.js` picks up to credit the
  team and grant the GameStage once the tier's threshold is crossed.
- **Team resolution**: `ProgressionTiers.resolveTeam(level, pos)` maps a block position to
  an FTB Team via FTBChunks' claim data. **Server-side only** — see Pitfall #4. Team
  research/flags are stored in `team.getExtraData()` NBT (a CompoundTag), the same pattern
  KubeJS uses (`team.markDirty()` after mutating).
- **Tier gating**: GameStages (net.darkhax.gamestages) grants a stage per unlocked tier.
  Since GameStages is per-player, "team" progression is implemented by mirroring the same
  stage onto every online team member (with a login-sync for offline members) — see
  `progression_listener.js` / `progression_commands.js`.
- **Gates** (`progression.json`'s `"gates"` array, one gating (multi-)block per tier
  transition): three mechanisms —
  - `interaction`: KubeJS `BlockEvents.rightClicked` cancels the interaction
    (`blocked_blocks.js`). For entities (e.g. rockets, which aren't blocks), use
    `ItemEvents.entityInteracted` instead and check `event.target.type` manually — there is
    no `EntityEvents.rightClicked`, don't assume API symmetry with `BlockEvents` (this was a
    real bug — see Pitfall #6).
  - `placement`: `BlockEvents.placed` cancels it.
  - `possession`: **Java-side** (`GatedItemEnforcer`, a tick handler scanning inventories) —
    used where placement/interaction gating alone is bypassable once a player has
    automation capable of placing blocks or acquiring items without the gated action firing.
- **Laboratory active/decorative split**: only the *first* Laboratory placed in a team's
  claim is functional; every other one (unclaimed chunk, or a team that already has one) is
  a decorative "out of order" copy — same block, `ACTIVE` blockstate property, same loot
  table (always drops a normal placeable Laboratory item regardless of state). See Pitfall
  #4 for the exact bug this produced and how it's fixed now.
- **Science items**: `ModScienceItems` registers 5 items (one per category) × 8 ages
  (`Age` enum) = 40 items, named `<age>_<category>_science`. `ModScienceItems.register()`
  fails fast at startup if `Age`/`Category` don't exactly match `progression.json`'s
  tiers/categories — keep these in lockstep.

## Configuration

### `progression.json` (single source of truth — both Java and every KubeJS script read this
one file at runtime; edit it, not hardcoded copies)

```jsonc
{
  "researchKey": "s3_progression_mod:research",     // NBT key on team extra data
  "categories": ["mining", "farming", "production", "exploration", "challenge"],
  "tiers": [
    { "key": "BRONZE", "displayName": "Bronze Age", "stageId": "bronze_unlocked", "threshold": 3 },
    // ... IRON, STEEL, STEAM, LV, HV, EV, IV — MV was deliberately removed, see Pitfall #7
  ],
  "gates": [
    { "requiresTier": "BRONZE", "mechanism": "interaction", "blocks": ["tfc:bloomery"], "message": "..." },
    // mechanism: "interaction" | "placement" | "possession"; add "entity": true for
    // entity-type blocks (rockets); "blocks" is an array of ids
  ]
}
```
Current gate list: bloomery (BRONZE), blast furnace (IRON), steam boilers (STEEL,
placement), LV generators (STEAM, possession), High Temp Precision Fabricator (LV,
interaction), moon rocket (HV, entity), mars rocket (EV, entity). Nothing gates IV yet —
it exists for future (0.14+) content.

### `science_recipes.js` (data-driven recipe generator)

The `SCIENCE_RECIPES` array (between `// ===RECIPES-JSON-START/END===` markers) is written
as **strict JSON** (quoted keys, no comments inside it, no trailing commas) specifically so
`tools/recipe_editor.py` can parse/rewrite it with Python's `json` module without needing a
real JS parser. It's still a valid JS array literal to KubeJS. Entry shape and supported
machine types (`crafting_table`, GTCEU recipe types with a `tier` for EU/t, Create recipe
types with optional `heat`) are documented in the file's own header comment — read that
before adding recipes, it's kept accurate.

**Only the "mining" category is filled in so far** (37 recipes, BRONZE through IV).
Farming/production/exploration/challenge were never designed via this system — see Known
Issues below, this is very likely the next real content task.

## Tools (`tools/`)

- **`recipe_editor.py`**: `python tools/recipe_editor.py`. Tkinter GUI, tree view grouped
  Age → Category → Recipe (collapsible, remembers expand state across edits). Add/Edit
  dialog has a "Search..." button per input row backed by `item_index.json`. "Save && deploy
  to instance" copies straight to the TFG dev instance's `kubejs/server_scripts/`.
- **`build_item_index.py`**: `python tools/build_item_index.py`. Builds the search index
  from **four** fallback sources, each only filling gaps the previous ones missed:
  1. Item model file paths in every mod jar (`assets/<modid>/models/item/**.json`) — the
     *only* source that gives correct ids with real slashes (see Pitfall #3).
  2. Lang keys, matched back to model-derived ids by dotting their slashes.
  3. The modpack's own FTB Quests `.snbt` chapters (blunt regex over quoted namespaced
     strings — imprecise but catches real ids nothing else finds).
  4. EMI's own `emi.json` (saved lookup history/favorites — real, live-resolved ids).
  5. **ProbeJS's dumped registry** (`kubejs/probe/generated/globals.d.ts`, written by
     running `/probejs dump` in-game with cheats on) — the *actual* authoritative source,
     added last because it needs a manual one-time game action. Contains a single
     `type Item = "a:b" | "c:d" | ...` union listing literally every live-registered item
     id. Currently the single highest-value source for GTCEU's runtime-generated material
     items (ingots/plates/dusts/etc. per material — these have no static model or lang key
     at all, see Pitfall #3), which nothing else here finds without someone manually
     looking the item up in EMI first.
  Re-run after mods change; `item_index.json` is gitignored (regenerable, tied to whichever
  mod jars happen to be installed locally, ~39k entries as of last build).
- **`PROBEJS_REFERENCE.md`** (German): where ProbeJS's dump lives, what each generated file
  contains, for a future session that needs to parse more of it (e.g. block/fluid/entity
  registries, not just items).

## Deployment — READ THIS, there are many target folders

This mod is **not** developed against a single running instance. Across sessions, the
following local folders have all mattered at different points — confirm which one is
actually relevant before assuming a jar or config change reached anywhere real:

| Path | What it is |
|---|---|
| `java_mod/build/libs/S3_progression_mod-0.1.0.jar` | build output — always redeploy from here |
| `C:\Users\erikp\curseforge\minecraft\Instances\TerraFirmaGreg-Modern` | primary dev/test instance used for most in-session testing |
| `C:\Users\erikp\curseforge\minecraft\Instances\TerraFirmaGreg-Modern (1)` | a **separate** instance — this is where the client-side placement crash (Pitfall #4) actually reproduced, don't assume it's the same as the one above |
| `C:\Users\erikp\Desktop\Civ TFG\s3_server` | an older local dedicated-server folder |
| `C:\Users\erikp\Desktop\Civ TFG\s3_server_13.10` | newer versioned local dedicated-server folder |
| `C:\Users\erikp\Desktop\Civ TFG\s3_client_13.10` | player-facing client counterpart, built from the server's mod list (see `project_civtfg_s3_client_pack` memory for the pakku-lock.json process) |
| **PebbleHost (remote, SFTP)** | **the actual live production server** — none of the local `s3_server*` folders are it; see the user's own memory note on this. Always confirm with the user which environment "the live server" means before debugging a report. |

**Deploy checklist** after any Java/resource change:
1. `cd java_mod && ./gradlew build` (from `java_mod/`, not repo root)
2. Verify the built jar actually contains what you expect — `unzip -p ...jar path/to/file`
   — don't just trust the Gradle log (UP-TO-DATE tasks can be misleading about what's
   actually inside the jar you're about to ship).
3. Copy `S3_progression_mod-0.1.0.jar` to *every* relevant target's `mods/` folder — plain
   file copies, not symlinks, so this must be repeated every time.
4. **Full game/server restart required** for jar or `progression.json` changes — both are
   read once into static fields at startup (`ProgressionTiers`'s static initializer).
   `/kubejs reload_server` only picks up edits to `.js` files themselves (server_scripts),
   nothing else.
5. KubeJS script changes (`kubejs_scripts/*`) deploy by copying into the instance's
   `kubejs/startup_scripts/s3_progression_mod/` or `kubejs/server_scripts/s3_progression_mod/`
   — these *can* hot-reload via `/kubejs reload_server` (server_scripts only; startup_scripts
   need a restart).

## Pitfalls (all real bugs that actually happened)

1. **`mods.toml` dependency version ranges must match what the pack actually ships, not
   whatever was installed in the dev environment.** `ftblibrary`'s range was originally
   `[2001.2.13,)` but the live pack shipped `2001.2.12` → mod refused to load entirely
   ("Missing or unsupported mandatory dependencies"). Fixed by loosening to
   `[2001.2.12,)`. When bumping a dependency range, check what's actually in the target
   pack's `mods/` folder, don't just match your dev instance.

2. **Two parallel, overlapping recipe systems exist for science items — reconcile before
   touching recipes.** `java_mod/src/main/resources/data/s3_progression_mod/recipes/*.json`
   are **static** Forge datapack recipes, one per age×category (45 files: all 8 real tiers
   plus 5 stale `mv_*` ones), seemingly an early placeholder set generated before
   `science_recipes.js` existed. `science_recipes.js` is the newer, actively-maintained,
   data-driven generator — but it currently only covers the `mining` category. This means:
   - For **mining**, both the old static recipes *and* the new `science_recipes.js` ones
     are simultaneously active for the same output items (e.g. `bronze_mining_science` can
     currently be crafted via the old hardcoded "2x bronze ingot + flint + paper" recipe
     *and* via 5 separate newer recipes) — almost certainly unintentional duplication.
   - For **farming/production/exploration/challenge**, the static files are the *only*
     working recipes right now (mining is the only category "upgraded" so far).
   - The 5 `mv_*` static files (`mv_challenge_science.json` etc.) are **currently broken**
     in production — confirmed via a real server log: `Failed to parse recipe
     's3_progression_mod:mv_exploration_science...' ItemStack 'result' can't be empty!`,
     because `ModScienceItems.Age` no longer has `MV` (removed deliberately, see Pitfall
     #7), so the result item literally doesn't exist. These 5 files (and their matching
     `models/item/mv_*.json`) should just be deleted.
   Before doing more recipe work: decide whether to (a) delete the static recipes for
   categories `science_recipes.js` already covers, (b) migrate the remaining 4 categories'
   static recipes into `science_recipes.js`'s format (probably the right call, for
   consistency and so the recipe editor GUI can manage them too), or (c) something else —
   this needs a real decision, not just deleting stuff unilaterally.

3. **GTCEU generates most of its items (ingots/plates/dusts/etc., one set per material) at
   *runtime*, with no static model file and no per-item lang key.** Confirmed by checking
   the real GTCEU jar: item model paths only cover ~2700 "fixed" items (machines, tools),
   not material items; and material display names are composed from two *separate*
   translations (`material.gtceu.tin` + `tagprefix.ingot`) concatenated in code, never
   stored as a literal `item.gtceu.tin_ingot` key. This means:
   - **Never reconstruct an item id from a dotted lang key if the mod might nest paths with
     `/`.** TerraFirmaCraft registers e.g. `tfc:metal/ingot/copper`; its lang key is
     `item.tfc.metal.ingot.copper`. Converting dots back to slashes blindly produces the
     wrong, non-existent id `tfc:metal.ingot.copper`. Always get the id from the item model
     file path (which preserves real slashes) when one exists; only fall back to
     lang-key-derived ids when there's no model-derived id at that same dotted path.
   - For mods like GTCEU with *no* static source at all, the only ways to get a real id are:
     the pack's own quest files, EMI's saved lookup history, or ProbeJS's live registry dump
     (see the tools section above) — all wired into `build_item_index.py` already.

4. **`ProgressionTiers.resolveTeam()` (and anything that calls
   `FTBChunksAPI.api().getManager()`) is server-side only — calling it from a method that
   also runs client-side crashes the game.** Real crash, confirmed via crash report:
   `LaboratoryBlock.getStateForPlacement()` called `resolveTeam()` unconditionally;
   `getStateForPlacement` runs on **both** logical sides (client for local placement
   prediction, server for the real placement), and `FTBChunksAPIImpl.getManager()` throws
   `NullPointerException` on the client. Fixed by checking `level.isClientSide()` first and
   returning a plain `defaultBlockState()` on the client (the server's authoritative state
   syncs back immediately after anyway, so the client's guess is only ever visible for a
   moment). **Any new code that touches FTBChunks/team resolution from a block/item method
   must check which side(s) that method actually runs on** — don't assume "it's on a Block
   class, must be server-only", several vanilla `Block`/`BlockItem` hooks
   (`getStateForPlacement`, `canSurvive`, etc.) run on both sides.

5. **Rhino (KubeJS's JS engine here) doesn't support object-spread (`{...obj}`) or,
   probably, array-spread in a call (`fn(...arr)`, never actually tested, avoided
   defensively).** Object-spread silently aborts the *entire enclosing script block* with
   `rhino.EvaluatorException: invalid property id` and no other indication — this caused a
   real bug where several block-interaction gates simply never registered, with no error
   surfaced anywhere obvious (found by grepping `latest.log` for `EvaluatorException`).
   Mutate objects in place instead of spreading them into new ones. Where a KubeJS Java
   varargs method needs a variable-length array (e.g. `itemInputs(InputItem...)`), use
   `Function.prototype.apply(builder, array)` instead of spread — plain ES5, definitely
   supported.

6. **Don't assume KubeJS event API symmetry — verify against the actual decompiled class,
   every time.** `EntityEvents.rightClicked` does not exist (assumed by symmetry with
   `BlockEvents.rightClicked`); the real event for entity interaction is
   `ItemEvents.entityInteracted`, which has no id-filter argument (unlike
   `BlockEvents.rightClicked`) and requires checking `event.target.type` manually inside the
   callback. Found via `javap` decompilation of the actual KubeJS jar's event classes —
   this is the reliable method when the official docs are sparse/unwritten for a given
   event (which they often are). The same "decompile and verify, don't guess" approach was
   used successfully for GTCEU's KubeJS recipe schema (`itemInputs`/`itemOutputs`/`duration`/
   `EUt(voltage, amperage)` — note `EUt` takes **two** required args, not one, confirmed via
   bytecode) and Create's (`event.recipes.create.<type>(outputs, inputs)` +
   `.processingTime()`/`.heated()`/`.superheated()`).

7. **`config/s3_progression_mod/progression.json` and Java's static fields only load once,
   at game/world startup.** Neither `/kubejs reload_server` nor editing the file on disk
   afterward has any effect until a full restart — this was the root cause of an early
   "interaction gates don't work" bug candidate (though the *actual* cause that time turned
   out to be the Rhino spread bug above; don't assume it's always the stale-load issue,
   check timestamps in the log to rule it in/out).

8. **MV tier was deliberately removed from progression** (commit "Removed MV stage..."):
   the gating blocks naturally land at the start of LV, middle of MV, middle of HV, middle
   of EV, so inserting one more gate between mid-MV and mid-HV wasn't good pacing, and
   there's no good candidate block to gate right before MV either. `ModScienceItems.Age`
   only has 8 entries now (BRONZE, IRON, STEEL, STEAM, LV, HV, EV, IV) — **do not** add MV
   back without also fully re-threading the gate spacing question. IV is intentionally kept
   even though nothing gates it yet — it's prep for 0.14+ content, not dead weight.

9. **A game crash from an unrelated mod bug can look like "my change broke it" — always
   read the actual crash report / log, don't guess from symptoms.** A reported "server
   crashed" turned out to be an unrelated `NullPointerException` in `TooManyRecipeViewers`'
   EMI plugin, triggered on player logout, with a completely different stack trace than
   anything in this mod. Conversely, a *different* reported crash ("crashed when placing a
   lab") *was* actually this mod's bug (Pitfall #4). Get the exact crash report/log line
   before deciding which one it is — `grep -in "ERROR\|FATAL\|Exception"` the relevant
   `logs/latest.log`, and check crash-reports folders. A server log ending abruptly with no
   shutdown sequence (no "Saving worlds"/"Saving chunks" messages) indicates a hang/crash;
   one ending with a clean save-and-shutdown sequence was just a normal stop, not a crash —
   don't assume every log rotation is a crash.

10. **Adding a new required blockstate property to an already-placed, already-released
    block is a real migration risk for existing world saves.** When `ACTIVE` was added to
    `LaboratoryBlock`, a pre-existing world's already-placed labs (saved before the
    property existed) triggered `org.embeddedt.modernfix.dynamicresources.ModelMissingException`
    ("missing model for variant: 's3_progression_mod:laboratory#'" — note the *empty*
    property string) when their chunk rendered, plus a cascading item-model load failure.
    This was investigated but not fully root-caused or fixed (see Known Issues) — if you
    add another blockstate property to an already-shipped block, expect this same class of
    bug on any world that already has the block placed, and test against an *existing* save
    with the block already placed, not just a fresh world.

## Known issues / unfinished work

- **Recipe system duplication and gaps** (Pitfall #2) — the biggest open item. Static
  per-category recipe jsons vs. `science_recipes.js`; only `mining` is designed in the new
  system; `mv_*` static recipes are actively broken in production logs right now.
- **Farming/production/exploration/challenge science recipes were never designed.** The
  original design intent (from the user): farming cheap-but-picky (tailored to specific
  crops per tier), production resource-expensive-but-automatable (GTCEU/Create machines —
  motors, conveyors, circuits), exploration needs items from across biomes/dimensions,
  challenge needs rare/hard-to-get-early items at the edge of the unlocked age.
- **The ModernFix/pre-existing-world blockstate migration issue (Pitfall #10) was found but
  not resolved** — worth a proper fix (or at least a documented recommendation: e.g. "break
  and re-place any Laboratory placed before this update") before shipping the active/
  decorative feature further.
- **Deployment drift**: several local copies (`s3_server`, `TerraFirmaGreg-Modern` without
  `(1)`) were explicitly left un-updated after the most recent fix, per the user's own
  scoped request at the time — don't assume they're current. `s3_client`/`s3_client_13.10`
  need re-syncing whenever the server-side mod list changes (see the client-pack memory
  note on the pakku-lock.json process).
- Texture animation for the active Laboratory (`laboratory_top_active.png`,
  `laboratory_side_active.png`) is currently a **placeholder** (a generated brightness-pulse
  effect over the static texture) — the user said they want to paint the real art
  themselves; don't treat the current frames as final.

## Where to look first for anything not covered here

- `README.md` — user-facing install/build/dependency docs, kept in sync with actual
  `mods.toml` dependencies; update it if you change dependencies or the tools workflow.
- Memory files (this session's persistent memory, not this repo) — particularly the CivTFG
  S2→S3 migration notes, the S3 client-pack build process, and the PebbleHost SFTP note —
  cover context that spans *outside* this repo (the wider modpack, the live server) which
  this file deliberately doesn't duplicate.
