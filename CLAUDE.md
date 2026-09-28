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
  `progression_listener.js` / `progression_commands.js`. The exact craft that pushes a
  tier's total past its threshold also triggers a **global** chat broadcast to every online
  player ("`<team name>` just researched `<tier>`!"), not just the team's own members -
  guarded by `previousTotal <= tierConfig.threshold` so it fires exactly once per
  tier-unlock, not on every later craft of that same tier's items (which would otherwise
  spam it, since `total > threshold` alone stays true forever after). Uses
  `net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()` directly (reflected
  in via `Java.loadClass`, same pattern as everywhere else) rather than any KubeJS "global
  server" binding, since this script lives in `startup_scripts` and its callback fires
  later at runtime - deliberately not assuming which bindings are available in that
  context without checking.
- **`/progression teams`** (in `progression_commands.js`) lists every FTB Team whose
  current tier isn't Bronze - i.e. has crossed at least one tier's threshold
  (`ProgressionTiers.isUnlocked(team, 'BRONZE')`) - with its current tier
  (`ProgressionTiers.currentProgress(team)`, or "Everything (fully researched)" once every
  tier is done). Teams still in Bronze are deliberately omitted, not listed as "Bronze Age".
- **Command permissions**: none of `/progression`'s three subcommands had a `.requires(...)`
  at all until this was actually checked - every one defaulted to Brigadier's level 0 (any
  survival player). `status`/`teams` are read-only and stay open on purpose. **`reset` is
  now `.requires(src => src.hasPermission(2))` (op-only)** - it zeroes a team's counter for
  a tier *and* immediately strips that tier's GameStage from every online team member, with
  no confirmation - any team member (not just the owner) being able to do that to the whole
  team unprompted was a real grief vector, not just a "self-cheat". If you add another
  command here that mutates team state (not just reads it), default to op-only and open it
  up deliberately, rather than the other way around.
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
  #4 for the exact bug this produced and how it's fixed now. Right-clicking a decorative
  copy names the actual reason (`LaboratoryBlock#outOfOrderMessage`): "chunk isn't claimed
  by any team" if `resolveTeam` returns null, or the exact position of the real active
  Laboratory (`ProgressionTiers.getLaboratoryPos(team)`) otherwise - the has-laboratory NBT
  went from a plain boolean to a compound storing `x`/`y`/`z` to make that position
  available (see `ProgressionTiers.setHasLaboratory`/`clearHasLaboratory`).
- **Five cumulative Laboratory variants** (`laboratory` aka "Primitive Laboratory" - kept
  its original registry name for save compatibility - `industrial_laboratory`,
  `electric_laboratory`, `advanced_laboratory`, `elite_laboratory`): five separate
  `Block`/`BlockItem` registrations, all just `new LaboratoryBlock(...)` with the same
  properties and a different `LaboratoryBlock.LabTier`, sharing one `BlockEntityType`
  (`Builder.of` takes valid-blocks as varargs). **Cumulative, not exclusive**: each
  successive tier's `LabTier.allowedAges` is the previous tier's set plus its own new pair
  of `ModScienceItems.Age`s (`ELITE` = every remaining Age, since Mars/EV is now the last
  pair - see Pitfall #16 for why this wasn't always true and what it looked like before).
  The "only one active lab" flag lives on the **team** (`ProgressionTiers.hasLaboratory(team)`/
  `setHasLaboratory`) and is NOT per-tier - a team gets exactly one functional Laboratory
  total, of whichever of the 5 tiers/variants was placed first; placing any other variant
  while one is already active just gets a decorative copy, same as placing a second of the
  same variant would (this was briefly per-tier instead, see Pitfall #17 - reverted).
  Each variant's own loot table drops itself (a real, distinct item per variant, not a
  shared/generic placeable). Two things had to be generalized away from a hardcoded single
  block when multiple variants were first introduced - if a sixth variant is ever added,
  check both again:
  - `LaboratoryMenu#stillValid` checks `blockEntity.getBlockState().getBlock()` (reuses
    vanilla's own `stillValid(ContainerLevelAccess, Player, Block)`, which is Forge's
    reach-attribute-aware version, not a naive fixed-distance check - don't reimplement
    that math, just pass it the right `Block`).
  - `LaboratoryBlockEntity#getDisplayName` returns `getBlockState().getBlock().getName()`
    (resolves to `Component.translatable(getDescriptionId())` automatically for whichever
    variant it is).
  Block textures are real, hand-painted art for all 5 tiers as of this writing - each has
  its own top/side/front/back/bottom faces via a full `minecraft:block/cube` model (see
  Pitfall #16 for why the model had to move off `cube_bottom_top`), plus the existing
  placeholder ACTIVE-state pulse animation (`_top_active`/`_side_active`, unrelated to this
  change, still a placeholder).
- **Science items**: `ModScienceItems` registers 5 items (one per category) × 10 ages
  (`Age` enum) = 50 items, named `<age>_<category>_science`. `ModScienceItems.register()`
  fails fast at startup if `Age`/`Category` don't exactly match `progression.json`'s
  tiers/categories — keep these in lockstep. IV was removed as a research tier entirely
  (Pitfall #16) - Mars is now the last one.
- **"Empty" science items**: `ModEmptyScienceItems` registers one additional
  `<age>_empty_science` item per `Age` (10 total) - a per-tier blank crafting base meant to
  be turned into that tier's 5 real science items. Deliberately a **separate registry**
  from `ModScienceItems`, not a 6th `Category` - it must never be a valid Laboratory
  research input, so it's simply never added to `ModScienceItems`'s
  `identify()`/`SCIENCE_ITEMS` map; `LaboratoryBlockEntity.getMatchingScience` already
  rejects any item it doesn't recognize, no extra check needed. **No recipes exist for
  these yet** (what crafts the empty item, what empty+X produces each category) - that's
  real recipe-design work, deliberately deferred (see Known Issues).
- **Science-item tags** (`data/s3_progression_mod/tags/items/`): 10 per-tier tags
  (`<age>_science`, e.g. `#s3_progression_mod:lv_science` = that tier's 5 category items)
  plus 5 per-category tags (`<category>_science`, e.g. `#s3_progression_mod:mining_science`
  = that category across all 10 tiers). Deliberately **excludes** the empty-science items
  (user's explicit choice) - a recipe or datapack that wants "any mining science item" or
  "any LV science item" can reference these instead of listing 5-10 ids by hand. Plain
  static tag files, no Java code involved.
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
    // ... IRON, STEEL, STEAM, LV, MV, HV, MOON, EV, MARS — MARS is the last tier now, IV
    // was removed as a research tier entirely (Pitfall #16); MV was removed once (Pitfall
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

The two rocket gates each list **two** entity ids, not one: `ad_astra:tier_1_rocket` +
`tfg:tier_1_double_rocket` for HV, `ad_astra:tier_2_rocket` + `tfg:tier_2_double_rocket` for
EV. The modpack's own addon mod (`tfg`, not `ad_astra`) adds a 2-person "double rocket"
variant alongside every Ad Astra rocket tier - found by grepping the ProbeJS dump for
`double_rocket` (they don't show up in `tools/item_index.json` at all since that index only
covers items, and rockets are entities). `tfg:tier_3_double_rocket`/`tier_4_double_rocket`
also exist but are deliberately not gated - there's no tier beyond Mars/EV for them to gate
against, same reasoning as `ad_astra:tier_3_rocket`/`tier_4_rocket` never having had gates.

### `science_recipes.js` (data-driven recipe generator)

The `SCIENCE_RECIPES` array (between `// ===RECIPES-JSON-START/END===` markers) is written
as **strict JSON** (quoted keys, no comments inside it, no trailing commas) specifically so
`tools/recipe_editor.py` can parse/rewrite it with Python's `json` module without needing a
real JS parser. It's still a valid JS array literal to KubeJS. Entry shape and supported
machine types (`crafting_table`, GTCEU recipe types with a `tier` for EU/t, Create recipe
types with optional `heat`) are documented in the file's own header comment — read that
before adding recipes, it's kept accurate.

**Currently empty** (`SCIENCE_RECIPES = []`) - the user had all science-item recipes deleted
outright (both this array's old mining-only content AND the 40 static
`data/s3_progression_mod/recipes/<age>_<category>_science.json` files, which were the only
working recipes for farming/production/exploration/challenge) to make room for a fresh
design built around the new empty-science items (see Known Issues) instead of extending the
old one. The 5 Laboratory-block recipes (`laboratory.json`,
`industrial_laboratory.json`, etc.) are unaffected - only science-item recipes were wiped.

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
  mod jars happen to be installed locally, 41357 entries as of last build with a fresh
  ProbeJS dump). **All four `DEFAULT_*` paths (and `recipe_editor.py`'s `INSTANCE_TARGET`)
  point at `Instances\TerraFirmaGreg-Modern (1)`** - that's the instance with an actual
  ProbeJS dump in it (`kubejs/probe/generated/globals.d.ts`); the base
  `TerraFirmaGreg-Modern` was renamed/archived to `CivTFG` earlier and the tools briefly
  pointed there instead (see Pitfall #19) before switching to `(1)` once it turned out to
  be the one with a live dump. **If the dump ever moves to a different instance again
  (or someone re-runs `/probejs dump` somewhere else), update all 5 of these together** -
  don't assume whichever instance "feels current" actually has the dump; check
  `kubejs/probe/generated/globals.d.ts` exists there first.
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

2. **(Historical - resolved) Two parallel, overlapping recipe systems existed for science
   items.** `data/s3_progression_mod/recipes/*.json` (static, one per age×category) and
   `science_recipes.js` (data-driven, mining-only) were both live at once for mining,
   producing unintentional duplicate recipes for the same output items; farming/production/
   exploration/challenge only ever had the static files; MOON/MARS never had any recipes at
   all. **Resolved by deleting all of it** - every static science-item recipe file and
   `science_recipes.js`'s `SCIENCE_RECIPES` array (now `[]`) - to make room for a fresh
   design built around the new empty-science items instead of reconciling two
   already-incomplete systems (see Known Issues for the new design's status). If you're
   looking for the old mining recipes, they're gone on purpose, not lost.

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

16. **A commit made by a different Claude session, working the same repo concurrently,
    landed a design that didn't match what the user actually wanted - the only way this
    surfaced was the user asking a clarifying question in a later, unrelated session.**
    While this session was mid-fix on the dispenser/tier bugs, another session's commit
    (`61cb638`, "Split laboratories into 6 tiers, each restricted to its own science pair")
    split the single Laboratory into 6 variants gated to *exclusive* pairs of Ages
    (Industrial = Steel/Steam only, nothing from Bronze/Iron). The user's actual intent,
    surfaced only when they asked "sind die erforschbaren Tiers schon auf 2 pro Labortyp
    begrenzt?" while delivering real art, was **cumulative** (each successive tier keeps
    everything earlier tiers could do, plus its own new pair) - fixed by changing
    `LaboratoryBlock.LabTier`'s `allowedAges` sets accordingly. **If you didn't write a
    commit yourself and its rationale doesn't fully add up, don't assume it's correct just
    because it's already merged** - ask, the same way you'd ask about a genuinely new
    request. Separately, in the same conversation the user also decided IV should stop
    being a research tier entirely (Mars is now last, "unlocks everything") - this made the
    6th variant (`QUANTUM`, ex-IV-only) redundant under the cumulative model, so it and the
    whole IV tier were removed together (see the repo layout / Science items sections
    above for exactly what that touched). The delivered Laboratory art also came as a 3x3
    face-net template per tier (top/side/front/back/bottom, with real front/back art, not
    just a repeated side) - the block model had to move from `minecraft:block/cube_bottom_top`
    (3 texture slots: top/side/bottom, one `side` on all 4 verticals) to full
    `minecraft:block/cube` (6 slots: `up`/`down`/`north`/`south`/`east`/`west`) to actually
    use the new faces - a plain texture swap wouldn't have been enough here.

17. **Pitfall #16's "one active lab per tier" was itself wrong - the user meant one active
    lab total, period.** Reported after actually testing in-game (placing one of each of
    the 5 tiers and seeing all 5 pulse/function at once). Fixed by reverting
    `ProgressionTiers.hasLaboratory`/`setHasLaboratory` from `(Team, LabTier)` back to
    plain `(Team)` - a single NBT boolean per team again, not one per tier. **Two "this
    seems like a real bug" reports about the same feature, from the same user, in a row
    (first "5 pulsing at once" dismissed as by-design, then this) is a strong signal to
    re-examine the premise, not just the latest symptom** - the *design itself* (one lab
    per tier) was the actual bug, inherited from Pitfall #16's concurrent-session commit
    and never actually confirmed as intentional by the user themselves until asked twice.

18. **A "cross/net" cube-texture template (6 faces laid out around a center square) needs
    the north/south/east/west assignment nailed down explicitly - a plausible-looking
    guess can still be a full quarter-turn off.** The delivered Laboratory art
    (`labN.png`, 3x3 grid `ABC/DEF/GHI` with corners transparent) has 4 *distinct* ring
    textures (D, E, F, I - confirmed by hashing: `D != F` for 4 of 5 tiers, only Primitive
    happened to have near-identical D/F), not "front + a shared side" as first assumed -
    that first attempt silently discarded D and duplicated F onto both east and west,
    losing information. The user's correction ("front should be right, left should be
    front, back should be left" - a rotation, described against what was actually
    deployed) plus "top rotated 90° clockwise" was enough to solve for a unique, fully
    self-consistent 4-cell assignment by elimination (3 stated relationships + exactly one
    cell left over pins down the 4th). Final mapping: `north=F, east=E, south=D, west=I`,
    `up=`cell B rotated 90° clockwise (`Image.transpose(Image.ROTATE_270)` in PIL - positive
    "rotate" in PIL is counter-clockwise, so 270° CCW = 90° CW), `down=H` unchanged. **When
    a cube net has more than 3 visually-similar faces, verify with a pixel diff (`hash`/
    `!=`) before assuming any two are "the same texture used twice"** - eyeballing small
    sprites at a glance isn't reliable enough to catch a near-identical-but-distinct pair.
    **Still wasn't fully right**: after seeing it in-game, front/back were swapped and
    east/west were swapped (a full 180° flip of the horizontal ring, on top of the
    rotation already applied) - current, actually-in-game-corrected mapping is
    `north=<prefix>_back.png, south=<prefix>_front.png, east=<prefix>_west.png,
    west=<prefix>_east.png` (i.e. the model's face *keys* are swapped, not the underlying
    PNG files/cell derivation - `_front`/`_back`/`_east`/`_west` still refer to the exact
    same cells as before, F/D/E/I are just wired to the opposite compass keys now). If
    this is STILL wrong, don't re-derive from scratch again - ask the user to describe
    what they see per-face directly this time (e.g. "north face shows X"), rather than a
    relative correction, now that there's a second data point to anchor from.

19. **A Python tool's hardcoded default path silently going stale (after the referenced
    CurseForge instance was renamed/archived elsewhere) doesn't error - it just quietly
    finds nothing, or falls back to whatever secondary sources still resolve.** Both
    `tools/build_item_index.py`'s four `DEFAULT_*` paths and `tools/recipe_editor.py`'s
    `INSTANCE_TARGET` still pointed at `Instances\TerraFirmaGreg-Modern`, which had been
    renamed to `Instances\CivTFG` in an earlier session (the base instance is now a `.7z`
    archive, not a live folder) - nobody had re-pointed the tools at the new name. Symptom
    reported: newly-added items (the empty-science items) "not in the recipe editor's
    index" - not because indexing them was ever broken, but because the whole tool had
    been silently scanning a folder that no longer existed for the mods themselves (still
    finding *some* items via the FTB Quests/EMI fallback sources, which happened to have
    their own separate stale-but-still-valid copies, masking the fact that the primary,
    most-authoritative source - actual mod jar model paths - was returning nothing at
    all). **When a "feature X isn't showing up" report turns out to affect *everything*
    equally (not just X), suspect the plumbing (paths/config) before the specific feature's
    own logic** - re-running `build_item_index.py` and comparing the total item count
    against a prior run (or just checking whether `DEFAULT_MODS_DIR.exists()`) would have
    caught this immediately.

20. **A `const`/`let` declared directly inside a bare block (an `if { }`, not a function
    body) at the top level of a KubeJS script can throw `TypeError: redeclaration of var X`
    from Rhino on the very first, otherwise-untouched load - not a symptom of anything else
    already using that name.** Real error, confirmed from `logs/latest.log`:
    `s3_progression_mod/blocked_blocks.js#124: ... redeclaration of var MetaMachineBlock`,
    on the mod's very first (and only) server-script load pass that session - "KubeJS
    server scripts" was only ever loaded once, so this wasn't a stale-scope-from-reload
    issue, and grepping the entire ProbeJS dump found no pre-existing global binding named
    `MetaMachineBlock` either. The actual difference from every OTHER top-level `const` in
    this same file (which all load fine) is that those are wrapped in a real IIFE
    (`(() => { ... })()`, genuine function scope), while the two that failed
    (`MetaMachineBlock`/`GTValues` inside `if (BLOCKED_BLOCKS_GTCEU_VOLTAGE_GATES.length > 0)
    { const ... }`) sat directly inside a bare `if` block with no function wrapper - Rhino's
    hoisting model for block-scoped `const`/`let` apparently double-declares the name in
    that specific shape (bare block at script top level), and the two declarations collide.
    Fixed by changing those two specific declarations from `const` to `var` (plain
    script-scoped, no block-hoisting semantics, so only one declaration site exists) -
    **not** by moving them into another IIFE, since `var`-not-`const` was the smaller,
    already-consistent-with-Pitfall-#5 fix (same "Rhino doesn't fully support an ES6-ism"
    family of bug as the object-spread issue). **If a future top-level `const`/`let` sitting
    directly inside a bare `if`/`for`/`while` block (not a function) throws this same
    "redeclaration of var" error on its very first load, don't assume some other script
    already claimed the name - check whether it's this exact shape first**, and either
    switch it to `var` or wrap it in a real IIFE like the file's other top-level consts.
    **Happened a second time, in a different file, once the code path actually ran**:
    `progression_listener.js`'s tier-unlock broadcast (`const Component`/
    `const ServerLifecycleHooks`/`const message` inside
    `if (previousTotal <= tierConfig.threshold) { ... }`) crashed the server the first
    time any team's research total actually crossed a tier threshold, since that branch
    had never executed before (confirmed via the crash report -
    `dev.latvian.mods.rhino.EvaluatorException: TypeError: redeclaration of var
    Component`). **This bug doesn't surface at script-load time if the offending branch is
    conditional and hasn't been hit yet** - unlike `blocked_blocks.js`'s occurrence (which
    threw immediately on load), this one only threw once real gameplay actually reached
    that specific `if` body for the first time. Fixed the same way (`const` → `var` for
    all three declarations in that block, not just the two that used `Java.loadClass`).
    **When auditing for this pattern, check every bare `if`/`for`/`while` block with a
    `const`/`let` inside it, not just the ones that have already been observed to fail** -
    a conditional branch that hasn't executed yet can be hiding the exact same bug. A
    proactive audit after this second crash found one more: `science_recipes.js`'s
    `CREATE_MACHINES` branch (`if (CREATE_MACHINES.indexOf(recipe.machine) !== -1) { const
    recipeId = ...; const builder = ... }`) has the identical shape and is currently
    silent for the same reason as `progression_listener.js` was - `SCIENCE_RECIPES` is
    empty (see Known Issues), so that branch has never run. Fixed proactively (`var`
    instead of `const`) before it gets the chance to crash the same way the moment someone
    adds a real Create-machine recipe entry. **This surfaced a second, real gotcha when
    fixing it: the sibling GTCEU branch further down in the same `forEach` callback
    already declared its own `const recipeId`/`const builder`, in the same function scope**
    - since `var` is function-scoped (not block-scoped), the two branches share one
    `recipeId`/`builder` binding for the whole callback despite looking like separate
    blocks, so `var` in one branch and `const` in the other for the same name is a genuine
    JS redeclaration **syntax error**, not just a Rhino quirk - both branches had to be
    switched to `var` together, not just the one that "needed" the fix.

21. **A GUI background texture that isn't padded to 256x256 (the vanilla convention) gets
    silently cropped-and-stretched by the 7-arg `GuiGraphics#blit(location, x, y, u, v,
    width, height)` overload, which hardcodes an assumed source-texture size of 256x256.**
    Reported symptom: the Laboratory GUI only showed "a bit more than four of the five
    research slots and a bit more than one inventory row" - looked like the declared
    176x166 image size was wrong and should instead be "around 120x105". It wasn't - the
    real `laboratory.png` genuinely is 176x166 (confirmed via PIL), and `imageWidth`/
    `imageHeight` in `LaboratoryScreen` were already correct. The actual bug:
    `renderBg`'s `guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight)` (7-arg,
    confirmed via `javap` against the real `GuiGraphics` class) internally calls the 9-arg
    overload with `textureWidth=256, textureHeight=256` hardcoded - the assumption baked
    into vanilla, since most vanilla GUI PNGs (`gui/container/inventory.png` etc.) really
    are padded to 256x256 even though only a smaller region is drawn. Our texture is a
    real, unpadded 176x166 file, so GL normalizes the requested `u=0..176, v=0..166`
    region against an assumed 256-wide/-tall canvas, sampling only the texture's top-left
    `176*176/256 ≈ 121` by `166*166/256 ≈ 108` px and stretching that over the whole
    176x166 draw box - which is *exactly* the "~120x105" the user reported seeing, once
    you do the math (not a coincidence - it's the same 256-assumption showing up twice,
    once in each dimension). Fixed with the 9-arg `blit` overload, passing
    `imageWidth`/`imageHeight` again as the explicit `textureWidth`/`textureHeight`
    arguments so GL knows the real texture size. **If a reported GUI-sizing bug's
    numbers look like a clean fraction of the declared size (roughly `declared^2/256` in
    each dimension), suspect this exact `blit`-overload issue before assuming the PNG
    itself or the declared `imageWidth`/`imageHeight` is wrong** - check the actual PNG's
    real pixel dimensions first (cheap, rules out "wrong file") before touching any
    layout constant.

22. **The modpack ships a `reliable_remover` mod that strips specific items from creative
    tabs/EMI/player inventories via `config/reliable_remover/<modid>.json` allowlists -
    the item is still technically a registered, valid crafting ingredient, but no player
    can ever legitimately obtain one, and EMI shows the recipe as if it doesn't exist at
    all (not just a blank/missing icon for that one ingredient).** Reported symptom: the
    Primitive and Industrial Laboratory recipes showed "no recipe" at all in EMI, while
    Advanced only had its plate ingredient's icon blank (a *different* class of bug - see
    Pitfall #23). Root-caused by finding `config/reliable_remover/tfc.json` in the actual
    test instance: it `"action": "remove"`s essentially all of TFC's basic per-metal
    `ingot`/`double_ingot`/`sheet`/`double_sheet`/`rod` items (including exactly
    `tfc:metal/ingot/copper` and `tfc:metal/sheet/wrought_iron`, the two ingredients
    Primitive/Industrial used) - this modpack (TerraFirmaGreg) intentionally replaces TFC's
    own basic metal item chain with GTCEU's equivalent material items once GTCEU is the
    primary tech mod, and removes the TFC originals from ever being visible/obtainable so
    players don't get confused by two parallel copper-ingot-shaped items. Fixed by
    switching those two ingredients to `gtceu:copper_double_ingot` /
    `gtceu:wrought_iron_plate` (confirmed not present in `config/reliable_remover/gtceu.json`'s
    own removal list, and confirmed real via `tools/item_index.json` - these are
    runtime-generated GTCEU material items with no static model file in the jar, so
    Pitfall #3's ProbeJS-dump-based lookup is the only way to verify them, `unzip -l` on
    the actual `gtceu-*.jar` for a model path won't show anything and that's expected, not
    a red flag). **Before using ANY TFC basic metal item id
    (`tfc:metal/<ingot|sheet|rod|...>/<metal>`) in a new recipe, check it against every
    `config/reliable_remover/*.json` in the actual target instance first** - a real,
    valid, existing item can still be effectively dead for crafting purposes in this
    specific modpack, and neither Minecraft's recipe loader nor EMI's basic display will
    flag this as an error; it just quietly shows "no recipe" like the ingredient was never
    there. Same caution likely applies to other early-game "basic material" items from any
    mod TFG's GTCEU integration supersedes, not just TFC specifically.

23. **`gtceu:aluminium_plate` was used in a recipe on the assumption that it existed
    (based on a since-superseded peer-session recipe that apparently used the same id at
    some earlier point) - it never actually existed.** Confirmed absent from
    `tools/item_index.json` entirely (41k+ entries, built from real mod jar model paths +
    a live ProbeJS dump - about as authoritative as this repo's tooling gets) - the only
    aluminium plate GTCEU registers is `gtceu:double_aluminium_plate`. Unlike Pitfall #22's
    Primitive/Industrial bug, a genuinely nonexistent item id doesn't hide the WHOLE
    recipe from EMI - the recipe still shows, just with a blank/missing icon in that one
    ingredient's slot ("the advanced one is missing the plates", as reported) - a useful
    tell for telling the two failure classes apart at a glance: **recipe entirely absent
    from EMI usually means a removed-but-real item (check `reliable_remover` configs
    first, per Pitfall #22); recipe present but with a blank ingredient slot usually means
    a genuinely nonexistent item id (check `tools/item_index.json`/the actual mod jar
    first).** Fixed by switching to `gtceu:double_aluminium_plate`.

## Known issues / unfinished work

- **Zero science-item recipes exist right now, anywhere, for any category** (Pitfall #2
  resolved by deletion) - not even mining. Every recipe (crafting the 10 new empty-science
  items, and what empty+X produces each of the 5 real categories per tier) needs to be
  designed from scratch. The original design intent (from the user, for the 5 categories
  generally): farming cheap-but-picky (tailored to specific crops per tier), production
  resource-expensive-but-automatable (GTCEU/Create machines — motors, conveyors, circuits),
  exploration needs items from across biomes/dimensions, challenge needs rare/hard-to-get-
  early items at the edge of the unlocked age. This is very likely the next real content
  task, and now the biggest open item in the whole mod.
- **The `gtceu_voltage_interaction` gate mechanism failed to even load once already**
  (Pitfall #20 - a Rhino block-scoped `const` bug, unrelated to GTCEU/KubeJS's class filter
  itself, now fixed) - it still hasn't been confirmed actually *working* in-game (right-click
  an actual LV/MV/HV/EV/IV GTCEU machine before and after the relevant tier is unlocked and
  confirm it's blocked/allowed correctly), just confirmed to load without error.
- **The 5 Laboratory tiers' block art has been corrected twice already** (Pitfall #18 -
  first a merged-face + quarter-turn issue, then a full front/back + left/right swap once
  seen in-game) - the CURRENT (third) face mapping is still only as good as the user's
  latest report, not independently re-confirmed after this last fix. Check for a follow-up
  report before assuming it's finally right. The GUI background (`textures/gui/laboratory.png`)
  is real art now too (was a placeholder before, never documented as such since it predates
  this file) - not yet confirmed to line up correctly with `LaboratoryScreen`'s slot/
  progress-bar coordinates (`PROGRESS_BAR_X/Y/WIDTH/HEIGHT` in that class) since those were
  tuned against the old placeholder. **Was rendering far too small/cropped** (Pitfall #21 -
  `renderBg`'s `blit` call assumed the vanilla-convention 256x256 padded texture, but our
  PNG is a real unpadded 176x166 file, so only its top-left ~121x108 px showed, stretched
  over the full box) - fixed by passing explicit `textureWidth`/`textureHeight` to `blit`.
  Not yet re-confirmed in-game since the fix.
- **The ModernFix/pre-existing-world blockstate migration issue (Pitfall #10) was found but
  not resolved** — worth a proper fix (or at least a documented recommendation: e.g. "break
  and re-place any Laboratory placed before this update") before shipping the active/
  decorative feature further.
- **Deployment drift**: several local copies (`s3_server`, `TerraFirmaGreg-Modern` without
  `(1)`) were explicitly left un-updated after the most recent fix, per the user's own
  scoped request at the time — don't assume they're current. `s3_client`/`s3_client_13.10`
  need re-syncing whenever the server-side mod list changes (see the client-pack memory
  note on the pakku-lock.json process).
- **The ACTIVE-state pulse animation was removed entirely** (it was never re-created for the
  Pitfall #16 real art, and having only 3 of 6 faces still pulse with the old placeholder
  looked broken in-game - reported after the art delivery). `<prefix>_active.json` now
  points every face at the exact same static textures as the non-active model - the two
  model files are currently texture-identical, kept as separate files only so the
  `ACTIVE` blockstate distinction (used for "out of order" logic, unrelated to rendering)
  stays easy to re-diverge visually later if the user ever wants a new animated indicator.
  The old `<prefix>_top_active.png`/`<prefix>_side_active.png` (+ `.mcmeta`) files were
  deleted for all 5 tiers - if you're looking for the pulse effect's implementation, it no
  longer exists.

## Where to look first for anything not covered here

- `README.md` — user-facing install/build/dependency docs, kept in sync with actual
  `mods.toml` dependencies; update it if you change dependencies or the tools workflow.
- Memory files (this session's persistent memory, not this repo) — particularly the CivTFG
  S2→S3 migration notes, the S3 client-pack build process, and the PebbleHost SFTP note —
  cover context that spans *outside* this repo (the wider modpack, the live server) which
  this file deliberately doesn't duplicate.
