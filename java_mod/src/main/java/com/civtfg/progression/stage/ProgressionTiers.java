package com.civtfg.progression.stage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ordered list of progression tiers/gamestages granted by KubeJS as each tier's
 * research threshold is crossed (see kubejs/startup_scripts/progression_listener.js).
 * Unlike the old single-track design, tiers here ARE required to be unlocked in order -
 * see {@link #canCraftTier} below, called from LaboratoryBlockEntity before a recipe is
 * even allowed to start progressing, so out-of-order recipes are rejected before their
 * items are consumed.
 *
 * Loaded from config/s3_progression_mod/progression.json (repo copy tracked at
 * config_files/s3_progression_mod/progression.json), which is the single source of
 * truth for tier order/thresholds/stage ids shared by this class AND every KubeJS
 * script that used to hand-copy the same table (progression_listener.js,
 * progression_commands.js, blocked_blocks.js). Edit that one file to change
 * tier/stage/threshold data - no Java or JS changes required.
 *
 * KubeJS scripts can't read this file directly - KubeJS's own ClassFilter denies the
 * entire java.io/java.nio packages by default (sandboxing scripts away from arbitrary
 * filesystem access), so `Java.loadClass('java.nio.file.Files')` always throws
 * "Class is not allowed by class filter!". {@link #rawJson()} below is the workaround:
 * scripts call this (unrestricted, like any other mod class) to get the bytes, then
 * JSON.parse it themselves - this class never hands them parsed Tier objects, so it's
 * still the JS side doing its own independent parsing of the same file, not a Java-side
 * API bridge.
 */
public final class ProgressionTiers {

    public record Tier(String key, String displayName, String stageId, int threshold) {
    }

    /** The tier a team is actively accumulating research toward, and how far along it is. */
    public record Progress(String tierKey, String displayName, int current, int threshold) {
    }

    /**
     * One gating (multi-)block/item per age transition - see blocked_blocks.js for the
     * "interaction"/"placement" mechanisms (KubeJS BlockEvents), and
     * {@link com.civtfg.progression.stage.GatedItemEnforcer} for "possession" (Java-side,
     * since gating by block-placement/interaction alone can be bypassed once a player has
     * unlocked automation capable of placing blocks or acquiring items without the
     * matching player action ever firing).
     */
    public record Gate(String requiresTier, String mechanism, boolean entity, String[] blocks, String message) {
    }

    private record Config(String researchKey, String[] categories, Tier[] tiers, Gate[] gates) {
    }

    /** Same NBT key KubeJS writes the per-tier research compound under on the team. */
    public static final String RESEARCH_KEY;

    /** Science item category suffixes, e.g. "mining" - must match ModScienceItems.Category names lowercased. */
    public static final String[] CATEGORIES;

    /** Order matters: index N requires index N-1's threshold to already be crossed. */
    public static final Tier[] TIERS;

    /** One gating (multi-)block/item per age transition - see {@link Gate}. */
    public static final Gate[] GATES;

    private static final String RAW_JSON;

    static {
        Path path = FMLPaths.CONFIGDIR.get().resolve("s3_progression_mod").resolve("progression.json");
        try {
            RAW_JSON = Files.readString(path, StandardCharsets.UTF_8);
            Gson gson = new GsonBuilder().create();
            Config config = gson.fromJson(RAW_JSON, Config.class);
            if (config == null || config.tiers() == null || config.tiers().length == 0) {
                throw new IllegalStateException("progression.json parsed but has no tiers: " + path);
            }
            RESEARCH_KEY = config.researchKey();
            CATEGORIES = config.categories();
            TIERS = config.tiers();
            GATES = config.gates() != null ? config.gates() : new Gate[0];
        } catch (IOException | JsonSyntaxException e) {
            throw new IllegalStateException(
                    "Failed to load " + path + " - this file is the single source of truth for progression "
                            + "tiers/stages/thresholds and must be deployed alongside the mod jar. "
                            + "See config_files/s3_progression_mod/progression.json in the mod repo.", e);
        }
    }

    /** Raw contents of progression.json, for KubeJS scripts to JSON.parse themselves (see class javadoc). */
    public static String rawJson() {
        return RAW_JSON;
    }

    @Nullable
    private static Tier find(String key) {
        for (Tier tier : TIERS) {
            if (tier.key().equals(key)) {
                return tier;
            }
        }
        return null;
    }

    public static boolean hasTier(String key) {
        return find(key) != null;
    }

    /** @return the gamestage id that tier {@code key} grants once unlocked, or {@code null} for an unknown key. */
    @Nullable
    public static String stageIdFor(String key) {
        Tier tier = find(key);
        return tier != null ? tier.stageId() : null;
    }

    /**
     * @return the team claiming the chunk at {@code pos}, or {@code null} if unclaimed.
     */
    @Nullable
    public static Team resolveTeam(Level level, BlockPos pos) {
        ChunkDimPos chunkDimPos = new ChunkDimPos(level, pos);
        ClaimedChunk claim = FTBChunksAPI.api().getManager().getChunk(chunkDimPos);
        return claim != null ? claim.getTeamData().getTeam() : null;
    }

    /**
     * @return whether {@code team} has already crossed {@code tierKey}'s own threshold
     * (i.e. that tier is fully unlocked), or {@code false} for an unknown tier key.
     */
    public static boolean isUnlocked(Team team, String tierKey) {
        Tier tier = find(tierKey);
        if (tier == null) {
            return false;
        }
        CompoundTag research = team.getExtraData().getCompound(RESEARCH_KEY);
        return research.getInt(tierKey) > tier.threshold();
    }

    /**
     * @return whether {@code team} is currently allowed to make progress on
     * {@code tierKey} - true for the first tier in sequence, otherwise only once the
     * immediately preceding tier is already unlocked. Unknown tier keys are rejected.
     */
    public static boolean canCraftTier(Team team, String tierKey) {
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].key().equals(tierKey)) {
                return i == 0 || isUnlocked(team, TIERS[i - 1].key());
            }
        }
        return false;
    }

    /**
     * Bronze Age is the implicit baseline every team starts in - nobody needs to craft
     * anything to "be in" Bronze Age. Completing a tier's research (crossing its
     * threshold, holding its stageId) is what moves a team INTO the next tier, so
     * holding TIERS[i]'s stage means the team is now actually in TIERS[i + 1].
     *
     * @return the display name of whichever tier {@code player}'s team is currently in,
     * or "Everything" once the last tier's research is complete.
     */
    public static String getCurrentTierName(Player player) {
        for (int i = TIERS.length - 1; i >= 0; i--) {
            if (GameStageHelper.hasStage(player, TIERS[i].stageId())) {
                return i + 1 < TIERS.length ? TIERS[i + 1].displayName() : "Everything";
            }
        }
        return TIERS[0].displayName();
    }

    /**
     * @return {@code team}'s progress on whichever tier it's currently accumulating
     * research toward (the first not-yet-unlocked tier in order), or {@code null} once
     * every tier is unlocked.
     */
    @Nullable
    public static Progress currentProgress(Team team) {
        CompoundTag research = team.getExtraData().getCompound(RESEARCH_KEY);
        for (Tier tier : TIERS) {
            if (!isUnlocked(team, tier.key())) {
                return new Progress(tier.key(), tier.displayName(), research.getInt(tier.key()), tier.threshold());
            }
        }
        return null;
    }

    private ProgressionTiers() {
    }
}
