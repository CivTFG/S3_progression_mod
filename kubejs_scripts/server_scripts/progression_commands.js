// Keeps each player's tier game stages in sync with their FTB Team's per-tier research
// totals (so progress made - or a /progression set - while a member was offline still applies once
// they log back in), and provides the /progression commands (op-only "set" for a team's
// tier, "teams" to list every team's current tier).

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

function getPlayerTeam(player) {
    return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null)
}

// FTB Teams' own lookup is by short name (what /ftbteams uses, e.g. "joerning" or a
// player's name for a solo player's team); falls back to the visible display name,
// case-insensitive, so the name shown by /progression teams works too.
function findTeam(name) {
    const manager = FTBTeamsAPI.api().getManager()
    const byShortName = manager.getTeamByName(name).orElse(null)
    if (byShortName) return byShortName
    const wanted = String(name).toLowerCase()
    let found = null
    manager.getTeams().forEach(team => {
        if (!found && String(team.getName().getString()).toLowerCase() === wanted) found = team
    })
    return found
}

// Grants/strips every tier stage on one player to match the team's research totals.
function syncStages(player, research) {
    PROGRESSION.tiers.forEach(tierConfig => {
        const unlocked = research.getInt(tierConfig.key) > tierConfig.threshold
        if (unlocked && !player.stages.has(tierConfig.stageId)) {
            player.stages.add(tierConfig.stageId)
        } else if (!unlocked && player.stages.has(tierConfig.stageId)) {
            player.stages.remove(tierConfig.stageId)
        }
    })
}

PlayerEvents.loggedIn(event => {
    const player = event.player
    const team = getPlayerTeam(player)
    if (!team) return

    syncStages(player, team.getExtraData().getCompound(RESEARCH_KEY))
})

ServerEvents.commandRegistry(event => {
    const { commands: Commands, arguments: Arguments } = event

    function suggestTiers(ctx, builder) {
        PROGRESSION.tiers.forEach(t => builder.suggest(t.key))
        return builder.buildFuture()
    }

    function suggestSetTiers(ctx, builder) {
        builder.suggest('NONE')
        return suggestTiers(ctx, builder)
    }

    // Party short names carry an id suffix ("Joerning#bf1..."), and Brigadier's string
    // argument only allows 0-9 A-Z a-z _ - . + unquoted - so anything else is suggested
    // in double quotes, which the same argument type accepts.
    function suggestTeams(ctx, builder) {
        FTBTeamsAPI.api().getManager().getTeams().forEach(team => {
            const shortName = String(team.getShortName())
            builder.suggest(/^[0-9A-Za-z_.+-]+$/.test(shortName) ? shortName : `"${shortName.replace(/(["\\])/g, '\\$1')}"`)
        })
        return builder.buildFuture()
    }

    event.register(
        Commands.literal('progression')
            .then(Commands.literal('set')
                // Op-only (level 2). "/progression set <team> <tier>" marks <tier> and every
                // tier before it as researched and wipes every tier after it (research
                // total 0, stage removed) - so it works both for raising and lowering a
                // team. NONE wipes everything. Every tier's total is set to exactly
                // threshold + 1 (the lowest "unlocked" value, see ProgressionTiers.isUnlocked -
                // 1025 with the current 1024 thresholds) or 0.
                .requires(src => src.hasPermission(2))
                .then(Commands.argument('team', Arguments.STRING.create(event))
                    .suggests(suggestTeams)
                    .then(Commands.argument('tier', Arguments.STRING.create(event))
                        .suggests(suggestSetTiers)
                        .executes(ctx => {
                            const teamName = Arguments.STRING.getResult(ctx, 'team')
                            const tier = String(Arguments.STRING.getResult(ctx, 'tier')).toUpperCase()

                            const team = findTeam(teamName)
                            if (!team) {
                                ctx.source.sendFailure(Component.red(`Unknown team: '${teamName}'`))
                                return 0
                            }

                            let targetIndex = -1
                            for (var i = 0; i < PROGRESSION.tiers.length; i++) {
                                if (PROGRESSION.tiers[i].key === tier) targetIndex = i
                            }
                            if (targetIndex === -1 && tier !== 'NONE') {
                                ctx.source.sendFailure(Component.red(`Unknown tier: '${tier}'`))
                                return 0
                            }

                            const data = team.getExtraData()
                            const research = data.getCompound(RESEARCH_KEY)
                            PROGRESSION.tiers.forEach((tierConfig, i) => {
                                research.putInt(tierConfig.key, i <= targetIndex ? tierConfig.threshold + 1 : 0)
                            })
                            data.put(RESEARCH_KEY, research)
                            team.markDirty()

                            team.getOnlineMembers().forEach(member => syncStages(member, research))

                            const name = team.getName().getString()
                            const summary = targetIndex === -1
                                ? `${name}: all research removed`
                                : `${name}: researched up to and including ${PROGRESSION.tiers[targetIndex].displayName}`
                            // Not sendSuccess: its 1.20.1 signature takes a Supplier<Component>,
                            // but KubeJS turned the JS arrow function into a Component of its
                            // own source text ("ArrowFunction (0) => {...}") instead of calling it.
                            ctx.source.sendSystemMessage(Component.green(`${summary}. Offline members will be updated on their next login.`))
                            return 1
                        })
                    )
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
                        // team.getName() is a Component (FTB Teams renders it as a
                        // clickable/colored link), not a plain string - .getString()
                        // extracts the visible text, same fix as progression_listener.js's
                        // broadcast message.
                        sender.tell(`${team.getName().getString()}: ${currentTierName}`)
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
