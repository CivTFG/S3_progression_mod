// priority: -100
"use strict";

// Recipe/transport tweaks on top of the TerraFirmaGreg pack, registered as adjusted copies under
// the original recipe ids. "priority: -100" makes it load after TFG's own scripts.
//
// KubeJS 6's event.remove/replaceInput/forEachRecipe only see ORIGINAL (datapack) recipes, never
// recipes another script added - so a recipe the pack itself creates in KubeJS can't be removed
// from here, and re-adding it under the same id logs "Duplicate added recipe" on every reload.
// Those pack definitions are therefore commented out in the pack's own scripts, each marked
// "CivTFG: replaced by .../tfg_tweaks.js" (pack scripts edited 2026-10-02, re-apply after a
// pack update!):
//   create/recipes.js              hose pulley (shaped + assembler + material info)
//   gregtech/recipes.js            basic tape from glue (shaped + assembler)
//   hangglider/recipes.js          reinforced hang glider (2 shaped + 2 assembler)
//   immersive_aircraft/recipes.js  economy plane, biplane, scarlet biplane
// event.remove below is only kept for real originals (GTCEU's own datapack recipe).
//
// Rhino rules (CLAUDE.md Pitfall #5/#20): no object-spread, `var` instead of const/let inside
// bare blocks.
//
// Every recipe id below was taken from the pack's recipe dump / source, not guessed:
//   shaped / mechanical_crafting ids are the explicit .id(...) of the original script,
//   GTCEU assembler ids are "<namespace>:assembler/<name given in the script>".

ServerEvents.recipes(event => {

    // ---- 1a. Hose Pulley: black steel plate instead of rubber foil --------------------------

    event.shaped('create:hose_pulley', [
        'DAE',
        ' B ',
        'CFC'
    ], {
        A: 'create:copper_casing',
        B: 'gtceu:black_steel_plate',
        C: '#forge:plates/copper',
        D: '#forge:tools/wrenches',
        E: '#forge:tools/hammers',
        F: 'minecraft:bucket'
    }).id('tfg:create/shaped/hose_pulley')

    event.recipes.gtceu.assembler('create:hose_pulley')
        .itemInputs('create:copper_casing', 'gtceu:black_steel_plate', '2x #forge:plates/copper', 'minecraft:bucket')
        .itemOutputs('create:hose_pulley')
        .duration(50)
        .circuit(1)
        .EUt(GTValues.VA[GTValues.ULV])

    try {
        TFGHelpers.registerMaterialInfo('create:hose_pulley', [GTMaterials.Copper, 3, GTMaterials.BlackSteel, 1])
    } catch (e) {
        console.warn('[tfg_tweaks] registerMaterialInfo(create:hose_pulley) failed: ' + e)
    }

    // ---- 1b. GregTech basic tape: thin rubber sheet instead of paper ------------------------
    // ASSUMPTION: the rubber foil (GTCEU names it "Thin Rubber Sheet") replaces the paper 1:1,
    // glue / sticky resin stays. The tag #tfg:rubber_foils = rubber, silicone rubber and
    // styrene-butadiene rubber foil (the tag the pack already uses for "rubber foil" inputs).
    // GTCEU's own recipe (sticky resin) is removed too, otherwise paper would still work there.
    event.remove({ id: 'gtceu:assembler/basic_tape' })

    event.shaped('gtceu:basic_tape', [
        ' A ',
        'ABA',
        ' A '
    ], {
        A: '#tfg:rubber_foils',
        B: 'tfc:glue'
    }).id('tfg:shaped/basic_tape_from_glue')

    event.recipes.gtceu.assembler('basic_tape_from_glue')
        .itemInputs('2x #tfg:rubber_foils', 'tfc:glue')
        .itemOutputs('2x gtceu:basic_tape')
        .duration(100)
        .EUt(GTValues.VA[GTValues.ULV])

    event.recipes.gtceu.assembler('basic_tape')
        .itemInputs('2x #tfg:rubber_foils', 'gtceu:sticky_resin')
        .itemOutputs('2x gtceu:basic_tape')
        .duration(100)
        .EUt(GTValues.VA[GTValues.ULV])

    // ---- 1c. Encased fan: soaked unrefined paper -> unrefined paper (fast) ------------------
    // Create's encased fan "smoking" (fan behind fire) processes vanilla minecraft:smoking
    // recipes. The slow firmalife drying recipe (tfg:drying/unrefined_paper) stays untouched.
    // ASSUMPTION: smoking, not blasting (lava) - say so if blasting is wanted instead.
    event.smoking('tfc:unrefined_paper', 'tfg:soaked_unrefined_paper')
        .id('s3_progression_mod:smoking/unrefined_paper')

    // ---- 2a. Reinforced hang glider: long steel rod instead of long aluminium rod ----------
    // Repair recipes need no rod and are unchanged.

    event.shaped('hangglider:reinforced_hang_glider', [
        ' A ',
        'ABA',
        ' C '
    ], {
        A: 'sns:reinforced_fabric',
        B: 'hangglider:hang_glider',
        C: '#forge:rods/long/steel'
    }).id('hangglider:shaped/reinforced_hang_glider')

    event.shaped('hangglider:reinforced_hang_glider', [
        ' A ',
        'ABA',
        ' C '
    ], {
        A: '#tfg:lightweight_cloth',
        B: 'hangglider:hang_glider',
        C: '#forge:rods/long/steel'
    }).id('hangglider:shaped/reinforced_hang_glider2')

    event.recipes.gtceu.assembler('tfg:hand_glider/reinforced_hang_glider')
        .itemInputs('3x sns:reinforced_fabric', '1x hangglider:hang_glider', '1x #forge:rods/long/steel')
        .circuit(3)
        .itemOutputs(Item.of('hangglider:reinforced_hang_glider', '{Damage:0}'))
        .duration(1200)
        .EUt(30)

    event.recipes.gtceu.assembler('tfg:hand_glider/reinforced_hang_glider2')
        .itemInputs('1x #tfg:lightweight_cloth', '1x hangglider:hang_glider', '1x #forge:rods/long/steel')
        .circuit(3)
        .itemOutputs(Item.of('hangglider:reinforced_hang_glider', '{Damage:0}'))
        .duration(600)
        .EUt(30)

    // ---- 2b. Aircraft on phases: economy plane (LV) -> biplane (MV) -> scarlet biplane (HV) -

    // Economy plane: immersive_aircraft:steel_boiler (H) -> immersive_aircraft:nether_engine
    // (the mod's MV engine), same cell, pattern otherwise unchanged.
    event.recipes.create.mechanical_crafting('man_of_many_planes:economy_plane', [
        ' AABCBAA ',
        'ADDBEBDDA',
        ' FGBHBGF ',
        '    D    ',
        '    D    ',
        '   DAD   ',
        '   DDD   ',
        '   AAA   '
    ], {
        A: 'immersive_aircraft:sail',
        B: 'gtceu:blue_steel_plate',
        C: 'tfg:black_steel_plated_airplane_propeller',
        D: 'gtceu:long_treated_wood_rod',
        E: '#create:seats',
        F: 'gtceu:black_steel_rotor',
        G: 'greate:rubber_belt_connector',
        H: 'immersive_aircraft:nether_engine'
    }).id('tfg:man_of_many_planes/mechanical_crafter/economy_plane')

    // Biplane: lv_aircraft_engine -> hv_aircraft_engine
    event.recipes.create.mechanical_crafting('immersive_aircraft:biplane', [
        '   A   ',
        'BFBCBFB',
        '  FDF  ',
        '  BBB  ',
        '   B   ',
        '   E   ',
        '  BEB  ',
        '   B   '
    ], {
        A: 'immersive_aircraft:enhanced_propeller',
        B: 'immersive_aircraft:hull',
        C: 'tfg:hv_aircraft_engine',
        D: 'man_of_many_planes:economy_plane',
        E: 'gtceu:treated_wood_plate',
        F: 'gtceu:treated_wood_frame'
    }).id('tfg:immersive_aircraft/mechanical_crafter/biplane')

    // Scarlet biplane: nether_engine -> hv_aircraft_engine (EV engine stays off limits), and the
    // two red steel plates flanking the biplane (E) become basalt fiber plates (I) as the HV marker.
    event.recipes.create.mechanical_crafting('man_of_many_planes:scarlet_biplane', [
        '    A    ',
        '    B    ',
        'CCCIEICCC',
        ' F DDD F ',
        ' CCDGDCC ',
        '    H    ',
        '   CHC   ',
        '    C    '
    ], {
        A: 'tfg:stainless_steel_plated_airplane_propeller',
        B: 'tfg:hv_aircraft_engine',
        C: 'immersive_aircraft:hull',
        D: 'gtceu:red_steel_plate',
        E: 'immersive_aircraft:biplane',
        F: 'gtceu:black_steel_frame',
        G: '#create:seats',
        H: 'tfc:metal/ingot/red_steel',
        I: 'tfg:basalt_fiber_plate'
    }).id('tfg:man_of_many_planes/mechanical_crafter/scarlet_biplane')
})
