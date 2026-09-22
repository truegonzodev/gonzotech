package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.menu.SteamGenMenu;
import com.gonzotech.machines.steamgen.SteamGenMath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/** Экран продвинутого парогенератора: три шкалы — GTH, вода, пар. */
public final class SteamGenScreen extends MachineScreen<SteamGenMenu> {

    public SteamGenScreen(SteamGenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("steamgen_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("steamgen_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int gthX = x + 80;
        int waterX = x + 116;
        int steamX = x + 152;

        int gthCapacity = menu.gthCapacity();
        int waterCapacity = menu.waterCapacity();
        int steamCapacity = menu.steamCapacity();
        float gth = gthCapacity <= 0 ? 0f : (float) menu.gth() / gthCapacity;
        float water = waterCapacity <= 0 ? 0f : (float) menu.water() / waterCapacity;
        float steam = steamCapacity <= 0 ? 0f : (float) menu.steam() / steamCapacity;
        drawVBarTex(graphics, gthX, barY, barW, barH, gth, BAR_GTH);
        drawVBarTex(graphics, waterX, barY, barW, barH, water, BAR_WATER);
        drawVBarTex(graphics, steamX, barY, barW, barH, steam, BAR_STEAM);

        String bonus = String.format(Locale.ROOT, "%.1f",
            SteamGenMath.bonusFraction(menu.sumCH(), menu.precious()) * 100.0D);
        if (inRect(mouseX, mouseY, gthX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gth(), gthCapacity),
                GtUnits.steamGenGthIn(menu.gthIn()),
                Component.translatable("gui.gonzotech.steamgen.cores", menu.cores()),
                Component.translatable("gui.gonzotech.steamgen.exchangers", menu.precious(), bonus)
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, waterX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.waterPair(menu.water(), waterCapacity),
                GtUnits.steamGenWaterIn(menu.waterIn())
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, steamX, barY, barW, barH)) {
            long ratedMilli = SteamGenMath.maxSteamPerTickMilli(menu.cores(), menu.sumCH(), menu.precious());
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.steamPair(menu.steam(), steamCapacity),
                GtUnits.steamGenSteamMade(menu.steamMade()),
                GtUnits.steamGenSteamOut(menu.steamOut()),
                GtUnits.steamGenRated(
                    String.format(Locale.ROOT, "%.1f", ratedMilli / 1000.0D))
            ), mouseX, mouseY);
        }
    }
}
