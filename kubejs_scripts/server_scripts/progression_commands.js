// Keeps each player's tier game stages in sync with their FTB Team's per-tier research
// totals (so progress made - or a reset - while a member was offline still applies once
// they log back in), and provides commands to check/reset a single tier's progression.

const FTBTeamsAPI = Java.loadClass('dev.ftb.mods.ftbteams.api.FTBTeamsAPI')

// Single source of truth for tier order/thresholds/stages, shared with
// progression_listener.js, blocked_blocks.js and Java's ProgressionTiers - see
// config_files/s3_progression_mod/progression.json in the repo.
//
// KubeJS's own class filter denies java.nio/java.io entirely (scripts can't read files
// directly), so the raw bytes come from ProgressionTiers.rawJson() (our own mod class,
// unrestricted) instead - this script still does its own JSON.parse of that text.
const PROGRESSION = loadProgressionConfig()
const RESEARCH_KEY = PROGRESSION.researchKey

function loadProgressionConfig() {
    const ProgressionTiers = Java.loadClass('com.civtfg.progression.stage.ProgressionTiers')
    return JSON.parse(String(ProgressionTiers.rawJson()))
}

function tierByKey(key) {
    return PROGRESSION.tiers.find(t => t.key === key)
}

function getPlayerTeam(player) {
    return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null)
}

PlayerEvents.loggedIn(event => {
    const player = event.player
    const team = getPlayerTeam(player)
    if (!team) return

    const research = team.getExtraData().getCompound(RESEARCH_KEY)
    PROGRESSION.tiers.forEach(tierConfig => {
        const unlocked = research.getInt(tierConfig.key) > tierConfig.threshold
        if (unlocked && !player.stages.has(tierConfig.stageId)) {
            player.stages.add(tierConfig.stageId)
        } else if (!unlocked && player.stages.has(tierConfig.stageId)) {
            player.stages.remove(tierConfig.stageId)
        }
    })
})

ServerEvents.commandRegistry(event => {
    const { commands: Commands, arguments: Arguments } = event

    function suggestTiers(ctx, builder) {
        PROGRESSION.tiers.forEach(t => builder.suggest(t.key))
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
                        const tierConfig = tierByKey(tier)
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
                // Op-only (level 2) - this zeroes a team's research counter for a tier and
                // immediately strips that tier's GameStage from every online team member,
                // with no confirmation and no consent from the rest of the team. Left
                // open to any player was a real grief vector (a lone member could nuke the
                // whole team's already-unlocked tier), not just a "self-cheat" - `status`/
                // `teams` stay open since those are read-only.
                .requires(src => src.hasPermission(2))
                .then(Commands.argument('tier', Arguments.STRING.create(event))
                    .suggests(suggestTiers)
                    .executes(ctx => {
                        const sender = ctx.source.entity
                        if (!sender) {
                            ctx.source.sendFailure(Component.red('This command can only be run by a player'))
                            return 0
                        }

                        const tier = Arguments.STRING.getResult(ctx, 'tier')
                        const tierConfig = tierByKey(tier)
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
                            if (member.stages.has(tierConfig.stageId)) {
                                member.stages.remove(tierConfig.stageId)
                            }
                        })

                        sender.tell(`${tier} progression reset. Offline members will be updated on their next login.`)
                        return 1
                    })
                )
            )
            .then(Commands.literal('teams')
                .executes(ctx => {
                    const sender = ctx.source.entity
                    if (!sender) {
                        ctx.source.sendFailure(Component.red('This command can only be run by a player'))
                        return 0
                    }

                    // "isUnlocked(team, 'BRONZE')" is exactly "has completed at least one
                    // tier" - Bronze is the implicit starting tier nobody needs to research
                    // into (see ProgressionTiers.getCurrentTierName's javadoc), so a team
                    // that hasn't crossed Bronze's own threshold yet hasn't finished
                    // anything - deliberately left out of this list.
                    const ProgressionTiers = Java.loadClass('com.civtfg.progression.stage.ProgressionTiers')
                    const teams = FTBTeamsAPI.api().getManager().getTeams()
                    let count = 0
                    teams.forEach(team => {
                        if (!ProgressionTiers.isUnlocked(team, 'BRONZE')) {
                            return
                        }
                        const progress = ProgressionTiers.currentProgress(team)
                        const currentTierName = progress ? progress.displayName() : 'Everything (fully researched)'
                        sender.tell(`${team.getName()}: ${currentTierName}`)
                        count++
                    })
                    if (count === 0) {
                        sender.tell('No team has progressed past Bronze yet')
                    }
                    return count
                })
            )
    )
})
