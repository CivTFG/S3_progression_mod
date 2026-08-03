package com.civtfg.progression.registry;

import com.civtfg.progression.ProgressionMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ProgressionMod.MOD_ID);

    public static final RegistryObject<Item> LABORATORY_ITEM = ITEMS.register("laboratory",
            () -> new BlockItem(ModBlocks.LABORATORY.get(), new Item.Properties()));
}
