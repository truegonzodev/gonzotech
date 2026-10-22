package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.PlotnickDefs;
import com.gonzotech.machines.block.entity.PlotnickBlockEntity;
import com.gonzotech.machines.menu.PlotnickButtonPayload;
import com.gonzotech.machines.menu.PlotnickMenu;
import java.util.ArrayList;
import net.minecraft.world.inventory.Slot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Плотник (0.3.256) — GUI по замеру {@code plotnick_gui.png} автора:
 * окно 176×180, лист 512×512 со смещением (−128,−114), шкала GTU (7,17)
 * 18×54 (заливка 16×52), шесть кнопок 18×18 в (26/44, 18/36/54).
 * Раскладка автора: бурение / импульс, связи / разведка, свернуться / ТНТ;
 * название каждой кнопки показывает тултип при наведении.
 */
public final class PlotnickScreen extends MachineScreen<PlotnickMenu> {

    /** Иконка кнопки — красный флажок автора (14×14). */
    private static final ResourceLocation BUTTON_ICON = gui("marker_flag.png");

    /**
     * Прямоугольники кнопок 18×18, построчно слева направо:
     * бурение / импульс, связи / разведка, свернуться / ТНТ.
     */
    /** Индекс кнопки «Постройка» в BUTTON_RECTS (второй ряд, слева). */
    private static final int BTN_NETWORK_RECT = 2;
    /** Индекс кнопки «Начать бурение» (первый ряд, слева). */
    private static final int BTN_DRILL_RECT = 0;

    private static final int[][] BUTTON_RECTS = {
        {26, 18}, {44, 18}, {26, 36}, {44, 36}, {26, 54}, {44, 54}
    };

    /** Названия кнопок для тултипов, в том же порядке, что BUTTON_RECTS. */
    private static final String[] BUTTON_TOOLTIPS = {
        "gui.gonzotech.plotnick.button.drill",
        "gui.gonzotech.plotnick.button.impulse",
        "gui.gonzotech.plotnick.button.network",
        "gui.gonzotech.plotnick.button.mark",
        "gui.gonzotech.plotnick.button.pack_up",
        "gui.gonzotech.plotnick.button.tnt"
    };

    /**
     * Текущее описание кнопки второй строкой тултипа. Два варианта — без
     * заполненной карты / с ней (состояние Плотника приходит через
     * ContainerData, никаких сканов территории на клиенте).
     */
    private static final String[][] BUTTON_DESCRIPTIONS = {
        {"gui.gonzotech.plotnick.button.drill.desc"},
        {"gui.gonzotech.plotnick.button.impulse.desc"},
        {"gui.gonzotech.plotnick.button.network.sources",
            "gui.gonzotech.plotnick.button.network.drills"},
        {"gui.gonzotech.plotnick.button.mark.sector",
            "gui.gonzotech.plotnick.button.mark.ores"},
        {"gui.gonzotech.plotnick.button.pack_up.desc"},
        {"gui.gonzotech.plotnick.button.tnt.desc"}
    };



    public PlotnickScreen(PlotnickMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PlotnickMenu.IMAGE_WIDTH;
        this.imageHeight = PlotnickMenu.IMAGE_HEIGHT;
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return machineGui("plotnick_gui_bg.png", "plotnick_gui_bg_neo.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return machineGui("plotnick_gui.png", "plotnick_gui_neo.png");
    }

    @Override
    protected int texOffsetX() {
        return -128;
    }

    @Override
    protected int texOffsetY() {
        return -114;
    }

    // 0.3.267: собственный тултип ячейки со строкой «хранит: N шт» удалён —
    // с хранилищем v3 активные слоты держат истинные ванильные стопки, и
    // второй счётчик над ними только вводит в заблуждение. Обычный тултип
    // предмета показывает всё, что есть.

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Макет автора без текстовых меток.
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Шкала GTU (7,17) 18×54, интерьер (8,18) 16×52 (замер автора).
        int capacity = MachineDefs.toUnits((int) PlotnickDefs.GTU_CAPACITY_MILLI);
        int gtuX = x + 8;
        int barY = y + 18;
        float gtu = capacity == 0 ? 0f : (float) menu.gtu() / capacity;
        drawVBarTex(g, gtuX, barY, 16, 52, gtu, BAR_GTU);

        // Шесть кнопок: флажок-маркер действия в центре каждой 18×18,
        // при наведении — название кнопки.
        for (int i = 0; i < BUTTON_RECTS.length; i++) {
            int[] rect = BUTTON_RECTS[i];
            int bx = x + rect[0];
            int by = y + rect[1];
            g.blit(RenderType::guiTextured, BUTTON_ICON, bx + 2, by + 2, 0f, 0f, 14, 14, 14, 14);
            if (inRect(mouseX, mouseY, bx, by, 18, 18)) {
                if (i == BTN_DRILL_RECT) {
                    // 0.3.261: явные ЛКМ — старт, ПКМ — стоп бурения.
                    g.renderComponentTooltip(font, List.of(
                        Component.translatable(BUTTON_TOOLTIPS[i]),
                        clickLine("ЛКМ ", "gui.gonzotech.plotnick.button.drill.lmb"),
                        clickLine("ПКМ ", "gui.gonzotech.plotnick.button.drill.rmb")),
                        mouseX, mouseY);
                    continue;
                }
                if (i == BTN_NETWORK_RECT) {
                    // 0.3.259: ЛКМ строит всё (дороги + буры по точкам),
                    // ПКМ лишь ведёт сеть к уже поставленным бурям.
                    g.renderComponentTooltip(font, List.of(
                        Component.translatable(BUTTON_TOOLTIPS[i]),
                        clickLine("ЛКМ ", "gui.gonzotech.plotnick.button.network.lmb",
                            menu.networkDrillsToPlace(), menu.networkRoadsFull()),
                        clickLine("ПКМ ", "gui.gonzotech.plotnick.button.network.rmb",
                            menu.networkDrillsFound(), menu.networkRoadsToLink())),
                        mouseX, mouseY);
                    continue;
                }
                // Режим кнопок — по состоянию Плотника (заполненная карта),
                // не по ежесекундному скану территории.
                String[] keys = BUTTON_DESCRIPTIONS[i];
                String descKey = keys[menu.hasSectorCard() && keys.length > 1 ? 1 : 0];
                g.renderComponentTooltip(font, List.of(
                    Component.translatable(BUTTON_TOOLTIPS[i]),
                    Component.translatable(descKey).withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
            }
        }

        if (inRect(mouseX, mouseY, gtuX, barY, 16, 52)) {
            g.renderComponentTooltip(font, List.of(
                GtUnits.gtuPair(menu.gtu(), capacity)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            for (int i = 0; i < BUTTON_RECTS.length; i++) {
                int bx = leftPos + BUTTON_RECTS[i][0];
                int by = topPos + BUTTON_RECTS[i][1];
                if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18) {
                    if (button == 1) {
                        // ПКМ: «Постройка» — соединение буров, «Бурение» — стоп.
                        if (i == BTN_NETWORK_RECT) {
                            PacketDistributor.sendToServer(new PlotnickButtonPayload(
                                menu.containerId, PlotnickBlockEntity.BTN_NETWORK_LINK));
                        } else if (i == BTN_DRILL_RECT) {
                            PacketDistributor.sendToServer(new PlotnickButtonPayload(
                                menu.containerId, PlotnickBlockEntity.BTN_DRILL_STOP));
                        }
                        return true;
                    }
                    PacketDistributor.sendToServer(
                        new PlotnickButtonPayload(menu.containerId, i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Строка «&7&lЛКМ &r&7текст»: жирная серая метка клика + серый текст. */
    private static Component clickLine(String label, String key, int first, int second) {
        return Component.empty()
            .append(Component.literal(label).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
            .append(Component.translatable(key, first, second).withStyle(ChatFormatting.GRAY));
    }

    private static Component clickLine(String label, String key) {
        return Component.empty()
            .append(Component.literal(label).withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD))
            .append(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
}
