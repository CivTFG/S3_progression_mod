package com.civtfg.progression.stage;

import com.civtfg.progression.ProgressionMod;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDate;

/**
 * Keeps every team's counted size ({@link ProgressionTiers#countedSize}), which the research
 * threshold is based on, up to date:
 * <ul>
 *   <li>a player joining a party raises it immediately (also done lazily on every read, this
 *       just makes sure a join is recorded even if the player leaves before anything reads it);</li>
 *   <li>once per real-time midnight (server clock, {@link LocalDate#now()}) every team whose
 *       counted size is above its member count loses one - missed midnights while the server
 *       was offline are caught up on the first check after startup.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ProgressionMod.MOD_ID)
public final class TeamSizeTracker {

    /** How often the date is checked - a minute's delay after midnight doesn't matter. */
    private static final int CHECK_INTERVAL_TICKS = 20 * 60;

    private static boolean joinListenerRegistered = false;
    private static long lastProcessedDay = Long.MIN_VALUE;
    private static int ticks = 0;

    private TeamSizeTracker() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!joinListenerRegistered) {
            joinListenerRegistered = true;
            TeamEvent.PLAYER_JOINED_PARTY.register(joined -> ProgressionTiers.countedSize(joined.getTeam()));
        }
        lastProcessedDay = Long.MIN_VALUE; // new world/server: check (and catch up) right away
        ticks = CHECK_INTERVAL_TICKS;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < CHECK_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        long today = LocalDate.now().toEpochDay();
        if (today == lastProcessedDay || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        lastProcessedDay = today;
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            ProgressionTiers.applyMidnightDecrease(team, today);
        }
    }
}
