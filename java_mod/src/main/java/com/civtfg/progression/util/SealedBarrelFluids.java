package com.civtfg.progression.util;

import net.dries007.tfc.common.items.BarrelBlockItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Lets a sealed TFC barrel item be poured out even while a sealed barrel recipe is still
 * "active" in it. TFC's own item fluid handler ({@code BarrelBlockItem$BarrelItemStackInventory})
 * refuses every fill/drain as long as {@code BlockEntityTag} has a {@code "recipe"} key
 * (confirmed via javap on TFC 3.2.25: {@code canModify()} gates {@code BarrelInventory#drain/fill}).
 * In this pack that's practically every alcohol barrel - finished beer/cider/... still carries
 * an aging recipe (TFCAgedAlcohol's aged_*, TFG's vintage_*) - so buckets and GTCEU drums
 * worked on the Primitive Assembler but barrels didn't.
 *
 * Works on a copy with the recipe key removed, then lets TFC's own handler do the draining (it
 * rewrites {@code inventory}/{@code sealedTick} itself, and drops {@code BlockEntityTag}
 * entirely once the barrel is completely empty); the recipe key is put back only if something
 * is still left in the barrel for it to act on.
 */
public final class SealedBarrelFluids {

    private static final String BLOCK_ENTITY_TAG = "BlockEntityTag";
    private static final String RECIPE_KEY = "recipe";

    /**
     * @return the barrel stack after pouring as much as {@code target} accepts, or {@code null}
     * if {@code stack} isn't a single TFC barrel locked by an active recipe, or nothing could be
     * poured - callers then fall through to their normal handling.
     */
    @Nullable
    public static ItemStack tryEmptyInto(ItemStack stack, IFluidHandler target, @Nullable Player player) {
        if (!(stack.getItem() instanceof BarrelBlockItem) || stack.getCount() != 1) {
            return null;
        }
        CompoundTag blockEntityTag = stack.getTagElement(BLOCK_ENTITY_TAG);
        if (blockEntityTag == null || !blockEntityTag.contains(RECIPE_KEY)) {
            return null;
        }
        Tag recipe = blockEntityTag.get(RECIPE_KEY);

        ItemStack unlocked = stack.copy();
        unlocked.getOrCreateTagElement(BLOCK_ENTITY_TAG).remove(RECIPE_KEY);
        FluidActionResult result = FluidUtil.tryEmptyContainer(unlocked, target, Integer.MAX_VALUE, player, true);
        if (!result.isSuccess()) {
            return null;
        }

        ItemStack emptied = result.getResult();
        CompoundTag emptiedTag = emptied.getTagElement(BLOCK_ENTITY_TAG);
        if (emptiedTag != null) {
            emptiedTag.put(RECIPE_KEY, recipe.copy());
        }
        return emptied;
    }

    private SealedBarrelFluids() {
    }
}
