// One laboratory recipe per progression tier: insert that tier's 5 science items at
// once to advance its counter. Tiers must be unlocked in order - LaboratoryBlockEntity
// itself refuses to even start progressing a tier's recipe (in Java) until the
// immediately preceding tier is already unlocked, so this file doesn't need to worry
// about ordering itself.
//
// Tier keys here must match com.civtfg.progression.stage.ProgressionTiers.TIERS (Java)
// and the TIERS table in progression_listener.js/progression_commands.js (KubeJS) -
// keep all three in sync by hand when adding a tier.

const TIER_SCIENCE_ITEMS = {
    BRONZE: ['bronze_mining_science', 'bronze_farming_science', 'bronze_production_science', 'bronze_exploration_science', 'bronze_challenge_science'],
    IRON: ['iron_mining_science', 'iron_farming_science', 'iron_production_science', 'iron_exploration_science', 'iron_challenge_science'],
    STEEL: ['steel_mining_science', 'steel_farming_science', 'steel_production_science', 'steel_exploration_science', 'steel_challenge_science'],
    STEAM: ['steam_mining_science', 'steam_farming_science', 'steam_production_science', 'steam_exploration_science', 'steam_challenge_science'],
    LV: ['lv_mining_science', 'lv_farming_science', 'lv_production_science', 'lv_exploration_science', 'lv_challenge_science'],
    MV: ['mv_mining_science', 'mv_farming_science', 'mv_production_science', 'mv_exploration_science', 'mv_challenge_science'],
    HV: ['hv_mining_science', 'hv_farming_science', 'hv_production_science', 'hv_exploration_science', 'hv_challenge_science'],
    EV: ['ev_mining_science', 'ev_farming_science', 'ev_production_science', 'ev_exploration_science', 'ev_challenge_science'],
    IV: ['iv_mining_science', 'iv_farming_science', 'iv_production_science', 'iv_exploration_science', 'iv_challenge_science'],
}

ServerEvents.recipes(event => {
    Object.keys(TIER_SCIENCE_ITEMS).forEach(tier => {
        const ingredients = TIER_SCIENCE_ITEMS[tier].map(id => `s3_progression_mod:${id}`)
        event.recipes.s3_progression_mod.laboratory(ingredients, tier, 1)
    })
})
