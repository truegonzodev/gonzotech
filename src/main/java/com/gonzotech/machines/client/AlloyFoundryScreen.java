package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.AlloyFoundryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** 5×5 Alloy Foundry II UI with its GTU buffer and alloying-progress indicators. */
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
        int gtuX = x + 112;
        int gtuY = y + 17;
        int gtuW = 16;
        int gtuH = 52;
        int progressX = x + 112;
        int progressY = y + 80;
        int progressW = 48;
        int progressH = 16;
        int capacity = MachineDefs.toUnits(SecondTierDefs.ALLOY_FOUNDRY_GTU_CAPACITY);

        float gtu = capacity <= 0 ? 0f : (float) menu.gtu() / capacity;
        float progress = menu.alloyTotal() <= 0
            ? 0f
            : (float) menu.alloyProgress() / menu.alloyTotal();
        drawVBarTex(graphics, gtuX, gtuY, gtuW, gtuH, gtu, BAR_GTU);
        drawHBarTex(graphics, progressX, progressY, progressW, progressH, progress, BAR_SMELTING);

        // Keep the output legend clear of the energy bar on the left.
        graphics.drawString(this.font, Component.translatable("gui.gonzotech.alloy_foundry.output"),
            x + 132, y + 43, 0xAAB7C4, false);

        if (inRect(mouseX, mouseY, gtuX, gtuY, gtuW, gtuH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), capacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, progressX, progressY, progressW, progressH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.alloy_foundry.alloying_progress", menu.alloyProgressPercent())),
                mouseX, mouseY);
        }
    }
}
