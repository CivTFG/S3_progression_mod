package com.civtfg.progression.registry;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.blockentity.LaboratoryBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ProgressionMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<LaboratoryBlockEntity>> LABORATORY =
            BLOCK_ENTITIES.register("laboratory", () -> BlockEntityType.Builder.of(
                    LaboratoryBlockEntity::new, ModBlocks.LABORATORY.get()).build(null));
}
