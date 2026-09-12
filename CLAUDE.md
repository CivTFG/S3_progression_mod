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
    stage/GatedItemEnforcer.java tick-based inventory scan enforcing "possession" gates;
                                 also exposes lockedMessage(Player, ItemStack), the single
                                 check both this sweep and the mixins below share
    stage/FireStartGateEnforcer.java  HIGHEST-priority listener on TFC's own
                                 StartFireEvent, closing the Firestarter gate-bypass (see
                                 Pitfall #11) — the one place this mod links directly
                                 against TFC's Java classes instead of just block-id strings
    mixin/CraftingLockMixin.java        server-side: cancels taking a gated item out of a
                                 vanilla ResultSlot the instant it's clicked (possession
                                 gates only) - pre-empts GatedItemEnforcer's up-to-1s sweep
                                 delay for the common "craft it, grab it" case. Ported from
                                 the sibling CivTFG-Progression project - see Pitfall #12
    mixin/CraftingLockScreenMixin.java   client-side mirror of the above (same check, so
                                 the client never shows the item moving before a server
                                 correction reverts it)
  src/main/resources/
    META-INF/mods.toml           dependency version ranges — see Pitfall #1
    s3_progression_mod.mixins.json   Mixin config - see Pitfall #12 for the setup gotchas
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
  transition): four mechanisms —
  - `interaction`: KubeJS `BlockEvents.rightClicked` cancels the interaction
    (`blocked_blocks.js`). For entities (e.g. rockets, which aren't blocks), use
    `ItemEvents.entityInteracted` instead and check `event.target.type` manually — there is
    no `EntityEvents.rightClicked`, don't assume API symmetry with `BlockEvents` (this was a
    real bug — see Pitfall #6). For `interaction` gates on TFC blocks specifically,
    `FireStartGateEnforcer` (Java, see Pitfall #11) *also* re-checks the same gate list
    against TFC's `StartFireEvent`, since fire-starting tools (and even a plain dispensed
    Flint and Steel — see Pitfall #13) ignite blocks through that event, not a normal
    right-click.
  - `gtceu_voltage_interaction`: same idea as `interaction`, but for "every GTCEU machine of
    voltage tier X" at once instead of a fixed `blocks` list — see Pitfall #14 for how this
    avoids hand-maintaining a block-id list per voltage tier.
  - `placement`: `BlockEvents.placed` cancels it.
  - `possession`: **Java-side** (`GatedItemEnforcer`, a tick handler scanning inventories) —
    used where placement/interaction gating alone is bypassable once a player has
    automation capable of placing blocks or acquiring items without the gated action firing.
    The tick sweep alone has a real gap though: up to `CHECK_INTERVAL_TICKS` (1s) between a
    gated item being crafted and it actually getting stripped, during which a player can
    grab it from the crafting result slot and, on a real server, do something with it before
    the sweep catches up. `CraftingLockMixin`/`CraftingLockScreenMixin` close that
    specific gap by intercepting the result-slot click itself (see Pitfall #12) - the tick
    sweep still runs as the backstop for anything that doesn't go through a vanilla-style
    crafting result slot (dispensers, GTCEU machine output slots, etc., none of which are
    `ResultSlot`).
- **Laboratory active/decorative split**: only the *first* Laboratory placed in a team's
  claim is functional; every other one (unclaimed chunk, or a team that already has one) is
  a decorative "out of order" copy — same block, `ACTIVE` blockstate property. See Pitfall
  #4 for the exact bug this produced and how it's fixed now.
- **Three cosmetic Laboratory variants** (`laboratory` aka "Primitive Laboratory" - kept its
  original registry name for save compatibility, only its lang display name changed -
  `advanced_laboratory`, `quantum_laboratory`): three separate `Block`/`BlockItem`
  registrations, all just `new LaboratoryBlock(...)` with the same properties, sharing one
  `BlockEntityType` (`Builder.of` takes valid-blocks as varargs). The "only one active lab"
  flag lives on the **team** (`ProgressionTiers.hasLaboratory`), not per-block, so it
  automatically applies across all three variants with zero extra logic - placing an
  Advanced Laboratory while a Primitive one is already active just makes the Advanced one
  the decorative copy, and vice versa. Each variant's own loot table drops itself (not a
  shared/generic item) - unlike the single-item active/decorative case where a decorative
  copy just drops a normal placeable item, since here each variant *is* its own distinct,
  real item and collapsing them back to one on drop would be a real regression from the
  player's perspective. Two things had to be generalized away from a hardcoded single block
  when this was added - if a fourth variant is ever added, check both again:
  - `LaboratoryMenu#stillValid` used to hardcode `ModBlocks.LABORATORY.get()`; now checks
    `blockEntity.getBlockState().getBlock()` instead (reuses vanilla's own
    `stillValid(ContainerLevelAccess, Player, Block)`, which is Forge's reach-attribute-aware
    version, not a naive fixed-distance check - don't reimplement that math, just pass it
    the right `Block`).
  - `LaboratoryBlockEntity#getDisplayName` used to hardcode the `"...laboratory"`
    translation key; now returns `getBlockState().getBlock().getName()` (resolves to
    `Component.translatable(getDescriptionId())` automatically for whichever variant it is).
  Placeholder textures for the two new variants are the original Laboratory textures with a
  flat color tint applied (blue for Advanced, violet for Quantum) purely so the three are
  visually distinguishable at all before real art replaces them - same "functional
  placeholder, not final art" situation as the ACTIVE-state animated textures.
- **Science items**: `ModScienceItems` registers 5 items (one per category) × 11 ages
  (`Age` enum) = 55 items, named `<age>_<category>_science`. `ModScienceItems.register()`
  fails fast at startup if `Age`/`Category` don't exactly match `progression.json`'s
  tiers/categories — keep these in lockstep.
- **A tier's science items stop being craftable once that tier is itself unlocked** (not
  just gated against jumping ahead) — see Pitfall #13. `ProgressionTiers.canCraftTier`
  is the single enforcement point (`LaboratoryBlockEntity.getMatchingScience` rejects
  before anything is consumed), so a team can never waste items feeding an already-finished
  tier or an unreached future one.

## Configuration

### `progression.json` (single source of truth — both Java and every KubeJS script read this
one file at runtime; edit it, not hardcoded copies)

```jsonc
{
  "researchKey": "s3_progression_mod:research",     // NBT key on team extra data
  "categories": ["mining", "farming", "production", "exploration", "challenge"],
  "tiers": [
    { "key": "BRONZE", "displayName": "Bronze Age", "stageId": "bronze_unlocked", "threshold": 3 },
    // ... IRON, STEEL, STEAM, LV, MV, HV, MOON, EV, MARS, IV — MV was removed once (Pitfall
    // #8) and reintroduced later once real gating blocks existed for it (Pitfall #14);
    // MOON/MARS are tiers in their own right too, not just rocket labels
  ],
  "gates": [
    { "requiresTier": "BRONZE", "mechanism": "interaction", "blocks": ["tfc:bloomery"], "message": "..." },
    { "requiresTier": "STEAM", "mechanism": "gtceu_voltage_interaction", "voltage": "LV", "message": "..." },
    // mechanism: "interaction" | "placement" | "possession" | "gtceu_voltage_interaction";
    // add "entity": true for entity-type blocks (rockets); "blocks" is an array of ids
    // (not used by "gtceu_voltage_interaction", which uses "voltage" instead - see Pitfall #14)
  ]
}
```
Current gate list: bloomery (BRONZE, interaction), blast furnace (IRON, interaction), steam
boilers (STEEL, placement+possession), all LV GTCEU machines (STEAM,
`gtceu_voltage_interaction`), all MV GTCEU machines (LV), all HV GTCEU machines (MV), moon
rocket (HV, entity), all EV GTCEU machines (MOON), mars rocket (EV, entity), all IV GTCEU
machines (MARS). The old single-block "High Temp Precision Fabricator" LV gate and the old
STEAM-tier possession/placement gate on the three LV generator blocks were both removed -
subsumed by the generic "all LV machines" gate (see Pitfall #14).

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
   are **static** Forge datapack recipes, one per age×category, seemingly an early
   placeholder set generated before `science_recipes.js` existed. `science_recipes.js` is
   the newer, actively-maintained, data-driven generator — but it currently only covers the
   `mining` category. This means:
   - For **mining**, both the old static recipes *and* the new `science_recipes.js` ones
     are simultaneously active for the same output items (e.g. `bronze_mining_science` can
     currently be crafted via the old hardcoded "2x bronze ingot + flint + paper" recipe
     *and* via 5 separate newer recipes) — almost certainly unintentional duplication.
   - For **farming/production/exploration/challenge**, the static files are the *only*
     working recipes right now (mining is the only category "upgraded" so far).
   - The 5 `mv_*` static files (`mv_challenge_science.json` etc.) were **broken** for a
     while (`ModScienceItems.Age` had no `MV`, so the result item didn't exist - see
     Pitfall #7/#8) - now that `MV` is back (Pitfall #14), they load again, but their
     ingredients (`tfg:mv_universal_circuit`) referenced an item that no longer exists
     anywhere in the pack (confirmed: not in any mod jar's model paths or lang files) and
     have been swapped to `#gtceu:circuits/mv` instead, the same tag already used by
     `advanced_laboratory.json`'s recipe. **`MOON`/`MARS` have items registered
     (`moon_*_science`/`mars_*_science`, with placeholder textures - see Known Issues) but
     no recipes of any kind yet** - crafting them is intentionally out of scope until
     someone designs them.
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

8. **MV tier was deliberately removed from progression, then reintroduced later** (commit
   "Removed MV stage..."; reintroduced alongside Pitfall #14). It was originally removed
   because the gating blocks of the time naturally landed at the start of LV, middle of MV,
   middle of HV, middle of EV, so inserting one more gate between mid-MV and mid-HV wasn't
   good pacing, and there was no good candidate block to gate right before MV either. That
   blocker is what Pitfall #14's generic per-voltage-tier gate solves - "all MV machines"
   and "all HV machines" are real, well-paced gates that didn't exist back then. If you're
   ever tempted to remove a tier again because "there's no good gate for it", check whether
   a generic mechanism (like #14) could manufacture one before deciding there's truly none.

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

11. **A block-interaction gate only sees a normal right-click — a tool that fires its real
    effect through its own custom event, decoupled from any specific right-click, bypasses
    it entirely.** TFC's Firestarter doesn't ignite a block on right-click; the click just
    starts a ~3.5s "charge" (`Item#onUseTick`, TFC's `FirestarterItem`), which re-raycasts
    every tick and only fires TFC's own `@Cancelable` `StartFireEvent` once charging
    completes — a player can start charging while looking at anything, walk up to a gated
    Bloomery/Blast Furnace, and light it the moment the charge finishes, with
    `BlockEvents.rightClicked` never having fired for that block at all. Found this by
    decompiling `FirestarterItem` end to end (`onUseTick`'s bytecode literally calls
    `StartFireEvent.startFire(level, raycastPos, state, ...)` in its final branch) rather
    than guessing from the symptom (same "decompile, don't guess" method as Pitfall #6).
    Fixed with a **Java** listener (`FireStartGateEnforcer`) at `EventPriority.HIGHEST` on
    `StartFireEvent` directly — not KubeJS's generic `ForgeEvents.onEvent`, which always
    registers at `EventPriority.NORMAL` (confirmed via decompiling the KubeJS jar itself)
    and so can't guarantee running before TFC's own `StartFireEvent` handler
    (`ForgeEventHandler#onFireStart`, also `NORMAL` — Forge skips a `NORMAL` listener
    entirely once an earlier listener has already cancelled the event, so ordering between
    two `NORMAL` listeners is registration-order-dependent and not something KubeJS's
    generic hook lets you control). This is the first time this mod links against another
    mod's actual Java classes (TFC is now a **mandatory** `mods.toml` dependency,
    `[3.2.23,)`) rather than only referencing block ids as strings from KubeJS/JSON — worth
    noticing if this pattern needs repeating for some other mod's tool later. **If another
    gate ever gets bypassed the same way** (a tool with its own charge-up/custom-event
    ignition path instead of a plain right-click), check whether the relevant mod fires a
    custom Forge event for its actual effect, and consider the same Java-side
    `EventPriority.HIGHEST` approach rather than trying to catch it in KubeJS.

12. **Mixin infrastructure was added for the first time for `CraftingLockMixin`/
    `CraftingLockScreenMixin`** (ported from a sibling project, `CivTFG-Progression` at
    `C:\Users\erikp\Desktop\Civ TFG\CivTFG-Progression` - its own `CraftingLockMixin.java`/
    `CraftingLockScreenMixin.java`/`*.mixins.json` are the reference implementation this was
    cross-checked against, method-signature-for-method-signature, before writing anything).
    Two gotchas confirmed by directly inspecting that sibling project's own build:
    - **A mixin only actually loads from a *built* jar if `MixinConfigs` is set in the jar
      manifest.** Without it, the mixin still works in a dev environment (because the
      `-mixin.config=...` run argument covers that), which makes the bug easy to miss until
      someone runs the real, packaged jar. Confirmed CivTFG-Progression's own
      `build.gradle` is missing exactly this line - deliberately not repeated here (see
      `build.gradle`'s `jar` task manifest block).
    - The Mixin Gradle plugin (`org.spongepowered.mixin` `0.7.38`) needs: the plugin
      declaration, a `mixin { add sourceSets.main, "<modid>.refmap.json"; config
      "<modid>.mixins.json" }` block, `-mixin.config=...` args on every run config
      (client/server/gameTestServer/data - miss one and mixins silently don't apply for
      that run type only, easy to not notice since e.g. `data` runs rarely touch anything
      mixin-relevant), and `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`.
      After building, verify the refmap actually resolved real method names (`unzip -p
      ...jar s3_progression_mod.refmap.json` - a wrong `@Inject(method = "...")` name/
      signature shows up here as a resolution failure at compile time, not a silent runtime
      no-op, so checking this after every build is cheap insurance).
    - `AbstractContainerMenu#clicked(int, int, ClickType, Player)` and
      `AbstractContainerScreen#slotClicked(Slot, int, int, ClickType)` were both verified
      against the actual decompiled MC 1.20.1 classes before trusting the ported code (same
      "decompile, don't guess" method as Pitfalls #6 and #11).
    - This only covers vanilla-style `ResultSlot`s (crafting table and similar). GTCEU
      machine output slots are a different `Slot` subclass entirely and this mixin never
      sees them - `GatedItemEnforcer`'s tick sweep is still necessary as the backstop for
      those, this mixin is a latency fix for the common case, not a full replacement.

13. **A "must not have advanced past X" check needs its own explicit guard - checking only
    "the previous step is done" doesn't imply "this step isn't ALSO already done".**
    `ProgressionTiers.canCraftTier(team, tierKey)` only checked that the *preceding* tier
    was already unlocked, which correctly blocks jumping ahead to a future tier, but says
    nothing about whether `tierKey` itself was already crossed. Since the first tier's
    check was hardcoded `true` with no condition at all, a team that had long since unlocked
    Bronze could keep crafting (and consuming) `bronze_*_science` items forever, for zero
    effect. Fixed by adding an explicit `if (isUnlocked(team, tierKey)) return false;` at
    the top - don't assume a chain of "is the previous step done" checks automatically rules
    out "is this exact step already done", they're different questions.

14. **A KubeJS script can reflectively reach into ANOTHER mod's Java classes via
    `Java.loadClass(...)` without that mod becoming a compile-time dependency of this one** -
    used to replace a hand-maintained, per-voltage-tier GTCEU block-id list with one generic
    check. The ask was "block right-click on every GTCEU LV/MV/HV/EV/IV machine", which
    would otherwise mean enumerating ~50+ block ids per voltage tier by hand (and
    re-enumerating on every GTCEU update). Confirmed via `javap` against the real
    `gtceu-*.jar`: every GTCEU machine block (hatches/buses/casings included - genuinely
    "every" machine, not just the simple single-block ones) is an instance of
    `com.gregtechceu.gtceu.api.block.MetaMachineBlock`, whose `getDefinition().getTier()`
    returns an `int` that indexes directly into `com.gregtechceu.gtceu.api.GTValues.VN`
    (`["ULV","LV","MV","HV","EV","IV","LuV",...]`) to get the voltage name. `blocked_blocks.js`
    loads both classes once via `Java.loadClass(...)` (same mechanism already used for this
    mod's own `ProgressionTiers`) and does `MetaMachineBlock.isInstance(block)` +
    `GTValues.VN[...]` inside a single `BlockEvents.rightClicked` with no id filter, checked
    against a new `"gtceu_voltage_interaction"` gate mechanism (`requiresTier` + `voltage`
    instead of a `blocks` list). This is *still* only a soft/"by id" reference to GTCEU (no
    `libs/` jar, no `mods.toml` entry) - KubeJS's class filter only denies `java.io`/
    `java.nio`, not other mods' classes. **If `Java.loadClass` for some other mod's class
    ever does get denied**, the fallback is a real Java-side listener (vendored jar +
    `mods.toml` dependency, same pattern as TFC in Pitfall #11) instead. This mechanism was
    used to reintroduce MV as a real tier (see Pitfall #8) and to add MOON/MARS - "all LV/MV/
    HV/EV/IV machines" gates STEAM/LV/MV/MOON/MARS tiers respectively, and it *replaced* two
    older, more specific gates entirely: the single-block LV `tfg:high_temp_precision_fabricator`
    interaction gate, and the STEAM-tier possession+placement gate on the three specific LV
    generator blocks (`gtceu:lv_steam_turbine`/`lv_gas_turbine`/`lv_combustion`) - both are
    gone from `progression.json`, fully subsumed by the generic "all LV machines" gate.
    Not yet live-tested in-game (needs an actual GTCEU machine right-clicked before/after
    the relevant tier) - if `Java.loadClass` unexpectedly throws for either GTCEU class,
    check the exact ClassFilter error in the log first before assuming the fallback is
    needed.

15. **A gate-bypass fix that returns early on missing context (like a null `Player`) can
    silently stop enforcing the gate entirely, instead of just skipping the parts of the
    fix that need that context.** The original fix for the FireStartGateEnforcer NPE crash
    (a dispenser-fired `StartFireEvent` has no player) returned immediately whenever
    `player == null` - that stopped the crash, but it also meant a dispenser dispensing a
    plain Flint and Steel (TFC's `DispenserBehaviors$5`/`TFC_FLINT_AND_STEEL_BEHAVIOR`,
    confirmed via the same `javap` method as Pitfall #11 - it calls `StartFireEvent.startFire`
    directly, same chokepoint as the Firestarter) could light a gated Bloomery/Blast Furnace
    completely unchecked. The correct shape for this kind of fix: separate "should this be
    cancelled" (always evaluated, a dispenser has no stage so it never counts as unlocked)
    from "who do I tell about it" (conditional on a player actually existing) - never let a
    null-check for the second thing skip the first.

## Known issues / unfinished work

- **Recipe system duplication and gaps** (Pitfall #2) — the biggest open item. Static
  per-category recipe jsons vs. `science_recipes.js`; only `mining` is designed in the new
  system.
- **Farming/production/exploration/challenge science recipes were never designed**, and
  **MOON/MARS have no recipes in any system yet** (items exist, nothing crafts them). The
  original design intent (from the user): farming cheap-but-picky (tailored to specific
  crops per tier), production resource-expensive-but-automatable (GTCEU/Create machines —
  motors, conveyors, circuits), exploration needs items from across biomes/dimensions,
  challenge needs rare/hard-to-get-early items at the edge of the unlocked age.
- **The new `gtceu_voltage_interaction` gate mechanism (Pitfall #14) hasn't been live-tested
  in-game yet** — needs someone to right-click an actual LV/MV/HV/EV/IV GTCEU machine before
  and after the relevant tier is unlocked, and to confirm `Java.loadClass` for GTCEU's
  `MetaMachineBlock`/`GTValues` isn't denied by KubeJS's class filter in practice.
- **Moon/Mars science item textures are placeholders** (Pitfall below on Laboratory
  textures applies here too): a flat tint (silvery grey for Moon, rust red for Mars) over
  the existing IV-tier icon per category, purely to be visually distinct until real art
  exists.
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
  themselves; don't treat the current frames as final. Same applies to the Advanced/Quantum
  Laboratory variants' textures (all 10 `advanced_laboratory_*`/`quantum_laboratory_*` files)
  - these are just the original Laboratory textures with a flat color tint (blue/violet)
  applied programmatically, purely so the three variants are visually distinguishable at
  all right now.

## Where to look first for anything not covered here

- `README.md` — user-facing install/build/dependency docs, kept in sync with actual
  `mods.toml` dependencies; update it if you change dependencies or the tools workflow.
- Memory files (this session's persistent memory, not this repo) — particularly the CivTFG
  S2→S3 migration notes, the S3 client-pack build process, and the PebbleHost SFTP note —
  cover context that spans *outside* this repo (the wider modpack, the live server) which
  this file deliberately doesn't duplicate.
