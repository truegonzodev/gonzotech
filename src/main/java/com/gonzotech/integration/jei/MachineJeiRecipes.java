package com.gonzotech.integration.jei;

import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.item.AmpouleItem;
import com.gonzotech.core.item.CanisterItem;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.block.entity.DistillerBlockEntity;
import com.gonzotech.machines.block.entity.FermentationVatBlockEntity;
import com.gonzotech.machines.block.entity.FillerBlockEntity;
import com.gonzotech.machines.block.entity.RectifierBlockEntity;
import com.gonzotech.machines.block.entity.WortKettleBlockEntity;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.litho.SiliconFactoryBlockEntity;
import com.gonzotech.machines.processing.AlloyFoundryRecipes;
import com.gonzotech.machines.processing.CentrifugeRecipes;
import com.gonzotech.machines.processing.ChemicalPlantRecipes;
import com.gonzotech.machines.processing.CrusherRecipes;
import com.gonzotech.machines.processing.GrinderRecipes;
import com.gonzotech.machines.processing.PCFSOZRecipes;
import com.gonzotech.machines.processing.PressRecipes;
import com.gonzotech.machines.processing.ProcessingRecipeDisplay;
import mezz.jei.api.constants.RecipeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static mezz.jei.api.recipe.RecipeIngredientRole.CRAFTING_STATION;
import static mezz.jei.api.recipe.RecipeIngredientRole.INPUT;
import static mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT;
import static mezz.jei.api.recipe.RecipeIngredientRole.RENDER_ONLY;

/** Builds immutable, display-only JEI recipes from the live machine tables. */
public final class MachineJeiRecipes {
    private static final int[][] FOUR_OUTPUT_SLOTS = {{116, 17}, {152, 17}, {116, 53}, {152, 53}};

    private MachineJeiRecipes() {
    }

    public static Map<String, List<MachineJeiRecipe>> build() {
        Map<String, List<MachineJeiRecipe>> recipes = new LinkedHashMap<>();
        for (MachineJeiCategories.Definition definition : MachineJeiCategories.ALL) {
            recipes.put(definition.id(), new ArrayList<>());
        }

        addItemProcessor(recipes, MachineJeiCategories.SECOND_CRUSHER,
            CrusherRecipes.displayRecipes(), 62, 35, FOUR_OUTPUT_SLOTS, List.of());
        addItemProcessor(recipes, MachineJeiCategories.SECOND_CENTRIFUGE,
            CentrifugeRecipes.displayRecipes(), 62, 35, FOUR_OUTPUT_SLOTS, List.of());
        addItemProcessor(recipes, MachineJeiCategories.THIRD_PCFSOZ,
            PCFSOZRecipes.displayRecipes(), 62, 35, FOUR_OUTPUT_SLOTS, List.of());
        addItemProcessor(recipes, MachineJeiCategories.SECOND_GRINDER,
            GrinderRecipes.displayRecipes(), 44, 35, new int[][] {{116, 35}}, List.of());
        addPressRecipes(recipes);
        addAlloyRecipes(recipes);
        addChemicalPlantRecipes(recipes);
        addFillerRecipes(recipes);
        addSiliconFactoryRecipes(recipes);
        addFermentationRecipes(recipes);
        addWortKettleRecipes(recipes);
        addDistillerRecipes(recipes);
        addRectifierRecipe(recipes);
        addDispensingTapRecipes(recipes);
        addCrimsonObsidianRecipe(recipes);

        Map<String, List<MachineJeiRecipe>> result = new LinkedHashMap<>();
        for (var entry : recipes.entrySet()) {
            entry.getValue().sort(Comparator.comparing(recipe -> recipe.id().toString()));
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    /** Projects all vanilla smelting recipes without altering their original JEI category. */
    public static List<MachineJeiRecipe> vanillaSmelting(Stream<RecipeHolder<SmeltingRecipe>> smeltingRecipes,
                                                          boolean secondFurnace) {
        List<MachineJeiRecipe> result = new ArrayList<>();
        smeltingRecipes.forEach(holder -> {
            List<RecipeDisplay> displays = holder.value().display();
            FurnaceRecipeDisplay furnace = displays.stream()
                .filter(FurnaceRecipeDisplay.class::isInstance)
                .map(FurnaceRecipeDisplay.class::cast)
                .findFirst()
                .orElse(null);
            if (furnace == null) return;

            MachineJeiCategories.Definition definition = secondFurnace
                ? MachineJeiCategories.SECOND_ELECTRIC_FURNACE
                : MachineJeiCategories.FIRST_ELECTRIC_FURNACE;
            int inputX = secondFurnace ? 44 : 44;
            int inputY = secondFurnace ? 17 : 35;
            int outputX = secondFurnace ? 116 : 116;
            int outputY = secondFurnace ? 17 : 35;
            String sourceId = holder.id().location().toString().replace(':', '_');
            result.add(new MachineJeiRecipe(
                recipeId(definition, "smelting/" + sourceId),
                List.of(
                    MachineJeiRecipe.display(INPUT, inputX, inputY, furnace.ingredient()),
                    MachineJeiRecipe.display(OUTPUT, outputX, outputY, furnace.result())
                ),
                List.of(),
                electricFurnaceGauges(secondFurnace)));
        });
        result.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        return List.copyOf(result);
    }

    private static List<MachineJeiRecipe.Gauge> electricFurnaceGauges(boolean secondFurnace) {
        long energyMilli = secondFurnace ? SecondTierDefs.ELECTRIC_GTU_PER_ITEM : MachineDefs.ELECTRIC_GTU_PER_ITEM;
        int cookingTicks = secondFurnace ? SecondTierDefs.ELECTRIC_COOK_TIME : MachineDefs.ELECTRIC_COOK_TIME;
        ResourceLocation gtuTexture = barTexture("bar_gtu");
        ResourceLocation smeltingTexture = barTexture("bar_smelting");
        List<Component> energyTooltip = operationEnergyTooltip(energyMilli, false);
        List<Component> smeltingTooltip = durationTooltip("smelting", cookingTicks);

        if (secondFurnace) {
            return List.of(
                gauge(8, 17, 16, 52, gtuTexture, MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME, energyTooltip),
                gauge(63, 18, 50, 14, smeltingTexture, MachineJeiRecipe.GaugeDirection.HORIZONTAL_LEFT_TO_RIGHT,
                    MachineJeiRecipe.GaugeMotion.PRODUCE, smeltingTooltip));
        }
        return List.of(
            gauge(8, 17, 16, 52, gtuTexture, MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                MachineJeiRecipe.GaugeMotion.CONSUME, energyTooltip),
            gauge(63, 36, 50, 14, smeltingTexture, MachineJeiRecipe.GaugeDirection.HORIZONTAL_LEFT_TO_RIGHT,
                MachineJeiRecipe.GaugeMotion.PRODUCE, smeltingTooltip));
    }

    private static List<MachineJeiRecipe.Gauge> processorGauges(String categoryId) {
        ResourceLocation gtu = barTexture("bar_gtu");
        ResourceLocation hotWater = barTexture("bar_hot_water");
        ResourceLocation progress = barTexture("bar_washing");
        var vertical = MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP;
        var horizontal = MachineJeiRecipe.GaugeDirection.HORIZONTAL_LEFT_TO_RIGHT;
        var consume = MachineJeiRecipe.GaugeMotion.CONSUME;
        var produce = MachineJeiRecipe.GaugeMotion.PRODUCE;

        return switch (categoryId) {
            case "second_crusher" -> crusherGauges();
            case "second_centrifuge" -> List.of(
                gauge(8, 17, 16, 52, hotWater, vertical, consume,
                    operationFluidTooltip(MachineDefs.CENTRIFUGE_HOT_WATER_PER_WASH, "hot_water")),
                gauge(26, 17, 16, 52, gtu, vertical, consume,
                    operationEnergyTooltip(MachineDefs.CENTRIFUGE_GTU_MILLI_PER_WASH, false)),
                gauge(81, 35, 33, 16, progress, horizontal, produce,
                    durationTooltip("washing", MachineDefs.CENTRIFUGE_WASH_TICKS)));
            case "third_pcfsoz" -> List.of(
                gauge(8, 17, 16, 52, hotWater, vertical, consume,
                    operationFluidTooltip((long) MachineDefs.PCFSOZ_HOT_WATER_PER_TICK
                        * MachineDefs.PCFSOZ_SEPARATION_TICKS, "hot_water")),
                gauge(26, 17, 16, 52, gtu, vertical, consume,
                    operationEnergyTooltip((long) MachineDefs.PCFSOZ_GTU_MILLI_PER_TICK
                        * MachineDefs.PCFSOZ_SEPARATION_TICKS, false)),
                gauge(81, 35, 33, 16, progress, horizontal, produce,
                    durationTooltip("separation", MachineDefs.PCFSOZ_SEPARATION_TICKS)));
            case "second_grinder" -> List.of(
                gauge(8, 17, 16, 52, gtu, vertical, consume,
                    operationEnergyTooltip((long) SecondTierDefs.GRINDER_GTU_MILLI_PER_TICK
                        * SecondTierDefs.GRINDER_TICKS, false)),
                gauge(63, 36, 50, 14, barTexture("bar_smelting"), horizontal, produce,
                    durationTooltip("grinding", SecondTierDefs.GRINDER_TICKS)));
            default -> List.of();
        };
    }

    private static List<MachineJeiRecipe.Gauge> crusherGauges() {
        int fastestTicks = divideRoundUp(MachineDefs.CRUSHER_PROGRESS_TOTAL,
            MachineDefs.crusherProgressUnitsPerTick(MachineDefs.CRUSHER_GTU_CAPACITY));
        int slowestTicks = divideRoundUp(MachineDefs.CRUSHER_PROGRESS_TOTAL,
            MachineDefs.crusherProgressUnitsPerTick(0));
        return List.of(
            gauge(8, 17, 16, 52, barTexture("bar_gtu"),
                MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                MachineJeiRecipe.GaugeMotion.CONSUME,
                activeTickEnergyRangeTooltip(MachineDefs.CRUSHER_GTU_MILLI_PER_TICK_MIN,
                    MachineDefs.CRUSHER_GTU_MILLI_PER_TICK_MAX, false)),
            gauge(81, 35, 33, 16, barTexture("bar_crushing"),
                MachineJeiRecipe.GaugeDirection.HORIZONTAL_LEFT_TO_RIGHT,
                MachineJeiRecipe.GaugeMotion.PRODUCE,
                List.of(chargeDurationTooltip(100, fastestTicks), chargeDurationTooltip(0, slowestTicks))));
    }

    private static int divideRoundUp(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    private static MachineJeiRecipe.Gauge gauge(int x, int y, int width, int height, ResourceLocation texture,
                                                 MachineJeiRecipe.GaugeDirection direction,
                                                 MachineJeiRecipe.GaugeMotion motion, List<Component> tooltip) {
        return new MachineJeiRecipe.Gauge(x, y, width, height, texture, direction, motion, tooltip);
    }

    private static MachineJeiRecipe.Gauge gauge(int x, int y, int width, int height, ResourceLocation texture,
                                                 MachineJeiRecipe.GaugeDirection direction,
                                                 MachineJeiRecipe.GaugeMotion motion, List<Component> tooltip,
                                                 double cycleStart, double cycleEnd) {
        return new MachineJeiRecipe.Gauge(x, y, width, height, texture, direction, motion, tooltip,
            cycleStart, cycleEnd);
    }

    private static ResourceLocation barTexture(String name) {
        return ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/" + name + ".png");
    }

    private static int fluidColor(String fluidId) {
        return CanisterItem.getFluidColor(fluidId);
    }

    private static String fluidBarTexture(String fluidId) {
        return switch (fluidId) {
            case "water" -> "bar_water";
            case "rectificate" -> "bar_rectificate";
            case "sulfuric_acid" -> "bar_sulfuric_acid";
            case "ethylene" -> "bar_ethylene";
            case "aminoblazeethanol" -> "bar_aminoblazeethanol";
            case "formaldehyde" -> "bar_formaldehyde";
            case "mash" -> "bar_mash";
            case "wort" -> "bar_wort";
            case "distillate" -> "bar_distillate";
            case "hot_water" -> "bar_hot_water";
            case "poison_potion" -> "bar_poison";
            default -> throw new IllegalArgumentException("No JEI bar texture for fluid " + fluidId);
        };
    }

    private static String fluidId(Fluid fluid) {
        if (fluid == net.minecraft.world.level.material.Fluids.WATER) return "water";
        if (fluid == ModFluids.ETHANOL.get()) return "rectificate";
        if (fluid == ModFluids.SULFURIC_ACID.get()) return "sulfuric_acid";
        if (fluid == ModFluids.DISTILLATE.get()) return "distillate";
        if (fluid == ModFluids.FORMALDEHYDE.get()) return "formaldehyde";
        if (fluid == ModFluids.MASH.get()) return "mash";
        if (fluid == ModFluids.WORT.get()) return "wort";
        throw new IllegalArgumentException("No JEI identifier for fluid " + fluid);
    }

    private static Fluid registeredFluid(String fluidId) {
        return switch (fluidId) {
            case "water" -> net.minecraft.world.level.material.Fluids.WATER;
            case "rectificate" -> ModFluids.ETHANOL.get();
            case "sulfuric_acid" -> ModFluids.SULFURIC_ACID.get();
            case "formaldehyde" -> ModFluids.FORMALDEHYDE.get();
            case "mash" -> ModFluids.MASH.get();
            case "wort" -> ModFluids.WORT.get();
            case "distillate" -> ModFluids.DISTILLATE.get();
            default -> null;
        };
    }

    private static List<Component> operationEnergyTooltip(long milliUnits, boolean heat) {
        return List.of(energyAmountLine("total_per_operation", milliUnits, heat));
    }

    private static List<Component> activeTickEnergyCostTooltip(long milliUnits, boolean heat) {
        return List.of(energyAmountLine("active_tick", milliUnits, heat));
    }

    private static List<Component> activeTickEnergyLimitTooltip(long milliUnits, boolean heat) {
        int color = heat ? GtUnits.GTH : GtUnits.GTU;
        String unitKey = heat ? GtUnits.U_GTH : GtUnits.U_GTU;
        return List.of(Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge.active_tick").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(unitKey, color))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.up_to").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(milliUnits / (double) MachineDefs.MILLI), color)));
    }

    private static List<Component> activeTickEnergyRangeTooltip(long minMilliUnits, long maxMilliUnits, boolean heat) {
        int color = heat ? GtUnits.GTH : GtUnits.GTU;
        String unitKey = heat ? GtUnits.U_GTH : GtUnits.U_GTU;
        return List.of(Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge.active_tick").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(unitKey, color))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(minMilliUnits / (double) MachineDefs.MILLI), color))
            .append(Component.literal("–").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(maxMilliUnits / (double) MachineDefs.MILLI), color)));
    }

    private static Component energyAmountLine(String labelKey, long milliUnits, boolean heat) {
        int color = heat ? GtUnits.GTH : GtUnits.GTU;
        String unitKey = heat ? GtUnits.U_GTH : GtUnits.U_GTU;
        return Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge." + labelKey).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(unitKey, color))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(milliUnits / (double) MachineDefs.MILLI), color));
    }

    private static List<Component> operationFluidTooltip(long milliBuckets, String fluidId) {
        return List.of(fluidAmountLine("total_per_operation", milliBuckets, fluidColor(fluidId)));
    }

    private static List<Component> activeTickFluidTooltip(long milliBuckets, String fluidId) {
        return List.of(fluidAmountLine("active_tick", milliBuckets, fluidColor(fluidId)));
    }

    private static List<Component> activeTickFluidLimitTooltip(long milliBuckets, String fluidId) {
        return List.of(fluidAmountLine("active_tick", milliBuckets, fluidColor(fluidId), true));
    }

    private static List<Component> activeTickFluidRangeTooltip(long minMilliBuckets, long maxMilliBuckets,
                                                                String fluidId) {
        int color = fluidColor(fluidId);
        return List.of(Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge.active_tick").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(minMilliBuckets, color))
            .append(Component.literal("–").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(maxMilliBuckets, color))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(GtUnits.U_MB, color)));
    }

    private static Component fluidAmountLine(String labelKey, long milliBuckets, int color) {
        return fluidAmountLine(labelKey, milliBuckets, color, false);
    }

    private static Component fluidAmountLine(String labelKey, long milliBuckets, int color, boolean limit) {
        Component amount = GtUnits.num(milliBuckets, color);
        Component unit = GtUnits.key(GtUnits.U_MB, color);
        return Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge." + labelKey).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(limit
                ? Component.translatable("jei.gonzotech.gauge.up_to").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                : Component.empty())
            .append(amount)
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(unit);
    }

    private static List<Component> operationEnergyRangeTooltip(long minMilliUnits, long maxMilliUnits) {
        int color = GtUnits.GTU;
        return List.of(Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge.total_per_operation").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(GtUnits.U_GTU, color))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(minMilliUnits / (double) MachineDefs.MILLI), color))
            .append(Component.literal("–").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(maxMilliUnits / (double) MachineDefs.MILLI), color)));
    }

    private static List<Component> durationTooltip(String processKey, int ticks) {
        return List.of(durationLine(processKey, ticks));
    }

    private static List<Component> durationRangeTooltip(String processKey, int minTicks, int maxTicks) {
        Component process = Component.translatable("jei.gonzotech.gauge.process." + processKey)
            .withStyle(ChatFormatting.WHITE);
        return List.of(Component.empty()
            .append(process)
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(Integer.toString(minTicks)).withStyle(ChatFormatting.WHITE))
            .append(Component.literal("–").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(Integer.toString(maxTicks)).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.ticks").withStyle(ChatFormatting.GRAY)));
    }

    private static Component durationLine(String processKey, int ticks) {
        return Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge.process." + processKey).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(Integer.toString(ticks)).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.ticks").withStyle(ChatFormatting.GRAY));
    }

    private static Component chargeDurationTooltip(int chargePercent, int ticks) {
        return Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge.start_charge").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(chargePercent, GtUnits.GTU))
            .append(Component.literal("%: ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(Integer.toString(ticks)).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.ticks").withStyle(ChatFormatting.GRAY));
    }

    private static MachineJeiRecipe.Gauge fluidGauge(int x, int y, String texture,
                                                       MachineJeiRecipe.GaugeMotion motion,
                                                       List<Component> tooltip) {
        return gauge(x, y, 16, 16, barTexture(texture),
            MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP, motion, tooltip);
    }

    private static MachineJeiRecipe.Gauge fluidGauge(int x, int y, Fluid fluid,
                                                       MachineJeiRecipe.GaugeMotion motion,
                                                       List<Component> tooltip) {
        return fluidGauge(x, y, fluidBarTexture(fluidId(fluid)), motion, tooltip);
    }

    private static MachineJeiRecipe.Gauge fillerTankGauge(int x, String fluidId,
                                                           MachineJeiRecipe.GaugeMotion motion,
                                                           List<Component> tooltip) {
        List<MachineJeiRecipe.TooltipArea> hitboxes = switch (x) {
            case 55 -> List.of(new MachineJeiRecipe.TooltipArea(55, 17, 4, 52),
                new MachineJeiRecipe.TooltipArea(59, 36, 12, 14));
            case 105 -> List.of(new MachineJeiRecipe.TooltipArea(117, 17, 4, 52),
                new MachineJeiRecipe.TooltipArea(105, 36, 12, 14));
            default -> throw new IllegalArgumentException("Filler tank gauge must use x=55 or x=105");
        };
        return new MachineJeiRecipe.Gauge(x, 17, 16, 52, barTexture(fluidBarTexture(fluidId)),
            MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP, motion, tooltip, 0.0, 1.0, hitboxes);
    }

    private static void addItemProcessor(Map<String, List<MachineJeiRecipe>> target,
                                         MachineJeiCategories.Definition definition,
                                         List<ProcessingRecipeDisplay> displays,
                                         int inputX, int inputY, int[][] outputPositions,
                                         List<Component> notes) {
        for (ProcessingRecipeDisplay display : displays) {
            List<List<ItemStack>> inputs = display.inputSlots();
            if (inputs.isEmpty() || inputs.getFirst().isEmpty()) continue;

            List<MachineJeiRecipe.Slot> slots = new ArrayList<>();
            slots.add(MachineJeiRecipe.items(INPUT, inputX, inputY, inputs.getFirst()));
            List<ProcessingRecipeDisplay.Output> outputs = display.outputs();
            for (int index = 0; index < outputs.size() && index < outputPositions.length; index++) {
                ProcessingRecipeDisplay.Output output = outputs.get(index);
                int[] position = outputPositions[index];
                slots.add(MachineJeiRecipe.item(OUTPUT, position[0], position[1], output.stack())
                    .withChance(output.chancePermille()));
            }
            target.get(definition.id()).add(new MachineJeiRecipe(
                recipeId(definition, display.id()), slots, notes, processorGauges(definition.id())));
        }
    }

    private static void addPressRecipes(Map<String, List<MachineJeiRecipe>> target) {
        for (ProcessingRecipeDisplay display : PressRecipes.displayRecipes()) {
            List<List<ItemStack>> inputSlots = display.inputSlots();
            if (inputSlots.isEmpty() || inputSlots.getFirst().isEmpty()) continue;
            List<MachineJeiRecipe.Slot> slots = new ArrayList<>();
            slots.add(MachineJeiRecipe.items(INPUT, 44, 35, inputSlots.getFirst()));
            boolean consumesForm = display.id().startsWith("tvel/");
            if (inputSlots.size() > 1) {
                slots.add(inputSlots.get(1).isEmpty()
                    ? MachineJeiRecipe.empty(CRAFTING_STATION, 80, 53)
                    : MachineJeiRecipe.items(consumesForm ? INPUT : CRAFTING_STATION,
                        80, 53, inputSlots.get(1)));
            }
            if (inputSlots.size() > 2 && !inputSlots.get(2).isEmpty()) {
                slots.add(MachineJeiRecipe.items(CRAFTING_STATION, 80, 17, inputSlots.get(2)));
            }
            ProcessingRecipeDisplay.Output output = display.outputs().getFirst();
            slots.add(MachineJeiRecipe.item(OUTPUT, 116, 35, output.stack()));
            target.get(MachineJeiCategories.SECOND_PRESS.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.SECOND_PRESS, display.id()), slots,
                List.of(), List.of(
                    gauge(8, 17, 16, 52, barTexture("bar_gtu"),
                        MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                        MachineJeiRecipe.GaugeMotion.CONSUME,
                        operationEnergyTooltip(SecondTierDefs.PRESS_GTU_PER_STAMP, false)),
                    gauge(63, 36, 50, 14, barTexture("bar_smelting"),
                        MachineJeiRecipe.GaugeDirection.HORIZONTAL_LEFT_TO_RIGHT,
                        MachineJeiRecipe.GaugeMotion.PRODUCE,
                        durationTooltip("press_recovery", SecondTierDefs.PRESS_FATIGUE_TICKS)))));
        }
    }

    private static List<MachineJeiRecipe.Gauge> alloyGauges(List<List<ItemStack>> inputSlots) {
        int minItems = 0;
        int maxItems = 0;
        for (List<ItemStack> variants : inputSlots) {
            if (variants.isEmpty()) return List.of();
            minItems += variants.stream().mapToInt(ItemStack::getCount).min().orElse(0);
            maxItems += variants.stream().mapToInt(ItemStack::getCount).max().orElse(0);
        }
        int minTicks = SecondTierDefs.alloyFoundryTicksForIngredients(minItems);
        int maxTicks = SecondTierDefs.alloyFoundryTicksForIngredients(maxItems);
        long minEnergyMilli = (long) minTicks * SecondTierDefs.ALLOY_FOUNDRY_GTU_MILLI_PER_TICK;
        long maxEnergyMilli = (long) maxTicks * SecondTierDefs.ALLOY_FOUNDRY_GTU_MILLI_PER_TICK;
        return List.of(
            gauge(152, 15, 16, 110, barTexture("bar_gtu"),
                MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                MachineJeiRecipe.GaugeMotion.CONSUME,
                operationEnergyRangeTooltip(minEnergyMilli, maxEnergyMilli)),
            gauge(8, 109, 88, 16, barTexture("bar_smelting"),
                MachineJeiRecipe.GaugeDirection.HORIZONTAL_LEFT_TO_RIGHT,
                MachineJeiRecipe.GaugeMotion.PRODUCE,
                durationRangeTooltip("alloying", minTicks, maxTicks)));
    }

    private static void addAlloyRecipes(Map<String, List<MachineJeiRecipe>> target) {
        for (ProcessingRecipeDisplay display : AlloyFoundryRecipes.displayRecipes()) {
            List<MachineJeiRecipe.Slot> slots = new ArrayList<>();
            List<List<ItemStack>> inputSlots = display.inputSlots();
            for (int index = 0; index < inputSlots.size(); index++) {
                List<ItemStack> variants = inputSlots.get(index);
                int x = 8 + (index % 5) * 18;
                int y = 15 + (index / 5) * 18;
                slots.add(MachineJeiRecipe.items(INPUT, x, y, variants));
            }
            ProcessingRecipeDisplay.Output output = display.outputs().getFirst();
            slots.add(MachineJeiRecipe.item(OUTPUT, 116, 51, output.stack()));
            target.get(MachineJeiCategories.SECOND_ALLOY_FOUNDRY.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.SECOND_ALLOY_FOUNDRY, display.id()), slots,
                List.of(), alloyGauges(inputSlots)));
        }
    }

    private static void addChemicalPlantRecipes(Map<String, List<MachineJeiRecipe>> target) {
        ChemicalPlantRecipes.init();
        for (ChemicalPlantRecipes.ChemicalRecipe recipe : ChemicalPlantRecipes.RECIPES) {
            List<MachineJeiRecipe.Slot> slots = new ArrayList<>();
            for (int index = 0; index < recipe.ingredients().size() && index < 9; index++) {
                ChemicalPlantRecipes.IngredientMatcher ingredient = recipe.ingredients().get(index);
                slots.add(MachineJeiRecipe.items(INPUT, 62 + (index % 3) * 18,
                    17 + (index / 3) * 18, ingredient.displayStacks()));
            }
            if (recipe.catalystRequired() > 0) {
                Item platinum = ModItems.NUGGET_ITEMS.get("platinum_nugget").get();
                Item palladium = ModItems.NUGGET_ITEMS.get("palladium_nugget").get();
                slots.add(MachineJeiRecipe.items(INPUT, 26, 35,
                    List.of(new ItemStack(platinum, recipe.catalystRequired()),
                        new ItemStack(palladium, recipe.catalystRequired()))));
            }
            slots.add(MachineJeiRecipe.item(OUTPUT, 152, 35, recipe.output()));

            long operationGtuMilli = Math.multiplyExact(recipe.gtuPerTickMilli(), (long) recipe.reactionTicks());
            target.get(MachineJeiCategories.THIRD_CHEMICAL_PLANT.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.THIRD_CHEMICAL_PLANT, recipe.id()), slots, List.of(), List.of(
                    gauge(8, 17, 16, 52, barTexture("bar_gtu"),
                        MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                        MachineJeiRecipe.GaugeMotion.CONSUME, operationEnergyTooltip(operationGtuMilli, false)),
                    gauge(116, 35, 34, 16, barTexture("bar_chemical"),
                        MachineJeiRecipe.GaugeDirection.HORIZONTAL_LEFT_TO_RIGHT,
                        MachineJeiRecipe.GaugeMotion.PRODUCE,
                        durationTooltip("reaction", recipe.reactionTicks())))));
        }
    }

    private static List<MachineJeiRecipe.Gauge> fillerGauges(String id) {
        List<MachineJeiRecipe.Gauge> gauges = new ArrayList<>(switch (id) {
            case "sulfuric_acid" -> fillerProcessGauges(90, 20, 9);
            case "ethylene" -> fillerProcessGauges(150, 32, 3);
            case "calcite_dissolution" -> fillerProcessGauges(30, 0, 0);
            case "salt_evaporation" -> fillerProcessGauges(120, 11, 0);
            case "aminoblazeethanol" -> fillerProcessGauges(270, 2, 12);
            case "formaldehyde" -> fillerProcessGauges(165, 16, 7);
            case "calcium_chloride" -> fillerProcessGauges(75, 24, 2);
            case "yellow_cake" -> fillerProcessGauges(FillerBlockEntity.YELLOW_CAKE_TICKS, 12, 4);
            default -> List.of();
        });
        gauges.addAll(fillerFluidGauges(id));
        return List.copyOf(gauges);
    }

    private static List<MachineJeiRecipe.Gauge> fillerFluidGauges(String id) {
        return switch (id) {
            case "sulfuric_acid" -> List.of(
                fillerTankGauge(55, "water", MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidLimitTooltip(23, "water")),
                fillerTankGauge(105, "sulfuric_acid", MachineJeiRecipe.GaugeMotion.PRODUCE,
                    activeTickFluidLimitTooltip(17, "sulfuric_acid")));
            case "ethylene" -> List.of(
                fillerTankGauge(55, "rectificate", MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidLimitTooltip(19, "rectificate")),
                fillerTankGauge(105, "ethylene", MachineJeiRecipe.GaugeMotion.PRODUCE,
                    activeTickFluidLimitTooltip(20, "ethylene")));
            case "calcite_dissolution", "salt_evaporation" -> List.of(
                fillerTankGauge(55, "water", MachineJeiRecipe.GaugeMotion.STATIC, List.of()));
            case "aminoblazeethanol" -> List.of(
                fillerTankGauge(55, "rectificate", MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidLimitTooltip(19, "rectificate")),
                fillerTankGauge(105, "aminoblazeethanol", MachineJeiRecipe.GaugeMotion.PRODUCE,
                    activeTickFluidLimitTooltip(19, "aminoblazeethanol")));
            case "formaldehyde" -> List.of(
                fillerTankGauge(55, "rectificate", MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidLimitTooltip(19, "rectificate")),
                fillerTankGauge(105, "formaldehyde", MachineJeiRecipe.GaugeMotion.PRODUCE,
                    activeTickFluidLimitTooltip(16, "formaldehyde")));
            case "calcium_chloride" -> List.of(
                fillerTankGauge(55, "water", MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidLimitTooltip(12, "water")));
            case "yellow_cake" -> List.of(
                fillerTankGauge(55, "sulfuric_acid", MachineJeiRecipe.GaugeMotion.CONSUME,
                    operationFluidTooltip(FillerBlockEntity.YELLOW_CAKE_ACID_TOTAL, "sulfuric_acid")));
            default -> List.of();
        };
    }

    private static List<MachineJeiRecipe.Gauge> fillerProcessGauges(int ticks, int gthPerTick, int gtuPerTick) {
        List<MachineJeiRecipe.Gauge> gauges = new ArrayList<>();
        var vertical = MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP;
        var consume = MachineJeiRecipe.GaugeMotion.CONSUME;
        if (gthPerTick > 0) {
            gauges.add(gauge(8, 17, 16, 52, barTexture("bar_gth"), vertical, consume,
                operationEnergyTooltip((long) gthPerTick * ticks * MachineDefs.MILLI, true)));
        }
        if (gtuPerTick > 0) {
            gauges.add(gauge(26, 17, 16, 52, barTexture("bar_gtu"), vertical, consume,
                operationEnergyTooltip((long) gtuPerTick * ticks * MachineDefs.MILLI, false)));
        }
        gauges.add(gauge(99, 1, 59, 16, barTexture("bar_chemical"),
            MachineJeiRecipe.GaugeDirection.HORIZONTAL_RIGHT_TO_LEFT,
            MachineJeiRecipe.GaugeMotion.PRODUCE, durationTooltip("reaction", ticks)));
        return List.copyOf(gauges);
    }

    private static void addFillerRecipes(Map<String, List<MachineJeiRecipe>> target) {
        var recipes = target.get(MachineJeiCategories.THIRD_FILLER.id());

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/sulfuric_acid"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, net.minecraft.world.level.material.Fluids.WATER, 23),
                MachineJeiRecipe.items(INPUT, 134, 17, sulfurItems(1)),
                MachineJeiRecipe.fluid(OUTPUT, 105, 35, ModFluids.SULFURIC_ACID.get(), 17)),
            List.of(), fillerGauges("sulfuric_acid")));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/ethylene"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, ModFluids.ETHANOL.get(), 19),
                MachineJeiRecipe.items(INPUT, 134, 17, sulfurItems(2)),
                MachineJeiRecipe.items(INPUT, 134, 35,
                    List.of(new ItemStack(item(ModItems.DUST_ITEMS, "aluminum_dust"))))),
            List.of(), fillerGauges("ethylene")));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/calcite_dissolution"),
            List.of(
                MachineJeiRecipe.fluid(CRAFTING_STATION, 55, 35, net.minecraft.world.level.material.Fluids.WATER, 1),
                MachineJeiRecipe.item(INPUT, 134, 17, Items.CALCITE)),
            List.of(), fillerGauges("calcite_dissolution")));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/salt_evaporation"),
            List.of(
                MachineJeiRecipe.fluid(CRAFTING_STATION, 55, 35, net.minecraft.world.level.material.Fluids.WATER, 1),
                MachineJeiRecipe.item(OUTPUT, 62, 53, ModItems.SALT.get())),
            List.of(), fillerGauges("salt_evaporation")));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/aminoblazeethanol"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, ModFluids.ETHANOL.get(), 19),
                MachineJeiRecipe.item(INPUT, 134, 17, new ItemStack(Items.BLAZE_POWDER, 2))),
            List.of(), fillerGauges("aminoblazeethanol")));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/formaldehyde"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, ModFluids.ETHANOL.get(), 19),
                MachineJeiRecipe.item(INPUT, 134, 17, ModItems.SALT.get()),
                MachineJeiRecipe.item(INPUT, 134, 35, Items.NETHER_WART),
                MachineJeiRecipe.items(INPUT, 134, 53,
                    List.of(new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL))),
                MachineJeiRecipe.fluid(OUTPUT, 105, 35, ModFluids.FORMALDEHYDE.get(), 16)),
            List.of(), fillerGauges("formaldehyde")));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/calcium_chloride"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, net.minecraft.world.level.material.Fluids.WATER, 12),
                MachineJeiRecipe.item(INPUT, 134, 17, ModItems.SALT.get()),
                MachineJeiRecipe.item(INPUT, 134, 35, Items.CALCITE),
                MachineJeiRecipe.items(INPUT, 134, 53, calciumItems()),
                MachineJeiRecipe.item(OUTPUT, 62, 53, ModItems.CALCIUM_CHLORIDE.get())),
            List.of(), fillerGauges("calcium_chloride")));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/yellow_cake"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, ModFluids.SULFURIC_ACID.get(), 512),
                MachineJeiRecipe.items(INPUT, 134, 17, uraniumItems()),
                MachineJeiRecipe.item(OUTPUT, 62, 53, ModItems.YELLOW_CAKE.get())),
            List.of(), fillerGauges("yellow_cake")));

        addFillerContainerRecipes(target);
    }

    private static void addFillerContainerRecipes(Map<String, List<MachineJeiRecipe>> target) {
        String[] bucketFluids = {
            "water", "rectificate", "sulfuric_acid", "ethylene", "aminoblazeethanol",
            "formaldehyde", "mash", "wort", "distillate"
        };
        for (String fluidId : bucketFluids) {
            Item bucket = fillerBucket(fluidId);
            addFillerContainerRecipe(target, "bucket/" + fluidId,
                new ItemStack(Items.BUCKET), new ItemStack(bucket), fluidId, 1_000);
        }

        String[] ampouleFluids = {
            "water", "rectificate", "sulfuric_acid", "ethylene", "aminoblazeethanol",
            "formaldehyde", "mash", "wort", "distillate", "hot_water", "poison_potion"
        };
        for (String fluidId : ampouleFluids) {
            addFillerContainerRecipe(target, "ampoule/" + fluidId,
                new ItemStack(ModItems.EMPTY_AMPOULE.get()),
                AmpouleItem.createFilled(fluidId, AmpouleItem.CAPACITY, false), fluidId, AmpouleItem.CAPACITY);
            addFillerContainerRecipe(target, "durable_ampoule/" + fluidId,
                new ItemStack(ModItems.DURABLE_AMPOULE.get()),
                AmpouleItem.createFilled(fluidId, AmpouleItem.CAPACITY, true), fluidId, AmpouleItem.CAPACITY);
        }
    }

    private static Item fillerBucket(String fluidId) {
        return switch (fluidId) {
            case "water" -> Items.WATER_BUCKET;
            case "rectificate" -> ModFluids.ETHANOL_BUCKET.get();
            case "sulfuric_acid" -> ModFluids.SULFURIC_ACID_BUCKET.get();
            case "ethylene" -> ModItems.ETHYLENE_BUCKET.get();
            case "aminoblazeethanol" -> ModItems.AMINOBLAZEETHANOL_BUCKET.get();
            case "formaldehyde" -> ModFluids.FORMALDEHYDE_BUCKET.get();
            case "mash" -> ModFluids.MASH_BUCKET.get();
            case "wort" -> ModFluids.WORT_BUCKET.get();
            case "distillate" -> ModFluids.DISTILLATE_BUCKET.get();
            default -> throw new IllegalArgumentException("No filler bucket for " + fluidId);
        };
    }

    private static void addFillerContainerRecipe(Map<String, List<MachineJeiRecipe>> target, String path,
                                                 ItemStack emptyContainer, ItemStack filledContainer,
                                                 String fluidId, int milliBuckets) {
        List<MachineJeiRecipe.Slot> slots = new ArrayList<>();
        slots.add(MachineJeiRecipe.item(INPUT, 62, 17, emptyContainer));
        slots.add(MachineJeiRecipe.item(OUTPUT, 62, 53, filledContainer));
        Fluid fluid = registeredFluid(fluidId);
        if (fluid != null) {
            slots.add(MachineJeiRecipe.fluid(INPUT, 55, 17, fluid, milliBuckets));
        }
        target.get(MachineJeiCategories.THIRD_FILLER.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.THIRD_FILLER, "fill/" + path), slots, List.of(),
            List.of(fillerTankGauge(55, fluidId, MachineJeiRecipe.GaugeMotion.STATIC,
                operationFluidTooltip(milliBuckets, fluidId)))));
    }

    private static List<MachineJeiRecipe.Gauge> siliconFactoryGauges() {
        int totalTicks = 0;
        for (int ticks : SiliconFactoryBlockEntity.STEP_TICKS) totalTicks += ticks;
        long spikeCount = SiliconFactoryBlockEntity.STEP_TICKS[SiliconFactoryBlockEntity.STEP_VULCANIZATION]
            / SiliconFactoryBlockEntity.SPIKE_INTERVAL_TICKS;
        long energyMilli = (long) totalTicks * (SiliconFactoryBlockEntity.RUN_MILLI_PER_TICK
            + SiliconFactoryBlockEntity.IDLE_MILLI_PER_TICK)
            + spikeCount * SiliconFactoryBlockEntity.SPIKE_MILLI;
        List<MachineJeiRecipe.Gauge> gauges = new ArrayList<>();
        gauges.add(gauge(8, 17, 16, 52, barTexture("bar_gtu"),
            MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
            MachineJeiRecipe.GaugeMotion.CONSUME, operationEnergyTooltip(energyMilli, false)));

        double start = 0.0;
        String[] processes = {"etching", "photolithography", "vulcanization"};
        for (int index = 0; index < SiliconFactoryBlockEntity.STEP_TICKS.length; index++) {
            int ticks = SiliconFactoryBlockEntity.STEP_TICKS[index];
            double end = start + ticks / (double) totalTicks;
            gauges.add(gauge(62 + index * 36, 35, 16, 34, barTexture("bar_smelting"),
                MachineJeiRecipe.GaugeDirection.HORIZONTAL_LEFT_TO_RIGHT,
                MachineJeiRecipe.GaugeMotion.PRODUCE,
                durationTooltip(processes[index], ticks), start, end));
            start = end;
        }
        return List.copyOf(gauges);
    }

    private static void addSiliconFactoryRecipes(Map<String, List<MachineJeiRecipe>> target) {
        ItemStack soup = new ItemStack(ModItems.CHIP_SOUP.get());
        ItemStack blank = new ItemStack(ModItems.CHIP_BLANKY.get());
        Item[] outputs = {ModItems.CHIP_1.get(), ModItems.CHIP_2.get(), ModItems.CHIP_3.get()};
        for (int index = 0; index < outputs.length; index++) {
            target.get(MachineJeiCategories.THIRD_SILICON_FACTORY.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.THIRD_SILICON_FACTORY, "chip/variant_" + (index + 1)),
                List.of(
                    MachineJeiRecipe.item(INPUT, 44, 53, soup),
                    MachineJeiRecipe.item(RENDER_ONLY, 80, 53, blank),
                    MachineJeiRecipe.item(OUTPUT, 152, 53, new ItemStack(outputs[index]))),
                List.of(), siliconFactoryGauges()));
        }
    }

    private static void addFermentationRecipes(Map<String, List<MachineJeiRecipe>> target) {
        List<ItemStack> generalOrganic = new ArrayList<>();
        Item protoMash = ModItems.THE_PROTO_MASH.get();
        Item fruitMash = ModItems.THE_FRUIT_MASH.get();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || item == protoMash || item == fruitMash
                || item == Items.GOLDEN_APPLE || item == Items.GOLDEN_CARROT || item == Items.ENCHANTED_GOLDEN_APPLE) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (FermentationVatBlockEntity.isPlantOrganic(stack)) generalOrganic.add(stack);
        }
        generalOrganic.sort(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));

        var recipes = target.get(MachineJeiCategories.THIRD_FERMENTATION_VAT.id());
        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FERMENTATION_VAT, "organic/general"),
            List.of(
                MachineJeiRecipe.items(INPUT, 80, 35, generalOrganic),
                MachineJeiRecipe.fluid(OUTPUT, 152, 35, ModFluids.MASH.get(), 128)),
            List.of(), fermentationFluidGauge()));
        addFermentationSpecial(recipes, "proto_mash", protoMash);
        addFermentationSpecial(recipes, "fruit_mash", fruitMash);
        addFermentationSpecial(recipes, "golden_apple", Items.GOLDEN_APPLE);
        addFermentationSpecial(recipes, "golden_carrot", Items.GOLDEN_CARROT);
        addFermentationSpecial(recipes, "enchanted_golden_apple", Items.ENCHANTED_GOLDEN_APPLE);
    }

    private static void addFermentationSpecial(List<MachineJeiRecipe> recipes, String id, Item input) {
        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FERMENTATION_VAT, "organic/" + id),
            List.of(
                MachineJeiRecipe.item(INPUT, 80, 35, input),
                MachineJeiRecipe.fluid(OUTPUT, 152, 35, ModFluids.MASH.get(), 128)),
            List.of(), fermentationFluidGauge()));
    }

    private static List<MachineJeiRecipe.Gauge> fermentationFluidGauge() {
        return List.of(fluidGauge(152, 35, "bar_mash", MachineJeiRecipe.GaugeMotion.STATIC,
            List.of(fluidAmountLine("per_input_item", 128, fluidColor("mash")))));
    }

    private static void addWortKettleRecipes(Map<String, List<MachineJeiRecipe>> target) {
        var recipes = target.get(MachineJeiCategories.THIRD_WORT_KETTLE.id());
        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "boil/mash_to_wort"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.MASH.get(), WortKettleBlockEntity.BOIL_RATE),
                MachineJeiRecipe.fluid(OUTPUT, 116, 35, ModFluids.WORT.get(), WortKettleBlockEntity.BOIL_RATE)),
            List.of(), List.of(
                fluidGauge(80, 35, "bar_mash", MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidLimitTooltip(WortKettleBlockEntity.BOIL_RATE, "mash")),
                fluidGauge(116, 35, "bar_wort", MachineJeiRecipe.GaugeMotion.PRODUCE,
                    activeTickFluidLimitTooltip(WortKettleBlockEntity.BOIL_RATE, "wort")),
                gauge(152, 17, 16, 52, barTexture("bar_gth"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickEnergyLimitTooltip((long) WortKettleBlockEntity.BOIL_GTH_COST * MachineDefs.MILLI, true)))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "evaporate/wort"),
            List.of(MachineJeiRecipe.fluid(INPUT, 116, 35, ModFluids.WORT.get(), WortKettleBlockEntity.EVAPORATE_RATE)),
            List.of(), List.of(
                fluidGauge(116, 35, "bar_wort", MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidTooltip(WortKettleBlockEntity.EVAPORATE_RATE, "wort")),
                gauge(152, 17, 16, 52, barTexture("bar_gth"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickEnergyCostTooltip((long) WortKettleBlockEntity.EVAPORATE_GTH_COST * MachineDefs.MILLI,
                        true)))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "package/beer_mug"),
            List.of(
                MachineJeiRecipe.item(INPUT, 26, 35, Items.GLASS_BOTTLE),
                MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.WORT.get(), 128),
                MachineJeiRecipe.item(OUTPUT, 44, 35, ModItems.BEER_MUG.get())),
            List.of(), List.of(fluidGauge(80, 35, "bar_wort", MachineJeiRecipe.GaugeMotion.STATIC,
                operationFluidTooltip(128, "wort")))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "package/beer_bucket"),
            List.of(
                MachineJeiRecipe.item(INPUT, 26, 35, Items.BUCKET),
                MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.WORT.get(), 1_000),
                MachineJeiRecipe.item(OUTPUT, 44, 35, ModItems.BEER_BUCKET.get())),
            List.of(), List.of(fluidGauge(80, 35, "bar_wort", MachineJeiRecipe.GaugeMotion.STATIC,
                operationFluidTooltip(1_000, "wort")))));
    }

    private static void addDistillerRecipes(Map<String, List<MachineJeiRecipe>> target) {
        for (Fluid rawFluid : List.of(ModFluids.MASH.get(), ModFluids.WORT.get())) {
            String rawId = fluidId(rawFluid);
            List<MachineJeiRecipe.Slot> inputs = List.of(
                MachineJeiRecipe.fluid(INPUT, 116, 17, rawFluid, DistillerBlockEntity.PROCESS_RAW_RATE),
                MachineJeiRecipe.fluid(INPUT, 44, 17, net.minecraft.world.level.material.Fluids.WATER,
                    DistillerBlockEntity.PROCESS_WATER_RATE));
            List<MachineJeiRecipe.Gauge> inputGauges = List.of(
                gauge(8, 17, 16, 52, barTexture("bar_gth"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickEnergyCostTooltip((long) DistillerBlockEntity.PROCESS_GTH_COST * MachineDefs.MILLI, true)),
                fluidGauge(116, 17, rawFluid, MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidLimitTooltip(DistillerBlockEntity.PROCESS_RAW_RATE, rawId)),
                fluidGauge(44, 17, net.minecraft.world.level.material.Fluids.WATER, MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidTooltip(DistillerBlockEntity.PROCESS_WATER_RATE, "water")),
                gauge(80, 17, 16, 52, barTexture("bar_hot_water"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.PRODUCE,
                    activeTickFluidTooltip(DistillerBlockEntity.PROCESS_WATER_RATE, "hot_water")));

            List<MachineJeiRecipe.Gauge> cleanGauges = new ArrayList<>(inputGauges);
            cleanGauges.add(fluidGauge(152, 17, ModFluids.DISTILLATE.get(), MachineJeiRecipe.GaugeMotion.PRODUCE,
                activeTickFluidLimitTooltip(DistillerBlockEntity.PROCESS_RAW_RATE, "distillate")));
            target.get(MachineJeiCategories.THIRD_DISTILLER.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.THIRD_DISTILLER, "distill/clean/" + rawId),
                List.of(
                    inputs.get(0), inputs.get(1),
                    MachineJeiRecipe.fluid(OUTPUT, 152, 17, ModFluids.DISTILLATE.get(), DistillerBlockEntity.PROCESS_RAW_RATE)),
                List.of(), cleanGauges));

            int poisonMax = (DistillerBlockEntity.PROCESS_RAW_RATE + 1) / 2;
            List<MachineJeiRecipe.Gauge> contaminatedGauges = new ArrayList<>(inputGauges);
            contaminatedGauges.add(fluidGauge(152, 17, "bar_poison", MachineJeiRecipe.GaugeMotion.PRODUCE,
                activeTickFluidLimitTooltip(poisonMax, "poison_potion")));
            target.get(MachineJeiCategories.THIRD_DISTILLER.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.THIRD_DISTILLER, "distill/contaminated/" + rawId),
                inputs, List.of(), contaminatedGauges));
        }
    }

    private static void addRectifierRecipe(Map<String, List<MachineJeiRecipe>> target) {
        List<MachineJeiRecipe.Gauge> gauges = List.of(
            gauge(8, 17, 16, 52, barTexture("bar_gtu"),
                MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                MachineJeiRecipe.GaugeMotion.CONSUME,
                activeTickEnergyCostTooltip((long) RectifierBlockEntity.PROCESS_GTU_COST * MachineDefs.MILLI, false)),
            gauge(26, 17, 16, 52, barTexture("bar_gth"),
                MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                MachineJeiRecipe.GaugeMotion.CONSUME,
                activeTickEnergyCostTooltip((long) RectifierBlockEntity.PROCESS_GTH_COST * MachineDefs.MILLI, true)),
            fluidGauge(62, 35, ModFluids.DISTILLATE.get(), MachineJeiRecipe.GaugeMotion.CONSUME,
                activeTickFluidTooltip(RectifierBlockEntity.PROCESS_DISTILLATE_COST, "distillate")),
            fluidGauge(98, 35, ModFluids.ETHANOL.get(), MachineJeiRecipe.GaugeMotion.PRODUCE,
                activeTickFluidTooltip(RectifierBlockEntity.PROCESS_RECTIFICATE_YIELD, "rectificate")));
        target.get(MachineJeiCategories.THIRD_RECTIFIER.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.THIRD_RECTIFIER, "rectify/distillate"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 62, 35, ModFluids.DISTILLATE.get(), RectifierBlockEntity.PROCESS_DISTILLATE_COST),
                MachineJeiRecipe.fluid(OUTPUT, 98, 35, ModFluids.ETHANOL.get(), RectifierBlockEntity.PROCESS_RECTIFICATE_YIELD)),
            List.of(), gauges));
    }

    private static void addDispensingTapRecipes(Map<String, List<MachineJeiRecipe>> target) {
        addDispensingTapRecipe(target, "bottle/vodka", Items.GLASS_BOTTLE, ModItems.VODKA_BOTTLE.get(),
            "distillate", ModFluids.DISTILLATE.get(), 128, 8);
        addDispensingTapRecipe(target, "bottle/beer_mug", Items.GLASS_BOTTLE, ModItems.BEER_MUG.get(),
            "wort", ModFluids.WORT.get(), 128, 152);
        addDispensingTapRecipe(target, "bucket/beer", Items.BUCKET, ModItems.BEER_BUCKET.get(),
            "wort", ModFluids.WORT.get(), 1_000, 152);
    }

    private static void addDispensingTapRecipe(Map<String, List<MachineJeiRecipe>> target, String path,
                                                Item input, Item output, String fluidId, Fluid fluid,
                                                int milliBuckets, int barX) {
        target.get(MachineJeiCategories.THIRD_DISPENSING_TAP.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.THIRD_DISPENSING_TAP, path),
            List.of(
                MachineJeiRecipe.item(INPUT, 80, 17, input),
                MachineJeiRecipe.item(OUTPUT, 80, 53, output),
                MachineJeiRecipe.fluid(INPUT, barX, 17, fluid, milliBuckets)),
            List.of(), List.of(gauge(barX, 17, 16, 52, barTexture(fluidBarTexture(fluidId)),
                MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                MachineJeiRecipe.GaugeMotion.STATIC, operationFluidTooltip(milliBuckets, fluidId)))));
    }

    private static void addCrimsonObsidianRecipe(Map<String, List<MachineJeiRecipe>> target) {
        target.get(MachineJeiCategories.CRIMSON_OBSIDIAN.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.CRIMSON_OBSIDIAN, "reaction/lava_redstone_contact"), List.of(),
            List.of(Component.translatable("jei.gonzotech.note.crimson_obsidian"))));
    }

    private static List<ItemStack> sulfurItems(int count) {
        List<ItemStack> stacks = new ArrayList<>();
        addIfPresent(stacks, ModItems.RAW_ORE_ITEMS.get("sulfur"), count);
        addIfPresent(stacks, ModItems.INGOT_ITEMS.get("sulfur_ingot"), count);
        return List.copyOf(stacks);
    }

    private static List<ItemStack> calciumItems() {
        List<ItemStack> stacks = new ArrayList<>();
        addIfPresent(stacks, ModItems.INGOT_ITEMS.get("calcium_ingot"), 1);
        addIfPresent(stacks, ModItems.DUST_ITEMS.get("calcium_dust"), 1);
        return List.copyOf(stacks);
    }

    private static List<ItemStack> uraniumItems() {
        List<ItemStack> stacks = new ArrayList<>();
        addIfPresent(stacks, ModItems.RAW_ORE_ITEMS.get("uranium"), 1);
        addIfPresent(stacks, ModItems.INGOT_ITEMS.get("uranium_ingot"), 1);
        addIfPresent(stacks, ModItems.DUST_ITEMS.get("uranium_dust"), 1);
        return List.copyOf(stacks);
    }

    private static void addIfPresent(List<ItemStack> stacks, Supplier<? extends Item> item, int count) {
        if (item != null) stacks.add(new ItemStack(item.get(), count));
    }

    private static Item item(Map<String, ? extends Supplier<? extends Item>> items, String id) {
        Supplier<? extends Item> supplier = items.get(id);
        if (supplier == null) throw new IllegalStateException("Missing registered item: " + id);
        return supplier.get();
    }

    private static ResourceLocation recipeId(MachineJeiCategories.Definition definition, String path) {
        String safePath = path.toLowerCase(Locale.ROOT)
            .replace(':', '_')
            .replaceAll("[^a-z0-9/._-]", "_");
        return ResourceLocation.fromNamespaceAndPath("gonzotech", definition.id() + "/" + safePath);
    }
}
