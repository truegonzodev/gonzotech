package com.gonzotech.machines.client;

import com.gonzotech.core.config.GonzoClientConfig;
import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.menu.ItemFilterMenu;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Экран фильтра: число ghost-слотов зависит от уровня фильтра; экранный лист
 * выбирается клиентской настройкой NEO/классических GUI. Ghost-слоты рисуются
 * полупрозрачно, взаимодействие задаёт образец кликом (логика в {@link ItemFilterMenu}).
 */
public class ItemFilterScreen extends AbstractContainerScreen<ItemFilterMenu> {

    private static final String FIRST_TIER_BG = "item_filter_gui.png";
    private static final String SECOND_TIER_BG = "second_item_filter_gui.png";

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
        String textureFile = menu.blockEntity().getBlockState().is(ModMachines.SECOND_ITEM_FILTER.get())
            ? SECOND_TIER_BG
            : FIRST_TIER_BG;
        g.blit(RenderType::guiTextured, filterGuiTexture(textureFile), x, y, 0f, 0f,
            this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    private static ResourceLocation filterGuiTexture(String classicFile) {
        String selected = classicFile;
        boolean useClassic = GonzoClientConfig.SPEC.isLoaded()
                && GonzoClientConfig.USE_CLASSIC_MACHINE_GUIS.get();
        if (!useClassic) {
            selected = classicFile.substring(0, classicFile.length() - ".png".length()) + "_neo.png";
        }
        return ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/gui/" + selected);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
