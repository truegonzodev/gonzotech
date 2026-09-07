package com.gonzotech.machines.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.menu.ItemFilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Экран ФИЛЬТРА: 3 ghost-слота (образцы) + инвентарь игрока. Фон — плейсхолдер
 * (художник заменит). Ghost-слоты рисуются полупрозрачно (см. подсказку игроку),
 * взаимодействие — задание образца кликом (логика в {@link ItemFilterMenu}).
 */
public class ItemFilterScreen extends AbstractContainerScreen<ItemFilterMenu> {

    private static final ResourceLocation BG =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/gui/item_filter_gui.png");

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
        g.blit(RenderType::guiTextured, BG, x, y, 0f, 0f, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
