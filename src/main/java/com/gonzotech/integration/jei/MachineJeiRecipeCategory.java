package com.gonzotech.integration.jei;

import com.gonzotech.core.text.GtUnits;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT;

/** Renderer shared by every machine processing category. */
public final class MachineJeiRecipeCategory implements IRecipeCategory<MachineJeiRecipe> {
    private final MachineJeiCategories.Definition definition;
    private final IDrawable machineGuiBackground;
    private final IDrawable machineGuiForeground;
    private final IDrawable icon;

    public MachineJeiRecipeCategory(IGuiHelper guiHelper, MachineJeiCategories.Definition definition) {
        this.definition = definition;
        if (definition.textOnly()) {
            this.machineGuiBackground = guiHelper.createBlankDrawable(definition.width(), definition.height());
            this.machineGuiForeground = null;
        } else {
            ResourceLocation backgroundTexture = ResourceLocation.fromNamespaceAndPath(
                "gonzotech", "textures/gui/" + definition.texture() + "_gui_bg.png");
            ResourceLocation foregroundTexture = ResourceLocation.fromNamespaceAndPath(
                "gonzotech", "textures/gui/" + definition.texture() + "_gui.png");
            this.machineGuiBackground = guiHelper.drawableBuilder(
                    backgroundTexture, definition.textureX(), definition.textureY(), definition.width(), definition.height())
                .setTextureSize(512, 512)
                .build();
            this.machineGuiForeground = guiHelper.drawableBuilder(
                    foregroundTexture, definition.textureX(), definition.textureY(), definition.width(), definition.height())
                .setTextureSize(512, 512)
                .build();
        }
        this.icon = guiHelper.createDrawableIngredient(
            VanillaTypes.ITEM_STACK, new ItemStack(definition.station().get()));
    }

    @Override
    public mezz.jei.api.recipe.types.IRecipeType<MachineJeiRecipe> getRecipeType() {
        return definition.type();
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.gonzotech.category." + definition.id());
    }

    @Override
    public IDrawable getBackground() {
        return machineGuiBackground;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return definition.width();
    }

    @Override
    public int getHeight() {
        return definition.height();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineJeiRecipe recipe, IFocusGroup focuses) {
        if (definition.textOnly()) {
            builder.addInvisibleIngredients(OUTPUT).add(new ItemStack(definition.station().get()));
            return;
        }
        for (MachineJeiRecipe.Slot slot : recipe.slots()) {
            if (!slot.fluids().isEmpty()) {
                // The visible representation is the colocated gauge; retain the ingredient for JEI lookup/focus.
                var invisibleIngredients = builder.addInvisibleIngredients(slot.role());
                for (MachineJeiRecipe.FluidAmount fluid : slot.fluids()) {
                    invisibleIngredients.add(fluid.fluid(), fluid.amount());
                }
                continue;
            }
            var slotBuilder = builder.addSlot(slot.role(), slot.x(), slot.y());
            // Machine GUI slots are 16x16 for every role; do not use JEI's oversized crafting-output frame.
            slotBuilder.setStandardSlotBackground();

            if (!slot.itemStacks().isEmpty()) {
                slotBuilder.addItemStacks(slot.itemStacks());
            } else if (slot.display() != null) {
                slotBuilder.add(slot.display());
            }

            if (slot.chancePermille() < 1_000) {
                slotBuilder.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.empty()
                    .append(Component.translatable("jei.gonzotech.output_chance").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                    .append(GtUnits.num(GtUnits.x1(slot.chancePermille() / 10.0), GtUnits.ITEM))
                    .append(Component.literal("%").withColor(GtUnits.ITEM))));
            }
            for (Component extra : slot.tooltips()) {
                slotBuilder.addRichTooltipCallback((view, tooltip) -> tooltip.add(extra));
            }
        }
    }

    @Override
    public void draw(MachineJeiRecipe recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        if (!recipe.gauges().isEmpty()) {
            double operationProgress = (System.currentTimeMillis() % GAUGE_CYCLE_MILLIS) / (double) GAUGE_CYCLE_MILLIS;
            for (MachineJeiRecipe.Gauge gauge : recipe.gauges()) {
                drawGauge(graphics, gauge, gauge.fillFraction(operationProgress));
            }
        }
        // Mirror the in-game Z stack: GUI background, animated bars, then the
        // foreground window that masks the bars to its authored cut-outs.
        if (machineGuiForeground != null) {
            machineGuiForeground.draw(graphics, 0, 0);
        }

        // Only the world-reaction category carries explanatory text; machine pages stay uncluttered.
        if (!definition.textOnly() || recipe.notes().isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int maxWidth = getWidth() - 16;
        List<net.minecraft.util.FormattedCharSequence> lines = recipe.notes().stream()
            .flatMap(note -> font.split(note, maxWidth).stream())
            .toList();
        if (lines.isEmpty()) return;

        int lineHeight = 9;
        int firstY = Math.max(4, getHeight() - 7 - lines.size() * lineHeight);
        int boxTop = firstY - 3;
        graphics.fill(4, boxTop, getWidth() - 4, getHeight() - 4, 0xA0000000);
        int y = firstY;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            graphics.drawString(font, line, 8, y, 0xFFFFFFFF, false);
            y += lineHeight;
        }
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, MachineJeiRecipe recipe,
                           mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlotsView,
                           double mouseX, double mouseY) {
        for (MachineJeiRecipe.Gauge gauge : recipe.gauges()) {
            if (!gauge.contains(mouseX, mouseY)) continue;
            for (Component line : gauge.tooltip()) {
                tooltip.add(line);
            }
            return;
        }
    }

    private static final long GAUGE_CYCLE_MILLIS = 5_000L;

    private static void drawGauge(GuiGraphics graphics, MachineJeiRecipe.Gauge gauge, float fraction) {
        int width = gauge.width();
        int height = gauge.height();
        int x = gauge.x();
        int y = gauge.y();
        boolean vertical = gauge.direction() == MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP
            || gauge.direction() == MachineJeiRecipe.GaugeDirection.VERTICAL_TOP_TO_BOTTOM;
        int filled = Math.round(Math.max(0.0F, Math.min(1.0F, fraction)) * (vertical ? height : width));
        if (filled <= 0) return;

        int clipLeft = x;
        int clipTop = y;
        int clipRight = x + width;
        int clipBottom = y + height;
        switch (gauge.direction()) {
            case VERTICAL_BOTTOM_TO_TOP -> clipTop = y + height - filled;
            case VERTICAL_TOP_TO_BOTTOM -> clipBottom = y + filled;
            case HORIZONTAL_LEFT_TO_RIGHT -> clipRight = x + filled;
            case HORIZONTAL_RIGHT_TO_LEFT -> clipLeft = x + width - filled;
        }
        List<MachineJeiRecipe.TooltipArea> renderAreas = gauge.tooltipAreas().isEmpty()
            ? List.of(new MachineJeiRecipe.TooltipArea(x, y, width, height))
            : gauge.tooltipAreas();
        for (MachineJeiRecipe.TooltipArea area : renderAreas) {
            int areaLeft = Math.max(clipLeft, area.x());
            int areaTop = Math.max(clipTop, area.y());
            int areaRight = Math.min(clipRight, area.x() + area.width());
            int areaBottom = Math.min(clipBottom, area.y() + area.height());
            if (areaLeft >= areaRight || areaTop >= areaBottom) continue;

            graphics.enableScissor(areaLeft, areaTop, areaRight, areaBottom);
            switch (gauge.direction()) {
                case VERTICAL_BOTTOM_TO_TOP -> {
                    for (int tileY = y + height - 16; tileY > y - 16; tileY -= 16) {
                        for (int tileX = x; tileX < x + width; tileX += 16) {
                            blitGaugeTile(graphics, gauge.texture(), tileX, tileY);
                        }
                    }
                }
                case VERTICAL_TOP_TO_BOTTOM, HORIZONTAL_LEFT_TO_RIGHT, HORIZONTAL_RIGHT_TO_LEFT -> {
                    for (int tileY = y; tileY < y + height; tileY += 16) {
                        for (int tileX = x; tileX < x + width; tileX += 16) {
                            blitGaugeTile(graphics, gauge.texture(), tileX, tileY);
                        }
                    }
                }
            }
            graphics.disableScissor();
        }
    }

    private static void blitGaugeTile(GuiGraphics graphics, ResourceLocation texture, int x, int y) {
        graphics.blit(RenderType::guiTextured, texture, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    @Override
    public ResourceLocation getRegistryName(MachineJeiRecipe recipe) {
        return recipe.id();
    }
}
