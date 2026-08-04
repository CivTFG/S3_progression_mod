// One laboratory recipe per progression tier: insert that tier's 5 science items at
// once to advance its counter. Tiers must be unlocked in order - LaboratoryBlockEntity
// itself refuses to even start progressing a tier's recipe (in Java) until the
// immediately preceding tier is already unlocked, so this file doesn't need to worry
// about ordering itself.
//
// Tier keys and science-item categories come from config/s3_progression_mod/progression.json
// (repo copy at config_files/s3_progression_mod/progression.json) - the single source of
// truth also used by Java's ProgressionTiers/ModScienceItems and the other progression
// scripts. Each tier's 5 item ids are derived as "<tier key lowercased>_<category>_science",
// matching ModScienceItems.itemId() in Java exactly - don't hand-list them here.
//
// Wrapped in an IIFE so this file's only top-level name is LABORATORY_RECIPES_PROGRESSION -
// other server_scripts files load the same progression.json independently and must not
// collide on shared top-level names (KubeJS loads all server_scripts into one scope).
const LABORATORY_RECIPES_PROGRESSION = (() => {
    const Files = Java.loadClass('java.nio.file.Files')
    const Paths = Java.loadClass('java.nio.file.Paths')
    const path = Paths.get('config', 's3_progression_mod', 'progression.json')
    return JSON.parse(String(Files.readString(path)))
})()

ServerEvents.recipes(event => {
    LABORATORY_RECIPES_PROGRESSION.tiers.forEach(tier => {
        const ingredients = LABORATORY_RECIPES_PROGRESSION.categories.map(category => `s3_progression_mod:${tier.key.toLowerCase()}_${category}_science`)
        event.recipes.s3_progression_mod.laboratory(ingredients, tier.key, 1)
    })
})
