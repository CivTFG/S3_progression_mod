package com.civtfg.progression.stage;

import com.civtfg.progression.ProgressionMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Enforces "placement"/"gtceu_voltage_placement" gates for players. Runs on BOTH logical
 * sides on purpose: when only the server refused (the old KubeJS-only version), the client
 * had already predicted the placement and removed the item from its own copy of the
 * inventory, so the item looked gone while the server still had it. Forge fires
 * RightClickBlock client-side before that prediction and skips {@code ItemStack#useOn} when
 * useItem is DENY (verified via javap on MultiPlayerGameMode#performUseItemOn). Denying only
 * the item use - not cancelling the whole event - still lets the clicked block react, so
 * opening a chest while holding a gated machine keeps working. blocked_blocks.js keeps its
 * BlockEvents.placed backstop for placements that don't come from a player's right-click.
 */
@Mod.EventBusSubscriber(modid = ProgressionMod.MOD_ID)
public final class PlacementGateEnforcer {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        String message = ItemGates.placementLockedMessage(player, event.getItemStack());
        if (message == null) {
            return;
        }
        event.setUseItem(Event.Result.DENY);
        if (!player.level().isClientSide()) {
            player.displayClientMessage(Component.literal(message), false);
        }
    }

    private PlacementGateEnforcer() {
    }
}
