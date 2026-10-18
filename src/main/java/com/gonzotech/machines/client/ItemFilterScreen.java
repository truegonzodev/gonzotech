package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.ItemFilterMenu;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Экран фильтра: число ghost-слотов зависит от уровня фильтра; экранный лист
 * выбирается клиентской настройкой NEO/классических GUI. Ghost-слоты рисуются
 * полупрозрачно, взаимодействие задаёт образец кликом (логика в {@link ItemFilterMenu}).
 */
public class ItemFilterScreen extends AbstractContainerScreen<ItemFilterMenu> {

    private static final String FIRST_TIER_BG = "item_filter_gui.png";
    private static final String FIRST_TIER_NEO_BG = "item_filter_gui_neo.png";
    private static final String SECOND_TIER_BG = "second_item_filter_gui.png";
    private static final String SECOND_TIER_NEO_BG = "second_item_filter_gui_neo.png";

    public ItemFilterScreen(ItemFilterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 184;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Название контейнера НЕ рисуем (по просьбе). Подпись инвентаря игрока
        // тоже убираем — фон-плейсхолдер без текста.
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        boolean secondTier = menu.blockEntity().getBlockState().is(ModMachines.SECOND_ITEM_FILTER.get());
        String classicFile = secondTier ? SECOND_TIER_BG : FIRST_TIER_BG;
        String neoFile = secondTier ? SECOND_TIER_NEO_BG : FIRST_TIER_NEO_BG;
        g.blit(RenderType::guiTextured, MachineScreen.machineGui(classicFile, neoFile), x, y, 0f, 0f,
            this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
