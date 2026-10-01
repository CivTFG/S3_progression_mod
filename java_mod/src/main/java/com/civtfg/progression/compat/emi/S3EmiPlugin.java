package com.civtfg.progression.compat.emi;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.recipe.PrimitiveAssemblerRecipe;
import com.civtfg.progression.registry.ModItems;
import com.civtfg.progression.registry.ModRecipeTypes;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;

/**
 * EMI integration for the Primitive Assembler: EMI only lists recipe types it has a plugin for,
 * so without this the machine's own recipe type never shows up. EMI discovers this class through
 * the {@link EmiEntrypoint} annotation, so nothing here is loaded when EMI is not installed
 * (EMI is a compile-only dependency, not in mods.toml).
 */
@EmiEntrypoint
public class S3EmiPlugin implements EmiPlugin {

    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            new ResourceLocation(ProgressionMod.MOD_ID, "primitive_assembler"),
            EmiStack.of(ModItems.PRIMITIVE_ASSEMBLER_ITEM.get()));

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(CATEGORY);
        registry.addWorkstation(CATEGORY, EmiStack.of(ModItems.PRIMITIVE_ASSEMBLER_ITEM.get()));
        for (PrimitiveAssemblerRecipe recipe : registry.getRecipeManager()
                .getAllRecipesFor(ModRecipeTypes.PRIMITIVE_ASSEMBLER.get())) {
            registry.addRecipe(new PrimitiveAssemblerEmiRecipe(recipe));
        }
    }

    /** 3x3 item grid, optional fluid tank, arrow, output - mirrors the machine's own GUI. */
    private static class PrimitiveAssemblerEmiRecipe extends BasicEmiRecipe {
        private final int inputCount;
        private final EmiIngredient fluid;
        private final int duration;

        PrimitiveAssemblerEmiRecipe(PrimitiveAssemblerRecipe recipe) {
            super(CATEGORY, recipe.getId(), 136, 56);
            for (PrimitiveAssemblerRecipe.ItemInput input : recipe.getInputs()) {
                this.inputs.add(EmiIngredient.of(input.ingredient(), input.count()));
            }
            this.inputCount = this.inputs.size();
            PrimitiveAssemblerRecipe.FluidInput fluidInput = recipe.getFluidInput();
            if (fluidInput != null) {
                this.fluid = fluidInput.fluid() != null
                        ? EmiStack.of(fluidInput.fluid(), fluidInput.amount())
                        : EmiIngredient.of(fluidInput.tag(), fluidInput.amount());
                this.inputs.add(this.fluid);
            } else {
                this.fluid = null;
            }
            this.outputs.add(EmiStack.of(recipe.getOutput()));
            this.duration = recipe.getDuration();
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            for (int i = 0; i < 9; i++) {
                int x = (i % 3) * 18;
                int y = (i / 3) * 18;
                if (i < inputCount) {
                    widgets.addSlot(inputs.get(i), x, y);
                } else {
                    widgets.addSlot(EmiStack.EMPTY, x, y);
                }
            }
            if (fluid != null) {
                widgets.addTank(fluid, 58, 0, 18, 54, 8000);
            }
            widgets.addFillingArrow(82, 18, duration * 50);
            widgets.addSlot(outputs.get(0), 110, 18).recipeContext(this);
            widgets.addText(net.minecraft.network.chat.Component.literal(duration / 20.0 + "s"), 82, 44, 0xFF808080, false);
        }
    }
}
