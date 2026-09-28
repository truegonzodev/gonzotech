package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.SiliconFactoryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * GUI литографии: слои bg (_gui_bg) → шкала прогресса (8, 17) 16×52 → fg (_gui).
 * Каждый вариант структуры имеет СВОЮ пару PNG
 * ({code silicon_factory_chip_N_gui*.png}); пока автор их не нарисовал —
 * общие {@code silicon_factory_gui*.png}.
 */
public final class SiliconFactoryScreen extends MachineScreen<SiliconFactoryMenu> {
    private int resolvedVariant = -1;
    private ResourceLocation background;
    private ResourceLocation foreground;

    public SiliconFactoryScreen(SiliconFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        resolveSheets();
        return background;
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        resolveSheets();
        return foreground;
    }

    private void resolveSheets() {
        int variant = menu.variant();
        if (resolvedVariant == variant) return;
        resolvedVariant = variant;
        if (variant >= 1 && variant <= 3) {
            ResourceLocation bg = gui("silicon_factory_chip_" + variant + "_gui_bg.png");
            ResourceLocation fg = gui("silicon_factory_chip_" + variant + "_gui.png");
            var resources = Minecraft.getInstance().getResourceManager();
            background = resources.getResource(bg).isPresent() ? bg : gui("silicon_factory_gui_bg.png");
            foreground = resources.getResource(fg).isPresent() ? fg : gui("silicon_factory_gui.png");
        } else {
            background = gui("silicon_factory_gui_bg.png");
            foreground = gui("silicon_factory_gui.png");
        }
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        drawVBarTex(graphics, x + 8, y + 17, 16, 52,
            menu.progress() / (float) menu.progressTotal(), BAR_GTU);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        if (inRect(mouseX, mouseY, x + 8, y + 17, 16, 52)) {
            int percent = Math.round(menu.progress() * 100f / menu.progressTotal());
            graphics.renderComponentTooltip(graphics, List.of(Component.translatable(
                "gui.gonzotech.silicon_factory.progress", percent)), mouseX, mouseY);
        }
    }
}
