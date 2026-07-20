package com.civtfg.progression.registry;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.menu.LaboratoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ProgressionMod.MOD_ID);

    public static final RegistryObject<MenuType<LaboratoryMenu>> LABORATORY =
            MENUS.register("laboratory", () -> IForgeMenuType.create(
                    (windowId, inv, data) -> new LaboratoryMenu(windowId, inv, data.readBlockPos())));
}
