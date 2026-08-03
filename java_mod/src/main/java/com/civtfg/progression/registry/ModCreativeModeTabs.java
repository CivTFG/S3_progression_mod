package com.civtfg.progression.registry;

import com.civtfg.progression.ProgressionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeModeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProgressionMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> PROGRESSION_TAB = CREATIVE_MODE_TABS.register("progression_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.s3_progression_mod.progression_tab"))
                    .icon(() -> new ItemStack(ModItems.LABORATORY_ITEM.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.LABORATORY_ITEM.get());
                        int count = 1;
                        for (ModScienceItems.Age age : ModScienceItems.Age.values()) {
                            for (ModScienceItems.Category category : ModScienceItems.Category.values()) {
                                output.accept(ModScienceItems.get(age, category).get());
                                count++;
                            }
                        }
                        ProgressionMod.LOGGER.info("[s3_progression_mod] progression_tab displayItems generator ran, added {} items", count);
                    })
                    .build());

    private ModCreativeModeTabs() {
    }
}
