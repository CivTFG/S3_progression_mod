package com.civtfg.progression.registry;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.recipe.PrimitiveAssemblerRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, ProgressionMod.MOD_ID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ProgressionMod.MOD_ID);

    public static final RegistryObject<RecipeType<PrimitiveAssemblerRecipe>> PRIMITIVE_ASSEMBLER =
            RECIPE_TYPES.register("primitive_assembler",
                    () -> RecipeType.simple(new ResourceLocation(ProgressionMod.MOD_ID, "primitive_assembler")));

    public static final RegistryObject<RecipeSerializer<PrimitiveAssemblerRecipe>> PRIMITIVE_ASSEMBLER_SERIALIZER =
            RECIPE_SERIALIZERS.register("primitive_assembler", PrimitiveAssemblerRecipe.Serializer::new);

    private ModRecipeTypes() {
    }
}
