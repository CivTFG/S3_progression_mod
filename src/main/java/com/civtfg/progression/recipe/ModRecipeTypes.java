package com.civtfg.progression.recipe;

import com.civtfg.progression.ProgressionMod;
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

    public static final RegistryObject<RecipeType<LaboratoryRecipe>> LABORATORY_TYPE =
            RECIPE_TYPES.register("laboratory", () -> RecipeType.simple(
                    new net.minecraft.resources.ResourceLocation(ProgressionMod.MOD_ID, "laboratory")));

    public static final RegistryObject<RecipeSerializer<LaboratoryRecipe>> LABORATORY_SERIALIZER =
            RECIPE_SERIALIZERS.register("laboratory", LaboratoryRecipe.Serializer::new);

    private ModRecipeTypes() {
    }
}
