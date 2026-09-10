package com.civtfg.progression.registry;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.block.LaboratoryBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ProgressionMod.MOD_ID);

    /**
     * Three purely cosmetic variants of the same LaboratoryBlock class/behavior - the
     * registry name "laboratory" stays as the original (its display name is now "Primitive
     * Laboratory", set in lang/en_us.json) rather than being renamed to "primitive_laboratory",
     * so existing world saves with one already placed don't break. Which variant gets to be
     * ACTIVE for a team is handled entirely by the existing per-team
     * ProgressionTiers.hasLaboratory/setHasLaboratory flag (not per-block), so registering
     * three separate instances of the same Block class is all that's needed for "only one
     * lab total, of any variant, can be active" - no extra logic required.
     */
    public static final RegistryObject<Block> LABORATORY = BLOCKS.register("laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> ADVANCED_LABORATORY = BLOCKS.register("advanced_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> QUANTUM_LABORATORY = BLOCKS.register("quantum_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));
}
