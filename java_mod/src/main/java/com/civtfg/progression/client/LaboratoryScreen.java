package com.civtfg.progression.client;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.menu.LaboratoryMenu;
import com.civtfg.progression.registry.ModMenuTypes;
import com.civtfg.progression.stage.ProgressionTiers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public class LaboratoryScreen extends AbstractContainerScreen<LaboratoryMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ProgressionMod.MOD_ID, "textures/gui/laboratory.png");

    // Progress bar recess, directly under the 5 laboratory slots - must match the
    // background texture's bar art and LaboratoryMenu's slot layout.
    private static final int PROGRESS_BAR_X = 45;
    private static final int PROGRESS_BAR_Y = 58;
    private static final int PROGRESS_BAR_WIDTH = 88;
    private static final int PROGRESS_BAR_HEIGHT = 6;
    private static final int PROGRESS_BAR_COLOR = 0xFFFFFFFF;

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
            int filled = (int) ((float) menu.getProgress() / menu.getMaxProgress() * PROGRESS_BAR_WIDTH);
            guiGraphics.fill(x + PROGRESS_BAR_X, y + PROGRESS_BAR_Y,
                    x + PROGRESS_BAR_X + filled, y + PROGRESS_BAR_Y + PROGRESS_BAR_HEIGHT,
                    PROGRESS_BAR_COLOR);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 0x404040, false);

        // GameStages syncs each player's own stages to their own client, so this can be
        // read directly off the local player - no server->client networking of our own needed.
        Player localPlayer = Minecraft.getInstance().player;
        String tierName = localPlayer != null ? ProgressionTiers.getCurrentTierName(localPlayer) : null;
        guiGraphics.drawString(font, "Current Tier: " + (tierName != null ? tierName : "None"), 8, 18, 0x404040, false);

        if (!menu.hasTeam()) {
            guiGraphics.drawString(font, "Unclaimed chunk - claim it to make progress here", 8, 29, 0xAA0000, false);
        } else {
            // Threshold of -1 with a claimed chunk means every tier is already unlocked -
            // nothing left to show progress for.
            int tierThreshold = menu.getTierThreshold();
            if (tierThreshold >= 0) {
                guiGraphics.drawString(font, "Progress: " + menu.getTierProgress() + " / " + tierThreshold, 8, 29, 0x404040, false);
            }
        }

        guiGraphics.drawString(font, playerInventoryTitle, 8, imageHeight - 96 + 2, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
