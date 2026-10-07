package com.civtfg.progression.stage;

import com.civtfg.progression.ProgressionMod;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Our own "last seen" log per player (world saved data {@value #DATA_NAME}): updated on login,
 * logout and once a minute for everyone online. A player who has been offline for
 * {@code teamSize.inactiveAfterDays} days (progression.json, 0 = off) is inactive and no longer
 * counts towards their team's size ({@link ProgressionTiers#memberCount}) - like a player who
 * left: the counted size then drops by one per midnight, and logging in again counts them
 * immediately. On the first start with this feature, last-seen times are imported once from
 * FTB Essentials ({@code world/ftbessentials/playerdata/<uuid>.snbt}, {@code last_seen.time});
 * every other team member starts at that moment.
 */
@Mod.EventBusSubscriber(modid = ProgressionMod.MOD_ID)
public final class PlayerActivity {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlayerActivity.class);
    private static final String DATA_NAME = "s3_progression_mod_player_activity";
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
    private static final Pattern FTBE_LAST_SEEN = Pattern.compile("last_seen\\s*:\\s*\\{[^}]*?\\btime\\s*:\\s*(\\d+)L");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static int ticks = 0;

    private PlayerActivity() {
    }

    /** The saved data: last-seen millis per player, plus whether the one-time import ran. */
    private static final class Data extends SavedData {
        final Map<UUID, Long> lastSeen = new HashMap<>();
        boolean imported = false;

        static Data load(CompoundTag tag) {
            Data data = new Data();
            data.imported = tag.getBoolean("imported");
            CompoundTag players = tag.getCompound("last_seen");
            for (String key : players.getAllKeys()) {
                try {
                    data.lastSeen.put(UUID.fromString(key), players.getLong(key));
                } catch (IllegalArgumentException ignored) {
                    // not a UUID - skip
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("imported", imported);
            CompoundTag players = new CompoundTag();
            lastSeen.forEach((id, time) -> players.putLong(id.toString(), time));
            tag.put("last_seen", players);
            return tag;
        }

        void seen(UUID id, long time) {
            lastSeen.put(id, time);
            setDirty();
        }
    }

    @Nullable
    private static Data data(@Nullable MinecraftServer server) {
        if (server == null) {
            return null;
        }
        return server.overworld().getDataStorage().computeIfAbsent(Data::load, Data::new, DATA_NAME);
    }

    private static long inactiveAfterMillis() {
        return ProgressionTiers.TEAM_SIZE.inactiveAfterDays() * DAY_MILLIS;
    }

    /**
     * @return whether {@code player} counts towards their team's size: online, or seen within the
     * last {@code inactiveAfterDays} days (always true if that is 0 or no server is running).
     */
    public static boolean isActive(UUID player) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Data data = data(server);
        if (data == null || inactiveAfterMillis() <= 0 || server.getPlayerList().getPlayer(player) != null) {
            return true;
        }
        Long seen = data.lastSeen.get(player);
        if (seen == null) {
            // never logged since 0.8.0 and not imported: start the clock now
            data.seen(player, System.currentTimeMillis());
            return true;
        }
        return System.currentTimeMillis() - seen < inactiveAfterMillis();
    }

    /** @return when {@code player} was last seen (epoch millis), or {@code null} if unknown. */
    @Nullable
    public static Long lastSeen(UUID player) {
        Data data = data(ServerLifecycleHooks.getCurrentServer());
        return data != null ? data.lastSeen.get(player) : null;
    }

    /** @return how many of {@code team}'s members (owner/officers/members) are inactive. */
    public static int inactiveCount(Team team) {
        int inactive = 0;
        for (UUID member : team.getMembers()) {
            if (!isActive(member)) {
                inactive++;
            }
        }
        return inactive;
    }

    /**
     * @return one line per member of {@code team} - name, online / last seen (server time zone)
     * and active/inactive - for {@code /progression members}.
     */
    public static List<String> describeMembers(Team team) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<String> lines = new ArrayList<>();
        for (UUID member : team.getMembers()) {
            String name = server == null ? member.toString()
                    : server.getProfileCache().get(member).map(profile -> profile.getName()).orElse(member.toString());
            String status;
            if (server != null && server.getPlayerList().getPlayer(member) != null) {
                status = "online";
            } else {
                Long seen = lastSeen(member);
                String when = seen == null ? "never" : DATE.format(Instant.ofEpochMilli(seen));
                status = "last seen " + when + (isActive(member) ? "" : " - INACTIVE");
            }
            lines.add(name + ": " + status);
        }
        return lines;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Data data = data(player.getServer());
            if (data != null) {
                data.seen(player.getUUID(), System.currentTimeMillis());
            }
            // back from inactivity counts immediately, like a join
            FTBTeamsAPI.api().getManager().getTeamForPlayer(player).ifPresent(ProgressionTiers::countedSize);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Data data = data(player.getServer());
            if (data != null) {
                data.seen(player.getUUID(), System.currentTimeMillis());
            }
        }
    }

    /** Keeps online players' last-seen current, so a crash without logout still leaves a recent time. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 20 * 60) {
            return;
        }
        ticks = 0;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Data data = data(server);
        if (data == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            data.seen(player.getUUID(), now);
        }
    }

    /** One-time import from FTB Essentials, then every still-unknown team member starts now. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Data data = data(server);
        if (data == null || data.imported) {
            return;
        }
        int imported = 0;
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("ftbessentials").resolve("playerdata");
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    String fileName = file.getFileName().toString();
                    if (!fileName.endsWith(".snbt")) {
                        continue;
                    }
                    try {
                        UUID id = UUID.fromString(fileName.substring(0, fileName.length() - ".snbt".length()));
                        Matcher m = FTBE_LAST_SEEN.matcher(Files.readString(file, StandardCharsets.UTF_8));
                        if (m.find()) {
                            data.lastSeen.merge(id, Long.parseLong(m.group(1)), Math::max);
                            imported++;
                        }
                    } catch (IllegalArgumentException | IOException e) {
                        LOGGER.warn("[s3_progression_mod] Couldn't import last-seen time from {}: {}", file, e.toString());
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("[s3_progression_mod] Couldn't read {}: {}", dir, e.toString());
            }
        }
        long now = System.currentTimeMillis();
        int started = 0;
        if (FTBTeamsAPI.api().isManagerLoaded()) {
            for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
                for (UUID member : team.getMembers()) {
                    if (data.lastSeen.putIfAbsent(member, now) == null) {
                        started++;
                    }
                }
            }
        }
        data.imported = true;
        data.setDirty();
        LOGGER.info("[s3_progression_mod] Player activity: imported {} last-seen time(s) from FTB Essentials, {} other member(s) start now",
                imported, started);
    }
}
