// Keeps each player's tier game stages in sync with their FTB Team's per-tier research
// totals (so progress made - or a /progression set - while a member was offline still applies once
// they log back in), and provides the /progression commands (op-only "set" for a team's
// tier, "clearlab" for a stale active-lab record and "members" for a team's activity; "teams"
// lists every team's current tier).

const FTBTeamsAPI = Java.loadClass('dev.ftb.mods.ftbteams.api.FTBTeamsAPI')

// Single source of truth for tier order/thresholds/stages, shared with
// progression_listener.js, blocked_blocks.js and Java's ProgressionTiers - see
// config_files/s3_progression_mod/progression.json in the repo.
//
// KubeJS's own class filter denies java.nio/java.io entirely (scripts can't read files
// directly), so the raw bytes come from S3ProgressionTiers.rawJson() (our own mod class,
// unrestricted) instead - this script still does its own JSON.parse of that text.
// prefixed: KubeJS server scripts share one global scope
const S3ProgressionTiers = Java.loadClass('com.civtfg.progression.stage.ProgressionTiers')
const S3PlayerActivity = Java.loadClass('com.civtfg.progression.stage.PlayerActivity')
const PROGRESSION = JSON.parse(String(S3ProgressionTiers.rawJson()))
const RESEARCH_KEY = PROGRESSION.researchKey

function getPlayerTeam(player) {
    return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null)
}

// FTB Teams' own lookup is by short name (what /ftbteams uses, e.g. "joerning" or a
// player's name for a solo player's team); falls back to the visible display name,
// case-insensitive, so the name shown by /progression teams works too.
function findTeam(name) {
    // Tolerates surrounding quotes and whitespace (console input, copied suggestions).
    name = String(name).trim().replace(/^"(.*)"$/, '$1').trim()
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

// Grants/strips every tier stage on one player to match the team's stored unlocks
// (S3ProgressionTiers.isUnlocked - permanent flags, not derived from the research totals,
// since the threshold depends on the team size).
function syncStages(player, team) {
    PROGRESSION.tiers.forEach(tierConfig => {
        const unlocked = S3ProgressionTiers.isUnlocked(team, tierConfig.key)
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

    syncStages(player, team)
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

    // For a team argument that is the LAST argument (GREEDY_STRING takes the rest of the
    // line, '#' included), so the short names are suggested without quotes.
    function suggestTeamsGreedy(ctx, builder) {
        FTBTeamsAPI.api().getManager().getTeams().forEach(team => builder.suggest(String(team.getShortName())))
        return builder.buildFuture()
    }

    event.register(
        Commands.literal('progression')
            .then(Commands.literal('set')
                // Op-only (level 2). "/progression set <team> <tier>" marks <tier> and every
                // tier before it as researched and wipes every tier after it (research
                // total 0, stage removed) - so it works both for raising and lowering a
                // team. NONE wipes everything. Unlocked tiers get the team's current threshold
                // as total (S3ProgressionTiers.thresholdFor, team-size dependent), the others 0.
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
                            const threshold = S3ProgressionTiers.thresholdFor(team)
                            PROGRESSION.tiers.forEach((tierConfig, i) => {
                                research.putInt(tierConfig.key, i <= targetIndex ? threshold : 0)
                                S3ProgressionTiers.setUnlocked(team, tierConfig.key, i <= targetIndex)
                            })
                            data.put(RESEARCH_KEY, research)
                            team.markDirty()

                            team.getOnlineMembers().forEach(member => syncStages(member, team))

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
            .then(Commands.literal('clearlab')
                // Op-only. Deletes a team's "has an active Laboratory" record when it points at
                // a lab that no longer exists (it is only cleared when the active lab is removed
                // from a chunk the same team still claims, and it stores no dimension). Labs
                // placed while the record existed stay out of order - break and re-place them.
                .requires(src => src.hasPermission(2))
                // GREEDY_STRING: "Deserters#1a2b" works unquoted (STRING stops at '#').
                .then(Commands.argument('team', Arguments.GREEDY_STRING.create(event))
                    .suggests(suggestTeamsGreedy)
                    .executes(ctx => {
                        const teamName = Arguments.GREEDY_STRING.getResult(ctx, 'team')
                        const team = findTeam(teamName)
                        if (!team) {
                            ctx.source.sendFailure(Component.red(`Unknown team: '${teamName}'`))
                            return 0
                        }
                        const name = team.getName().getString()
                        if (!S3ProgressionTiers.hasLaboratory(team)) {
                            ctx.source.sendSystemMessage(Component.yellow(`${name} has no active laboratory recorded`))
                            return 0
                        }
                        const pos = S3ProgressionTiers.getLaboratoryPos(team)
                        const where = pos ? `${pos.getX()}, ${pos.getY()}, ${pos.getZ()}` : 'unknown position'
                        S3ProgressionTiers.clearHasLaboratory(team)
                        ctx.source.sendSystemMessage(Component.green(`${name}: active laboratory record (${where}) cleared. Break and re-place their laboratory to make it active.`))
                        return 1
                    })
                )
            )
            .then(Commands.literal('members')
                // Op-only (shows other players' online times): every member of a team with
                // online / last seen and whether they count as inactive (see PlayerActivity).
                .requires(src => src.hasPermission(2))
                .then(Commands.argument('team', Arguments.GREEDY_STRING.create(event))
                    .suggests(suggestTeamsGreedy)
                    .executes(ctx => {
                        const teamName = Arguments.GREEDY_STRING.getResult(ctx, 'team')
                        const team = findTeam(teamName)
                        if (!team) {
                            ctx.source.sendFailure(Component.red(`Unknown team: '${teamName}'`))
                            return 0
                        }
                        const counted = S3ProgressionTiers.countedSize(team)
                        const active = S3ProgressionTiers.memberCount(team)
                        const progress = S3ProgressionTiers.currentProgress(team)
                        const needed = progress
                            ? `, ${progress.threshold()} needed for ${progress.displayName()}${progress.discountPercent() > 0 ? ` after -${progress.discountPercent()}% discount` : ''}`
                            : ''
                        ctx.source.sendSystemMessage(Component.gold(
                            `${team.getName().getString()}: ${team.getMembers().size()} members, ${active} active, ${counted} counted for the threshold (${S3ProgressionTiers.thresholdFor(team)} points${needed})`))
                        S3PlayerActivity.describeMembers(team).forEach(line => ctx.source.sendSystemMessage(Component.literal(`  ${line}`)))
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
                    // into (see S3ProgressionTiers.getCurrentTierName's javadoc), so a team
                    // that hasn't crossed Bronze's own threshold yet hasn't finished
                    // anything - deliberately left out of this list.
                    const teams = FTBTeamsAPI.api().getManager().getTeams()
                    let count = 0
                    teams.forEach(team => {
                        if (!S3ProgressionTiers.isUnlocked(team, 'BRONZE')) {
                            return
                        }
                        const progress = S3ProgressionTiers.currentProgress(team)
                        const counted = S3ProgressionTiers.countedSize(team)
                        const inactive = S3PlayerActivity.inactiveCount(team)
                        const inactiveText = inactive > 0 ? `, ${inactive} inactive` : ''
                        // threshold() already includes the catch-up discount (ResearchDiscount)
                        const discountText = progress && progress.discountPercent() > 0 ? ` -${progress.discountPercent()}%` : ''
                        const currentTierName = progress
                            ? `${progress.displayName()} (${progress.current()} / ${progress.threshold()} points${discountText}, ${counted} players counted${inactiveText})`
                            : 'Everything (fully researched)'
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
