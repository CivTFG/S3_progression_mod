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

// ===RECIPES-JSON-START===
const SCIENCE_RECIPES = [
  {"age": "BRONZE", "category": "EMPTY", "output": 32, "machine": "crafting_table", "inputs": [{"item": "gtceu:bronze_ingot", "count": 1}]},
  {"age": "BRONZE", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "#forge:dusts/copper", "count": 1}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "MINING", "output": 4, "machine": "crafting_table", "inputs": [{"item": "gtceu:lead_dust", "count": 1}, {"item": "gtceu:bismuth_dust", "count": 1}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "FARMING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "#tfc:foods/breads", "count": 1}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "FARMING", "output": 16, "machine": "crafting_table", "inputs": [{"item": "tfc:food/barley_bread", "count": 1}, {"item": "tfc:food/maize_bread", "count": 1}, {"item": "tfc:food/oat_bread", "count": 1}, {"item": "tfc:food/rice_bread", "count": 1}, {"item": "tfc:food/rye_bread", "count": 1}, {"item": "tfc:food/wheat_bread", "count": 1}, {"item": "firmalife:food/pickled_egg", "count": 1}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "FARMING", "output": 24, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:food/garlic", "count": 4}, {"item": "tfg:food/cooked_lentil", "count": 16}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "PRODUCTION", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "gtceu:flint_block", "count": 7}, {"item": "tfc:candle", "count": 2}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "PRODUCTION", "output": 2, "machine": "crafting_table", "inputs": [{"item": "#forge:cloth", "count": 2}, {"item": "#forge:leather", "count": 1}, {"item": "tfc:wattle/unstained", "count": 4}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "PRODUCTION", "output": 8, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "#forge:cloth", "count": 2}, {"item": "#forge:leather", "count": 1}, {"item": "tfc:wattle/unstained", "count": 4}, {"item": "minecraft:packed_ice", "count": 32}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "EXPLORATION", "output": 18, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:wood/log/acacia", "count": 32}, {"item": "tfc:groundcover/mollusk", "count": 4}, {"item": "tfg:plant/mussels", "count": 2}, {"item": "tfg:plant/barnacles", "count": 1}, {"item": "tfg:plant/silken_pincushion_cactus", "count": 32}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "EXPLORATION", "output": 24, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:wood/log/acacia", "count": 32}, {"item": "tfc:groundcover/mollusk", "count": 4}, {"item": "tfg:plant/mussels", "count": 2}, {"item": "tfg:plant/barnacles", "count": 1}, {"item": "tfg:plant/edelweiss", "count": 4}, {"item": "tfc:wood/log/ash", "count": 32}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "EXPLORATION", "output": 4, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:wood/log/ash", "count": 64}, {"item": "tfc:wood/log/spruce", "count": 64}, {"item": "tfc:wood/log/acacia", "count": 32}, {"item": "tfg:plant/buttercup", "count": 1}, {"item": "tfc:plant/star_grass", "count": 1}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "BRONZE", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "greate:rose_quartz_dust", "count": 2}, {"item": "s3_progression_mod:bronze_empty_science", "count": 1}]},
  {"age": "IRON", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:wrought_iron_ingot", "count": 1}]},
  {"age": "IRON", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "#forge:dusts/iron", "count": 1}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "MINING", "output": 4, "machine": "crafting_table", "inputs": [{"item": "gtceu:silver_dust", "count": 2}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "FARMING", "output": 32, "machine": "crafting_table", "inputs": [{"item": "beneath:ghost_pepper", "count": 5}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}], "fluids": [{"fluid": "tfg:vintage_corn_whiskey", "amount": 10000}, {"fluid": "tfg:vintage_rye_whiskey", "amount": 10000}, {"fluid": "tfg:vintage_rum", "amount": 10000}]},
  {"age": "IRON", "category": "FARMING", "output": 48, "machine": "crafting_table", "inputs": [{"item": "tfg:food/cooked_lentil", "count": 1}, {"item": "tfg:radish_product", "count": 2}, {"item": "tfg:mining_powder", "count": 1}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}], "fluids": [{"fluid": "tfg:vintage_corn_whiskey", "amount": 10000}, {"fluid": "tfg:vintage_vodka", "amount": 10000}, {"fluid": "tfg:vintage_rum", "amount": 10000}, {"fluid": "tfc:maple_syrup", "amount": 10000}]},
  {"age": "IRON", "category": "FARMING", "output": 24, "machine": "crafting_table", "inputs": [{"item": "s3_progression_mod:iron_empty_science", "count": 1}], "fluids": [{"fluid": "gtceu:creosote", "amount": 10000}, {"fluid": "gtceu:creosote", "amount": 10000}, {"fluid": "gtceu:creosote", "amount": 10000}, {"fluid": "#forge:milk", "amount": 10000}, {"fluid": "#forge:milk", "amount": 10000}, {"fluid": "#tfg:vintage_alcohols", "amount": 10000}, {"fluid": "tfc:tallow", "amount": 10000}, {"fluid": "tfc:tallow", "amount": 10000}]},
  {"age": "IRON", "category": "PRODUCTION", "output": 1, "machine": "crafting_table", "inputs": [{"item": "#forge:cloth", "count": 1}, {"item": "#create:tracks", "count": 2}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "PRODUCTION", "output": 6, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "#forge:cloth", "count": 1}, {"item": "#create:tracks", "count": 2}, {"item": "tfg:treated_chipboard_composite", "count": 8}, {"item": "tfg:plant/silken_pincushion_cactus", "count": 16}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "PRODUCTION", "output": 6, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "#forge:cloth", "count": 1}, {"item": "#create:tracks", "count": 4}, {"item": "tfg:treated_chipboard_composite", "count": 16}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}], "fluids": [{"fluid": "gtceu:creosote", "amount": 8000}]},
  {"age": "IRON", "category": "EXPLORATION", "output": 8, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "minecraft:packed_ice", "count": 32}, {"item": "firmalife:ice_shavings", "count": 64}, {"item": "tfc:wood/log/palm", "count": 32}, {"item": "tfc:plant/gutweed", "count": 1}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "EXPLORATION", "output": 18, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "minecraft:packed_ice", "count": 32}, {"item": "firmalife:ice_shavings", "count": 64}, {"item": "tfc:wood/log/palm", "count": 32}, {"item": "tfc:wood/log/mangrove", "count": 32}, {"item": "tfc:plant/labrador_tea", "count": 4}, {"item": "tfg:plant/edelweiss", "count": 2}, {"item": "tfc_textile:caribou_fur", "count": 2}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "EXPLORATION", "output": 18, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfc:groundcover/mollusk", "count": 4}, {"item": "tfg:plant/mussels", "count": 2}, {"item": "minecraft:packed_ice", "count": 32}, {"item": "firmalife:ice_shavings", "count": 64}, {"item": "tfc:wood/log/palm", "count": 32}, {"item": "tfg:plant/palash", "count": 2}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "IRON", "category": "CHALLENGE", "output": 2, "machine": "crafting_table", "inputs": [{"item": "minecraft:blue_ice", "count": 1}, {"item": "gtceu:dark_ash_dust", "count": 1}, {"item": "s3_progression_mod:iron_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:steel_ingot", "count": 1}]},
  {"age": "STEEL", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:coal_dust", "count": 1}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "MINING", "output": 4, "machine": "crafting_table", "inputs": [{"item": "gtceu:gold_dust", "count": 2}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "FARMING", "output": 40, "machine": "crafting_table", "inputs": [{"item": "firmalife:food/vanilla_ice_cream", "count": 1}, {"item": "tfg:spice/bay_leaf", "count": 2}, {"item": "firmalife:food/garlic_bread", "count": 2}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}], "fluids": [{"fluid": "#tfg:vintage_alcohols", "amount": 10000}, {"fluid": "tfc:maple_syrup", "amount": 10000}, {"fluid": "tfg:vintage_corn_whiskey", "amount": 10000}]},
  {"age": "STEEL", "category": "FARMING", "output": 40, "machine": "crafting_table", "inputs": [{"item": "firmalife:food/vanilla_ice_cream", "count": 1}, {"item": "tfg:spice/bay_leaf", "count": 2}, {"item": "firmalife:food/garlic_bread", "count": 2}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}], "fluids": [{"fluid": "#tfg:vintage_alcohols", "amount": 10000}, {"fluid": "tfc:maple_syrup", "amount": 10000}, {"fluid": "tfg:vintage_vodka", "amount": 10000}]},
  {"age": "STEEL", "category": "FARMING", "output": 2, "machine": "crafting_table", "inputs": [{"item": "firmalife:food/chocolate_chip_cookie", "count": 1}, {"item": "firmalife:food/cooked_lasagna", "count": 1}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "FARMING", "output": 18, "machine": "crafting_table", "inputs": [{"item": "firmalife:food/garlic_bread", "count": 2}, {"item": "tfg:cassava_product", "count": 3}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}], "fluids": [{"fluid": "firmalife:goat_milk", "amount": 10000}, {"fluid": "firmalife:yak_milk", "amount": 10000}, {"fluid": "minecraft:milk", "amount": 10000}]},
  {"age": "STEEL", "category": "PRODUCTION", "output": 2, "machine": "crafting_table", "inputs": [{"item": "#forge:cloth", "count": 1}, {"item": "minecraft:writable_book", "count": 1}, {"item": "minecraft:paper", "count": 5}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "PRODUCTION", "output": 12, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "#forge:cloth", "count": 1}, {"item": "minecraft:writable_book", "count": 1}, {"item": "minecraft:paper", "count": 5}, {"item": "minecraft:packed_ice", "count": 16}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}], "fluids": [{"fluid": "#forge:milk", "amount": 8000}]},
  {"age": "STEEL", "category": "PRODUCTION", "output": 10, "machine": "crafting_table", "inputs": [{"item": "#forge:cloth", "count": 1}, {"item": "minecraft:writable_book", "count": 1}, {"item": "minecraft:paper", "count": 5}, {"item": "gtceu:magnetic_iron_block", "count": 1}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "EXPLORATION", "output": 32, "machine": "crafting_table", "inputs": [{"item": "afc:wood/log/mahogany", "count": 1}, {"item": "tfc:wood/log/pine", "count": 1}, {"item": "tfc:plant/hibiscus", "count": 1}, {"item": "tfg:plant/blackthorn", "count": 1}, {"item": "beneath:gleamflower", "count": 1}, {"item": "firmalife:hollow_shell", "count": 1}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}], "fluids": [{"fluid": "gtceu:oil_heavy", "amount": 10000}, {"fluid": "gtceu:oil_light", "amount": 10000}]},
  {"age": "STEEL", "category": "EXPLORATION", "output": 50, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "afc:wood/log/rainbow_eucalyptus", "count": 32}, {"item": "tfc_textile:crocodile_leather", "count": 2}, {"item": "tfc_textile:caribou_fur", "count": 8}, {"item": "tfc_textile:polar_bear_fur", "count": 1}, {"item": "tfc_textile:tiger_fur", "count": 1}, {"item": "tfc_textile:lion_fur", "count": 1}, {"item": "tfc:plant/kangaroo_paw", "count": 4}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEEL", "category": "CHALLENGE", "output": 1, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfg:charred_log", "count": 8}, {"item": "ae2:ender_dust", "count": 2}, {"item": "s3_progression_mod:steel_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:hp_steam_solid_boiler", "count": 1}]},
  {"age": "STEAM", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "minecraft:gunpowder", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "MINING", "output": 5, "machine": "crafting_table", "inputs": [{"item": "tfc:metal/ingot/red_steel", "count": 1}, {"item": "tfc:metal/ingot/blue_steel", "count": 1}, {"item": "tfc:metal/ingot/black_steel", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "FARMING", "output": 18, "machine": "crafting_table", "inputs": [{"item": "tfg:food/yogurt", "count": 2}, {"item": "firmalife:food/garlic_bread", "count": 3}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}], "fluids": [{"fluid": "tfg:vintage_corn_whiskey", "amount": 10000}, {"fluid": "tfc:maple_syrup", "amount": 10000}, {"fluid": "tfg:vintage_cider", "amount": 10000}]},
  {"age": "STEAM", "category": "FARMING", "output": 18, "machine": "crafting_table", "inputs": [{"item": "firmalife:food/vanilla_ice_cream", "count": 1}, {"item": "firmalife:food/chocolate_ice_cream", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}], "fluids": [{"fluid": "#tfg:vintage_alcohols", "amount": 10000}, {"fluid": "tfc:maple_syrup", "amount": 10000}]},
  {"age": "STEAM", "category": "FARMING", "output": 18, "machine": "crafting_table", "inputs": [{"item": "firmalife:food/cooked_pizza", "count": 1}, {"item": "tfg:food/smoothie", "count": 1}, {"item": "tfg:food/cheeseburger", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}], "fluids": [{"fluid": "tfg:peanut_oil", "amount": 10000}, {"fluid": "gtceu:seed_oil", "amount": 10000}, {"fluid": "gtceu:seed_oil", "amount": 10000}, {"fluid": "gtceu:seed_oil", "amount": 10000}]},
  {"age": "STEAM", "category": "PRODUCTION", "output": 6, "machine": "crafting_table", "inputs": [{"item": "gtceu:matchbox", "count": 1}, {"item": "create:precision_mechanism", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}], "fluids": [{"fluid": "gtceu:diesel", "amount": 1000}]},
  {"age": "STEAM", "category": "PRODUCTION", "output": 16, "machine": "crafting_table", "inputs": [{"item": "gtceu:matchbox", "count": 1}, {"item": "gtceu:rubber_ingot", "count": 1}, {"item": "minecraft:blue_ice", "count": 2}, {"item": "create:precision_mechanism", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}], "fluids": [{"fluid": "gtceu:diesel", "amount": 1500}]},
  {"age": "STEAM", "category": "PRODUCTION", "output": 7, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "tfg:lv_universal_circuit", "count": 2}, {"item": "tfg:weakness_pill", "count": 8}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}], "fluids": [{"fluid": "gtceu:naphtha", "amount": 1000}]},
  {"age": "STEAM", "category": "EXPLORATION", "output": 8, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "minecraft:blue_ice", "count": 4}, {"item": "tfg:wood/stripped_log/mahoe", "count": 32}, {"item": "tfc:plant/sapphire_tower", "count": 1}, {"item": "minecraft:spore_blossom", "count": 1}, {"item": "tfg:plant/azalea", "count": 1}, {"item": "minecraft:bamboo", "count": 64}, {"item": "tfc:wood/stripped_log/ash", "count": 32}, {"item": "wan_ancient_beasts:bellflower", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "STEAM", "category": "EXPLORATION", "output": 16, "machine": "primitive_assembler", "duration": 200, "inputs": [{"item": "minecraft:blue_ice", "count": 4}, {"item": "tfc:wood/stripped_log/spruce", "count": 32}, {"item": "tfg:plant/prickly_pear", "count": 1}, {"item": "tfg:plant/silken_pincushion_cactus", "count": 32}, {"item": "tfg:plant/penwortel", "count": 1}, {"item": "tfg:plant/kinnikinnick", "count": 1}, {"item": "tfc_textile:caribou_fur", "count": 6}, {"item": "wan_ancient_beasts:moonflower", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}], "fluids": [{"fluid": "tfg:muddy_water", "amount": 8000}]},
  {"age": "STEAM", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "tfc:cake", "count": 1}, {"item": "createaddition:chocolate_cake", "count": 1}, {"item": "createaddition:honey_cake", "count": 1}, {"item": "firmalife:food/banana_split", "count": 1}, {"item": "gtceu:wax_nugget", "count": 1}, {"item": "s3_progression_mod:steam_empty_science", "count": 1}]},
  {"age": "LV", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:lv_machine_hull", "count": 1}]},
  {"age": "LV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:aluminium_ingot", "count": 1}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "FARMING", "output": 1, "machine": "assembler", "tier": "LV", "duration": 100, "inputs": [{"item": "gtceu:plant_ball", "count": 128}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "FARMING", "output": 2, "machine": "assembler", "tier": "LV", "duration": 100, "inputs": [{"item": "gtceu:plant_ball", "count": 32}, {"item": "firmalife:food/banana_split", "count": 1}, {"item": "tfg:food/cooked_instant_mac", "count": 4}, {"item": "tfg:food/cooked_beer_battered_cheese_curds", "count": 4}, {"item": "tfg:food/poutine", "count": 4}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "PRODUCTION", "output": 20, "machine": "assembler", "tier": "LV", "duration": 100, "inputs": [{"item": "tfg:paracetamol_pill", "count": 1}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "LV", "duration": 100, "inputs": [{"item": "tfg:bakelite_ingot", "count": 2}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "LV", "duration": 100, "inputs": [{"item": "tfg:mv_universal_circuit", "count": 4}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "EXPLORATION", "output": 1, "machine": "crafting_table", "inputs": [{"item": "tfc:groundcover/guano", "count": 4}, {"item": "minecraft:blue_ice", "count": 4}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "LV", "category": "CHALLENGE", "output": 1, "machine": "assembler", "tier": "LV", "duration": 100, "inputs": [{"item": "betterend:murkweed", "count": 32}, {"item": "tfc:plant/blood_lily", "count": 32}, {"item": "tfc:food/rainbow_trout", "count": 16}, {"item": "gtceu:manganese_phosphide_ingot", "count": 1}, {"item": "s3_progression_mod:lv_empty_science", "count": 1}]},
  {"age": "MV", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:aluminium_ingot", "count": 1}]},
  {"age": "MV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:stainless_steel_ingot", "count": 1}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "FARMING", "output": 1, "machine": "assembler", "tier": "MV", "duration": 100, "inputs": [{"item": "minecraft:charcoal", "count": 512}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "FARMING", "output": 3, "machine": "assembler", "tier": "MV", "duration": 100, "inputs": [{"item": "minecraft:charcoal", "count": 128}, {"item": "firmalife:food/banana_split", "count": 4}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "MV", "duration": 100, "inputs": [{"item": "gtceu:polyethylene_ingot", "count": 2}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "MV", "duration": 100, "inputs": [{"item": "tfg:hv_universal_circuit", "count": 4}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "EXPLORATION", "output": 64, "machine": "assembler", "tier": "MV", "duration": 100, "inputs": [{"item": "primitive_creatures:totem_0", "count": 1}, {"item": "primitive_creatures:totem_2", "count": 1}, {"item": "primitive_creatures:totem_3", "count": 1}, {"item": "beneath:cursed_hide", "count": 12}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "CHALLENGE", "output": 12, "machine": "assembler", "tier": "MV", "duration": 100, "inputs": [{"item": "tfc_textile:caribou_fur", "count": 8}, {"item": "tfc_textile:polar_bear_fur", "count": 1}, {"item": "gtceu:magnesium_diboride_ingot", "count": 12}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "MV", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:magnesium_diboride_ingot", "count": 6}, {"item": "s3_progression_mod:mv_empty_science", "count": 1}]},
  {"age": "HV", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:stainless_steel_ingot", "count": 1}]},
  {"age": "HV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:rocket_alloy_t1_ingot", "count": 1}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "FARMING", "output": 2, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "beneath:food/portobello", "count": 64}, {"item": "tfg:detox_capsule", "count": 5}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "gtceu:silicone_rubber_ingot", "count": 1}, {"item": "tfg:vitrified_pearl", "count": 1}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "tfg:hv_universal_circuit", "count": 10}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "EXPLORATION", "output": 16, "machine": "crafting_table", "inputs": [{"item": "tfg:huge_quartz", "count": 1}, {"item": "tfc:groundcover/guano", "count": 2}, {"item": "tfc_textile:crocodile_leather", "count": 1}, {"item": "tfc_textile:caribou_fur", "count": 3}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "CHALLENGE", "output": 4, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "tfg:huge_quartz", "count": 1}, {"item": "tfc:food/venison", "count": 32}, {"item": "gtceu:mercury_barium_calcium_cuprate_ingot", "count": 4}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "HV", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:mercury_barium_calcium_cuprate_ingot", "count": 6}, {"item": "s3_progression_mod:hv_empty_science", "count": 1}]},
  {"age": "MOON", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "ad_astra:desh_ingot", "count": 1}]},
  {"age": "MOON", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "ad_astra:desh_ingot", "count": 1}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "FARMING", "output": 2, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "betterend:chorus_lily", "count": 32}, {"item": "tfg:lunar_roots", "count": 32}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}], "fluids": [{"fluid": "tfc:maple_syrup", "amount": 10000}]},
  {"age": "MOON", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "gtceu:polytetrafluoroethylene_ingot", "count": 1}, {"item": "tfg:polysilicon_dust", "count": 1}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "tfg:ev_universal_circuit", "count": 5}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "EXPLORATION", "output": 24, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "minecraft:ochre_froglight", "count": 32}, {"item": "minecraft:pearlescent_froglight", "count": 32}, {"item": "minecraft:verdant_froglight", "count": 32}, {"item": "tfg:food/cooked_crawlermari", "count": 3}, {"item": "tfc:food/cooked_gran_feline", "count": 2}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "CHALLENGE", "output": 2, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "tfc:groundcover/guano", "count": 8}, {"item": "tfc_textile:crocodile_leather", "count": 1}, {"item": "gtceu:mercury_barium_calcium_cuprate_ingot", "count": 4}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "MOON", "category": "CHALLENGE", "output": 1, "machine": "assembler", "tier": "HV", "duration": 100, "inputs": [{"item": "gtceu:mercury_barium_calcium_cuprate_ingot", "count": 16}, {"item": "s3_progression_mod:moon_empty_science", "count": 1}]},
  {"age": "EV", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "gtceu:ev_machine_hull", "count": 1}]},
  {"age": "EV", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:titanium_ingot", "count": 1}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "FARMING", "output": 8, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "minecraft:popped_chorus_fruit", "count": 32}, {"item": "createaddition:honey_cake", "count": 8}, {"item": "createaddition:chocolate_cake", "count": 8}, {"item": "tfc:cake", "count": 8}, {"item": "tfg:al_cr_y_hydroxide_cake", "count": 2}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "PRODUCTION", "output": 4, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "tfg:chemical_prismatic_dye", "count": 1}, {"item": "gtceu:polyvinyl_butyral_ingot", "count": 1}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "tfg:ev_universal_circuit", "count": 12}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "EXPLORATION", "output": 64, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "tfc:groundcover/guano", "count": 16}, {"item": "primitive_creatures:totem_0", "count": 1}, {"item": "primitive_creatures:totem_2", "count": 1}, {"item": "primitive_creatures:totem_3", "count": 1}, {"item": "tfc_textile:polar_bear_fur", "count": 1}, {"item": "tfc_textile:grizzly_bear_fur", "count": 1}, {"item": "tfc_textile:black_bear_fur", "count": 1}, {"item": "tfc:groundcover/mollusk", "count": 4}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "CHALLENGE", "output": 2, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "tfg:plant/anemone_purple", "count": 8}, {"item": "tfc:groundcover/guano", "count": 4}, {"item": "tfc:food/gran_feline", "count": 32}, {"item": "gtceu:uranium_triplatinum_ingot", "count": 2}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "EV", "category": "CHALLENGE", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:uranium_triplatinum_ingot", "count": 6}, {"item": "s3_progression_mod:ev_empty_science", "count": 1}]},
  {"age": "MARS", "category": "EMPTY", "output": 64, "machine": "crafting_table", "inputs": [{"item": "ad_astra:ostrum_ingot", "count": 1}]},
  {"age": "MARS", "category": "MINING", "output": 1, "machine": "crafting_table", "inputs": [{"item": "gtceu:tungsten_steel_ingot", "count": 1}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "FARMING", "output": 1, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "minecraft:warped_stem", "count": 32}, {"item": "minecraft:crimson_stem", "count": 32}, {"item": "ad_astra:aeronos_stem", "count": 32}, {"item": "ad_astra:strophar_stem", "count": 32}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "PRODUCTION", "output": 32, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "tfg:thorium_rod", "count": 1}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "PRODUCTION", "output": 1, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "tfg:iv_universal_circuit", "count": 6}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "EXPLORATION", "output": 4, "machine": "crafting_table", "inputs": [{"item": "wan_ancient_beasts:crusher_spike", "count": 1}, {"item": "tfg:food/cooked_wraptor", "count": 1}, {"item": "tfg:food/cooked_walker_steak", "count": 4}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "CHALLENGE", "output": 2, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "firmalife:hollow_shell", "count": 8}, {"item": "tfc_textile:tiger_fur", "count": 1}, {"item": "tfc_textile:lion_fur", "count": 1}, {"item": "gtceu:uranium_triplatinum_ingot", "count": 4}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]},
  {"age": "MARS", "category": "CHALLENGE", "output": 1, "machine": "assembler", "tier": "EV", "duration": 100, "inputs": [{"item": "gtceu:uranium_triplatinum_ingot", "count": 16}, {"item": "s3_progression_mod:mars_empty_science", "count": 1}]}
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
