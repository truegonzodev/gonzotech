package com.gonzotech.integration.jei;

import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** One display-only recipe for a machine category; the machine remains authoritative. */
public final class MachineJeiRecipe {
    private final ResourceLocation id;
    private final List<Slot> slots;
    private final List<Component> notes;
    private final List<Gauge> gauges;

    public MachineJeiRecipe(ResourceLocation id, List<Slot> slots, List<Component> notes) {
        this(id, slots, notes, List.of());
    }

    public MachineJeiRecipe(ResourceLocation id, List<Slot> slots, List<Component> notes, List<Gauge> gauges) {
        this.id = Objects.requireNonNull(id, "id");
        this.slots = List.copyOf(slots);
        this.notes = List.copyOf(notes);
        this.gauges = List.copyOf(gauges);
    }

    public ResourceLocation id() {
        return id;
    }

    public List<Slot> slots() {
        return slots;
    }

    public List<Component> notes() {
        return notes;
    }

    public List<Gauge> gauges() {
        return gauges;
    }

    public enum GaugeDirection {
        VERTICAL_BOTTOM_TO_TOP,
        VERTICAL_TOP_TO_BOTTOM,
        HORIZONTAL_LEFT_TO_RIGHT,
        HORIZONTAL_RIGHT_TO_LEFT
    }

    public enum GaugeMotion {
        CONSUME,
        PRODUCE,
        STATIC
    }

    /** A visual-only bar with a stable operation, active-tick, or static-fluid tooltip. */
    public record Gauge(int x, int y, int width, int height, ResourceLocation texture,
                        GaugeDirection direction, GaugeMotion motion, List<Component> tooltip,
                        double cycleStart, double cycleEnd, List<TooltipArea> tooltipAreas) {
        public Gauge(int x, int y, int width, int height, ResourceLocation texture,
                     GaugeDirection direction, GaugeMotion motion, List<Component> tooltip) {
            this(x, y, width, height, texture, direction, motion, tooltip, 0.0, 1.0, List.of());
        }

        public Gauge(int x, int y, int width, int height, ResourceLocation texture,
                     GaugeDirection direction, GaugeMotion motion, List<Component> tooltip,
                     double cycleStart, double cycleEnd) {
            this(x, y, width, height, texture, direction, motion, tooltip, cycleStart, cycleEnd, List.of());
        }

        public Gauge {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Gauge dimensions must be positive");
            if (cycleStart < 0.0 || cycleEnd > 1.0 || cycleStart >= cycleEnd) {
                throw new IllegalArgumentException("Gauge cycle interval must be within [0, 1] and non-empty");
            }
            Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(motion, "motion");
            tooltip = List.copyOf(tooltip);
            tooltipAreas = List.copyOf(tooltipAreas);
            for (int index = 0; index < tooltipAreas.size(); index++) {
                TooltipArea area = tooltipAreas.get(index);
                if (area.x() < x || area.y() < y || area.x() + area.width() > x + width
                    || area.y() + area.height() > y + height) {
                    throw new IllegalArgumentException("Gauge render/tooltip area must fit inside the gauge");
                }
                for (int other = index + 1; other < tooltipAreas.size(); other++) {
                    TooltipArea next = tooltipAreas.get(other);
                    boolean overlaps = area.x() < next.x() + next.width() && area.x() + area.width() > next.x()
                        && area.y() < next.y() + next.height() && area.y() + area.height() > next.y();
                    if (overlaps) throw new IllegalArgumentException("Gauge render/tooltip areas must not overlap");
                }
            }
        }

        public float fillFraction(double operationProgress) {
            if (motion == GaugeMotion.STATIC) return 1.0F;
            double phaseProgress = (operationProgress - cycleStart) / (cycleEnd - cycleStart);
            float progress = (float) Math.max(0.0, Math.min(1.0, phaseProgress));
            return motion == GaugeMotion.CONSUME ? 1.0F - progress : progress;
        }

        public boolean contains(double mouseX, double mouseY) {
            if (tooltipAreas.isEmpty()) {
                return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            }
            return tooltipAreas.stream().anyMatch(area -> area.contains(mouseX, mouseY));
        }
    }

    /** Independent render regions and matching tooltip hitboxes for non-rectangular gauges. */
    public record TooltipArea(int x, int y, int width, int height) {
        public TooltipArea {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Tooltip area must be positive");
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public static Slot items(RecipeIngredientRole role, int x, int y, List<ItemStack> stacks) {
        return new Slot(role, x, y, stacks, List.of(), null, 1_000, List.of());
    }

    public static Slot item(RecipeIngredientRole role, int x, int y, ItemStack stack) {
        return items(role, x, y, List.of(stack));
    }

    public static Slot item(RecipeIngredientRole role, int x, int y, ItemLike item) {
        return item(role, x, y, new ItemStack(item));
    }

    /** A deliberately blank machine slot, useful for showing an unused reusable tool position. */
    public static Slot empty(RecipeIngredientRole role, int x, int y) {
        return new Slot(role, x, y, List.of(), List.of(), null, 1_000, List.of());
    }

    public static Slot fluid(RecipeIngredientRole role, int x, int y, Fluid fluid, long amount) {
        return new Slot(role, x, y, List.of(), List.of(new FluidAmount(fluid, amount)), null, 1_000, List.of());
    }

    public static Slot display(RecipeIngredientRole role, int x, int y, SlotDisplay display) {
        return new Slot(role, x, y, List.of(), List.of(), display, 1_000, List.of());
    }

    public record FluidAmount(Fluid fluid, long amount) {
        public FluidAmount {
            Objects.requireNonNull(fluid, "fluid");
            if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive");
        }
    }

    public record Slot(
        RecipeIngredientRole role,
        int x,
        int y,
        List<ItemStack> itemStacks,
        List<FluidAmount> fluids,
        @Nullable SlotDisplay display,
        int chancePermille,
        List<Component> tooltips
    ) {
        public Slot {
            Objects.requireNonNull(role, "role");
            itemStacks = itemStacks.stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
            fluids = List.copyOf(fluids);
            tooltips = List.copyOf(tooltips);
            int contentKinds = (!itemStacks.isEmpty() ? 1 : 0) + (!fluids.isEmpty() ? 1 : 0) + (display != null ? 1 : 0);
            if (contentKinds > 1) throw new IllegalArgumentException("A JEI slot can contain only one ingredient kind");
            if (chancePermille < 0 || chancePermille > 1_000) throw new IllegalArgumentException("Chance must be in permille");
        }

        @Override
        public List<ItemStack> itemStacks() {
            return itemStacks.stream().map(ItemStack::copy).toList();
        }

        public Slot withChance(int permille) {
            return new Slot(role, x, y, itemStacks, fluids, display, permille, tooltips);
        }

        public Slot withTooltip(Component tooltip) {
            java.util.ArrayList<Component> next = new java.util.ArrayList<>(tooltips);
            next.add(Objects.requireNonNull(tooltip, "tooltip"));
            return new Slot(role, x, y, itemStacks, fluids, display, chancePermille, next);
        }
    }
}
