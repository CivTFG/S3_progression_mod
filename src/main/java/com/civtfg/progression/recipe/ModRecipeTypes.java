package com.civtfg.progression.recipe;

import com.civtfg.progression.ProgressionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The "laboratory" recipe type itself is NOT registered in Java - it is created
 * by a KubeJS recipe schema (see kubejs/startup_scripts/laboratory_recipe_schema.js).
 * KubeJS registers that schema's RecipeType into the Forge recipe-type registry
 * under this id, so we look it up dynamically at runtime instead of holding a
 * compile-time reference.
 */
public final class ModRecipeTypes {

    public static final ResourceLocation LABORATORY_ID =
            new ResourceLocation(ProgressionMod.MOD_ID, "laboratory");

    private ModRecipeTypes() {
    }

    @SuppressWarnings("unchecked")
    public static RecipeType<Recipe<Container>> laboratory() {
        return (RecipeType<Recipe<Container>>) ForgeRegistries.RECIPE_TYPES.getValue(LABORATORY_ID);
    }
}
