// Keeps each player's furnace game stage in sync with their FTB Team's research total
// (so progress made - or a reset - while a member was offline still applies once they
// log back in), and provides a command to reset a team's progression.

const FTBTeamsAPI = Java.loadClass('dev.ftb.mods.ftbteams.api.FTBTeamsAPI')

// Shared with progression_listener.js (startup_scripts) - keep both in sync if changed.
const RESEARCH_KEY = 's3_progression_mod:research'
const RESEARCH_THRESHOLD = 3
const FURNACE_STAGE = 'furnace_unlocked'

function getPlayerTeam(player) {
    return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null)
}

PlayerEvents.loggedIn(event => {
    const player = event.player
    const team = getPlayerTeam(player)
    if (!team) return

    const unlocked = team.getExtraData().getInt(RESEARCH_KEY) > RESEARCH_THRESHOLD
    if (unlocked && !player.stages.has(FURNACE_STAGE)) {
        player.stages.add(FURNACE_STAGE)
    } else if (!unlocked && player.stages.has(FURNACE_STAGE)) {
        player.stages.remove(FURNACE_STAGE)
    }
})

ServerEvents.commandRegistry(event => {
    const { commands: Commands } = event

    event.register(
        Commands.literal('progression')
            .then(Commands.literal('status')
                .executes(ctx => {
                    const sender = ctx.source.entity
                    if (!sender) {
                        ctx.source.sendFailure(Component.red('This command can only be run by a player'))
                        return 0
                    }

                    const team = getPlayerTeam(sender)
                    if (!team) {
                        sender.tell('You are not on a team')
                        return 0
                    }

                    const total = team.getExtraData().getInt(RESEARCH_KEY)
                    sender.tell(`Team research total: ${total} (unlocks the furnace above ${RESEARCH_THRESHOLD})`)
                    sender.tell(`Furnace unlocked: ${total > RESEARCH_THRESHOLD}`)
                    return 1
                })
            )
            .then(Commands.literal('reset')
                .executes(ctx => {
                    const sender = ctx.source.entity
                    if (!sender) {
                        ctx.source.sendFailure(Component.red('This command can only be run by a player'))
                        return 0
                    }

                    const team = getPlayerTeam(sender)
                    if (!team) {
                        sender.tell('You are not on a team')
                        return 0
                    }

                    team.getExtraData().putInt(RESEARCH_KEY, 0)
                    team.markDirty()

                    team.getOnlineMembers().forEach(member => {
                        if (member.stages.has(FURNACE_STAGE)) {
                            member.stages.remove(FURNACE_STAGE)
                        }
                    })

                    sender.tell('Team progression reset. Offline members will be updated on their next login.')
                    return 1
                })
            )
    )
})
