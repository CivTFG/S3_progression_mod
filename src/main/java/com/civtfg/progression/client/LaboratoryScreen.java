package com.civtfg.progression.client;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.menu.LaboratoryMenu;
import com.civtfg.progression.registry.ModMenuTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class LaboratoryScreen extends AbstractContainerScreen<LaboratoryMenu> {

    // Placeholder texture - replace with a real GUI background at this path:
    // src/main/resources/assets/s3_progression_mod/textures/gui/laboratory.png (256x166 vanilla-style canvas)
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ProgressionMod.MOD_ID, "textures/gui/laboratory.png");

    private static final int PROGRESS_ARROW_X = 79;
    private static final int PROGRESS_ARROW_Y = 34;
    private static final int PROGRESS_ARROW_WIDTH = 24;
    private static final int PROGRESS_ARROW_HEIGHT = 17;

    public LaboratoryScreen(LaboratoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    public static void registerScreen() {
        MenuScreens.register(ModMenuTypes.LABORATORY.get(), LaboratoryScreen::new);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        if (menu.getProgress() > 0) {
            int filled = (int) ((float) menu.getProgress() / menu.getMaxProgress() * PROGRESS_ARROW_WIDTH);
            guiGraphics.blit(TEXTURE, x + PROGRESS_ARROW_X, y + PROGRESS_ARROW_Y,
                    176, 0, filled, PROGRESS_ARROW_HEIGHT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 0x404040, false);
        guiGraphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
