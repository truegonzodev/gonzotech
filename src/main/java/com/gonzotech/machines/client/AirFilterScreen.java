package com.gonzotech.machines.client;

import com.gonzotech.cleanroom.AirFilterBlockEntity;
import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.menu.AirFilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.util.List;

/** Canvas: GTU (136,145), bars (190,145)/(190,181), supplies (190/208/226,163). */
public final class AirFilterScreen extends MachineScreen<AirFilterMenu> {
    private static final ResourceLocation BAR_AIR_1 = gui("bar_air_1.png");
    private static final ResourceLocation BAR_AIR_2 = gui("bar_air_2.png");

    public AirFilterScreen(AirFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
    @Override protected ResourceLocation backgroundTexture() { return gui("third_air_filter_gui_bg.png"); }
    @Override protected ResourceLocation foregroundTexture() { return gui("third_air_filter_gui.png"); }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        drawVBarTex(graphics, x + 8, y + 17, 16, 52,
                menu.gtuMilli() / (AirFilterBlockEntity.CAPACITY_GTU * 1000f), BAR_GTU);
        drawHBarTex(graphics, x + 62, y + 17, 52, 16,
                menu.coalUsedHundredths() / 10000f, BAR_AIR_1);
        drawHBarTexRightToLeft(graphics, x + 62, y + 53, 52, 16,
                menu.catalystUsedHundredths() / 10000f, BAR_AIR_2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Tooltips are above the finished GUI, not part of its masked background layer.
        int x = leftPos, y = topPos;
        if (inRect(mouseX, mouseY, x + 8, y + 17, 16, 52)) {
            graphics.renderComponentTooltip(font, List.of(GtUnits.gtuPair(
                    BigDecimal.valueOf(menu.gtuMilli(), 3).stripTrailingZeros().toPlainString(),
                    AirFilterBlockEntity.CAPACITY_GTU)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, x + 62, y + 17, 52, 16)) {
            graphics.renderComponentTooltip(font, List.of(quality(), Component.translatable(
                    "gui.gonzotech.air_filter.coal_used", Math.round(menu.coalUsedHundredths() / 100f))), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, x + 62, y + 53, 52, 16)) {
            graphics.renderComponentTooltip(font, List.of(quality(), Component.translatable(
                    "gui.gonzotech.air_filter.catalyst_used", Math.round(menu.catalystUsedHundredths() / 100f))), mouseX, mouseY);
        }
    }

    private Component quality() {
        return menu.qualityHundredths() < 0
                ? Component.translatable("gui.gonzotech.air_filter.no_room")
                : Component.translatable("message.gonzotech.telifon.air_quality",
                        Math.round(menu.qualityHundredths() / 100f) + "%");
    }
}
