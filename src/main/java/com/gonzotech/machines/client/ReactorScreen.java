package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.menu.ReactorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Panel-driven reactor menu with live GTH/water/temperature/neutron indicators. */
public final class ReactorScreen extends MachineScreen<ReactorMenu> {

    private static final int TEMP_COLOR = 0xED3261;
    private static final int NEUTRON_COLOR = 0x6C54E3;
    private static final int PRESSURE_COLOR = 0x6A89A3;

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
                GtUnits.gthPair(GtUnits.x1(menu.gthAmountTenths() / 10.0),
                    GtUnits.x1(menu.gthCapacityTenths() / 10.0)),
                white("gui.gonzotech.reactor.generation",
                    Component.literal(GtUnits.x1(menu.generationTenthsGthPerTick() / 10.0) + " GTH")
                        .withColor(GtUnits.GTH)
                        .append(Component.translatable(GtUnits.K_PER_TICK)
                            .withStyle(ChatFormatting.WHITE))),
                white("gui.gonzotech.reactor.loaded_tvels",
                    GtUnits.x1(menu.loadedTvels()))
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, waterX, barY, 16, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.waterPair(GtUnits.x1(menu.waterAmount()), GtUnits.x1(menu.waterCapacity())),
                white("gui.gonzotech.reactor.model_water_temperature",
                    Component.literal(GtUnits.x1(menu.modelWaterTemperatureTenths() / 10.0) + " °C")
                        .withColor(TEMP_COLOR)),
                white("gui.gonzotech.reactor.model_water_pressure",
                    Component.translatable("gui.gonzotech.reactor.pressure_value",
                        GtUnits.x1(menu.modelWaterPressureTenthsMpa() / 10.0))
                        .withColor(PRESSURE_COLOR)),
                Component.translatable("gui.gonzotech.reactor.water_buffer")
                    .withStyle(Style.EMPTY.withColor(0xA0A0A0))
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, temperatureX, barY, 7, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                white("gui.gonzotech.reactor.hot_temperature",
                    temperatureValue(menu.hottestTemperatureTenths())),
                white("gui.gonzotech.reactor.mean_temperature",
                    temperatureValue(menu.meanTemperatureTenths()))
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, neutronsX, barY, 7, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                white("gui.gonzotech.reactor.strongest_flow",
                    percentValue(menu.strongestFlowTenthsPercent())),
                white("gui.gonzotech.reactor.average_flow",
                    percentValue(menu.averageFlowTenthsPercent())),
                white("gui.gonzotech.reactor.strongest_leak",
                    percentValue(menu.strongestLeakTenthsPercent())),
                white("gui.gonzotech.reactor.average_leak",
                    percentValue(menu.averageLeakTenthsPercent()))
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

    /** Whole tooltip text is &f; only the measured value keeps its accent color. */
    private static Component white(String key, Object value) {
        return Component.translatable(key, value).withStyle(ChatFormatting.WHITE);
    }

    private static MutableComponent temperatureValue(long tenths) {
        // Last synced reading doubles as the saturation marker of the short field.
        return Component.literal(GtUnits.x1(tenths / 10.0) + " °C").withColor(TEMP_COLOR);
    }

    private static MutableComponent percentValue(int tenthsPercent) {
        return Component.literal(GtUnits.x1(tenthsPercent / 10.0) + "%").withColor(NEUTRON_COLOR);
    }
}
