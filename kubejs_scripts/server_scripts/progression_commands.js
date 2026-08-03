// Keeps each player's tier game stages in sync with their FTB Team's per-tier research
// totals (so progress made - or a reset - while a member was offline still applies once
// they log back in), and provides commands to check/reset a single tier's progression.

const FTBTeamsAPI = Java.loadClass('dev.ftb.mods.ftbteams.api.FTBTeamsAPI')

// Shared with progression_listener.js (startup_scripts) and Java's ProgressionTiers -
// keep all three in sync by hand.
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

function getPlayerTeam(player) {
    return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null)
}

PlayerEvents.loggedIn(event => {
    const player = event.player
    const team = getPlayerTeam(player)
    if (!team) return

    const research = team.getExtraData().getCompound(RESEARCH_KEY)
    Object.keys(TIERS).forEach(tier => {
        const tierConfig = TIERS[tier]
        const unlocked = research.getInt(tier) > tierConfig.threshold
        if (unlocked && !player.stages.has(tierConfig.stage)) {
            player.stages.add(tierConfig.stage)
        } else if (!unlocked && player.stages.has(tierConfig.stage)) {
            player.stages.remove(tierConfig.stage)
        }
    })
})

ServerEvents.commandRegistry(event => {
    const { commands: Commands, arguments: Arguments } = event

    function suggestTiers(ctx, builder) {
        Object.keys(TIERS).forEach(tier => builder.suggest(tier))
        return builder.buildFuture()
    }

    event.register(
        Commands.literal('progression')
            .then(Commands.literal('status')
                .then(Commands.argument('tier', Arguments.STRING.create(event))
                    .suggests(suggestTiers)
                    .executes(ctx => {
                        const sender = ctx.source.entity
                        if (!sender) {
                            ctx.source.sendFailure(Component.red('This command can only be run by a player'))
                            return 0
                        }

                        const tier = Arguments.STRING.getResult(ctx, 'tier')
                        const tierConfig = TIERS[tier]
                        if (!tierConfig) {
                            ctx.source.sendFailure(Component.red(`Unknown tier: '${tier}'`))
                            return 0
                        }

                        const team = getPlayerTeam(sender)
                        if (!team) {
                            sender.tell('You are not on a team')
                            return 0
                        }

                        const total = team.getExtraData().getCompound(RESEARCH_KEY).getInt(tier)
                        sender.tell(`${tier} research total: ${total} (unlocks above ${tierConfig.threshold})`)
                        sender.tell(`${tier} unlocked: ${total > tierConfig.threshold}`)
                        return 1
                    })
                )
            )
            .then(Commands.literal('reset')
                .then(Commands.argument('tier', Arguments.STRING.create(event))
                    .suggests(suggestTiers)
                    .executes(ctx => {
                        const sender = ctx.source.entity
                        if (!sender) {
                            ctx.source.sendFailure(Component.red('This command can only be run by a player'))
                            return 0
                        }

                        const tier = Arguments.STRING.getResult(ctx, 'tier')
                        const tierConfig = TIERS[tier]
                        if (!tierConfig) {
                            ctx.source.sendFailure(Component.red(`Unknown tier: '${tier}'`))
                            return 0
                        }

                        const team = getPlayerTeam(sender)
                        if (!team) {
                            sender.tell('You are not on a team')
                            return 0
                        }

                        const data = team.getExtraData()
                        const research = data.getCompound(RESEARCH_KEY)
                        research.putInt(tier, 0)
                        data.put(RESEARCH_KEY, research)
                        team.markDirty()

                        team.getOnlineMembers().forEach(member => {
                            if (member.stages.has(tierConfig.stage)) {
                                member.stages.remove(tierConfig.stage)
                            }
                        })

                        sender.tell(`${tier} progression reset. Offline members will be updated on their next login.`)
                        return 1
                    })
                )
            )
    )
})
