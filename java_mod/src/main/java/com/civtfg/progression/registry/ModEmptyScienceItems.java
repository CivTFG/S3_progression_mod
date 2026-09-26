package com.civtfg.progression.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * One "empty" science item per {@link ModScienceItems.Age}, id {@code <age>_empty_science}
 * - a shared, tier-specific crafting base meant to be turned into that tier's 5 real
 * science items (recipes for that are a separate, not-yet-designed task - see
 * {@code science_recipes.js}'s header and CLAUDE.md's Known Issues).
 *
 * Deliberately NOT part of {@link ModScienceItems.Category} and never registered into
 * {@link ModScienceItems}'s {@code SCIENCE_ITEMS}/{@code identify()} map - an empty item
 * must never be a valid Laboratory research input (it would let a team "research" nothing
 * for free), so {@link com.civtfg.progression.blockentity.LaboratoryBlockEntity#getMatchingScience}
 * already rejects it automatically, the same way it rejects any other non-science item.
 *
 * Registered onto {@link ModItems#ITEMS} - call {@link #register()} once from the mod
 * constructor (alongside {@link ModScienceItems#register()}) to force this class to load
 * and actually run the registrations.
 */
public final class ModEmptyScienceItems {

    private static final Map<ModScienceItems.Age, RegistryObject<Item>> EMPTY_SCIENCE_ITEMS =
            new EnumMap<>(ModScienceItems.Age.class);

    public static RegistryObject<Item> get(ModScienceItems.Age age) {
        return EMPTY_SCIENCE_ITEMS.get(age);
    }

    public static String itemId(ModScienceItems.Age age) {
        return age.name().toLowerCase() + "_empty_science";
    }

    public static void register() {
        for (ModScienceItems.Age age : ModScienceItems.Age.values()) {
            String id = itemId(age);
            EMPTY_SCIENCE_ITEMS.put(age, ModItems.ITEMS.register(id, () -> new Item(new Item.Properties())));
        }
    }

    private ModEmptyScienceItems() {
    }
}
