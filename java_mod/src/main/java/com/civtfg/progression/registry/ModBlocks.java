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
     * Six purely cosmetic variants of the same LaboratoryBlock class/behavior, each hard
     * -restricted (via its {@link LaboratoryBlock.LabTier}) to research only its own pair
     * (or singleton) of science Ages - see {@link LaboratoryBlock.LabTier} and
     * {@link com.civtfg.progression.blockentity.LaboratoryBlockEntity#getMatchingScience}.
     * The registry names "laboratory", "advanced_laboratory" and "quantum_laboratory" stay
     * as their original names (their display names are set in lang/en_us.json) rather than
     * being renamed, so existing world saves with one already placed don't break. Which
     * variant gets to be ACTIVE for a team is handled per-(team, tier) by
     * ProgressionTiers.hasLaboratory/setHasLaboratory - so a team can have up to one
     * functional lab of each of the 6 tiers active simultaneously.
     */
    public static final RegistryObject<Block> LABORATORY = BLOCKS.register("laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.PRIMITIVE));

    public static final RegistryObject<Block> INDUSTRIAL_LABORATORY = BLOCKS.register("industrial_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.INDUSTRIAL));

    public static final RegistryObject<Block> ELECTRIC_LABORATORY = BLOCKS.register("electric_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.ELECTRIC));

    public static final RegistryObject<Block> ADVANCED_LABORATORY = BLOCKS.register("advanced_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.ADVANCED));

    public static final RegistryObject<Block> ELITE_LABORATORY = BLOCKS.register("elite_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.ELITE));

    public static final RegistryObject<Block> QUANTUM_LABORATORY = BLOCKS.register("quantum_laboratory",
            () -> new LaboratoryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.5f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion(),
                    LaboratoryBlock.LabTier.QUANTUM));
}
