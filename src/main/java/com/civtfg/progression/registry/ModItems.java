package com.civtfg.progression.registry;

import com.civtfg.progression.ProgressionMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ProgressionMod.MOD_ID);

    public static final RegistryObject<Item> LABORATORY_ITEM = ITEMS.register("laboratory",
            () -> new BlockItem(ModBlocks.LABORATORY.get(), new Item.Properties()));

    // If you want it in a creative tab, add it via a CreativeModeTabEvent.BuildContents
    // listener elsewhere, or put it in an existing tab through a datapack/KubeJS script:
    //   StartupEvents.registry('item', event => {}) is not needed for this, but
    //   ItemRegistryEventJS -> creative tab tagging can be done from KubeJS if preferred.
}
