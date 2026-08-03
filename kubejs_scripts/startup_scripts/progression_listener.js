// Listens for the custom Forge event fired by LaboratoryBlockEntity#craft, attributes
// the crafted value to the FTB Team that claims the chunk the laboratory sits in, and
// unlocks a tier's game stage once that tier's running total passes its threshold.
//
// Tiers must be unlocked in order (Bronze -> Iron -> Steel -> Steam -> LV -> MV -> HV
// -> EV -> IV) - but that ordering is enforced in Java (LaboratoryBlockEntity refuses to
// even start progressing an out-of-order recipe), so by the time this event fires the
// craft has already been confirmed as in-order. This script only needs to track each
// tier's own counter/threshold/stage.
//
// ForgeEvents is only bound in startup scripts, not server_scripts, so this must stay
// here - but the callback itself only fires later during real gameplay, so calling the
// FTB Chunks API from inside it is safe even though registration happens at startup.

const FTBChunksAPI = Java.loadClass('dev.ftb.mods.ftbchunks.api.FTBChunksAPI')

// Shared with progression_commands.js (server_scripts) and Java's ProgressionTiers -
// keep all three in sync by hand. Each key must match the "tier" string used in that
// tier's laboratory recipes (see laboratory_recipes.js).
const RESEARCH_KEY = 's3_progression_mod:research'
const TIERS = {
    BRONZE: { threshold: 3, stage: 'bronze_unlocked' },
    IRON: { threshold: 3, stage: 'iron_unlocked' },
    STEEL: { threshold: 3, stage: 'steel_unlocked' },
    STEAM: { threshold: 3, stage: 'steam_unlocked' },
    LV: { threshold: 3, stage: 'furnace_unlocked' },
    MV: { threshold: 3, stage: 'mv_unlocked' },
    HV: { threshold: 3, stage: 'hv_unlocked' },
    EV: { threshold: 3, stage: 'ev_unlocked' },
    IV: { threshold: 3, stage: 'iv_unlocked' },
}

ForgeEvents.onEvent('com.civtfg.progression.event.ProgressionEvent', event => {
    const pos = event.pos     // dev.ftb.mods.ftblibrary.math.ChunkDimPos - chunk + dimension only
    const tier = event.tier   // e.g. "LV", "MV", "HV"
    const value = event.value // int

    const tierConfig = TIERS[tier]
    if (!tierConfig) {
        console.warn(`[s3_progression_mod] Laboratory crafted a recipe for unknown tier "${tier}" - add it to the TIERS table in progression_listener.js, ignoring`)
        return
    }

    const claim = FTBChunksAPI.api().getManager().getChunk(pos)
    if (!claim) {
        console.warn(`[s3_progression_mod] Laboratory crafted in an unclaimed chunk (dim=${pos.dimension().location()}, chunk=[${pos.x()}, ${pos.z()}]) - no team to credit, ignoring`)
        return
    }

    const team = claim.getTeamData().getTeam()
    const data = team.getExtraData()
    const research = data.getCompound(RESEARCH_KEY)
    const total = research.getInt(tier) + value
    research.putInt(tier, total)
    data.put(RESEARCH_KEY, research)
    team.markDirty()

    console.info(`[s3_progression_mod] Team ${team.getId()} ${tier} research total: ${total} (+${value})`)

    if (total > tierConfig.threshold) {
        team.getOnlineMembers().forEach(player => {
            if (!player.stages.has(tierConfig.stage)) {
                player.stages.add(tierConfig.stage)
                player.tell(`Your team's research has unlocked the ${tier} tier!`)
            }
        })
    }
})
