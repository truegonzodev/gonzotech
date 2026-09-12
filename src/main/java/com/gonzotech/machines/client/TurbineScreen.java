package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.TurbineMenu;
import com.gonzotech.machines.turbine.TurbineMath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран турбины: две шкалы общего запаса Steam и GTU. */
public final class TurbineScreen extends MachineScreen<TurbineMenu> {

    public TurbineScreen(TurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("turbine_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("turbine_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int steamX = x + 51;
        int gtuX = x + 109;

        int steamCapacity = menu.steamCapacity();
        int gtuCapacity = menu.gtuCapacity();
        float steam = steamCapacity <= 0 ? 0f : (float) menu.steam() / steamCapacity;
        float gtu = gtuCapacity <= 0 ? 0f : (float) menu.gtu() / gtuCapacity;
        drawVBarTex(graphics, steamX, barY, barW, barH, steam, BAR_STEAM);
        drawVBarTex(graphics, gtuX, barY, barW, barH, gtu, BAR_GTU);

        if (inRect(mouseX, mouseY, steamX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.turbine.steam", menu.steam(), steamCapacity),
                Component.translatable("gui.gonzotech.turbine.rotors", menu.rotors()),
                Component.translatable("gui.gonzotech.turbine.steam_rate", menu.steamConsumed())
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            long ratedMilli = TurbineMath.maxGtuOutputMilli(menu.rotors());
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), gtuCapacity),
                Component.translatable("gui.gonzotech.turbine.gtu_rate",
                    String.format(java.util.Locale.ROOT, "%.1f", ratedMilli / 1000.0D))
            ), mouseX, mouseY);
        }
    }
}
