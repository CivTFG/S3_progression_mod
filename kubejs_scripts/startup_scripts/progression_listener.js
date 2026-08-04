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

// Single source of truth for tier order/thresholds/stages, shared with
// progression_commands.js, blocked_blocks.js, laboratory_recipes.js and Java's
// ProgressionTiers - see config_files/s3_progression_mod/progression.json in the repo.
const PROGRESSION = loadProgressionConfig()
const RESEARCH_KEY = PROGRESSION.researchKey

function loadProgressionConfig() {
    const Files = Java.loadClass('java.nio.file.Files')
    const Paths = Java.loadClass('java.nio.file.Paths')
    const path = Paths.get('config', 's3_progression_mod', 'progression.json')
    return JSON.parse(String(Files.readString(path)))
}

function tierByKey(key) {
    return PROGRESSION.tiers.find(t => t.key === key)
}

ForgeEvents.onEvent('com.civtfg.progression.event.ProgressionEvent', event => {
    const pos = event.pos     // dev.ftb.mods.ftblibrary.math.ChunkDimPos - chunk + dimension only
    const tier = event.tier   // e.g. "LV", "MV", "HV"
    const value = event.value // int

    const tierConfig = tierByKey(tier)
    if (!tierConfig) {
        console.warn(`[s3_progression_mod] Laboratory crafted a recipe for unknown tier "${tier}" - add it to config/s3_progression_mod/progression.json, ignoring`)
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
            if (!player.stages.has(tierConfig.stageId)) {
                player.stages.add(tierConfig.stageId)
                player.tell(`Your team's research has unlocked the ${tier} tier!`)
            }
        })
    }
})
