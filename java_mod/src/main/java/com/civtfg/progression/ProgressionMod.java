package com.civtfg.progression;

import com.civtfg.progression.client.LaboratoryScreen;
import com.civtfg.progression.registry.ModBlockEntities;
import com.civtfg.progression.registry.ModBlocks;
import com.civtfg.progression.registry.ModCreativeModeTabs;
import com.civtfg.progression.registry.ModItems;
import com.civtfg.progression.registry.ModMenuTypes;
import com.civtfg.progression.registry.ModScienceItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ProgressionMod.MOD_ID)
public class ProgressionMod {

    // NOTE: lowercased from the requested "S3_progression_mod" - Forge/Minecraft
    // resource-location namespaces must match [a-z0-9_.-], uppercase letters are
    // not legal and will crash the game at startup.
    public static final String MOD_ID = "s3_progression_mod";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public ProgressionMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModScienceItems.register();
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // FMLClientSetupEvent fires after every mod's constructor has run and all
        // registries are populated - RegistryObject#get() is not safe to call
        // directly in the constructor itself, which is what caused the
        // "Registry Object not present" crash.
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::buildCreativeModeTabContents);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> LaboratoryScreen::registerScreen);
    }

    private void buildCreativeModeTabContents(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            LOGGER.info("[s3_progression_mod] BuildCreativeModeTabContentsEvent fired for FUNCTIONAL_BLOCKS, adding laboratory");
            event.accept(ModItems.LABORATORY_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            LOGGER.info("[s3_progression_mod] BuildCreativeModeTabContentsEvent fired for INGREDIENTS, adding 45 science items");
            for (ModScienceItems.Age age : ModScienceItems.Age.values()) {
                for (ModScienceItems.Category category : ModScienceItems.Category.values()) {
                    event.accept(ModScienceItems.get(age, category));
                }
            }
        }
    }
}
