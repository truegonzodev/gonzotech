package com.gonzotech.machines.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Minimal UI base surface used to typecheck SteamGenScreen. */
public abstract class MachineScreen<T> {
    protected final Font font = new Font();
    protected static final ResourceLocation BAR_GTH = gui("bar_gth.png");
    protected static final ResourceLocation BAR_WATER = gui("bar_water.png");
    protected static final ResourceLocation BAR_STEAM = gui("bar_steam.png");

    protected MachineScreen(T menu, Inventory inventory, Component title) { }
    protected static ResourceLocation gui(String path) {
        return ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/" + path);
    }
    protected ResourceLocation backgroundTexture() { return null; }
    protected ResourceLocation foregroundTexture() { return null; }
    protected abstract void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY);
    protected void drawVBarTex(GuiGraphics graphics, int x, int y, int width, int height,
                               float fraction, ResourceLocation texture) { }
    protected boolean inRect(int mouseX, int mouseY, int x, int y, int width, int height) {
        return false;
    }
}
