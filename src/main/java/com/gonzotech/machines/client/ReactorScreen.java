package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.menu.ReactorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/** Panel-driven reactor menu with live GTH/water/temperature/neutron indicators. */
public final class ReactorScreen extends MachineScreen<ReactorMenu> {

    private static final int TEMP_COLOR = 0xED3261;
    private static final int NEUTRON_COLOR = 0x6C54E3;
    private static final long MAX_SYNC_TEMPERATURE_TENTHS = 327_679_999L;

    public ReactorScreen(ReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return machineGui("third_uranium_reactor_gui_bg.png", "third_uranium_reactor_gui_bg_neo.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return machineGui("third_uranium_reactor_gui.png", "third_uranium_reactor_gui_neo.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17; // sheet y=145; the three 18px fuel rows share one 52px vertical gauge
        int barH = 52;
        int gthX = x + 8;       // sheet x=136
        int waterX = x + 26;    // sheet x=154
        int temperatureX = x + 44; // sheet x=172, 7px wide
        int neutronsX = x + 53; // sheet x=181, 7px wide

        drawVBarTex(graphics, gthX, barY, 16, barH, menu.gthFraction(), BAR_GTH);
        drawVBarTex(graphics, waterX, barY, 16, barH, menu.waterFraction(), BAR_WATER);
        drawVBarTex(graphics, temperatureX, barY, 7, barH,
            menu.temperatureFraction(), BAR_TEMP);
        drawVBarTex(graphics, neutronsX, barY, 7, barH,
            menu.neutronFraction(), BAR_NEUTRONS);

        if (inRect(mouseX, mouseY, gthX, barY, 16, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gthAmount(), menu.gthCapacity()),
                Component.translatable("gui.gonzotech.reactor.generation",
                    formatRate(menu.generationGthPerTick()))
                    .withStyle(Style.EMPTY.withColor(GtUnits.GTH)),
                Component.translatable("gui.gonzotech.reactor.loaded_tvels", menu.loadedTvels())
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, waterX, barY, 16, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.waterPair(menu.waterAmount(), menu.waterCapacity()),
                Component.translatable("gui.gonzotech.reactor.water_buffer")
                    .withStyle(Style.EMPTY.withColor(0xA0A0A0))
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, temperatureX, barY, 7, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                colored("gui.gonzotech.reactor.hot_temperature",
                    formatTemperature(menu.hottestTemperatureTenths()), TEMP_COLOR),
                colored("gui.gonzotech.reactor.mean_temperature",
                    formatTemperature(menu.meanTemperatureTenths()), TEMP_COLOR)
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, neutronsX, barY, 7, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                colored("gui.gonzotech.reactor.strongest_flow",
                    formatPercent(menu.strongestFlowTenthsPercent()), NEUTRON_COLOR),
                colored("gui.gonzotech.reactor.average_flow",
                    formatPercent(menu.averageFlowTenthsPercent()), NEUTRON_COLOR),
                colored("gui.gonzotech.reactor.strongest_leak",
                    formatPercent(menu.strongestLeakTenthsPercent()), NEUTRON_COLOR),
                colored("gui.gonzotech.reactor.average_leak",
                    formatPercent(menu.averageLeakTenthsPercent()), NEUTRON_COLOR)
            ), mouseX, mouseY);
        }
    }

    @Override
    protected void drawAfterForeground(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        if (menu.activeFuelSlots() <= 3) return;

        ResourceLocation slotFrame = machineGui(
            "third_uranium_reactor_gui_slot.png", "third_uranium_reactor_gui_slot_neo1.png");

        // The first sheath's three frames are part of gui.png; only later slots
        // are overlaid here, above that sheet and below vanilla slot tints/items.
        for (int slot = 3; slot < menu.activeFuelSlots(); slot++) {
            int slotX = x + ReactorMenu.fuelSlotX(slot) - 1;
            int slotY = y + ReactorMenu.fuelSlotY(slot) - 1;
            graphics.blit(RenderType::guiTextured, slotFrame, slotX, slotY,
                0.0F, 0.0F, 18, 18, 18, 18);
        }
    }

    private static Component colored(String key, Object value, int rgb) {
        return Component.translatable(key, value).withStyle(Style.EMPTY.withColor(rgb));
    }

    private static String formatTemperature(long tenths) {
        if (tenths >= MAX_SYNC_TEMPERATURE_TENTHS) return "≥32767999.9 °C";
        return String.format(Locale.ROOT, "%.1f °C", tenths / 10.0);
    }

    private static String formatPercent(int tenthsPercent) {
        return String.format(Locale.ROOT, "%.1f%%", tenthsPercent / 10.0);
    }

    private static String formatRate(int gthPerTick) {
        return Integer.toString(gthPerTick);
    }
}
