package com.civtfg.progression.mixin;

import com.civtfg.progression.stage.GatedItemEnforcer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-side mirror of CraftingLockMixin: blocks the take-out visually/instantly on the
 * client too, instead of relying on the server rejecting it and the client's own local
 * prediction briefly showing the item move before the correction syncs back. Uses the same
 * GatedItemEnforcer.lockedMessage check as the server side (not a separate/hardcoded test
 * check) so the client can never drift from the real gate config in progression.json.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class CraftingLockScreenMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void s3progression$blockLockedCraftingClient(
            Slot slot, int slotId, int button, ClickType clickType, CallbackInfo ci
    ) {
        if (!(slot instanceof ResultSlot)) {
            return;
        }
        ItemStack result = slot.getItem();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        String message = GatedItemEnforcer.lockedMessage(minecraft.player, result);
        if (message != null) {
            minecraft.player.displayClientMessage(Component.literal(message), true);
            ci.cancel();
        }
    }
}
