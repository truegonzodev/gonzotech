package com.gonzotech.integration.jei;

import com.gonzotech.chalkboard.advancement.RecipeUnlocks;
import com.gonzotech.core.tooltip.GateRequirement;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.List;

/** Adds a non-blocking gate marker to the vanilla crafting category only. */
public final class GatedCraftingJeiExtension implements ICraftingCategoryExtension<CraftingRecipe> {
    private static final int MARKER_LEFT = 104;
    private static final int MARKER_WIDTH = 12;
    private static final int MARKER_HEIGHT = 12;

    @Override
    public boolean isHandled(RecipeHolder<CraftingRecipe> holder) {
        if (holder.value().isSpecial() || gate(holder).equals(GateRequirement.NONE)) return false;
        return firstDisplay(holder) instanceof ShapedCraftingRecipeDisplay
            || firstDisplay(holder) instanceof ShapelessCraftingRecipeDisplay;
    }

    @Override
    public List<SlotDisplay> getIngredients(RecipeHolder<CraftingRecipe> holder) {
        RecipeDisplay display = firstDisplay(holder);
        if (display instanceof ShapedCraftingRecipeDisplay shaped) return shaped.ingredients();
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) return shapeless.ingredients();
        return List.of();
    }

    @Override
    public int getWidth(RecipeHolder<CraftingRecipe> holder) {
        return holder.value() instanceof ShapedRecipe shaped ? shaped.getWidth() : 0;
    }

    @Override
    public int getHeight(RecipeHolder<CraftingRecipe> holder) {
        return holder.value() instanceof ShapedRecipe shaped ? shaped.getHeight() : 0;
    }

    @Override
    public void drawInfo(RecipeHolder<CraftingRecipe> holder, int recipeWidth, int recipeHeight,
                         GuiGraphics graphics, double mouseX, double mouseY) {
        graphics.drawString(Minecraft.getInstance().font, "!", recipeWidth - 9, 2, 0xFFFF5555, true);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, RecipeHolder<CraftingRecipe> holder,
                           double mouseX, double mouseY) {
        if (mouseX < MARKER_LEFT || mouseX >= MARKER_LEFT + MARKER_WIDTH
            || mouseY < 0 || mouseY >= MARKER_HEIGHT) return;
        tooltip.add(Component.translatable("tooltip.gonzotech.gate.craft", describe(gate(holder)))
            .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static GateRequirement gate(RecipeHolder<CraftingRecipe> holder) {
        return RecipeUnlocks.gateForRecipe(holder.id().location());
    }

    private static RecipeDisplay firstDisplay(RecipeHolder<CraftingRecipe> holder) {
        List<RecipeDisplay> displays = holder.value().display();
        return displays.isEmpty() ? null : displays.getFirst();
    }

    private static Component describe(GateRequirement requirement) {
        MutableComponent description = requirement.discovery() > 0
            ? Component.translatable("tooltip.gonzotech.gate.discovery", requirement.discovery())
            : Component.empty();
        if (requirement.extra() != GateRequirement.Extra.NONE) {
            if (requirement.discovery() > 0) description.append(" + ");
            description.append(Component.translatable(requirement.extra() == GateRequirement.Extra.SUN_EVENT
                ? "tooltip.gonzotech.gate.sun_event"
                : "tooltip.gonzotech.gate.play_time"));
        }
        return description;
    }
}
