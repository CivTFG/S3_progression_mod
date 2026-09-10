package com.civtfg.progression.stage;

import com.civtfg.progression.ProgressionMod;
import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Enforces {@link ProgressionTiers.Gate}s whose mechanism is "possession" - unlike the
 * "interaction"/"placement" gates in blocked_blocks.js (which only catch a player in the
 * act of using or placing a block), this scans every online player's inventory each
 * second and strips any gated item they aren't allowed to hold yet.
 *
 * This exists because block-placement/interaction gates can be bypassed once a player
 * has unlocked some means of placing blocks or acquiring items other than their own
 * direct action (automation, dispensers, etc.) - continuously enforcing "you may not
 * possess this item" closes that gap regardless of how the item was obtained.
 */
@Mod.EventBusSubscriber(modid = ProgressionMod.MOD_ID)
public final class GatedItemEnforcer {

    private static final int CHECK_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide() || player.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        for (ProgressionTiers.Gate gate : ProgressionTiers.GATES) {
            if (!"possession".equals(gate.mechanism())) {
                continue;
            }
            String stageId = ProgressionTiers.stageIdFor(gate.requiresTier());
            if (stageId == null || GameStageHelper.hasStage(player, stageId)) {
                continue;
            }
            if (stripGatedItems(player.getInventory(), gate.blocks())) {
                player.displayClientMessage(Component.literal(gate.message()), false);
            }
        }
    }

    /**
     * @return the message to show if {@code stack} is currently locked for {@code player}
     * (a "possession" gate whose required tier that player doesn't have yet), or
     * {@code null} if the stack is allowed. Shared by the tick-based sweep above and
     * {@link com.civtfg.progression.mixin.CraftingLockMixin}/
     * {@link com.civtfg.progression.mixin.CraftingLockScreenMixin} (which pre-empt taking a
     * gated item out of a vanilla-style crafting result slot the instant it's clicked,
     * instead of waiting up to {@link #CHECK_INTERVAL_TICKS} for the next sweep) - single
     * source of truth stays {@code progression.json} either way.
     */
    @Nullable
    public static String lockedMessage(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        for (ProgressionTiers.Gate gate : ProgressionTiers.GATES) {
            if (!"possession".equals(gate.mechanism())) {
                continue;
            }
            String stageId = ProgressionTiers.stageIdFor(gate.requiresTier());
            if (stageId == null || GameStageHelper.hasStage(player, stageId)) {
                continue;
            }
            for (String blockedId : gate.blocks()) {
                if (blockedId.equals(id)) {
                    return gate.message();
                }
            }
        }
        return null;
    }

    private static boolean stripGatedItems(Inventory inventory, String[] blockedIds) {
        boolean removedAny = false;
        removedAny |= stripFrom(inventory.items, blockedIds);
        removedAny |= stripFrom(inventory.armor, blockedIds);
        removedAny |= stripFrom(inventory.offhand, blockedIds);
        return removedAny;
    }

    private static boolean stripFrom(List<ItemStack> slots, String[] blockedIds) {
        boolean removedAny = false;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            for (String blockedId : blockedIds) {
                if (blockedId.equals(id)) {
                    slots.set(i, ItemStack.EMPTY);
                    removedAny = true;
                    break;
                }
            }
        }
        return removedAny;
    }

    private GatedItemEnforcer() {
    }
}
