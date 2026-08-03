package com.civtfg.progression.stage;

import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.world.entity.player.Player;

/**
 * Ordered list of gamestages granted by KubeJS as laboratory research thresholds are
 * crossed (see kubejs/startup_scripts/progression_listener.js). There is currently no
 * shared source of truth between the KubeJS and Java sides - keep this list and that
 * script's RESEARCH_THRESHOLD/FURNACE_STAGE constants in sync by hand.
 */
public final class ProgressionTiers {

    public record Tier(String stageId, String displayName) {
    }

    public static final Tier[] TIERS = {
            new Tier("furnace_unlocked", "Furnace")
    };

    /**
     * @return the display name of the highest tier {@code player} currently holds the
     * stage for, or {@code null} if they don't have any of the known tier stages yet.
     */
    public static String getCurrentTierName(Player player) {
        for (int i = TIERS.length - 1; i >= 0; i--) {
            if (GameStageHelper.hasStage(player, TIERS[i].stageId())) {
                return TIERS[i].displayName();
            }
        }
        return null;
    }

    private ProgressionTiers() {
    }
}
