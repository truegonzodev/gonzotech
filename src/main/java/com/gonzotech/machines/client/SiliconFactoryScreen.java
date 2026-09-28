package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.litho.SiliconFactoryBlockEntity;
import com.gonzotech.machines.menu.SiliconFactoryMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigDecimal;
import java.util.List;

/**
 * GUI литографии (автор 28.09.2026): ГТУ-шкала (136,145) 16×52 классическая
 * вертикальная; три бара 16×34 (190/226/262, y163) растут слева направо
 * (bar_smelting); конвейер (y181): вход 172 → 208 → 244 → выход 280; шлак
 * (y145): 190/226/262. Каждый вариант структуры имеет СВОЮ пару
 * silicon_factory_chip_N_gui*.png; пока автора нет — общие silicon_factory_gui*.
 */
public final class SiliconFactoryScreen extends MachineScreen<SiliconFactoryMenu> {
    private static final String[] STEP_KEYS = {
        "gui.gonzotech.silicon_factory.etching",
        "gui.gonzotech.silicon_factory.photolithography",
        "gui.gonzotech.silicon_factory.vulcanization",
    };
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
        drawVBarTex(graphics, x + 136, y + 145, 16, 52,
            menu.gtuMilli() / (float) SiliconFactoryBlockEntity.CAPACITY_MILLI, BAR_GTU);
        int step = menu.step();
        for (int i = 0; i < 3; i++) {
            float fraction = step > i ? 1.0f
                : step == i ? menu.progressTicks() / (float) SiliconFactoryBlockEntity.STEP_TICKS[i]
                : 0.0f;
            drawHBarTex(graphics, x + 190 + i * 36, y + 163, 16, 34, fraction, BAR_SMELTING);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        if (inRect(mouseX, mouseY, x + 136, y + 145, 16, 52)) {
            graphics.renderComponentTooltip(font, List.of(GtUnits.gtuPair(
                BigDecimal.valueOf(menu.gtuMilli(), 3).stripTrailingZeros().toPlainString(),
                SiliconFactoryBlockEntity.CAPACITY_GTU)), mouseX, mouseY);
            return;
        }
        for (int i = 0; i < 3; i++) {
            if (inRect(mouseX, mouseY, x + 190 + i * 36, y + 163, 16, 34)) {
                int percent = menu.step() > i ? 100
                    : menu.step() == i
                    ? Math.round(menu.progressTicks() * 100f / SiliconFactoryBlockEntity.STEP_TICKS[i])
                    : 0;
                int quality = menu.qualityHundredths();
                double chance = SiliconFactoryBlockEntity.rejectPercent(quality);
                Component line2 = quality < 0
                    ? Component.translatable("gui.gonzotech.silicon_factory.quality_ambient",
                        Math.round(chance))
                    : Component.translatable("gui.gonzotech.silicon_factory.quality",
                        Math.round(quality / 100f), Math.round(chance));
                graphics.renderComponentTooltip(font, List.of(
                    Component.translatable(STEP_KEYS[i], percent), line2), mouseX, mouseY);
                return;
            }
        }
    }
}
