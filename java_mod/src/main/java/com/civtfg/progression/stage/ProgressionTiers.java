package com.civtfg.progression.stage;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Ordered list of progression tiers/gamestages granted by KubeJS as each tier's
 * research threshold is crossed (see kubejs/startup_scripts/progression_listener.js).
 * Unlike the old single-track design, tiers here ARE required to be unlocked in order -
 * see {@link #canCraftTier} below, called from LaboratoryBlockEntity before a recipe is
 * even allowed to start progressing, so out-of-order recipes are rejected before their
 * items are consumed.
 *
 * There is no shared source of truth between the KubeJS and Java sides - keep this list
 * and that script's TIERS table in sync by hand (same keys, same thresholds).
 */
public final class ProgressionTiers {

    /** Same NBT key KubeJS writes the per-tier research compound under on the team. */
    public static final String RESEARCH_KEY = "s3_progression_mod:research";

    public record Tier(String key, String displayName, String stageId, int threshold) {
    }

    /** Order matters: index N requires index N-1's threshold to already be crossed. */
    public static final Tier[] TIERS = {
            new Tier("BRONZE", "Bronze Age", "bronze_unlocked", 3),
            new Tier("IRON", "Iron Age", "iron_unlocked", 3),
            new Tier("STEEL", "Steel Age", "steel_unlocked", 3),
            new Tier("STEAM", "Steam Age", "steam_unlocked", 3),
            new Tier("LV", "LV", "furnace_unlocked", 3),
            new Tier("MV", "MV", "mv_unlocked", 3),
            new Tier("HV", "HV", "hv_unlocked", 3),
            new Tier("EV", "EV", "ev_unlocked", 3),
            new Tier("IV", "IV", "iv_unlocked", 3),
    };

    @Nullable
    private static Tier find(String key) {
        for (Tier tier : TIERS) {
            if (tier.key().equals(key)) {
                return tier;
            }
        }
        return null;
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

    private ProgressionTiers() {
    }
}
