package com.civtfg.progression.recipe;

import com.civtfg.progression.ProgressionMod;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * A plain Forge recipe (no KubeJS involvement) - matches an unordered set of up
 * to 5 ingredients (shapeless) against the Laboratory's 5 slots, has no output
 * item, and carries two extra fields read straight off the recipe json: "tier"
 * (which progression tier this recipe's "value" counts toward - e.g. "LV", "MV",
 * "HV" - tiers are independent tracks, a tier's counter only moves via its own
 * recipes) and "value" (how much that tier's counter advances on a successful craft).
 *
 * Future recipes are added purely as datapack json files under
 * data/&lt;namespace&gt;/recipes/, no Java or KubeJS changes required, e.g.:
 *
 * {
 *   "type": "s3_progression_mod:laboratory",
 *   "ingredients": [
 *     { "item": "minecraft:stick" },
 *     { "item": "minecraft:stick" },
 *     { "item": "minecraft:stick" },
 *     { "item": "minecraft:stick" },
 *     { "item": "minecraft:stick" }
 *   ],
 *   "tier": "LV",
 *   "value": 1
 * }
 */
public class LaboratoryRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final NonNullList<Ingredient> ingredients;
    private final String tier;
    private final int value;

    public LaboratoryRecipe(ResourceLocation id, NonNullList<Ingredient> ingredients, String tier, int value) {
        this.id = id;
        this.ingredients = ingredients;
        this.tier = tier;
        this.value = value;
    }

    public String getTier() {
        return tier;
    }

    public int getValue() {
        return value;
    }

    @Override
    public boolean matches(Container container, Level level) {
        List<Ingredient> remaining = new ArrayList<>(ingredients);

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            boolean matchedThisStack = false;
            for (int i = 0; i < remaining.size(); i++) {
                if (remaining.get(i).test(stack)) {
                    remaining.remove(i);
                    matchedThisStack = true;
                    break;
                }
            }
            if (!matchedThisStack) {
                return false;
            }
        }

        return remaining.isEmpty();
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        // Hides this from the recipe book / any generic "crafting" recipe listing -
        // this isn't a player-crafted-in-a-grid recipe, it's the Laboratory's own thing.
        return true;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.LABORATORY_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.LABORATORY_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<LaboratoryRecipe> {

        @Override
        public LaboratoryRecipe fromJson(ResourceLocation id, com.google.gson.JsonObject json) {
            com.google.gson.JsonArray jsonIngredients = net.minecraft.util.GsonHelper.getAsJsonArray(json, "ingredients");
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (int i = 0; i < jsonIngredients.size(); i++) {
                Ingredient ingredient = Ingredient.fromJson(jsonIngredients.get(i));
                if (!ingredient.isEmpty()) {
                    ingredients.add(ingredient);
                }
            }

            String tier = net.minecraft.util.GsonHelper.getAsString(json, "tier");
            int value = net.minecraft.util.GsonHelper.getAsInt(json, "value", 1);

            return new LaboratoryRecipe(id, ingredients, tier, value);
        }

        @Override
        public LaboratoryRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.withSize(count, Ingredient.EMPTY);
            for (int i = 0; i < count; i++) {
                ingredients.set(i, Ingredient.fromNetwork(buffer));
            }
            String tier = buffer.readUtf();
            int value = buffer.readVarInt();
            return new LaboratoryRecipe(id, ingredients, tier, value);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, LaboratoryRecipe recipe) {
            buffer.writeVarInt(recipe.ingredients.size());
            for (Ingredient ingredient : recipe.ingredients) {
                ingredient.toNetwork(buffer);
            }
            buffer.writeUtf(recipe.tier);
            buffer.writeVarInt(recipe.value);
        }
    }
}
