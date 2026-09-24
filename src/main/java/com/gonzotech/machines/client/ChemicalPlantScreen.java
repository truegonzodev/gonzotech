package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.menu.ChemicalPlantMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Экран Химического завода:
 * <ul>
 *   <li>Шкала GTU: (8, 17) 16×52 с тултипом GTU;</li>
 *   <li>Шкала реакции chemical: (116, 35) 24×16 (слева направо);</li>
 *   <li>Слоты катализаторов и сетка 3×3 оформлены в текстуре.</li>
 * </ul>
 */
public class ChemicalPlantScreen extends MachineScreen<ChemicalPlantMenu> {

    public ChemicalPlantScreen(ChemicalPlantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("third_chemical_plant_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("third_chemical_plant_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // 1. Шкала GTU слева (8, 17) 16×52
        int gtuX = x + 8;
        int gtuY = y + 17;
        int gtuW = 16;
        int gtuH = 52;
        float gtuFraction = menu.maxGtu() > 0 ? (float) menu.gtu() / menu.maxGtu() : 0f;
        drawVBarTex(graphics, gtuX, gtuY, gtuW, gtuH, gtuFraction, BAR_GTU);

        // 2. Стрелка прогресса реакции chemical слева направо (116, 35) 24×16
        int progX = x + 116;
        int progY = y + 35;
        int progW = 24;
        int progH = 16;
        float progFraction = menu.total() > 0 ? (float) menu.progress() / menu.total() : 0f;
        drawHBarTex(graphics, progX, progY, progW, progH, progFraction, BAR_CHEMICAL);

        // Тултипы
        if (inRect(mouseX, mouseY, gtuX, gtuY, gtuW, gtuH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.gtuPair(menu.gtu(), menu.maxGtu())), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, progX, progY, progW, progH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.chemical_plant.reaction", menu.progressPercent())), mouseX, mouseY);
        }
    }
}
