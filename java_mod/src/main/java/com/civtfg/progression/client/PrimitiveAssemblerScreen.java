package com.civtfg.progression.client;

import com.civtfg.progression.ProgressionMod;
import com.civtfg.progression.blockentity.PrimitiveAssemblerBlockEntity;
import com.civtfg.progression.menu.PrimitiveAssemblerMenu;
import com.civtfg.progression.registry.ModMenuTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;

public class PrimitiveAssemblerScreen extends AbstractContainerScreen<PrimitiveAssemblerMenu> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ProgressionMod.MOD_ID, "textures/gui/primitive_assembler.png");
    private static final int TEXTURE_SIZE = 256;

    // Must match the frames drawn into primitive_assembler.png.
    private static final int TANK_X = 10;
    private static final int TANK_Y = 17;
    private static final int TANK_WIDTH = 16;
    private static final int TANK_HEIGHT = 52;

    private static final int ARROW_X = 90;
    private static final int ARROW_Y = 36;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 17;

    public PrimitiveAssemblerScreen(PrimitiveAssemblerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    public static void registerScreen() {
        MenuScreens.register(ModMenuTypes.PRIMITIVE_ASSEMBLER.get(), PrimitiveAssemblerScreen::new);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(TEXTURE, x, y, 0.0F, 0.0F, imageWidth, imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);

        int max = menu.getMaxProgress();
        if (max > 0 && menu.getProgress() > 0) {
            int filled = Math.min(ARROW_WIDTH, menu.getProgress() * ARROW_WIDTH / max);
            guiGraphics.blit(TEXTURE, x + ARROW_X, y + ARROW_Y, 176.0F, 0.0F, filled, ARROW_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        }

        renderFluid(guiGraphics, x + TANK_X, y + TANK_Y);
    }

    private void renderFluid(GuiGraphics guiGraphics, int left, int top) {
        Fluid fluid = currentFluid();
        int amount = menu.getFluidAmount();
        if (fluid == null || amount <= 0) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture());
        int tint = ext.getTintColor();
        RenderSystem.setShaderColor(
                ((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F, ((tint >> 24) & 0xFF) / 255.0F);

        int filled = Math.max(1, amount * TANK_HEIGHT / PrimitiveAssemblerBlockEntity.TANK_CAPACITY);
        int bottom = top + TANK_HEIGHT;
        int fillTop = bottom - filled;
        // Tile the 16x16 sprite upward from the bottom, clipped to the filled height.
        guiGraphics.enableScissor(left, fillTop, left + TANK_WIDTH, bottom);
        for (int ty = bottom - 16; ty > fillTop - 16; ty -= 16) {
            guiGraphics.blit(left, ty, 0, TANK_WIDTH, 16, sprite);
        }
        guiGraphics.disableScissor();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private Fluid currentFluid() {
        int id = menu.getFluidId();
        return id < 0 ? null : BuiltInRegistries.FLUID.byId(id);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = (width - imageWidth) / 2 + TANK_X;
        int top = (height - imageHeight) / 2 + TANK_Y;
        if (!menu.getCarried().isEmpty()
                && mouseX >= left && mouseX < left + TANK_WIDTH && mouseY >= top && mouseY < top + TANK_HEIGHT
                && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PrimitiveAssemblerMenu.BUTTON_TANK_CLICK);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

        int left = (width - imageWidth) / 2 + TANK_X;
        int top = (height - imageHeight) / 2 + TANK_Y;
        if (mouseX >= left && mouseX < left + TANK_WIDTH && mouseY >= top && mouseY < top + TANK_HEIGHT) {
            Fluid fluid = currentFluid();
            Component name = fluid != null && menu.getFluidAmount() > 0
                    ? new FluidStack(fluid, 1).getDisplayName()
                    : Component.translatable("gui.s3_progression_mod.primitive_assembler.empty");
            guiGraphics.renderTooltip(font,
                    List.of(name, Component.literal(menu.getFluidAmount() + " / "
                            + PrimitiveAssemblerBlockEntity.TANK_CAPACITY + " mB")),
                    Optional.empty(), mouseX, mouseY);
        }
    }
}
