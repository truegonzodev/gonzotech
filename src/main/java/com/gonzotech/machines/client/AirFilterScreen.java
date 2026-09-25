package com.gonzotech.machines.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.menu.AirFilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** GUI for the clean-room air filter. */
public final class AirFilterScreen extends AbstractContainerScreen<AirFilterMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            GonzoTechMod.MOD_ID, "textures/gui/third_air_filter_gui_bg.png");

    public AirFilterScreen(AirFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(RenderType::guiTextured, BACKGROUND, x, y, 0, 0,
                imageWidth, imageHeight, imageWidth, imageHeight);
    }
}
