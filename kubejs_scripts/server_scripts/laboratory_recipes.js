// The initial "5 sticks -> value 1" recipe.
//
// Once the schema above is registered, this is exactly equivalent to dropping
// the following file into a datapack at
//   data/s3_progression_mod/recipes/five_sticks.json :
//
// {
//   "type": "s3_progression_mod:laboratory",
//   "ingredients": [
//     { "item": "minecraft:stick" },
//     { "item": "minecraft:stick" },
//     { "item": "minecraft:stick" },
//     { "item": "minecraft:stick" },
//     { "item": "minecraft:stick" }
//   ],
//   "value": 1
// }
//
// so future recipes can just be added as raw datapack json files with no
// further script changes required - the block entity reads "value" straight
// out of whichever json backs the matched recipe.

ServerEvents.recipes(event => {
    event.recipes.s3_progression_mod.laboratory(
        ['minecraft:stick', 'minecraft:stick', 'minecraft:stick', 'minecraft:stick', 'minecraft:stick'],
        1
    )
})
