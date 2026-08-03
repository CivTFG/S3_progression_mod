// ============================================================================
// Verified against the actual installed jar via javap:
//   - dev.latvian.mods.kubejs.recipe.RecipeSchemaRegistryEventJS
//       .register(ResourceLocation, RecipeSchema)   <- register() takes a
//       full ResourceLocation directly; namespace(String) is a separate,
//       unrelated method and was never the right thing to chain off.
//   - dev.latvian.mods.kubejs.recipe.schema.minecraft.ShapelessRecipeSchema
//       .INGREDIENTS is a RecipeKey<InputItem[]>
//   - dev.latvian.mods.kubejs.recipe.schema.RecipeSchema(RecipeKey<?>...)
//   - dev.latvian.mods.kubejs.recipe.component.NumberComponent.INT
//   - dev.latvian.mods.kubejs.recipe.component.StringComponent.NON_EMPTY
// ============================================================================

const RecipeSchema = Java.loadClass('dev.latvian.mods.kubejs.recipe.schema.RecipeSchema')
const ShapelessRecipeSchema = Java.loadClass('dev.latvian.mods.kubejs.recipe.schema.minecraft.ShapelessRecipeSchema')
const NumberComponent = Java.loadClass('dev.latvian.mods.kubejs.recipe.component.NumberComponent')
const StringComponent = Java.loadClass('dev.latvian.mods.kubejs.recipe.component.StringComponent')
const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')

StartupEvents.recipeSchemaRegistry(event => {
    // Which independent progression tier this recipe's "value" counts toward - e.g.
    // "LV", "MV", "HV". Tiers are separate tracks: an LV recipe only ever advances the
    // LV counter, never MV/HV. Mandatory - every laboratory recipe must declare one.
    const TIER = StringComponent.NON_EMPTY.key('tier')
    const VALUE = NumberComponent.INT.key('value').optional(1)

    const laboratorySchema = new RecipeSchema(
        ShapelessRecipeSchema.INGREDIENTS,
        TIER,
        VALUE
    )

    event.register(new ResourceLocation('s3_progression_mod', 'laboratory'), laboratorySchema)
})
