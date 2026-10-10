package com.civtfg.progression.stage;

import com.civtfg.progression.ProgressionMod;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Catch-up discount (since 0.9.0): a team researching a tier needs {@code percentPerTeam} percent
 * fewer points for every OTHER team that has already unlocked that tier, at most
 * {@code maxPercent} (progression.json "discount", 10 / 40 by default - 4 teams ahead = -40%).
 * Party and solo teams count; server teams don't, and neither does the dormant solo team of a
 * player who is in a party now (FTB Teams keeps it, but the player's effective team is the party).
 * The discount never goes down: the highest count seen per team and tier is stored on the team
 * ({@value #DISCOUNT_TEAMS_KEY}), so a team that disbands or gets reset by /progression set doesn't
 * take it away again.
 * It only lowers the points needed ({@link ProgressionTiers#requiredPoints}), never the lab craft
 * time or the free points - those are based on the undiscounted threshold, or the discount would
 * cancel itself out. A tier that becomes reachable through a higher discount unlocks on the team's
 * next Laboratory craft, like a member leaving.
 */
@Mod.EventBusSubscriber(modid = ProgressionMod.MOD_ID)
public final class ResearchDiscount {

    /** Compound tier key -> highest number of teams ahead seen so far (the discount never sinks). */
    private static final String DISCOUNT_TEAMS_KEY = "s3_progression_mod:discount_teams";

    private static boolean refreshPending = false;

    private ResearchDiscount() {
    }

    /** @return the discount in percent {@code team} gets on {@code tierKey}'s threshold (0..maxPercent). */
    public static int percentFor(Team team, String tierKey) {
        return percentForTeams(teamsAhead(team, tierKey));
    }

    private static int percentForTeams(int teams) {
        ProgressionTiers.DiscountConfig config = ProgressionTiers.DISCOUNT;
        int percent = Math.min(config.maxPercent(), Math.max(0, config.percentPerTeam()) * teams);
        return Math.max(0, Math.min(100, percent));
    }

    /**
     * @return how many other teams count as ahead of {@code team} on {@code tierKey}: the stored
     * highest count, raised (and stored) if more teams have unlocked it by now.
     */
    public static int teamsAhead(Team team, String tierKey) {
        CompoundTag data = team.getExtraData();
        CompoundTag stored = data.getCompound(DISCOUNT_TEAMS_KEY);
        int highest = stored.getInt(tierKey);
        int now = countTeamsAhead(team, tierKey);
        if (now > highest) {
            stored.putInt(tierKey, now);
            data.put(DISCOUNT_TEAMS_KEY, stored);
            team.markDirty();
            return now;
        }
        return highest;
    }

    private static int countTeamsAhead(Team team, String tierKey) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return 0;
        }
        TeamManager manager = FTBTeamsAPI.api().getManager();
        int count = 0;
        for (Team other : manager.getTeams()) {
            if (other.getId().equals(team.getId()) || !counts(manager, other)) {
                continue;
            }
            if (ProgressionTiers.isUnlocked(other, tierKey)) {
                count++;
            }
        }
        return count;
    }

    /** Party teams and solo teams of players who aren't in a party - not server teams. */
    private static boolean counts(TeamManager manager, Team team) {
        if (!team.isValid() || team.isServerTeam()) {
            return false;
        }
        if (team.isPlayerTeam()) {
            return manager.getTeamForPlayerID(team.getOwner()).map(effective -> effective.getId().equals(team.getId())).orElse(true);
        }
        return team.isPartyTeam();
    }

    /**
     * Called when any team unlocks a tier: on the next server tick (after progression_listener.js'
     * "just researched" broadcast), every team's discount on its current tier is brought up to
     * date and online members of teams whose discount rose get a chat message.
     */
    public static void scheduleRefresh() {
        refreshPending = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !refreshPending || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        refreshPending = false;
        TeamManager manager = FTBTeamsAPI.api().getManager();
        for (Team team : manager.getTeams()) {
            if (!counts(manager, team)) {
                continue;
            }
            // currentTier, not currentProgress: the latter already raises the stored count
            ProgressionTiers.Tier tier = ProgressionTiers.currentTier(team);
            if (tier == null) {
                continue;
            }
            int before = percentForTeams(team.getExtraData().getCompound(DISCOUNT_TEAMS_KEY).getInt(tier.key()));
            int after = percentForTeams(teamsAhead(team, tier.key()));
            if (after > before) {
                Component message = Component.literal("Other teams are ahead: researching " + tier.displayName()
                        + " is now " + after + "% cheaper (" + ProgressionTiers.requiredPoints(team, tier.key())
                        + " points needed).").withStyle(ChatFormatting.GOLD);
                team.getOnlineMembers().forEach(player -> player.sendSystemMessage(message));
            }
        }
    }
}
