package com.civtfg.progression.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * The 5 "science" items for each of the 9 progression tiers (see
 * com.civtfg.progression.stage.ProgressionTiers for the tier order/thresholds/stages -
 * the {@link #AGES} keys here must match that class's Tier.key() values exactly, since
 * laboratory recipes tie a tier key to whichever 5 items are its science items).
 *
 * Registered onto {@link ModItems#ITEMS} - call {@link #register()} once from the mod
 * constructor to force this class to load and actually run the registrations.
 */
public final class ModScienceItems {

    public enum Age {
        BRONZE("Bronze Age"),
        IRON("Iron Age"),
        STEEL("Steel Age"),
        STEAM("Steam Age"),
        LV("LV"),
        MV("MV"),
        HV("HV"),
        EV("EV"),
        IV("IV");

        public final String displayName;

        Age(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum Category {
        MINING,
        FARMING,
        PRODUCTION,
        EXPLORATION,
        CHALLENGE
    }

    private static final Map<Age, Map<Category, RegistryObject<Item>>> SCIENCE_ITEMS = new EnumMap<>(Age.class);

    public static RegistryObject<Item> get(Age age, Category category) {
        return SCIENCE_ITEMS.get(age).get(category);
    }

    public static String itemId(Age age, Category category) {
        return age.name().toLowerCase() + "_" + category.name().toLowerCase() + "_science";
    }

    public static void register() {
        for (Age age : Age.values()) {
            Map<Category, RegistryObject<Item>> forAge = new EnumMap<>(Category.class);
            for (Category category : Category.values()) {
                String id = itemId(age, category);
                forAge.put(category, ModItems.ITEMS.register(id, () -> new Item(new Item.Properties())));
            }
            SCIENCE_ITEMS.put(age, forAge);
        }
    }

    private ModScienceItems() {
    }
}
