// Listens for the custom Forge event fired by LaboratoryBlockEntity#craft, attributes
// the crafted value to the FTB Team that claims the chunk the laboratory sits in, and
// unlocks the furnace game stage once that team's running total passes the threshold.
//
// ForgeEvents is only bound in startup scripts, not server_scripts, so this must stay
// here - but the callback itself only fires later during real gameplay, so calling the
// FTB Chunks API from inside it is safe even though registration happens at startup.

const FTBChunksAPI = Java.loadClass('dev.ftb.mods.ftbchunks.api.FTBChunksAPI')

// Shared with progression_commands.js (server_scripts) - keep both in sync if changed.
const RESEARCH_KEY = 's3_progression_mod:research'
const RESEARCH_THRESHOLD = 3
const FURNACE_STAGE = 'furnace_unlocked'

ForgeEvents.onEvent('com.civtfg.progression.event.ProgressionEvent', event => {
    const pos = event.pos     // dev.ftb.mods.ftblibrary.math.ChunkDimPos - chunk + dimension only
    const value = event.value // int

    const claim = FTBChunksAPI.api().getManager().getChunk(pos)
    if (!claim) {
        console.warn(`[s3_progression_mod] Laboratory crafted in an unclaimed chunk (dim=${pos.dimension().location()}, chunk=[${pos.x()}, ${pos.z()}]) - no team to credit, ignoring`)
        return
    }

    const team = claim.getTeamData().getTeam()
    const data = team.getExtraData()
    const total = data.getInt(RESEARCH_KEY) + value
    data.putInt(RESEARCH_KEY, total)
    team.markDirty()

    console.info(`[s3_progression_mod] Team ${team.getId()} research total: ${total} (+${value})`)

    if (total > RESEARCH_THRESHOLD) {
        team.getOnlineMembers().forEach(player => {
            if (!player.stages.has(FURNACE_STAGE)) {
                player.stages.add(FURNACE_STAGE)
                player.tell('Your team\'s research has unlocked the furnace!')
            }
        })
    }
})
