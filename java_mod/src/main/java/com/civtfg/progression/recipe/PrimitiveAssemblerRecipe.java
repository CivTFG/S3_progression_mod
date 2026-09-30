package com.civtfg.progression.recipe;

import com.civtfg.progression.registry.ModRecipeTypes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A recipe for the Primitive Assembler block: up to {@link #MAX_INPUTS} item inputs (each an
 * ingredient + count), an optional fluid input, one item output and a duration in ticks.
 * No energy cost - the machine doesn't use power.
 *
 * <pre>
 * {
 *   "type": "s3_progression_mod:primitive_assembler",
 *   "inputs": [ { "ingredient": { "item": "minecraft:iron_ingot" }, "count": 2 },
 *               { "tag": "forge:plates/copper" } ],          // shorthand: ingredient with optional "count"
 *   "fluid_input": { "fluid": "gtceu:soldering_alloy", "amount": 144 },   // or { "tag": "...", "amount": n }
 *   "output": { "item": "minecraft:piston", "count": 1 },
 *   "duration": 100
 * }
 * </pre>
 */
public class PrimitiveAssemblerRecipe implements Recipe<Container> {

    public static final int MAX_INPUTS = 9;

    public record ItemInput(Ingredient ingredient, int count) {
    }

    /** A fluid requirement by fluid id or fluid tag, plus a minimum amount in mB. */
    public record FluidInput(@Nullable Fluid fluid, @Nullable TagKey<Fluid> tag, int amount) {
        public boolean test(FluidStack stack) {
            if (stack.isEmpty() || stack.getAmount() < amount) {
                return false;
            }
            return fluid != null ? stack.getFluid() == fluid : stack.getFluid().is(tag);
        }
    }

    private final ResourceLocation id;
    private final List<ItemInput> inputs;
    @Nullable
    private final FluidInput fluidInput;
    private final ItemStack output;
    private final int duration;

    public PrimitiveAssemblerRecipe(ResourceLocation id, List<ItemInput> inputs, @Nullable FluidInput fluidInput,
                                ItemStack output, int duration) {
        this.id = id;
        this.inputs = inputs;
        this.fluidInput = fluidInput;
        this.output = output;
        this.duration = duration;
    }

    public List<ItemInput> getInputs() {
        return inputs;
    }

    @Nullable
    public FluidInput getFluidInput() {
        return fluidInput;
    }

    public ItemStack getOutput() {
        return output;
    }

    public int getDuration() {
        return duration;
    }

    /**
     * Whether the machine's input slots (first {@code inputSlots} slots of the handler) and
     * tank satisfy this recipe. Extra unrelated items in other slots are fine; two inputs
     * are never allowed to count the same item twice.
     */
    public boolean matches(IItemHandler handler, int inputSlots, FluidStack fluid) {
        if (fluidInput != null && !fluidInput.test(fluid)) {
            return false;
        }
        int[] remaining = new int[inputSlots];
        for (int s = 0; s < inputSlots; s++) {
            remaining[s] = handler.getStackInSlot(s).getCount();
        }
        for (ItemInput input : inputs) {
            int need = input.count();
            for (int s = 0; s < inputSlots && need > 0; s++) {
                if (remaining[s] > 0 && input.ingredient().test(handler.getStackInSlot(s))) {
                    int take = Math.min(remaining[s], need);
                    remaining[s] -= take;
                    need -= take;
                }
            }
            if (need > 0) {
                return false;
            }
        }
        return true;
    }

    /** Removes this recipe's inputs; call only after {@link #matches} returned true. */
    public void consume(IItemHandlerModifiable handler, int inputSlots, IFluidHandler tank) {
        for (ItemInput input : inputs) {
            int need = input.count();
            for (int s = 0; s < inputSlots && need > 0; s++) {
                if (input.ingredient().test(handler.getStackInSlot(s))) {
                    need -= handler.extractItem(s, need, false).getCount();
                }
            }
        }
        if (fluidInput != null) {
            tank.drain(fluidInput.amount(), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    // ---- Recipe<Container> boilerplate (the machine never uses the vanilla Container path) ----

    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return output.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return output;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (ItemInput input : inputs) {
            list.add(input.ingredient());
        }
        return list;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.PRIMITIVE_ASSEMBLER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.PRIMITIVE_ASSEMBLER.get();
    }

    public static class Serializer implements RecipeSerializer<PrimitiveAssemblerRecipe> {

        @Override
        public PrimitiveAssemblerRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<ItemInput> inputs = new ArrayList<>();
            JsonArray array = GsonHelper.getAsJsonArray(json, "inputs", new JsonArray());
            for (JsonElement element : array) {
                JsonObject object = GsonHelper.convertToJsonObject(element, "input");
                JsonElement ingredientJson = object.has("ingredient") ? object.get("ingredient") : object;
                inputs.add(new ItemInput(Ingredient.fromJson(ingredientJson), GsonHelper.getAsInt(object, "count", 1)));
            }
            if (inputs.size() > MAX_INPUTS) {
                throw new JsonSyntaxException("primitive_assembler recipe " + id + " has more than " + MAX_INPUTS + " inputs");
            }

            FluidInput fluidInput = null;
            if (json.has("fluid_input")) {
                JsonObject fluidJson = GsonHelper.getAsJsonObject(json, "fluid_input");
                int amount = GsonHelper.getAsInt(fluidJson, "amount");
                if (fluidJson.has("fluid")) {
                    ResourceLocation fluidId = new ResourceLocation(GsonHelper.getAsString(fluidJson, "fluid"));
                    Fluid fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElseThrow(
                            () -> new JsonSyntaxException("Unknown fluid '" + fluidId + "' in recipe " + id));
                    fluidInput = new FluidInput(fluid, null, amount);
                } else {
                    fluidInput = new FluidInput(null,
                            TagKey.create(Registries.FLUID, new ResourceLocation(GsonHelper.getAsString(fluidJson, "tag"))), amount);
                }
            }
            if (inputs.isEmpty() && fluidInput == null) {
                throw new JsonSyntaxException("primitive_assembler recipe " + id + " has no inputs");
            }

            ItemStack output = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "output"));
            int duration = GsonHelper.getAsInt(json, "duration", 100);
            return new PrimitiveAssemblerRecipe(id, inputs, fluidInput, output, duration);
        }

        @Nullable
        @Override
        public PrimitiveAssemblerRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<ItemInput> inputs = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Ingredient ingredient = Ingredient.fromNetwork(buf);
                inputs.add(new ItemInput(ingredient, buf.readVarInt()));
            }
            FluidInput fluidInput = null;
            if (buf.readBoolean()) {
                boolean isTag = buf.readBoolean();
                ResourceLocation key = buf.readResourceLocation();
                int amount = buf.readVarInt();
                fluidInput = isTag
                        ? new FluidInput(null, TagKey.create(Registries.FLUID, key), amount)
                        : new FluidInput(BuiltInRegistries.FLUID.get(key), null, amount);
            }
            ItemStack output = buf.readItem();
            int duration = buf.readVarInt();
            return new PrimitiveAssemblerRecipe(id, inputs, fluidInput, output, duration);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, PrimitiveAssemblerRecipe recipe) {
            buf.writeVarInt(recipe.inputs.size());
            for (ItemInput input : recipe.inputs) {
                input.ingredient().toNetwork(buf);
                buf.writeVarInt(input.count());
            }
            buf.writeBoolean(recipe.fluidInput != null);
            if (recipe.fluidInput != null) {
                boolean isTag = recipe.fluidInput.tag() != null;
                buf.writeBoolean(isTag);
                buf.writeResourceLocation(isTag
                        ? recipe.fluidInput.tag().location()
                        : BuiltInRegistries.FLUID.getKey(recipe.fluidInput.fluid()));
                buf.writeVarInt(recipe.fluidInput.amount());
            }
            buf.writeItem(recipe.output);
            buf.writeVarInt(recipe.duration);
        }
    }
}
