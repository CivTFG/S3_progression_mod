package com.civtfg.progression.stage;

import com.civtfg.progression.block.LaboratoryBlock;
import com.civtfg.progression.registry.ModScienceItems;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.FMLPaths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

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

    /** A research tier - its unlock threshold is NOT per tier but per team, see {@link #thresholdFor}. */
    public record Tier(String key, String displayName, String stageId) {
    }

    /**
     * progression.json's "teamSize" block: every tier needs
     * {@code round(baseThreshold + curveFactor * (c - 1)^curveExponent)} points, {@code c} being
     * the counted team size (1..{@code maxPlayers}) - 512 / 672 / 1024 / 1522 / 2400 / 2646 for
     * 1 / 2 / 5 / 10 / 20 / 23 players by default. Members offline for {@code inactiveAfterDays}
     * days or more don't count (0 = off) - see {@link PlayerActivity}.
     */
    public record TeamSize(int baseThreshold, double curveFactor, double curveExponent, int maxPlayers, int inactiveAfterDays) {
    }

    /**
     * progression.json's "labCraft" block: one Laboratory craft takes {@code referenceMinutes}
     * for a team whose threshold is {@code referenceThreshold}, and proportionally less for a
     * higher threshold (40 min x 512 / T) - so every team size needs about the same time per
     * tier. See {@link #labCraftTicks}.
     */
    public record LabCraftConfig(int referenceMinutes, int referenceThreshold) {
    }

    /**
     * progression.json's "passiveResearch" block: every {@code intervalMinutes} of server
     * uptime, each team with an active Laboratory gets {@code T / referenceThreshold} research
     * points (T = its threshold), the fraction carried over per team - see
     * {@link #passivePointsFor}, {@link #awardPassiveResearch} and {@link PassiveResearch}.
     */
    public record PassiveResearchConfig(int intervalMinutes, int referenceThreshold) {
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
    public record Gate(String requiresTier, String mechanism, boolean entity, String[] blocks, String voltage,
                       String[] exceptBlocks, String message) {
    }

    private record Config(String researchKey, String[] categories, Tier[] tiers, Gate[] gates, TeamSize teamSize,
                          LabCraftConfig labCraft, PassiveResearchConfig passiveResearch) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressionTiers.class);

    /** Used when progression.json has no (valid) "passiveResearch" block: T / 512 points every 40 minutes. */
    private static final PassiveResearchConfig DEFAULT_PASSIVE_RESEARCH = new PassiveResearchConfig(40, 512);

    /** Used when progression.json has no (valid) "labCraft" block: 40 minutes at a threshold of 512. */
    private static final LabCraftConfig DEFAULT_LAB_CRAFT = new LabCraftConfig(40, 512);

    /** Used when progression.json has no "teamSize" block: 512 + 160.3 x (c - 1)^0.8375, up to 23 players (2646). */
    private static final TeamSize DEFAULT_TEAM_SIZE = new TeamSize(512, 160.3, 0.8375, 23, 14);

    /** Fraction of a free research point carried over to the next interval (double) on the team. */
    private static final String PASSIVE_FRACTION_KEY = "s3_progression_mod:passive_fraction";

    /** Per-tier "unlocked" flags (compound of tier key -> boolean) on the team - unlocks are permanent. */
    private static final String UNLOCKED_KEY = "s3_progression_mod:unlocked";

    /**
     * Team size used for the threshold: rises immediately with the member count, falls by at
     * most one per real-time midnight (server clock) - see {@link #countedSize} and
     * {@link TeamSizeTracker}. {@link #COUNTED_SIZE_DAY_KEY} is the epoch day it was last
     * brought up to date, so missed midnights (server offline) are caught up.
     */
    private static final String COUNTED_SIZE_KEY = "s3_progression_mod:counted_size";
    private static final String COUNTED_SIZE_DAY_KEY = "s3_progression_mod:counted_size_day";

    /**
     * The fixed threshold of 0.7.x: a team that has no {@link #UNLOCKED_KEY} flags yet gets
     * them derived once from its research totals with the old rule (total > 1024).
     */
    private static final int LEGACY_THRESHOLD = 1024;

    /** Same NBT key KubeJS writes the per-tier research compound under on the team. */
    public static final String RESEARCH_KEY;

    /**
     * NBT key marking that a team already has a functional Laboratory - of ANY tier -
     * placed somewhere in their claim, and exactly where (a compound with "x"/"y"/"z" ints
     * and, since 0.8.0, a "dim" dimension id - not a boolean - so a decorative "out of
     * order" copy can tell the player where the real one is, and a newly placed higher-tier
     * lab can find and deactivate it). See {@link LaboratoryBlock}'s "out of order"
     * decorative-copy mechanic, which reads/writes this via {@link #hasLaboratory}/
     * {@link #getLaboratoryPos} and {@link #setHasLaboratory}/{@link #clearHasLaboratory}
     * to allow only the first Laboratory placed per team to be functional, regardless of
     * which of the 5 tiers it is - a team gets exactly one active lab total, not one per
     * tier (this was briefly one-per-tier, see Pitfall #16 and #17 - reverted because that
     * wasn't what the user actually wanted).
     */
    private static final String HAS_LABORATORY_KEY = "s3_progression_mod:has_laboratory";

    /** Science item category suffixes, e.g. "mining" - must match ModScienceItems.Category names lowercased. */
    public static final String[] CATEGORIES;

    /** Order matters: index N requires index N-1 to already be unlocked. */
    public static final Tier[] TIERS;

    public static final TeamSize TEAM_SIZE;

    public static final LabCraftConfig LAB_CRAFT;

    public static final PassiveResearchConfig PASSIVE_RESEARCH;

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
            TEAM_SIZE = validTeamSize(config.teamSize());
            LAB_CRAFT = validLabCraft(config.labCraft());
            PASSIVE_RESEARCH = validPassiveResearch(config.passiveResearch());
        } catch (IOException | JsonSyntaxException e) {
            throw new IllegalStateException(
                    "Failed to load " + path + " - this file is the single source of truth for progression "
                            + "tiers/stages/thresholds and must be deployed alongside the mod jar. "
                            + "See config_files/s3_progression_mod/progression.json in the mod repo.", e);
        }
    }

    /*
     * Gson fills fields missing from progression.json with 0, so a config still in the 0.8.0
     * format ("pointsPerPlayer"/"freePlayers", "points"/"playersPerExtraPoint", no "labCraft")
     * parses fine but would make every threshold 512 and every craft instant - fall back to the
     * built-in defaults for the missing values instead, and say so in the log.
     */

    private static TeamSize validTeamSize(@Nullable TeamSize teamSize) {
        if (teamSize == null) {
            return DEFAULT_TEAM_SIZE;
        }
        if (teamSize.baseThreshold() > 0 && teamSize.curveFactor() > 0 && teamSize.curveExponent() > 0
                && teamSize.maxPlayers() > 0) {
            return teamSize;
        }
        LOGGER.warn("[s3_progression_mod] progression.json \"teamSize\" lacks baseThreshold/curveFactor/curveExponent/maxPlayers "
                + "(0.8.0 format?) - using the defaults for those: {}", DEFAULT_TEAM_SIZE);
        // inactiveAfterDays is kept - it has the same meaning in both formats
        return new TeamSize(
                teamSize.baseThreshold() > 0 ? teamSize.baseThreshold() : DEFAULT_TEAM_SIZE.baseThreshold(),
                teamSize.curveFactor() > 0 ? teamSize.curveFactor() : DEFAULT_TEAM_SIZE.curveFactor(),
                teamSize.curveExponent() > 0 ? teamSize.curveExponent() : DEFAULT_TEAM_SIZE.curveExponent(),
                teamSize.maxPlayers() > 0 ? teamSize.maxPlayers() : DEFAULT_TEAM_SIZE.maxPlayers(),
                teamSize.inactiveAfterDays());
    }

    private static LabCraftConfig validLabCraft(@Nullable LabCraftConfig labCraft) {
        if (labCraft != null && labCraft.referenceMinutes() > 0 && labCraft.referenceThreshold() > 0) {
            return labCraft;
        }
        LOGGER.warn("[s3_progression_mod] progression.json has no valid \"labCraft\" block - using {}", DEFAULT_LAB_CRAFT);
        return DEFAULT_LAB_CRAFT;
    }

    private static PassiveResearchConfig validPassiveResearch(@Nullable PassiveResearchConfig passiveResearch) {
        // the 0.8.0 block (no referenceThreshold) meant 1 point every 20 minutes - not kept for T/512 points
        if (passiveResearch != null && passiveResearch.intervalMinutes() > 0 && passiveResearch.referenceThreshold() > 0) {
            return passiveResearch;
        }
        LOGGER.warn("[s3_progression_mod] progression.json has no valid \"passiveResearch\" block (0.8.0 format?) - using {}",
                DEFAULT_PASSIVE_RESEARCH);
        return DEFAULT_PASSIVE_RESEARCH;
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
     * Server-side only - {@code FTBChunksAPI.api().getManager()} throws an NPE if called
     * on the client (confirmed via a real crash from {@link com.civtfg.progression.block.LaboratoryBlock#getStateForPlacement},
     * which runs on both sides); every caller must check {@code level.isClientSide()} first.
     */
    @Nullable
    public static Team resolveTeam(Level level, BlockPos pos) {
        ChunkDimPos chunkDimPos = new ChunkDimPos(level, pos);
        ClaimedChunk claim = FTBChunksAPI.api().getManager().getChunk(chunkDimPos);
        return claim != null ? claim.getTeamData().getTeam() : null;
    }

    /**
     * @return whether {@code team} has unlocked {@code tierKey}, or {@code false} for an
     * unknown tier key. Unlocks are stored flags, not derived from the research total: the
     * threshold depends on the team size and can rise later, an unlocked tier stays unlocked.
     */
    public static boolean isUnlocked(Team team, String tierKey) {
        return find(tierKey) != null && unlockedFlags(team).getBoolean(tierKey);
    }

    /** Sets or clears {@code team}'s unlock flag for {@code tierKey} (stages are synced separately). */
    public static void setUnlocked(Team team, String tierKey, boolean unlocked) {
        CompoundTag flags = unlockedFlags(team);
        flags.putBoolean(tierKey, unlocked);
        team.getExtraData().put(UNLOCKED_KEY, flags);
        team.markDirty();
    }

    /**
     * Unlocks {@code tierKey} if {@code team}'s research total has reached its current
     * threshold (the threshold-th point unlocks it, not the one after) - called after a
     * Laboratory craft added points (progression_listener.js), so a threshold that dropped
     * below the total (a member left) only takes effect on the team's next craft.
     *
     * @return {@code true} only if the tier was unlocked by this call
     */
    public static boolean tryUnlock(Team team, String tierKey) {
        if (find(tierKey) == null || isUnlocked(team, tierKey)) {
            return false;
        }
        if (team.getExtraData().getCompound(RESEARCH_KEY).getInt(tierKey) < thresholdFor(team)) {
            return false;
        }
        setUnlocked(team, tierKey, true);
        return true;
    }

    /**
     * The team's unlock flags, created on first access from the research totals with the
     * 0.7.x rule ({@link #LEGACY_THRESHOLD}) so existing teams keep what they have.
     */
    private static CompoundTag unlockedFlags(Team team) {
        CompoundTag data = team.getExtraData();
        if (data.contains(UNLOCKED_KEY, Tag.TAG_COMPOUND)) {
            return data.getCompound(UNLOCKED_KEY);
        }
        CompoundTag research = data.getCompound(RESEARCH_KEY);
        CompoundTag flags = new CompoundTag();
        for (Tier tier : TIERS) {
            flags.putBoolean(tier.key(), research.getInt(tier.key()) > LEGACY_THRESHOLD);
        }
        data.put(UNLOCKED_KEY, flags);
        team.markDirty();
        return flags;
    }

    /** @return the points {@code team} needs for its next tier - see {@link #thresholdForSize}. */
    public static int thresholdFor(Team team) {
        return thresholdForSize(countedSize(team));
    }

    /**
     * @return the threshold for a counted team size {@code counted}:
     * {@code round(baseThreshold + curveFactor * (c - 1)^curveExponent)} with {@code c} clamped
     * to 1..{@code maxPlayers} (512..2646 with the default config).
     */
    public static int thresholdForSize(int counted) {
        int c = Math.max(1, Math.min(counted, TEAM_SIZE.maxPlayers()));
        return (int) Math.round(TEAM_SIZE.baseThreshold() + TEAM_SIZE.curveFactor() * Math.pow(c - 1, TEAM_SIZE.curveExponent()));
    }

    /**
     * @return how many ticks one Laboratory craft takes for {@code team}: {@code referenceMinutes}
     * scaled by {@code referenceThreshold / threshold} (48000 ticks = 40 min for a solo team,
     * 9288 for 23 players by default) - see {@link LabCraftConfig}.
     */
    public static int labCraftTicks(Team team) {
        return labCraftTicksFor(thresholdFor(team));
    }

    private static int labCraftTicksFor(int threshold) {
        double referenceTicks = LAB_CRAFT.referenceMinutes() * 60.0 * 20.0;
        return (int) Math.max(1, Math.round(referenceTicks * LAB_CRAFT.referenceThreshold() / (double) threshold));
    }

    /** @return the longest a Laboratory craft can take (at the lowest threshold, a team of one). */
    public static int maxLabCraftTicks() {
        return labCraftTicksFor(thresholdForSize(1));
    }

    /** @return the number of ACTIVE FTB Teams members (owner, officers, members - not allies or invites; inactive ones see PlayerActivity). */
    public static int memberCount(Team team) {
        int active = 0;
        for (java.util.UUID member : team.getMembers()) {
            if (PlayerActivity.isActive(member)) {
                active++;
            }
        }
        return active;
    }

    /**
     * @return the team size the threshold is based on - raised to the current member count
     * immediately (stored, so a member who joins and leaves again before midnight still
     * counts until then); lowered only by {@link #applyMidnightDecrease}.
     */
    public static int countedSize(Team team) {
        CompoundTag data = team.getExtraData();
        int members = memberCount(team);
        if (!data.contains(COUNTED_SIZE_KEY, Tag.TAG_INT) || data.getInt(COUNTED_SIZE_KEY) < members) {
            data.putInt(COUNTED_SIZE_KEY, members);
            if (!data.contains(COUNTED_SIZE_DAY_KEY, Tag.TAG_LONG)) {
                data.putLong(COUNTED_SIZE_DAY_KEY, LocalDate.now().toEpochDay());
            }
            team.markDirty();
        }
        return data.getInt(COUNTED_SIZE_KEY);
    }

    /**
     * Lowers {@code team}'s counted size by one per real-time midnight (server clock) passed
     * since it was last brought up to date - several at once after the server was offline -
     * but never below the current member count. Called by {@link TeamSizeTracker}.
     */
    public static void applyMidnightDecrease(Team team, long today) {
        int counted = countedSize(team);
        CompoundTag data = team.getExtraData();
        long day = data.getLong(COUNTED_SIZE_DAY_KEY);
        if (day >= today) {
            return;
        }
        long midnightsPassed = today - day;
        // countedSize() above already raised it to at least the member count
        data.putInt(COUNTED_SIZE_KEY, (int) Math.max(memberCount(team), counted - midnightsPassed));
        data.putLong(COUNTED_SIZE_DAY_KEY, today);
        team.markDirty();
    }

    /**
     * @return whether {@code team} is currently allowed to make progress on
     * {@code tierKey} - true for the first tier in sequence, otherwise only once the
     * immediately preceding tier is already unlocked, AND only as long as {@code tierKey}
     * itself isn't already unlocked. Without that last check, a team could keep crafting
     * (and consuming) a tier's science items forever after already crossing its threshold -
     * the preceding-tier check alone only ever guarded against jumping AHEAD, not against
     * continuing to feed an already-finished tier. Unknown tier keys are rejected.
     */
    public static boolean canCraftTier(Team team, String tierKey) {
        if (isUnlocked(team, tierKey)) {
            return false;
        }
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

    /** @return whether {@code team} already has a functional Laboratory (any tier) placed somewhere in their claim. */
    public static boolean hasLaboratory(Team team) {
        return team.getExtraData().contains(HAS_LABORATORY_KEY);
    }

    /**
     * @return the position of {@code team}'s one functional Laboratory, or {@code null} if
     * it doesn't have one or its position isn't known yet - used to tell a player standing
     * at a decorative "out of order" copy exactly where the real one is. Labs placed before
     * 0.5.0 stored a plain boolean here instead of a position compound; that reads as
     * unknown (not 0,0,0) until the active lab re-records itself, see
     * {@code LaboratoryBlockEntity#serverTick}.
     */
    @Nullable
    public static BlockPos getLaboratoryPos(Team team) {
        if (!team.getExtraData().contains(HAS_LABORATORY_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = team.getExtraData().getCompound(HAS_LABORATORY_KEY);
        return new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    }

    /**
     * @return the dimension id (e.g. "minecraft:overworld") of {@code team}'s functional
     * Laboratory, or {@code null} if unknown - records written before 0.8.0 stored only
     * x/y/z (read those as "same dimension as whoever asks", see {@link #getLaboratoryLevel}).
     */
    @Nullable
    public static String getLaboratoryDimension(Team team) {
        if (!team.getExtraData().contains(HAS_LABORATORY_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = team.getExtraData().getCompound(HAS_LABORATORY_KEY);
        return tag.contains("dim", Tag.TAG_STRING) ? tag.getString("dim") : null;
    }

    /**
     * @return the server level {@code team}'s functional Laboratory is in - {@code level}
     * itself for records without a dimension, {@code null} if the recorded dimension doesn't
     * exist (anymore) or this isn't a server level. Server-side only.
     */
    @Nullable
    public static Level getLaboratoryLevel(Team team, Level level) {
        String dim = getLaboratoryDimension(team);
        if (dim == null) {
            return level;
        }
        ResourceLocation id = ResourceLocation.tryParse(dim);
        if (id == null || level.getServer() == null) {
            return null;
        }
        return level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** @return whether {@code team}'s recorded functional Laboratory is exactly the one at {@code pos} in {@code level}. */
    public static boolean isLaboratoryAt(Team team, Level level, BlockPos pos) {
        return pos.equals(getLaboratoryPos(team)) && getLaboratoryLevel(team, level) == level;
    }

    /**
     * @return the {@link LaboratoryBlock.LabTier} of {@code team}'s functional Laboratory, or
     * {@code null} if unknown (no record, or a record written before 0.8.0 - filled in the
     * first time that lab ticks, see LaboratoryBlockEntity#serverTick).
     */
    @Nullable
    public static LaboratoryBlock.LabTier getLaboratoryTier(Team team) {
        if (!team.getExtraData().contains(HAS_LABORATORY_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = team.getExtraData().getCompound(HAS_LABORATORY_KEY);
        try {
            return tag.contains("tier", Tag.TAG_STRING) ? LaboratoryBlock.LabTier.valueOf(tag.getString("tier")) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Marks that {@code team}'s functional Laboratory, of tier {@code labTier}, is at {@code pos} in {@code level} - see {@link #hasLaboratory}. */
    public static void setHasLaboratory(Team team, Level level, BlockPos pos, LaboratoryBlock.LabTier labTier) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putString("dim", level.dimension().location().toString());
        tag.putString("tier", labTier.name());
        team.getExtraData().put(HAS_LABORATORY_KEY, tag);
        team.markDirty();
    }

    /** Clears {@code team}'s functional-Laboratory flag - see {@link #hasLaboratory}. */
    public static void clearHasLaboratory(Team team) {
        team.getExtraData().remove(HAS_LABORATORY_KEY);
        team.markDirty();
    }

    /**
     * @return the free research points {@code team} gets per interval: its threshold divided by
     * {@code referenceThreshold} - 1.00 / 1.31 / 2.00 / 2.97 / 5.17 for 1 / 2 / 5 / 10 / 23 players
     * by default. Fractions are carried over per team, see {@link #awardPassiveResearch}.
     */
    public static double passivePointsFor(Team team) {
        return thresholdFor(team) / (double) PASSIVE_RESEARCH.referenceThreshold();
    }

    /**
     * Adds {@link #passivePointsFor} to {@code team}'s carried-over fraction and credits the
     * whole points of it to the team's current research tier, if the team has an active
     * Laboratory that can research that tier (by its recorded {@link LaboratoryBlock.LabTier}).
     * Never unlocks the tier itself - reaching the threshold this way unlocks on the team's next
     * Laboratory craft, with the usual messages (user's choice).
     *
     * @return the points added, 0 if none
     */
    public static int awardPassiveResearch(Team team) {
        LaboratoryBlock.LabTier labTier = getLaboratoryTier(team);
        Progress progress = labTier != null ? currentProgress(team) : null;
        if (progress == null) {
            return 0;
        }
        ModScienceItems.Age age;
        try {
            age = ModScienceItems.Age.valueOf(progress.tierKey());
        } catch (IllegalArgumentException e) {
            return 0;
        }
        if (!labTier.allows(age)) {
            return 0;
        }
        CompoundTag data = team.getExtraData();
        double owed = data.getDouble(PASSIVE_FRACTION_KEY) + passivePointsFor(team);
        int points = (int) Math.floor(owed);
        data.putDouble(PASSIVE_FRACTION_KEY, owed - points);
        CompoundTag research = data.getCompound(RESEARCH_KEY);
        research.putInt(progress.tierKey(), research.getInt(progress.tierKey()) + points);
        data.put(RESEARCH_KEY, research);
        team.markDirty();
        return points;
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
                return new Progress(tier.key(), tier.displayName(), research.getInt(tier.key()), thresholdFor(team));
            }
        }
        return null;
    }

    private ProgressionTiers() {
    }
}
