package com.civtfg.progression.stage;

import com.civtfg.progression.ProgressionMod;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Free research for teams with an active Laboratory: every {@code intervalMinutes} of server
 * uptime (progression.json "passiveResearch", default 20 minutes / 1 point, +1 per 10 counted
 * players) each such team gets points on its current tier, if its lab can research that tier - see
 * {@link ProgressionTiers#passivePointsFor} and {@link ProgressionTiers#awardPassiveResearch}. Measured by the wall clock while the server
 * runs: time the server is offline doesn't count, and a partly elapsed interval is lost on
 * restart. Silent - the tier still only unlocks on the team's next Laboratory craft.
 */
@Mod.EventBusSubscriber(modid = ProgressionMod.MOD_ID)
public final class PassiveResearch {

    private static final Logger LOGGER = LoggerFactory.getLogger(PassiveResearch.class);

    private static long nextAwardAtMillis = Long.MAX_VALUE;
    private static int ticks = 0;

    private PassiveResearch() {
    }

    private static long intervalMillis() {
        return Math.max(1, ProgressionTiers.PASSIVE_RESEARCH.intervalMinutes()) * 60_000L;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        nextAwardAtMillis = System.currentTimeMillis() + intervalMillis();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 20) {
            return;
        }
        ticks = 0;
        long now = System.currentTimeMillis();
        if (now < nextAwardAtMillis || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        // one award per interval; after a long stall (e.g. the machine slept) don't burst-award
        nextAwardAtMillis = Math.max(nextAwardAtMillis + intervalMillis(), now + intervalMillis() / 2);

        int teams = 0;
        int total = 0;
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            int points = ProgressionTiers.awardPassiveResearch(team);
            if (points > 0) {
                teams++;
                total += points;
            }
        }
        LOGGER.info("[s3_progression_mod] Passive research: {} point(s) for {} team(s) with an active laboratory", total, teams);
    }
}
