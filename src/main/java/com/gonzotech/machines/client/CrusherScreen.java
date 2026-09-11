package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.CrusherMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран дробилки: та же четырёхвыходная раскладка, GTU-шкала и PNG-прогресс дробления. */
public class CrusherScreen extends MachineScreen<CrusherMenu> {

    private static final ResourceLocation BAR_CRUSHING = gui("bar_crushing.png");

    public CrusherScreen(CrusherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("crusher_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("crusher_gui.png");
    }

    @Override
    protected boolean usesRasterMachineSlotHover() {
        return true;
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int gtuX = x + 28;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int crushX = x + 76;
        int crushY = y + 35;
        int crushW = 24;
        int crushH = 16;

        float gtu = (float) menu.gtu() / MachineDefs.toUnits(MachineDefs.CRUSHER_GTU_CAPACITY);
        float progress = menu.crushTotal() > 0 ? (float) menu.crushProgress() / menu.crushTotal() : 0f;
        drawVBarTex(graphics, gtuX, barY, barW, barH, gtu, BAR_GTU);
        drawHBarTex(graphics, crushX, crushY, crushW, crushH, progress, BAR_CRUSHING);

        if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), MachineDefs.toUnits(MachineDefs.CRUSHER_GTU_CAPACITY))),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, crushX, crushY, crushW, crushH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.crusher.crushing_progress", menu.crushProgressPercent())),
                mouseX, mouseY);
        }
    }
}
