package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.AlloyFoundryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Static UI for the first energy-free 5×5 Alloy Foundry pass. */
public final class AlloyFoundryScreen extends MachineScreen<AlloyFoundryMenu> {

    public AlloyFoundryScreen(AlloyFoundryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = 222;
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("alloy_foundry_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        graphics.drawString(this.font, Component.translatable("gui.gonzotech.alloy_foundry.auto"),
            x + 115, y + 25, 0xD6DDE5, false);
        graphics.drawString(this.font, Component.translatable("gui.gonzotech.alloy_foundry.output"),
            x + 119, y + 43, 0xAAB7C4, false);
    }
}
