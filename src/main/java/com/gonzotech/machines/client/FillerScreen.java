package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.block.entity.FillerBlockEntity;
import com.gonzotech.machines.menu.FillerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Экран Наполнителя:
 * <ul>
 *   <li>Шкала GTH (16×52) в (8, 17) [холст 136, 145];</li>
 *   <li>Шкала GTU (16×52) в (26, 17) [холст 154, 145];</li>
 *   <li>Шкала левого бака (16×52) в (55, 17) [холст 183, 145];</li>
 *   <li>Кнопка смены баков (16×16) в (80, 35) [холст 208, 163];</li>
 *   <li>Шкала правого бака (16×52) в (105, 17) [холст 233, 145];</li>
 *   <li>Арочная шкала bar_smelting (59×16) в (99, 2) [холст 227, 130], растёт справа налево.</li>
 * </ul>
 */
public class FillerScreen extends MachineScreen<FillerMenu> {

    public FillerScreen(FillerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("third_filler_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("third_filler_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;

        int gthX = x + 8;
        int gtuX = x + 26;
        int leftTankX = x + 55;
        int rightTankX = x + 105;

        float gthFrac = (float) menu.gth() / (float) menu.maxGth();
        float gtuFrac = (float) menu.gtu() / (float) menu.maxGtu();
        float leftFrac = (float) menu.leftFluidAmount() / (float) FillerBlockEntity.TANK_CAPACITY;
        float rightFrac = (float) menu.rightFluidAmount() / (float) FillerBlockEntity.TANK_CAPACITY;

        drawVBarTex(g, gthX, barY, barW, barH, gthFrac, BAR_GTH);
        drawVBarTex(g, gtuX, barY, barW, barH, gtuFrac, BAR_GTU);

        ResourceLocation leftTex = getBarTexture(menu.leftFluidType());
        if (leftTex != null && leftFrac > 0) {
            drawVBarTex(g, leftTankX, barY, barW, barH, leftFrac, leftTex);
        }

        ResourceLocation rightTex = getBarTexture(menu.rightFluidType());
        if (rightTex != null && rightFrac > 0) {
            drawVBarTex(g, rightTankX, barY, barW, barH, rightFrac, rightTex);
        }

        // Арочная шкала bar_chemical: (99, 1), 59×16, растёт справа налево
        int archX = x + 99;
        int archY = y + 1;
        int archW = 59;
        int archH = 16;
        float smeltFrac = menu.smeltTotal() > 0 ? (float) menu.smeltProgress() / (float) menu.smeltTotal() : 0f;
        if (smeltFrac > 0) {
            drawHBarTexRightToLeft(g, archX, archY, archW, archH, smeltFrac, BAR_CHEMICAL);
        }

        // Тултипы
        if (inRect(mouseX, mouseY, gthX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(GtUnits.gthPair(menu.gth(), menu.maxGth())), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(GtUnits.gtuPair(menu.gtu(), menu.maxGtu())), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, leftTankX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, getFluidTooltip(menu.leftFluidType(), menu.leftFluidAmount(), menu.leftSaltMb()), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, rightTankX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, getFluidTooltip(menu.rightFluidType(), menu.rightFluidAmount(), menu.rightSaltMb()), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, x + 80, y + 35, 16, 16)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.filler.swap").withStyle(ChatFormatting.WHITE),
                Component.translatable("gui.gonzotech.filler.swap_cost").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, archX, archY, archW, archH)) {
            g.renderComponentTooltip(this.font, getSmeltTooltip(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int btnX = this.leftPos + 80;
        int btnY = this.topPos + 35;
        if (button == 0 && mouseX >= btnX && mouseX < btnX + 16 && mouseY >= btnY && mouseY < btnY + 16) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ResourceLocation getBarTexture(int fluidType) {
        return switch (fluidType) {
            case FillerBlockEntity.FLUID_WATER -> BAR_WATER;
            case FillerBlockEntity.FLUID_RECTIFICATE -> BAR_RECTIFICATE;
            case FillerBlockEntity.FLUID_SULFURIC_ACID -> BAR_SULFURIC_ACID;
            case FillerBlockEntity.FLUID_ETHYLENE -> BAR_ETHYLENE;
            case FillerBlockEntity.FLUID_AMINOBLAZEETHANOL -> BAR_AMINOBLAZEETHANOL;
            case FillerBlockEntity.FLUID_FORMALDEHYDE -> BAR_FORMALDEHYDE;
            case FillerBlockEntity.FLUID_MASH -> BAR_MASH;
            case FillerBlockEntity.FLUID_WORT -> BAR_WORT;
            case FillerBlockEntity.FLUID_DISTILLATE -> BAR_DISTILLATE;
            case FillerBlockEntity.FLUID_HOT_WATER -> BAR_HOT_WATER;
            case FillerBlockEntity.FLUID_POISON_POTION -> BAR_POISON;
            default -> null;
        };
    }

    private List<Component> getFluidTooltip(int fluidType, int amount, int saltMb) {
        if (amount <= 0 || fluidType == FillerBlockEntity.FLUID_EMPTY) {
            return List.of(Component.translatable("gui.gonzotech.canister.empty").withStyle(ChatFormatting.GRAY));
        }
        return switch (fluidType) {
            case FillerBlockEntity.FLUID_WATER -> {
                double saltPct = amount > 0 ? (double) saltMb * 100.0 / amount : 0;
                yield GtUnits.waterTooltip(amount, FillerBlockEntity.TANK_CAPACITY, saltPct);
            }
            case FillerBlockEntity.FLUID_RECTIFICATE -> GtUnits.rectificateTooltip(amount, FillerBlockEntity.TANK_CAPACITY);
            case FillerBlockEntity.FLUID_SULFURIC_ACID -> GtUnits.sulfuricAcidTooltip(amount, FillerBlockEntity.TANK_CAPACITY);
            case FillerBlockEntity.FLUID_ETHYLENE -> GtUnits.ethyleneTooltip(amount, FillerBlockEntity.TANK_CAPACITY);
            case FillerBlockEntity.FLUID_AMINOBLAZEETHANOL -> GtUnits.aminoblazeethanolTooltip(amount, FillerBlockEntity.TANK_CAPACITY);
            case FillerBlockEntity.FLUID_FORMALDEHYDE -> GtUnits.formaldehydeTooltip(amount, FillerBlockEntity.TANK_CAPACITY);
            case FillerBlockEntity.FLUID_MASH -> GtUnits.mashTooltip(amount, FillerBlockEntity.TANK_CAPACITY, 0, 0);
            case FillerBlockEntity.FLUID_WORT -> GtUnits.wortTooltip(amount, FillerBlockEntity.TANK_CAPACITY, 0);
            case FillerBlockEntity.FLUID_DISTILLATE -> GtUnits.distillateTooltip(amount, FillerBlockEntity.TANK_CAPACITY);
            case FillerBlockEntity.FLUID_HOT_WATER -> GtUnits.boilingWaterTooltip(amount, FillerBlockEntity.TANK_CAPACITY);
            case FillerBlockEntity.FLUID_POISON_POTION -> GtUnits.poisonTooltip(amount, FillerBlockEntity.TANK_CAPACITY);
            default -> List.of(Component.literal("Unknown: " + amount + " mB"));
        };
    }

    private List<Component> getSmeltTooltip() {
        int recipe = menu.activeRecipe();
        if (recipe == 0) {
            return List.of(Component.translatable("gui.gonzotech.filler.idle").withStyle(ChatFormatting.GRAY));
        }
        String key = switch (recipe) {
            case 1 -> "gui.gonzotech.filler.recipe.sulfuric_acid";
            case 2 -> "gui.gonzotech.filler.recipe.ethylene";
            case 3 -> "gui.gonzotech.filler.recipe.calcite";
            case 5 -> "gui.gonzotech.filler.recipe.aminoblazeethanol";
            case 6 -> "gui.gonzotech.filler.recipe.formaldehyde";
            case 7 -> "gui.gonzotech.filler.recipe.calcium_chloride";
            default -> "gui.gonzotech.filler.idle";
        };
        int pct = menu.smeltTotal() > 0 ? (menu.smeltProgress() * 100 / menu.smeltTotal()) : 0;
        return List.of(
            Component.empty()
                .append(Component.translatable(key).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(pct + "%").withStyle(ChatFormatting.WHITE))
        );
    }
}
