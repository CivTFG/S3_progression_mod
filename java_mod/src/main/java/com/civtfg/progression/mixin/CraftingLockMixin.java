package com.civtfg.progression.mixin;

import com.civtfg.progression.stage.GatedItemEnforcer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pre-empts GatedItemEnforcer's tick-based sweep for vanilla-style crafting: cancels
 * taking a gated item out of a ResultSlot the instant it's clicked, instead of waiting up
 * to CHECK_INTERVAL_TICKS for the next sweep to strip it after the fact. Ported from
 * CivTFG-Progression's CraftingLockMixin (see anleitung_kiwi_mod.md) - reworked to check
 * against this mod's own gate list (ProgressionTiers.GATES via
 * GatedItemEnforcer.lockedMessage) instead of hardcoded items, so progression.json stays
 * the single source of truth.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class CraftingLockMixin {

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void s3progression$blockLockedCrafting(
            int slotId, int button, ClickType clickType, Player player, CallbackInfo ci
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotId < 0 || slotId >= menu.slots.size()) {
            return;
        }
        Slot slot = menu.getSlot(slotId);
        if (!(slot instanceof ResultSlot)) {
            return;
        }
        ItemStack result = slot.getItem();
        String message = GatedItemEnforcer.lockedMessage(serverPlayer, result);
        if (message != null) {
            serverPlayer.displayClientMessage(Component.literal(message), false);
            ci.cancel();
        }
    }
}
