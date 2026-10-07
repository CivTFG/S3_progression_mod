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
     * progression.json's "teamSize" block: every tier needs {@code baseThreshold} points for
     * up to {@code freePlayers} counted members, plus {@code pointsPerPlayer} for each counted
     * member beyond that, counting at most {@code maxPlayers} members. Members offline for
     * {@code inactiveAfterDays} days or more don't count (0 = off) - see {@link PlayerActivity}.
     */
    public record TeamSize(int baseThreshold, int pointsPerPlayer, int freePlayers, int maxPlayers, int inactiveAfterDays) {
    }

    /**
     * progression.json's "passiveResearch" block: every {@code intervalMinutes} of server
     * uptime, each team with an active Laboratory gets {@code points} research points, plus one
     * more per full {@code playersPerExtraPoint} counted members (0 = no extra points) - see
     * {@link #passivePointsFor}, {@link #awardPassiveResearch} and {@link PassiveResearch}.
     */
    public record PassiveResearchConfig(int intervalMinutes, int points, int playersPerExtraPoint) {
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
                          PassiveResearchConfig passiveResearch) {
    }

    /** Used when progression.json has no "passiveResearch" block: 1 point every 20 minutes, +1 per 10 players. */
    private static final PassiveResearchConfig DEFAULT_PASSIVE_RESEARCH = new PassiveResearchConfig(20, 1, 10);

    /** Used when progression.json has no "teamSize" block: 512 for 1-2 players, +128 each, up to 23 players (3200). */
    private static final TeamSize DEFAULT_TEAM_SIZE = new TeamSize(512, 128, 2, 23, 14);

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
            TEAM_SIZE = config.teamSize() != null ? config.teamSize() : DEFAULT_TEAM_SIZE;
            PASSIVE_RESEARCH = config.passiveResearch() != null ? config.passiveResearch() : DEFAULT_PASSIVE_RESEARCH;
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

    /**
     * @return the points {@code team} needs for its next tier: {@code baseThreshold} for up
     * to {@code freePlayers} counted members, {@code pointsPerPlayer} more per member beyond
     * that, at most {@code maxPlayers} counted (512..3200 with the default config).
     */
    public static int thresholdFor(Team team) {
        int counted = Math.min(countedSize(team), TEAM_SIZE.maxPlayers());
        return TEAM_SIZE.baseThreshold() + TEAM_SIZE.pointsPerPlayer() * Math.max(0, counted - TEAM_SIZE.freePlayers());
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
     * @return the free research points {@code team} gets per interval: {@code points} plus one per
     * full {@code playersPerExtraPoint} of its counted size (capped at {@code maxPlayers}, the same
     * size the threshold uses) - 1 / 2 / 3 points for 1-9 / 10-19 / 20-23 players by default.
     */
    public static int passivePointsFor(Team team) {
        int perExtra = PASSIVE_RESEARCH.playersPerExtraPoint();
        int counted = Math.min(countedSize(team), TEAM_SIZE.maxPlayers());
        return PASSIVE_RESEARCH.points() + (perExtra > 0 ? counted / perExtra : 0);
    }

    /**
     * Adds {@link #passivePointsFor} points to {@code team}'s current research tier if the team
     * has an active Laboratory that can research that tier (by its recorded
     * {@link LaboratoryBlock.LabTier}). Never unlocks the tier itself - reaching the threshold this
     * way unlocks on the team's next Laboratory craft, with the usual messages (user's choice).
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
        int points = passivePointsFor(team);
        CompoundTag data = team.getExtraData();
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
