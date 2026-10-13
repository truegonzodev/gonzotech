package com.gonzotech.integration.jei;

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
import java.util.Locale;

/** Renderer shared by every machine processing category. */
public final class MachineJeiRecipeCategory implements IRecipeCategory<MachineJeiRecipe> {
    private final MachineJeiCategories.Definition definition;
    private final IDrawable machineGui;
    private final IDrawable icon;

    public MachineJeiRecipeCategory(IGuiHelper guiHelper, MachineJeiCategories.Definition definition) {
        this.definition = definition;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
            "gonzotech", "textures/gui/" + definition.texture() + "_gui.png");
        this.machineGui = guiHelper.drawableBuilder(texture, 128, 128, definition.width(), definition.height())
            .setTextureSize(512, 512)
            .build();
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
        return machineGui;
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
        for (MachineJeiRecipe.Slot slot : recipe.slots()) {
            var slotBuilder = builder.addSlot(slot.role(), slot.x(), slot.y());
            // Machine GUI slots are 16x16 for every role; do not use JEI's oversized crafting-output frame.
            slotBuilder.setStandardSlotBackground();

            if (!slot.itemStacks().isEmpty()) {
                slotBuilder.addItemStacks(slot.itemStacks());
            } else if (!slot.fluids().isEmpty()) {
                slotBuilder.setFluidRenderer(slot.rendererCapacity(), true, 16, 16);
                for (MachineJeiRecipe.FluidAmount fluid : slot.fluids()) {
                    slotBuilder.add(fluid.fluid(), fluid.amount());
                }
            } else if (slot.display() != null) {
                slotBuilder.add(slot.display());
            }

            if (slot.chancePermille() < 1_000) {
                slotBuilder.addRichTooltipCallback((view, tooltip) -> tooltip.add(
                    Component.translatable("jei.gonzotech.output_chance",
                        String.format(Locale.ROOT, "%.1f%%", slot.chancePermille() / 10.0))
                        .withStyle(ChatFormatting.GRAY)));
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

        if (recipe.notes().isEmpty()) return;
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
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);

        switch (gauge.direction()) {
            case VERTICAL_BOTTOM_TO_TOP -> {
                for (int tileY = y + height - 16; tileY > y - 16; tileY -= 16) {
                    for (int tileX = x; tileX < x + width; tileX += 16) {
                        blitGaugeTile(graphics, gauge.texture(), tileX, tileY);
                    }
                }
            }
            case VERTICAL_TOP_TO_BOTTOM -> {
                for (int tileY = y; tileY < y + height; tileY += 16) {
                    for (int tileX = x; tileX < x + width; tileX += 16) {
                        blitGaugeTile(graphics, gauge.texture(), tileX, tileY);
                    }
                }
            }
            case HORIZONTAL_LEFT_TO_RIGHT -> {
                for (int tileY = y; tileY < y + height; tileY += 16) {
                    for (int tileX = x; tileX < x + width; tileX += 16) {
                        blitGaugeTile(graphics, gauge.texture(), tileX, tileY);
                    }
                }
            }
            case HORIZONTAL_RIGHT_TO_LEFT -> {
                for (int tileY = y; tileY < y + height; tileY += 16) {
                    for (int tileX = x; tileX < x + width; tileX += 16) {
                        blitGaugeTile(graphics, gauge.texture(), tileX, tileY);
                    }
                }
            }
        }
        graphics.disableScissor();
    }

    private static void blitGaugeTile(GuiGraphics graphics, ResourceLocation texture, int x, int y) {
        graphics.blit(RenderType::guiTextured, texture, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    @Override
    public ResourceLocation getRegistryName(MachineJeiRecipe recipe) {
        return recipe.id();
    }
}
