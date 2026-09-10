package com.civtfg.progression.stage;

import com.civtfg.progression.ProgressionMod;
import net.darkhax.gamestages.GameStageHelper;
import net.dries007.tfc.util.events.StartFireEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Closes a gate-bypass: TFC's fire-starting tools (the Firestarter, at least - possibly
 * others) don't ignite a block through the normal right-click interaction that
 * blocked_blocks.js's BlockEvents.rightClicked gates can see. Confirmed via decompiling
 * FirestarterItem: the initial right-click just starts a ~3.5s "charge" (Item#onUseTick),
 * which re-raycasts every tick and only posts TFC's own StartFireEvent once the charge
 * completes - decoupled from whatever block (if any) the player was looking at when they
 * started charging. A player can start charging while looking at empty air, walk up next
 * to a gated Bloomery/Blast Furnace, and light it the moment the charge finishes, with no
 * BlockEvents.rightClicked ever firing for that block at all.
 *
 * StartFireEvent is @Cancelable, and TFC's own handler for it
 * (net.dries007.tfc.ForgeEventHandler#onFireStart, which does the actual "become lit" logic
 * for e.g. Blast Furnace) is registered at the default EventPriority.NORMAL. Forge skips a
 * NORMAL-priority listener entirely once an earlier (higher-priority) listener has already
 * cancelled the event, so subscribing here at HIGHEST guarantees this always runs first and
 * pre-empts TFC's own ignition logic, regardless of mod-loading/registration order between
 * this mod and TFC (which KubeJS's own generic ForgeEvents.onEvent can't guarantee - it
 * always registers at NORMAL, see the KubeJS jar's ForgeEventWrapper#onEvent bytecode).
 * This is why this specific gate check lives in Java instead of blocked_blocks.js.
 */
@Mod.EventBusSubscriber(modid = ProgressionMod.MOD_ID)
public final class FireStartGateEnforcer {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onStartFire(StartFireEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        String blockId = String.valueOf(ForgeRegistries.BLOCKS.getKey(event.getState().getBlock()));

        for (ProgressionTiers.Gate gate : ProgressionTiers.GATES) {
            if (gate.entity() || !"interaction".equals(gate.mechanism()) || !contains(gate.blocks(), blockId)) {
                continue;
            }
            String stageId = ProgressionTiers.stageIdFor(gate.requiresTier());
            Player player = event.getPlayer();
            if (stageId == null || GameStageHelper.hasStage(player, stageId)) {
                continue;
            }
            event.setCanceled(true);
            player.displayClientMessage(Component.literal(gate.message()), false);
            return;
        }
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private FireStartGateEnforcer() {
    }
}
