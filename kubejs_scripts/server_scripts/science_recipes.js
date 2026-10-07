// Data-driven generator for science-item recipes. To add a recipe, either edit the
// SCIENCE_RECIPES array below by hand, or use tools/recipe_editor.py - a small Tkinter
// GUI that reads/writes the exact same array. Either way, the loop at the bottom turns
// each entry into a real KubeJS recipe; no need to touch the generator itself unless a
// genuinely new machine type is needed.
//
// Entry shape:
//   {
//     "age": "BRONZE",              // must match a tier key in progression.json
//     "category": "MINING",         // must match a category in progression.json, or "EMPTY"
//                                    // to craft the age's empty-science token (<age>_empty_science)
//     "output": 1,                  // how many science items this craft produces (default 1)
//     "machine": "crafting_table",  // "crafting_table", "primitive_assembler" (this mod's unpowered
//                                    // assembler, no "tier"), a GTCEU recipe type id (e.g. "assembler"),
//                                    // or a Create processing recipe type id (e.g. "milling")
//     "tier": "LV",                 // GTCEU machines only - sets EU/t via VOLTAGE_BY_TIER
//     "duration": 100,              // ticks - GTCEU/Create machines only, ignored for crafting_table
//     "heat": "heated",             // Create mixing/compacting only - "heated" or "superheated"
//     "inputs": [
//       { "item": "tfc:metal/ingot/copper", "count": 1 }
//     ],
//     "fluids": [                   // optional - fluid ingredients, amount in mB
//       { "fluid": "tfc:beer", "amount": 500 }
//     ]
//   }
//
// How "fluids" works per machine:
//   - crafting_table: each fluid takes one grid slot, filled by any fluid container holding
//     at least "amount" mB of it (wooden bucket, jug, a sealed-then-broken barrel, ...) - via
//     TFC's advanced_shapeless_crafting + TFC.ingredient.fluid, the same mechanism the pack
//     uses for dough (water from a bucket). Fluid tags work here ("#tfc:alcohols" = any of
//     TFC's 8 base alcohols). NOTE: "amount" is only a minimum - TFC hands the container back
//     EMPTY, so the whole content is used up (confirmed via javap: FluidContainerItem and
//     BarrelBlockItem#getCraftingRemainingItem in TFC 3.2.25 both return a fresh empty
//     container). A full 10 B barrel spent on a 500 mB recipe loses all 10 B.
//   - GTCEU machines: real fluid input (builder.inputFluids), exact amount consumed. Plain
//     fluid ids only, no tags.
//   - primitive_assembler: max 9 input entries, max ONE fluid (id or "#tag"), tank holds 8000 mB;
//     "duration" in ticks (default 100).
//   - Create machines: added to the inputs list (mixing/compacting from the basin, filling
//     from the spout). Plain fluid ids only, no tags.
//
// Create's stress (SU) cost is a fixed property of the machine block itself, not of a
// recipe, so there's no "stress" field here - see CREATE_MACHINE_STRESS below for
// reference numbers when choosing which machine a recipe should use.
//
// The array below is written as strict JSON (quoted keys, double-quoted strings, no
// trailing commas, no comments inside it) so tools/recipe_editor.py can parse and
// rewrite it with Python's json module instead of needing a real JS parser - it's still
// a plain JS array literal as far as KubeJS/Rhino is concerned. Keep it that way; if you
// edit it by hand, valid JSON is still valid input here.
//
// Rhino (KubeJS's script engine here) doesn't support object-spread - see blocked_blocks.js -
// and array-spread in a call (`fn(...arr)`) is equally unverified, so this file uses
// Function.prototype.apply instead of spread anywhere a variable-length ingredient list
// needs to reach a Java varargs method.

// Item tags the recipes below use ("#s3_progression_mod:science/..."), so a recipe can accept any
// of several equivalent items (e.g. every cheese). Written by the science-recipe build (_build3/gen_js.py).
// ===TAGS-JSON-START===
const SCIENCE_TAGS = {
  "s3_progression_mod:science/cheese_wheels": [
    "firmalife:cheddar_wheel",
    "firmalife:chevre_wheel",
    "firmalife:feta_wheel",
    "firmalife:gouda_wheel",
    "firmalife:shosha_wheel",
    "firmalife:rajya_metok_wheel"
  ],
  "s3_progression_mod:science/cheeses": [
    "firmalife:food/cheddar",
    "firmalife:food/chevre",
    "firmalife:food/feta",
    "firmalife:food/gouda",
    "firmalife:food/shosha",
    "firmalife:food/rajya_metok",
    "tfg:food/slice_of_cheese"
  ],
  "s3_progression_mod:science/sandwiches": [
    "tfc:food/barley_bread_sandwich",
    "tfc:food/barley_bread_jam_sandwich",
    "tfc:food/maize_bread_sandwich",
    "tfc:food/maize_bread_jam_sandwich",
    "tfc:food/oat_bread_sandwich",
    "tfc:food/oat_bread_jam_sandwich",
    "tfc:food/rice_bread_sandwich",
    "tfc:food/rice_bread_jam_sandwich",
    "tfc:food/rye_bread_sandwich",
    "tfc:food/rye_bread_jam_sandwich",
    "tfc:food/wheat_bread_sandwich",
    "tfc:food/wheat_bread_jam_sandwich"
  ],
  "s3_progression_mod:science/raw_fish": [
    "tfc:food/cod",
    "tfc:food/salmon",
    "tfc:food/lake_trout",
    "tfc:food/rainbow_trout",
    "tfc:food/bluegill",
    "tfc:food/crappie",
    "tfc:food/smallmouth_bass",
    "tfc:food/tropical_fish"
  ],
  "s3_progression_mod:science/cooked_fish": [
    "tfc:food/cooked_cod",
    "tfc:food/cooked_salmon",
    "tfc:food/cooked_lake_trout",
    "tfc:food/cooked_rainbow_trout",
    "tfc:food/cooked_bluegill",
    "tfc:food/cooked_crappie",
    "tfc:food/cooked_smallmouth_bass",
    "tfc:food/cooked_tropical_fish"
  ],
  "s3_progression_mod:science/salves": [
    "tfg:fire_resistance_salvo",
    "tfg:resistance_salvo",
    "tfg:absorption_salvo",
    "tfg:instant_health_salvo",
    "tfg:invisibility_salvo",
    "tfg:luck_salvo"
  ],
  "s3_progression_mod:science/mars_cooked_meat": [
    "tfg:food/cooked_glacian_mutton",
    "tfg:food/cooked_surfer_steak",
    "tfg:food/cooked_walker_steak",
    "tfg:food/cooked_sniffer_beef",
    "tfg:food/cooked_springling_collar",
    "tfg:food/cooked_goober_meat",
    "tfg:food/cooked_stickastackatick",
    "tfg:food/cooked_wraptor",
    "tfg:food/cooked_glider_wings",
    "tfg:food/cooked_crusher_meat",
    "tfg:food/cooked_whole_soarer"
  ]
}
// ===TAGS-JSON-END===

ServerEvents.tags('item', event => {
    Object.keys(SCIENCE_TAGS).forEach(tag => {
        SCIENCE_TAGS[tag].forEach(id => event.add(tag, id))
    })
})

// ===RECIPES-JSON-START===
const SCIENCE_RECIPES = [
  {"age": "BRONZE", "category": "EMPTY", "output": 32, "machine": "crafting_table", "inputs": [{"item": "gtceu:bronze_ingot", "count": 1}]},
  {"age": "BRONZE", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:cassiterite_dust", "count": 2}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "MINING", "output": 3, "machine": "crafting_table", "inputs": [{"item": "gtceu:chalcopyrite_dust", "count": 2}, {"item": "gtceu:tetrahedrite_dust", "count": 2}, {"item": "gtceu:malachite_dust", "count": 2}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "FARMING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "firmalife:food/garlic_bread", "count": 3}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "FARMING", "output": 4, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:food/wheat_bread", "count": 18}, {"item": "tfc:food/barley_bread", "count": 18}, {"item": "tfc:food/rye_bread", "count": 18}, {"item": "tfc:food/oat_bread", "count": 18}, {"item": "tfc:food/maize_bread", "count": 18}, {"item": "tfc:food/rice_bread", "count": 18}, {"item": "#s3_progression_mod:science/sandwiches", "count": 9}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "EXPLORATION", "output": 3, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:kaolin_clay", "count": 8}, {"item": "tfc:ore/rich_native_copper", "count": 24}, {"item": "tfc:ore/rich_cassiterite", "count": 24}, {"item": "tfc:wood/log/kapok", "count": 24}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "EXPLORATION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:food/cherry", "count": 14}, {"item": "tfc:food/lemon", "count": 14}, {"item": "tfc:food/olive", "count": 14}, {"item": "tfc:food/orange", "count": 14}, {"item": "tfc:food/peach", "count": 14}, {"item": "tfc:food/plum", "count": 14}, {"item": "tfc:food/red_apple", "count": 14}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "PRODUCTION", "output": 3, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:double_bronze_plate", "count": 10}, {"item": "gtceu:bronze_gear", "count": 10}, {"item": "gtceu:long_bronze_rod", "count": 20}, {"item": "gtceu:bronze_ring", "count": 40}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "PRODUCTION", "output": 3, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:burlap_cloth", "count": 40}, {"item": "firmaciv:rope_coil", "count": 20}, {"item": "tfc:unrefined_paper", "count": 40}, {"item": "gtceu:double_copper_plate", "count": 20}, {"item": "gtceu:copper_single_wire", "count": 20}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "tfc:crucible", "count": 1}, {"item": "tfc:fire_bricks", "count": 1}, {"item": "tfc:metal/anvil/bronze", "count": 1}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "tfc:crucible", "count": 1}, {"item": "tfc:metal/anvil/bronze", "count": 1}, {"item": "tfc:metal/anvil/bismuth_bronze", "count": 1}, {"item": "tfc:metal/anvil/black_bronze", "count": 1}, {"item": "tfc:bellows", "count": 1}, {"item": "tfc:metal/tuyere/bronze", "count": 1}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "IRON", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:wrought_iron_ingot", "count": 1}]},
  {"age": "IRON", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:magnetite_dust", "count": 2}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "MINING", "output": 4, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:hematite_dust", "count": 3}, {"item": "gtceu:yellow_limonite_dust", "count": 3}, {"item": "gtceu:goethite_dust", "count": 3}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "FARMING", "output": 5, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "#s3_progression_mod:science/cheese_wheels", "count": 6}, {"item": "firmalife:food/hardtack", "count": 12}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "FARMING", "output": 3, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "firmalife:food/pickled_egg", "count": 10}, {"item": "firmalife:food/cured_maize", "count": 20}, {"item": "firmalife:food/hardtack", "count": 20}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "EXPLORATION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:ore/rich_hematite", "count": 8}, {"item": "tfc:ore/rich_magnetite", "count": 8}, {"item": "tfc:ore/rich_limonite", "count": 8}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "EXPLORATION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:sticky_resin", "count": 7}, {"item": "tfg:conifer_rosin", "count": 7}, {"item": "afc:wood/log/hevea", "count": 7}, {"item": "tfc:wood/log/kapok", "count": 7}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "PRODUCTION", "output": 3, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:wrought_iron_gear", "count": 7}, {"item": "gtceu:double_wrought_iron_plate", "count": 14}, {"item": "gtceu:long_wrought_iron_rod", "count": 28}, {"item": "gtceu:wrought_iron_rotor", "count": 7}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "PRODUCTION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:wrought_iron_spring", "count": 16}, {"item": "gtceu:wrought_iron_ring", "count": 32}, {"item": "gtceu:wrought_iron_bolt", "count": 64}, {"item": "firmaciv:rope_coil", "count": 16}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "tfc:bloomery", "count": 1}, {"item": "tfc:metal/anvil/wrought_iron", "count": 1}, {"item": "gtceu:coke_oven_bricks", "count": 2}, {"item": "firmalife:vat", "count": 1}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "CHALLENGE", "output": 3, "machine": "crafting_table", "inputs": [{"item": "gtceu:coke_oven", "count": 2}, {"item": "tfc:bloomery", "count": 1}, {"item": "tfc:metal/anvil/wrought_iron", "count": 1}, {"item": "gtceu:coke_oven_bricks", "count": 1}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:steel_ingot", "count": 1}]},
  {"age": "STEEL", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:iron_dust", "count": 1}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "MINING", "output": 7, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:nickel_dust", "count": 3}, {"item": "gtceu:silver_dust", "count": 3}, {"item": "gtceu:gold_dust", "count": 3}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "FARMING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "tfg:food/cooked_beans", "count": 4}, {"item": "tfg:food/cooked_cassava", "count": 4}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "FARMING", "output": 4, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfg:food/juice", "count": 14}, {"item": "tfg:food/ice_soup", "count": 14}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "EXPLORATION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "beneath:food/portobello", "count": 13}, {"item": "beneath:food/shittake", "count": 13}, {"item": "beneath:food/chantrelle", "count": 13}, {"item": "beneath:food/parasol", "count": 13}, {"item": "beneath:food/button", "count": 13}, {"item": "beneath:food/oyster", "count": 13}, {"item": "beneath:gleamflower", "count": 13}, {"item": "beneath:ghost_pepper", "count": 26}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "EXPLORATION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "beneath:cursecoal", "count": 24}, {"item": "beneath:ghost_pepper", "count": 24}, {"item": "gtceu:nether_quartz_dust", "count": 24}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "PRODUCTION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:steel_gear", "count": 3}, {"item": "gtceu:double_steel_plate", "count": 6}, {"item": "gtceu:long_steel_rod", "count": 12}, {"item": "gtceu:steel_spring", "count": 6}, {"item": "gtceu:steel_rotor", "count": 3}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "PRODUCTION", "output": 2, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:double_black_steel_plate", "count": 3}, {"item": "gtceu:black_steel_gear", "count": 3}, {"item": "gtceu:fine_black_steel_wire", "count": 6}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "tfc:blast_furnace", "count": 1}, {"item": "tfc:bellows", "count": 1}, {"item": "tfc:metal/anvil/black_steel", "count": 1}, {"item": "tfc:metal/anvil/steel", "count": 2}, {"item": "beneath:hellbricks", "count": 2}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "tfc:metal/anvil/red_steel", "count": 1}, {"item": "tfc:metal/anvil/blue_steel", "count": 1}, {"item": "tfc:metal/anvil/black_steel", "count": 1}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:hp_steam_solid_boiler", "count": 1}]},
  {"age": "STEAM", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:garnierite_dust", "count": 3}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "MINING", "output": 3, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:cobaltite_dust", "count": 4}, {"item": "gtceu:sphalerite_dust", "count": 4}, {"item": "gtceu:tetrahedrite_dust", "count": 4}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "FARMING", "output": 3, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "#s3_progression_mod:science/salves", "count": 12}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "FARMING", "output": 3, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:sticky_resin", "count": 11}, {"item": "gtceu:raw_rubber_dust", "count": 22}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "EXPLORATION", "output": 2, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:raw_cobalt", "count": 13}, {"item": "tfc:ore/rich_garnierite", "count": 13}, {"item": "gtceu:raw_pyrite", "count": 26}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "EXPLORATION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:ore/rich_sphalerite", "count": 7}, {"item": "tfc:ore/rich_native_copper", "count": 7}, {"item": "tfc:ore/rich_cassiterite", "count": 7}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "PRODUCTION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:resistor", "count": 8}, {"item": "gtceu:vacuum_tube", "count": 4}, {"item": "gtceu:resin_circuit_board", "count": 4}, {"item": "gtceu:glass_tube", "count": 8}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "PRODUCTION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:double_brass_plate", "count": 14}, {"item": "gtceu:small_brass_gear", "count": 14}, {"item": "gtceu:cobalt_brass_gear", "count": 7}, {"item": "gtceu:red_alloy_single_cable", "count": 7}, {"item": "gtceu:rubber_plate", "count": 7}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "CHALLENGE", "output": 2, "machine": "crafting_table", "inputs": [{"item": "gtceu:basic_electronic_circuit", "count": 7}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "CHALLENGE", "output": 2, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:hp_steam_macerator", "count": 7}, {"item": "gtceu:hp_steam_compressor", "count": 7}, {"item": "gtceu:hp_steam_alloy_smelter", "count": 7}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "LV", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:lv_machine_hull", "count": 1}]},
  {"age": "LV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:bauxite_dust", "count": 3}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "MINING", "output": 3, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "gtceu:sphalerite_dust", "count": 4}, {"item": "gtceu:cobaltite_dust", "count": 4}, {"item": "gtceu:garnierite_dust", "count": 4}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "FARMING", "output": 4, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "gtceu:plant_ball", "count": 14}, {"item": "gtceu:bio_chaff", "count": 14}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "FARMING", "output": 3, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "#s3_progression_mod:science/cheeses", "count": 20}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "tfc:plant/olive_sapling", "count": 3}, {"item": "tfg:palm_tree/oil_palm_sapling", "count": 3}, {"item": "tfc:seeds/soybean", "count": 3}, {"item": "tfg:peanut_seeds", "count": 3}, {"item": "tfg:rapeseed_seeds", "count": 3}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "EXPLORATION", "output": 2, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "gtceu:raw_bauxite", "count": 18}, {"item": "tfc:ore/rich_sphalerite", "count": 9}, {"item": "gtceu:raw_galena", "count": 18}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "PRODUCTION", "output": 3, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "gtceu:tin_single_cable", "count": 16}, {"item": "gtceu:copper_single_cable", "count": 16}, {"item": "gtceu:magnetic_steel_rod", "count": 16}, {"item": "gtceu:rubber_plate", "count": 16}, {"item": "gtceu:aluminium_foil", "count": 8}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "PRODUCTION", "output": 2, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "gtceu:diode", "count": 6}, {"item": "gtceu:phenolic_printed_circuit_board", "count": 3}, {"item": "tfg:bakelite_ingot", "count": 6}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "CHALLENGE", "output": 3, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "gtceu:electric_blast_furnace", "count": 5}, {"item": "gtceu:cupronickel_coil_block", "count": 5}, {"item": "gtceu:lv_machine_hull", "count": 5}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "CHALLENGE", "output": 3, "machine": "assembler", "tier": "LV", "duration": 200, "inputs": [{"item": "gtceu:good_electronic_circuit", "count": 5}, {"item": "gtceu:lv_robot_arm", "count": 5}, {"item": "gtceu:diode", "count": 5}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "MV", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:aluminium_ingot", "count": 1}]},
  {"age": "MV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:pyrolusite_dust", "count": 3}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "MINING", "output": 3, "machine": "crafting_table", "inputs": [{"item": "gtceu:chromite_dust", "count": 1}, {"item": "gtceu:garnierite_dust", "count": 2}, {"item": "gtceu:vanadium_magnetite_dust", "count": 2}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "FARMING", "output": 3, "machine": "crafting_table", "inputs": [{"item": "tfg:food/cooked_instant_mac", "count": 5}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "FARMING", "output": 9, "machine": "crafting_table", "inputs": [{"item": "tfg:food/freeze_dried_fruit", "count": 2}, {"item": "tfg:food/calorie_paste", "count": 2}, {"item": "tfg:food/meal_bag", "count": 2}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "MV", "duration": 200, "inputs": [{"item": "gtceu:raw_oilsands", "count": 5}, {"item": "gtceu:raw_rock_salt", "count": 5}, {"item": "gtceu:raw_molybdenite", "count": 5}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "MV", "duration": 200, "inputs": [{"item": "gtceu:raw_emerald", "count": 5}, {"item": "gtceu:raw_sodalite", "count": 5}, {"item": "gtceu:raw_monazite", "count": 5}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "PRODUCTION", "output": 5, "machine": "assembler", "tier": "MV", "duration": 200, "inputs": [{"item": "gtceu:double_aluminium_plate", "count": 4}, {"item": "gtceu:small_aluminium_gear", "count": 4}, {"item": "gtceu:aluminium_spring", "count": 4}, {"item": "gtceu:aluminium_rod", "count": 8}, {"item": "gtceu:electrum_single_cable", "count": 8}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "PRODUCTION", "output": 2, "machine": "assembler", "tier": "MV", "duration": 200, "inputs": [{"item": "gtceu:ilc_chip", "count": 5}, {"item": "gtceu:ulpic_chip", "count": 5}, {"item": "gtceu:ram_chip", "count": 5}, {"item": "gtceu:cpu_chip", "count": 5}, {"item": "gtceu:transistor", "count": 5}, {"item": "gtceu:capacitor", "count": 5}, {"item": "gtceu:inductor", "count": 5}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "CHALLENGE", "output": 4, "machine": "assembler", "tier": "MV", "duration": 200, "inputs": [{"item": "gtceu:advanced_integrated_circuit", "count": 5}, {"item": "tfg:hv_universal_circuit", "count": 5}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "CHALLENGE", "output": 8, "machine": "crafting_table", "inputs": [{"item": "gtceu:kanthal_coil_block", "count": 1}, {"item": "gtceu:silicon_boule", "count": 1}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "HV", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:stainless_steel_ingot", "count": 1}]},
  {"age": "HV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:rutile_dust", "count": 2}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:ilmenite_dust", "count": 2}, {"item": "gtceu:bauxite_dust", "count": 1}, {"item": "gtceu:pentlandite_dust", "count": 1}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "FARMING", "output": 3, "machine": "crafting_table", "inputs": [{"item": "#s3_progression_mod:science/raw_fish", "count": 8}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "FARMING", "output": 5, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "#s3_progression_mod:science/cooked_fish", "count": 8}, {"item": "tfg:food/freeze_dried_fruit", "count": 4}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "gtceu:raw_ilmenite", "count": 14}, {"item": "gtceu:raw_bauxite", "count": 7}, {"item": "gtceu:raw_pentlandite", "count": 14}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "gtceu:raw_tantalite", "count": 10}, {"item": "gtceu:raw_molybdenite", "count": 5}, {"item": "gtceu:raw_monazite", "count": 5}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "PRODUCTION", "output": 4, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "gtceu:double_stainless_steel_plate", "count": 3}, {"item": "gtceu:small_stainless_steel_gear", "count": 3}, {"item": "gtceu:stainless_steel_rod", "count": 6}, {"item": "gtceu:stainless_steel_ring", "count": 6}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "gtceu:smd_transistor", "count": 6}, {"item": "gtceu:smd_resistor", "count": 6}, {"item": "gtceu:smd_capacitor", "count": 6}, {"item": "gtceu:smd_diode", "count": 6}, {"item": "gtceu:smd_inductor", "count": 6}, {"item": "gtceu:polytetrafluoroethylene_plate", "count": 12}, {"item": "gtceu:stainless_steel_foil", "count": 12}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "CHALLENGE", "output": 3, "machine": "crafting_table", "inputs": [{"item": "ad_astra:rocket_fin", "count": 1}, {"item": "ad_astra:rocket_nose_cone", "count": 1}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "CHALLENGE", "output": 3, "machine": "crafting_table", "inputs": [{"item": "gtceu:cleanroom", "count": 1}, {"item": "gtceu:filter_casing", "count": 4}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "MOON", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "ad_astra:desh_ingot", "count": 1}]},
  {"age": "MOON", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:cooperite_dust", "count": 3}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "MINING", "output": 9, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "tfg:bright_regolith_dust", "count": 5}, {"item": "tfg:cassiterite_regolith_dust", "count": 5}, {"item": "tfg:certus_regolith_dust", "count": 5}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "FARMING", "output": 9, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "tfg:lunar_chorus_flower", "count": 16}, {"item": "minecraft:chorus_fruit", "count": 32}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "FARMING", "output": 5, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "tfg:food/cooked_moon_rabbit", "count": 4}, {"item": "tfg:food/cooked_birt", "count": 4}, {"item": "tfg:food/cooked_crawlermari", "count": 4}, {"item": "tfg:food/cooked_limpet", "count": 4}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "EXPLORATION", "output": 3, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "ad_astra:raw_desh", "count": 5}, {"item": "gtceu:rich_raw_desh", "count": 5}, {"item": "gtceu:poor_raw_desh", "count": 5}, {"item": "gtceu:raw_cooperite", "count": 5}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "betterend:inflexia", "count": 8}, {"item": "betterend:salteago", "count": 8}, {"item": "betterend:vaiolush_fern", "count": 8}, {"item": "tfg:lunar_roots", "count": 8}, {"item": "tfg:lunar_sprouts", "count": 8}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "PRODUCTION", "output": 4, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "ad_astra:desh_plate", "count": 5}, {"item": "gtceu:desh_rod", "count": 10}, {"item": "gtceu:desh_foil", "count": 10}, {"item": "gtceu:titanium_plate", "count": 5}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "PRODUCTION", "output": 5, "machine": "assembler", "tier": "HV", "duration": 200, "inputs": [{"item": "gtceu:titanium_gear", "count": 3}, {"item": "gtceu:small_titanium_gear", "count": 6}, {"item": "gtceu:titanium_ring", "count": 12}, {"item": "gtceu:titanium_screw", "count": 12}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "CHALLENGE", "output": 3, "machine": "crafting_table", "inputs": [{"item": "ad_astra:tier_1_rover", "count": 1}, {"item": "ad_astra:zip_gun", "count": 1}, {"item": "ad_astra:oxygen_sensor", "count": 1}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "CHALLENGE", "output": 5, "machine": "crafting_table", "inputs": [{"item": "ad_astra:oxygen_distributor", "count": 1}, {"item": "tfg:interplanetary_item_launcher", "count": 1}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "EV", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:ev_machine_hull", "count": 1}]},
  {"age": "EV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:tungstate_dust", "count": 3}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "MINING", "output": 4, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "gtceu:scheelite_dust", "count": 5}, {"item": "gtceu:uraninite_dust", "count": 5}, {"item": "gtceu:cooperite_dust", "count": 5}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "FARMING", "output": 1, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "tfg:sniffer_wool", "count": 32}, {"item": "tfg:glacian_wool", "count": 32}, {"item": "#s3_progression_mod:science/cheeses", "count": 3}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "FARMING", "output": 1, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "#s3_progression_mod:science/cheeses", "count": 29}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "gtceu:raw_uraninite", "count": 8}, {"item": "gtceu:raw_scheelite", "count": 8}, {"item": "gtceu:raw_tungstate", "count": 8}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "gtceu:raw_cooperite", "count": 6}, {"item": "gtceu:raw_platinum", "count": 6}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "PRODUCTION", "output": 3, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "gtceu:double_tungsten_steel_plate", "count": 2}, {"item": "gtceu:small_tungsten_steel_gear", "count": 2}, {"item": "gtceu:tungsten_steel_spring", "count": 2}, {"item": "gtceu:tungsten_steel_rod", "count": 4}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "PRODUCTION", "output": 3, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "gtceu:mpic_chip", "count": 8}, {"item": "gtceu:nor_memory_chip", "count": 16}, {"item": "gtceu:epoxy_printed_circuit_board", "count": 8}, {"item": "gtceu:grossular_lens", "count": 4}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "CHALLENGE", "output": 4, "machine": "crafting_table", "inputs": [{"item": "deafission:fission_reactor_mk1", "count": 1}, {"item": "tfg:nuclear_turbine", "count": 1}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "CHALLENGE", "output": 4, "machine": "crafting_table", "inputs": [{"item": "ad_astra:desh_engine", "count": 1}, {"item": "ad_astra:rocket_fin", "count": 1}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "MARS", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "ad_astra:ostrum_ingot", "count": 1}]},
  {"age": "MARS", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:pitchblende_dust", "count": 4}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "MINING", "output": 5, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "gtceu:ochrum_dust", "count": 12}, {"item": "gtceu:desh_dust", "count": 6}, {"item": "gtceu:cooperite_dust", "count": 6}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "FARMING", "output": 5, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "betterend:cave_pumpkin_pie", "count": 6}, {"item": "tfg:food/cooked_dino_nugget", "count": 12}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "FARMING", "output": 2, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "betterend:cave_pumpkin", "count": 6}, {"item": "ad_astra:aeronos_cap", "count": 6}, {"item": "ad_astra:strophar_cap", "count": 6}, {"item": "#s3_progression_mod:science/mars_cooked_meat", "count": 6}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "EXPLORATION", "output": 1, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "gtceu:rich_raw_pitchblende", "count": 5}, {"item": "gtceu:poor_raw_pitchblende", "count": 5}, {"item": "beneath:crimson_straw", "count": 5}, {"item": "betterend:glacian_hymenophore", "count": 5}, {"item": "tfg:glacian_wood", "count": 5}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "EXPLORATION", "output": 2, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "betterend:amber_root_seeds", "count": 13}, {"item": "betterend:blossom_berry_seeds", "count": 13}, {"item": "betterend:shadow_berry_seeds", "count": 13}, {"item": "betterend:cave_pumpkin_plant_seeds", "count": 13}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "PRODUCTION", "output": 4, "machine": "assembler", "tier": "EV", "duration": 200, "inputs": [{"item": "gtceu:hastelloy_x_plate", "count": 7}, {"item": "gtceu:watertight_steel_plate", "count": 7}, {"item": "gtceu:hastelloy_x_rod", "count": 14}, {"item": "gtceu:watertight_steel_rod", "count": 14}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "PRODUCTION", "output": 3, "machine": "crafting_table", "inputs": [{"item": "ad_astra:ostrum_plate", "count": 1}, {"item": "gtceu:ostrum_rod", "count": 2}, {"item": "gtceu:ostrum_iodide_foil", "count": 2}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:ostrum_iodide_ingot", "count": 1}, {"item": "endermanoverhaul:corrupted_shield", "count": 1}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "ad_astra:ostrum_ingot", "count": 1}, {"item": "species:ricoshield", "count": 1}, {"item": "wan_ancient_beasts:reinforced_shield", "count": 1}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]}
]
// ===RECIPES-JSON-END===

// GTCEU voltage-tier -> EU/t, for recipes that specify a "tier" instead of a raw voltage.
const VOLTAGE_BY_TIER = {
    ULV: 8, LV: 32, MV: 128, HV: 512, EV: 2048, IV: 8192,
    LUV: 32768, ZPM: 131072, UV: 524288, UHV: 2097152,
}

// Create recipe-type ids this generator supports - confirmed against AllRecipeTypes in
// create-1.20.1-6.0.8.jar. Deliberately excludes "mechanical_crafting" (shaped-grid
// recipe, doesn't fit the flat inputs/outputs shape here) and "sequenced_assembly"
// (multi-step with intermediate items) - write those by hand if ever needed.
const CREATE_MACHINES = [
    'crushing', 'milling', 'mixing', 'compacting', 'pressing', 'cutting',
    'splashing', 'haunting', 'deploying', 'filling', 'emptying', 'item_application',
]

// Reference only (not written into any recipe) - typical Create default SU cost of the
// machine block behind each recipe type, to help pick which one a recipe should use.
// Create addons in this pack (createaddition, createhorsepower, etc.) may retune these,
// so treat as a starting point and confirm in-game with a wrench/goggles on the block.
const CREATE_MACHINE_STRESS = {
    crushing: '4 SU per Crushing Wheel (8 SU for the pair)',
    milling: '4 SU (Millstone)',
    mixing: '4 SU (Mechanical Mixer, plus the Basin it sits on)',
    compacting: '4 SU (Mechanical Press, compacting mode)',
    pressing: '4 SU (Mechanical Press)',
    cutting: '2 SU (Mechanical Saw)',
    splashing: '0 SU (just needs a body of water)',
    haunting: '0 SU (just needs soul sand/soil nearby)',
    deploying: '4 SU (Deployer)',
    filling: '2 SU (Spout)',
    emptying: '2 SU (Item Drain)',
    item_application: '4 SU (Deployer)',
}

ServerEvents.recipes(event => {
    const ProgressionTiers = Java.loadClass('com.civtfg.progression.stage.ProgressionTiers')
    const progression = JSON.parse(String(ProgressionTiers.rawJson()))
    const validAges = progression.tiers.map(t => t.key)
    const validCategories = progression.categories.map(c => c.toUpperCase())

    function ingredientString(input) {
        return input.count > 1 ? `${input.count}x ${input.item}` : input.item
    }

    // One recipe per call; SCIENCE_RECIPES.forEach below wraps each call in try/catch, so a
    // single broken entry (e.g. an item id that isn't registered, which makes GTCEU throw
    // "Invalid or empty input item") is logged and skipped instead of aborting every
    // entry after it - that happened on the first real reload.
    function buildScienceRecipe(recipe, index) {
        if (validAges.indexOf(recipe.age) === -1) {
            console.error(`[s3_progression_mod] science_recipes[${index}]: unknown age "${recipe.age}" - check progression.json`)
            return
        }
        // "EMPTY" isn't a research category - it produces the age's empty-science token
        // (ModEmptyScienceItems, id <age>_empty_science) instead of a category science item.
        var isEmptyToken = recipe.category === 'EMPTY'
        if (!isEmptyToken && validCategories.indexOf(recipe.category) === -1) {
            console.error(`[s3_progression_mod] science_recipes[${index}]: unknown category "${recipe.category}" - check progression.json`)
            return
        }

        const outputId = isEmptyToken
            ? `s3_progression_mod:${recipe.age.toLowerCase()}_empty_science`
            : `s3_progression_mod:${recipe.age.toLowerCase()}_${recipe.category.toLowerCase()}_science`
        const outputCount = recipe.output || 1
        const outputString = outputCount > 1 ? `${outputCount}x ${outputId}` : outputId
        const inputStrings = recipe.inputs.map(ingredientString)
        const fluids = recipe.fluids || []
        for (var f = 0; f < fluids.length; f++) {
            if (!fluids[f].fluid || !(fluids[f].amount > 0)) {
                console.error(`[s3_progression_mod] science_recipes[${index}]: fluid #${f} needs a "fluid" id and a positive "amount" (mB)`)
                return
            }
        }

        if (recipe.machine === 'crafting_table') {
            if (fluids.length === 0) {
                event.shapeless(outputString, inputStrings)
                return
            }
            // One ingredient per grid slot - "2x item" is expanded by hand here rather than
            // relying on the TFC recipe schema to unwrap counts the way event.shapeless does.
            var gridIngredients = []
            recipe.inputs.forEach(input => {
                for (var n = 0; n < input.count; n++) gridIngredients.push(input.item)
            })
            fluids.forEach(fluid => gridIngredients.push(TFC.ingredient.fluid(TFC.fluidStackIngredient(fluid.fluid, fluid.amount))))
            if (gridIngredients.length > 9) {
                console.error(`[s3_progression_mod] science_recipes[${index}]: ${gridIngredients.length} ingredients (items + fluids) don't fit a 3x3 crafting grid`)
                return
            }
            event.recipes.tfc.advanced_shapeless_crafting(TFC.isp.of(outputString), gridIngredients)
                .id(`s3_progression_mod:crafting/${outputId.split(':')[1]}_${index}`)
            return
        }

        if (recipe.machine === 'primitive_assembler') {
            // The mod's own unpowered assembler (recipe type s3_progression_mod:primitive_assembler,
            // see PrimitiveAssemblerRecipe): up to 9 item inputs, at most ONE fluid input (id or
            // "#tag", amount in mB, tank holds 8000), one output. Not a GTCEU/Create machine, so it
            // is registered as a plain custom recipe. var, not const - see Pitfall #20.
            if (recipe.inputs.length > 9) {
                console.error(`[s3_progression_mod] science_recipes[${index}]: primitive_assembler takes at most 9 input entries (got ${recipe.inputs.length})`)
                return
            }
            if (fluids.length > 1) {
                console.error(`[s3_progression_mod] science_recipes[${index}]: primitive_assembler takes at most 1 fluid input (got ${fluids.length})`)
                return
            }
            var paInputs = recipe.inputs.map(input => {
                var entry = String(input.item).charAt(0) === '#'
                    ? { tag: String(input.item).substring(1) }
                    : { item: input.item }
                if (input.count > 1) entry.count = input.count
                return entry
            })
            var paJson = {
                type: 's3_progression_mod:primitive_assembler',
                inputs: paInputs,
                output: { item: outputId, count: outputCount },
                duration: recipe.duration || 100,
            }
            if (fluids.length === 1) {
                paJson.fluid_input = String(fluids[0].fluid).charAt(0) === '#'
                    ? { tag: String(fluids[0].fluid).substring(1), amount: fluids[0].amount }
                    : { fluid: fluids[0].fluid, amount: fluids[0].amount }
            }
            event.custom(paJson).id(`s3_progression_mod:primitive_assembler/${outputId.split(':')[1]}_${index}`)
            return
        }

        for (var t = 0; t < fluids.length; t++) {
            if (String(fluids[t].fluid).charAt(0) === '#') {
                console.error(`[s3_progression_mod] science_recipes[${index}]: fluid tag "${fluids[t].fluid}" - only crafting_table recipes support fluid tags, use a plain fluid id for machine "${recipe.machine}"`)
                return
            }
        }
        const fluidStacks = fluids.map(fluid => Fluid.of(fluid.fluid, fluid.amount))

        if (CREATE_MACHINES.indexOf(recipe.machine) !== -1) {
            // var, not const - see Pitfall #20/#22 in CLAUDE.md: a const/let declared
            // directly inside a bare if/for/while block can crash Rhino with
            // "redeclaration of var" the first time this branch actually runs - this one
            // was undetected only because SCIENCE_RECIPES is currently empty.
            var recipeId = `s3_progression_mod:${recipe.machine}/${outputId.split(':')[1]}_${index}`
            var builder = event.recipes.create[recipe.machine](outputString, inputStrings.concat(fluidStacks))
            builder.id(recipeId)
            builder.processingTime(recipe.duration || 100)
            if (recipe.heat === 'heated') builder.heated()
            if (recipe.heat === 'superheated') builder.superheated()
            return
        }

        const voltage = VOLTAGE_BY_TIER[recipe.tier]
        if (!voltage) {
            console.error(`[s3_progression_mod] science_recipes[${index}]: GTCEU machine "${recipe.machine}" needs a valid "tier" (one of ${Object.keys(VOLTAGE_BY_TIER).join(', ')})`)
            return
        }

        // var, not const - must match the CREATE_MACHINES branch above, which already
        // declares var recipeId/builder in this same function scope (var is
        // function-scoped, so a const with the same name here would be a real
        // redeclaration conflict, not just a Rhino quirk).
        // No "<machine>/" prefix here - GTCEU already prefixes the recipe type itself
        // (otherwise the id ends up as assembler/assembler/...).
        var recipeId = `s3_progression_mod:${outputId.split(':')[1]}_${index}`
        var builder = event.recipes.gtceu[recipe.machine](recipeId)
        builder.itemInputs.apply(builder, inputStrings)
        if (fluidStacks.length > 0) builder.inputFluids.apply(builder, fluidStacks)
        builder.itemOutputs(outputString)
        builder.duration(recipe.duration || 100)
        builder.EUt(voltage, 1)
    }

    SCIENCE_RECIPES.forEach((recipe, index) => {
        try {
            buildScienceRecipe(recipe, index)
        } catch (e) {
            console.error(`[s3_progression_mod] science_recipes[${index}] (${recipe.age}/${recipe.category}) failed: ${e}`)
        }
    })
})
