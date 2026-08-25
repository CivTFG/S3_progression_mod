// Data-driven generator for science-item recipes. To add a recipe, either edit the
// SCIENCE_RECIPES array below by hand, or use tools/recipe_editor.py - a small Tkinter
// GUI that reads/writes the exact same array. Either way, the loop at the bottom turns
// each entry into a real KubeJS recipe; no need to touch the generator itself unless a
// genuinely new machine type is needed.
//
// Entry shape:
//   {
//     "age": "BRONZE",              // must match a tier key in progression.json
//     "category": "MINING",         // must match a category in progression.json
//     "output": 1,                  // how many science items this craft produces (default 1)
//     "machine": "crafting_table",  // "crafting_table", a GTCEU recipe type id (e.g. "assembler"),
//                                    // or a Create processing recipe type id (e.g. "milling")
//     "tier": "LV",                 // GTCEU machines only - sets EU/t via VOLTAGE_BY_TIER
//     "duration": 100,              // ticks - GTCEU/Create machines only, ignored for crafting_table
//     "heat": "heated",             // Create mixing/compacting only - "heated" or "superheated"
//     "inputs": [
//       { "item": "tfc:metal/ingot/copper", "count": 1 }
//     ]
//   }
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
  { "age": "BRONZE", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "tfc:metal/ingot/copper", "count": 1 }] },
  { "age": "BRONZE", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "tfc:metal/ingot/tin", "count": 1 }] },
  { "age": "BRONZE", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "tfc:metal/ingot/bismuth_bronze", "count": 1 }] },
  { "age": "BRONZE", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "tfc:metal/ingot/black_bronze", "count": 1 }] },
  { "age": "BRONZE", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "tfc:metal/ingot/bronze", "count": 1 }] },
  { "age": "IRON", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "tfc:metal/ingot/wrought_iron", "count": 1 }] },
  { "age": "IRON", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:wrought_iron_plate", "count": 1 }] },
  { "age": "STEEL", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "tfc:metal/ingot/steel", "count": 1 }] },
  { "age": "STEEL", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "tfc:metal/ingot/pig_iron", "count": 1 }] },
  { "age": "STEEL", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "tfc:metal/ingot/black_steel", "count": 1 }] },
  { "age": "STEEL", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "tfc:metal/ingot/blue_steel", "count": 1 }] },
  { "age": "STEEL", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "tfc:metal/ingot/red_steel", "count": 1 }] },
  { "age": "STEAM", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:red_alloy_ingot", "count": 1 }] },
  { "age": "STEAM", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:cobalt_brass_ingot", "count": 1 }] },
  { "age": "STEAM", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:steel_frame", "count": 1 }] },
  { "age": "LV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:aluminium_ingot", "count": 1 }] },
  { "age": "LV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:cupronickel_ingot", "count": 1 }] },
  { "age": "LV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:battery_alloy_ingot", "count": 1 }] },
  { "age": "LV", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:battery_alloy_plate", "count": 1 }] },
  { "age": "HV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:kanthal_ingot", "count": 2 }] },
  { "age": "HV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:stainless_steel_ingot", "count": 2 }] },
  { "age": "HV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:vanadium_steel_ingot", "count": 2 }] },
  { "age": "HV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:nichrome_ingot", "count": 1 }] },
  { "age": "HV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:titanium_ingot", "count": 1 }] },
  { "age": "HV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:blue_alloy_ingot", "count": 1 }] },
  { "age": "HV", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:ultimet_ingot", "count": 1 }] },
  { "age": "EV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:hsla_steel_ingot", "count": 2 }] },
  { "age": "EV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:tungsten_steel_ingot", "count": 2 }] },
  { "age": "EV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:titanium_carbide_ingot", "count": 1 }] },
  { "age": "EV", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:tantalum_carbide_ingot", "count": 1 }] },
  { "age": "EV", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:rtm_alloy_ingot", "count": 1 }] },
  { "age": "IV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:hsse_ingot", "count": 2 }] },
  { "age": "IV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:hssg_ingot", "count": 2 }] },
  { "age": "IV", "category": "MINING", "machine": "crafting_table", "output": 1, "inputs": [{ "item": "gtceu:hsss_ingot", "count": 2 }] },
  { "age": "IV", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:iridium_ingot", "count": 1 }] },
  { "age": "IV", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:ruthenium_ingot", "count": 1 }] },
  { "age": "IV", "category": "MINING", "machine": "crafting_table", "output": 2, "inputs": [{ "item": "gtceu:rhodium_plated_palladium_ingot", "count": 1 }] }
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

    SCIENCE_RECIPES.forEach((recipe, index) => {
        if (validAges.indexOf(recipe.age) === -1) {
            console.error(`[s3_progression_mod] science_recipes[${index}]: unknown age "${recipe.age}" - check progression.json`)
            return
        }
        if (validCategories.indexOf(recipe.category) === -1) {
            console.error(`[s3_progression_mod] science_recipes[${index}]: unknown category "${recipe.category}" - check progression.json`)
            return
        }

        const outputId = `s3_progression_mod:${recipe.age.toLowerCase()}_${recipe.category.toLowerCase()}_science`
        const outputCount = recipe.output || 1
        const outputString = outputCount > 1 ? `${outputCount}x ${outputId}` : outputId
        const inputStrings = recipe.inputs.map(ingredientString)

        if (recipe.machine === 'crafting_table') {
            event.shapeless(outputString, inputStrings)
            return
        }

        if (CREATE_MACHINES.indexOf(recipe.machine) !== -1) {
            const recipeId = `s3_progression_mod:${recipe.machine}/${outputId.split(':')[1]}_${index}`
            const builder = event.recipes.create[recipe.machine](outputString, inputStrings)
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

        const recipeId = `s3_progression_mod:${recipe.machine}/${outputId.split(':')[1]}_${index}`
        const builder = event.recipes.gtceu[recipe.machine](recipeId)
        builder.itemInputs.apply(builder, inputStrings)
        builder.itemOutputs(outputString)
        builder.duration(recipe.duration || 100)
        builder.EUt(voltage, 1)
    })
})
