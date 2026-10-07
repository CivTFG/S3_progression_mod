package com.civtfg.progression.registry;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.block.PrimitiveAssemblerBlock;
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
     * Five purely cosmetic variants of the same LaboratoryBlock class/behavior, each
     * researching a cumulative, growing set of science Ages via its
     * {@link LaboratoryBlock.LabTier} - see {@link LaboratoryBlock.LabTier} and
     * {@link com.civtfg.progression.blockentity.LaboratoryBlockEntity#getMatchingScience}.
     * The registry names "laboratory" and "advanced_laboratory" stay as their original
     * names (their display names are set in lang/en_us.json) rather than being renamed, so
     * existing world saves with one already placed don't break. Which variant gets to be
     * ACTIVE for a team is handled per team by ProgressionTiers.hasLaboratory/setHasLaboratory -
     * one functional lab per team in total; placing a higher-tier lab takes over from the
     * current one (see LaboratoryBlock#getStateForPlacement). (A sixth variant,
     * "quantum_laboratory", existed briefly for the now-removed IV research tier and was
     * deleted before ever shipping to a live server - if you find a stray reference to it
     * anywhere, it's dead and should be removed too.)
     */
    public static final RegistryObject<Block> LABORATORY = BLOCKS.register("laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 3.0f) // like wood: breakable by hand, faster with a GT wrench (forge:mineable/wrench)
                    .sound(SoundType.STONE)
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.PRIMITIVE));

    public static final RegistryObject<Block> INDUSTRIAL_LABORATORY = BLOCKS.register("industrial_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 3.0f) // like wood: breakable by hand, faster with a GT wrench (forge:mineable/wrench)
                    .sound(SoundType.STONE)
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.INDUSTRIAL));

    public static final RegistryObject<Block> ELECTRIC_LABORATORY = BLOCKS.register("electric_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 3.0f) // like wood: breakable by hand, faster with a GT wrench (forge:mineable/wrench)
                    .sound(SoundType.STONE)
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.ELECTRIC));

    public static final RegistryObject<Block> ADVANCED_LABORATORY = BLOCKS.register("advanced_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 3.0f) // like wood: breakable by hand, faster with a GT wrench (forge:mineable/wrench)
                    .sound(SoundType.STONE)
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.ADVANCED));

    public static final RegistryObject<Block> ELITE_LABORATORY = BLOCKS.register("elite_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 3.0f) // like wood: breakable by hand, faster with a GT wrench (forge:mineable/wrench)
                    .sound(SoundType.STONE)
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.ELITE));

    /** Unpowered assembler (LV Assembler look-alike) with its own recipe type - see PrimitiveAssemblerBlockEntity. */
    public static final RegistryObject<Block> PRIMITIVE_ASSEMBLER = BLOCKS.register("primitive_assembler",
            () -> new PrimitiveAssemblerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f, 3.0f) // same as the labs: by hand, faster with a GT wrench
                    .sound(SoundType.METAL)));
}
