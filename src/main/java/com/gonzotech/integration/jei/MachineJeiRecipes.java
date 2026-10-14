package com.gonzotech.integration.jei;

import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.item.AmpouleItem;
import com.gonzotech.core.item.CanisterItem;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.block.entity.DistillerBlockEntity;
import com.gonzotech.machines.block.entity.FermentationVatBlockEntity;
import com.gonzotech.machines.block.entity.FillerBlockEntity;
import com.gonzotech.machines.block.entity.NuclearFireboxBlockEntity;
import com.gonzotech.machines.block.entity.RectifierBlockEntity;
import com.gonzotech.machines.block.entity.WortKettleBlockEntity;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.NuclearDefs;
import com.gonzotech.machines.energy.WaterProviders;
import com.gonzotech.machines.steamgen.SteamGenMath;
import com.gonzotech.machines.turbine.TurbineMath;
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
        addGeneratorRecipes(recipes);
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
                List.of(durationRangeTooltip("crushing", fastestTicks, slowestTicks).getFirst(),
                    chargeDurationTooltip(100, fastestTicks), chargeDurationTooltip(0, slowestTicks))));
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

    private static MachineJeiRecipe.Gauge dynamicGauge(int x, int y, int width, int height,
                                                         ResourceLocation texture,
                                                         MachineJeiRecipe.GaugeDirection direction,
                                                         MachineJeiRecipe.GaugeMotion motion,
                                                         MachineJeiRecipe.DynamicTooltip tooltip) {
        return new MachineJeiRecipe.Gauge(x, y, width, height, texture, direction, motion,
            List.of(), 0.0, 1.0, List.of(), tooltip);
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

    private static List<Component> withLines(List<Component> base, Component... additional) {
        List<Component> lines = new ArrayList<>(base);
        lines.addAll(List.of(additional));
        return List.copyOf(lines);
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
            .append(Component.literal("—").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(Integer.toString(maxTicks)).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.ticks").withStyle(ChatFormatting.WHITE)));
    }

    private static Component durationLine(String processKey, int ticks) {
        return Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge.process." + processKey).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(Integer.toString(ticks)).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.ticks").withStyle(ChatFormatting.WHITE));
    }

    private static Component chargeDurationTooltip(int chargePercent, int ticks) {
        return Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge.at").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(GtUnits.U_GTU, GtUnits.GTU))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(chargePercent, GtUnits.GTU))
            .append(Component.literal("%: ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(Integer.toString(ticks)).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.ticks").withStyle(ChatFormatting.WHITE));
    }

    private static MachineJeiRecipe.Gauge fluidGauge(int x, int y, String texture,
                                                       MachineJeiRecipe.GaugeMotion motion,
                                                       List<Component> tooltip) {
        return gauge(x, y, 16, 52, barTexture(texture),
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
        return fillerTankGauge(x, fluidId, motion, tooltip, null);
    }

    private static MachineJeiRecipe.Gauge fillerTankGauge(int x, String fluidId,
                                                           MachineJeiRecipe.GaugeMotion motion,
                                                           List<Component> tooltip,
                                                           MachineJeiRecipe.DynamicTooltip dynamicTooltip) {
        List<MachineJeiRecipe.TooltipArea> hitboxes = switch (x) {
            case 55 -> List.of(new MachineJeiRecipe.TooltipArea(55, 17, 4, 52),
                new MachineJeiRecipe.TooltipArea(59, 36, 12, 14));
            case 105 -> List.of(new MachineJeiRecipe.TooltipArea(117, 17, 4, 52),
                new MachineJeiRecipe.TooltipArea(105, 36, 12, 14));
            default -> throw new IllegalArgumentException("Filler tank gauge must use x=55 or x=105");
        };
        return new MachineJeiRecipe.Gauge(x, 17, 16, 52, barTexture(fluidBarTexture(fluidId)),
            MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP, motion, tooltip,
            0.0, 1.0, hitboxes, dynamicTooltip);
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
            case "calcite_dissolution" -> fillerProcessGauges(FillerBlockEntity.CALCITE_DISSOLUTION_TICKS, 0, 0);
            case "salt_evaporation" -> fillerProcessGauges(FillerBlockEntity.SALT_EVAPORATION_TICKS_PER_ITEM,
                FillerBlockEntity.SALT_EVAPORATION_GTH_PER_TICK, 0);
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
            case "calcite_dissolution" -> List.of(
                fillerTankGauge(55, "water", MachineJeiRecipe.GaugeMotion.STATIC, List.of(),
                    progress -> List.of(
                        saltAmountTooltip("jei.gonzotech.gauge.salt_added",
                            (int) Math.round(progress * FillerBlockEntity.CALCITE_SALT_MB_PER_TICK
                                * FillerBlockEntity.CALCITE_DISSOLUTION_TICKS)),
                        Component.translatable("jei.gonzotech.gauge.salt_dissolution_limit")
                            .withStyle(ChatFormatting.GRAY))));
            case "salt_evaporation" -> List.of(
                fillerTankGauge(55, "water", MachineJeiRecipe.GaugeMotion.STATIC, List.of(),
                    progress -> List.of(saltAmountTooltip("jei.gonzotech.gauge.salt_batch_remaining",
                        (int) Math.round((1.0 - progress) * FillerBlockEntity.SALT_PER_ITEM_MB)))));
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

    private static Component saltAmountTooltip(String labelKey, int milliBuckets) {
        return Component.empty()
            .append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(milliBuckets, GtUnits.WATER))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(GtUnits.U_MB, GtUnits.WATER));
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
        addFermentationDynamics(recipes);
    }

    private static void addFermentationSpecial(List<MachineJeiRecipe> recipes, String id, Item input) {
        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FERMENTATION_VAT, "organic/" + id),
            List.of(
                MachineJeiRecipe.item(INPUT, 80, 35, input),
                MachineJeiRecipe.fluid(OUTPUT, 152, 35, ModFluids.MASH.get(), 128)),
            List.of(), fermentationFluidGauge()));
    }

    private static List<MachineJeiRecipe.Gauge> fermentationFluidGauge() {
        return List.of(fluidGauge(152, 17, "bar_mash", MachineJeiRecipe.GaugeMotion.STATIC,
            List.of(fluidAmountLine("per_input_item", 128, fluidColor("mash")))));
    }

    private static void addFermentationDynamics(List<MachineJeiRecipe> recipes) {
        MachineJeiCategories.Definition category = MachineJeiCategories.THIRD_FERMENTATION_VAT;
        List<MachineJeiRecipe.Slot> referenceMash = List.of(
            MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.MASH.get(),
                FermentationVatBlockEntity.FERMENTATION_REFERENCE_MASH_MB));
        recipes.add(new MachineJeiRecipe(recipeId(category, "ferment/alcohol_growth"), referenceMash, List.of(),
            List.of(
                gauge(8, 17, 16, 52, barTexture("bar_gth"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME, fermentationHeatTooltip()),
                dynamicGauge(152, 17, 16, 52, barTexture("bar_mash"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.PRODUCE,
                    progress -> List.of(alcoholPercentLine(FermentationVatBlockEntity.MAX_ALCOHOL * progress))))));

        recipes.add(new MachineJeiRecipe(recipeId(category, "ferment/low_heat_rot"), referenceMash,
            List.of(), List.of(
                gauge(8, 17, 16, 52, barTexture("bar_gth"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.EMPTY, List.of()),
                dynamicGauge(152, 17, 16, 52, barTexture("bar_mash"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.PRODUCE,
                    MachineJeiRecipes::lowHeatFermentationTooltip))));
    }

    private static List<Component> fermentationHeatTooltip() {
        int mashMb = FermentationVatBlockEntity.FERMENTATION_REFERENCE_MASH_MB;
        Component heatRate = Component.empty()
            .append(GtUnits.num(FermentationVatBlockEntity.GTH_CONSUMPTION_PER_TICK, GtUnits.GTH))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(GtUnits.U_GTH, GtUnits.GTH))
            .append(GtUnits.perTick())
            .append(Component.translatable("jei.gonzotech.gauge.for_mash_amount",
                GtUnits.num(mashMb, GtUnits.MASH), GtUnits.key(GtUnits.U_MB, GtUnits.MASH))
                .withStyle(ChatFormatting.WHITE));
        Component growthRate = Component.empty()
            .append(GtUnits.num(FermentationVatBlockEntity.ALCOHOL_GAIN_PERCENT_PER_MINUTE, GtUnits.RECTIFICATE))
            .append(Component.literal("%").withColor(GtUnits.RECTIFICATE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.alcohol_per_minute").withStyle(ChatFormatting.WHITE))
            .append(Component.translatable("jei.gonzotech.gauge.for_mash_amount",
                GtUnits.num(mashMb, GtUnits.MASH), GtUnits.key(GtUnits.U_MB, GtUnits.MASH))
                .withStyle(ChatFormatting.WHITE));
        Component heatCondition = Component.translatable("jei.gonzotech.gauge.fermentation_heat_condition",
            Component.empty()
                .append(GtUnits.num(FermentationVatBlockEntity.FERMENTATION_HEAT_THRESHOLD_PERCENT, GtUnits.ITEM))
                .append(Component.literal("%").withColor(GtUnits.ITEM)))
            .withStyle(ChatFormatting.GRAY);
        return List.of(heatRate, growthRate, heatCondition);
    }

    private static List<Component> lowHeatFermentationTooltip(double progress) {
        double maximumShortage = FermentationVatBlockEntity.FERMENTATION_HEAT_THRESHOLD_PERCENT;
        double alcoholLossPerMinute =
            FermentationVatBlockEntity.ALCOHOL_LOSS_PERCENT_PER_MINUTE_PER_SHORTAGE * maximumShortage;
        double rotGainPerMinute = FermentationVatBlockEntity.ROT_GAIN_PERCENT_PER_MINUTE_PER_SHORTAGE * maximumShortage;
        double displayedDurationMinutes =
            (FermentationVatBlockEntity.MAX_ALCOHOL - 2.0) / alcoholLossPerMinute;
        double alcohol = FermentationVatBlockEntity.MAX_ALCOHOL - alcoholLossPerMinute * displayedDurationMinutes * progress;
        double rot = Math.min(FermentationVatBlockEntity.MAX_ROT,
            rotGainPerMinute * displayedDurationMinutes * progress);
        return List.of(Component.translatable("jei.gonzotech.gauge.fermentation_low_heat_basis")
                .withStyle(ChatFormatting.GRAY),
            alcoholPercentLine(alcohol), rotPercentLine(rot));
    }

    private static Component alcoholPercentLine(double percent) {
        return Component.empty()
            .append(Component.translatable("gui.gonzotech.lore.alcohol_prefix").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(percent), GtUnits.RECTIFICATE))
            .append(Component.literal("%").withColor(GtUnits.RECTIFICATE));
    }

    private static Component rotPercentLine(double percent) {
        return Component.empty()
            .append(Component.translatable("gui.gonzotech.lore.rot_prefix").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(percent), GtUnits.POISON_POTION))
            .append(Component.literal("%").withColor(GtUnits.POISON_POTION));
    }

    private static void addWortKettleRecipes(Map<String, List<MachineJeiRecipe>> target) {
        var recipes = target.get(MachineJeiCategories.THIRD_WORT_KETTLE.id());
        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "boil/mash_to_wort"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.MASH.get(), WortKettleBlockEntity.BOIL_RATE),
                MachineJeiRecipe.fluid(OUTPUT, 116, 35, ModFluids.WORT.get(), WortKettleBlockEntity.BOIL_RATE)),
            List.of(), List.of(
                fluidGauge(80, 17, "bar_mash", MachineJeiRecipe.GaugeMotion.CONSUME,
                    withLines(activeTickFluidLimitTooltip(WortKettleBlockEntity.BOIL_RATE, "mash"),
                        GtUnits.alcoholLore("0–13"), GtUnits.rotLore("0–98"))),
                fluidGauge(116, 17, "bar_wort", MachineJeiRecipe.GaugeMotion.PRODUCE,
                    withLines(activeTickFluidLimitTooltip(WortKettleBlockEntity.BOIL_RATE, "wort"),
                        GtUnits.alcoholLore("0–13"), GtUnits.rotLore("0"))),
                gauge(152, 17, 16, 52, barTexture("bar_gth"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickEnergyLimitTooltip((long) WortKettleBlockEntity.BOIL_GTH_COST * MachineDefs.MILLI, true)))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "evaporate/wort"),
            List.of(MachineJeiRecipe.fluid(INPUT, 116, 35, ModFluids.WORT.get(), WortKettleBlockEntity.EVAPORATE_RATE)),
            List.of(), List.of(
                fluidGauge(116, 17, "bar_wort", MachineJeiRecipe.GaugeMotion.CONSUME,
                    withLines(activeTickFluidTooltip(WortKettleBlockEntity.EVAPORATE_RATE, "wort"),
                        GtUnits.alcoholLore("0–30"),
                        Component.empty()
                            .append(Component.translatable("jei.gonzotech.gauge.alcohol_ceiling")
                                .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
                            .append(GtUnits.num(WortKettleBlockEntity.MAX_WORT_ALCOHOL, GtUnits.RECTIFICATE))
                            .append(Component.literal("%").withColor(GtUnits.RECTIFICATE)))),
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
            List.of(), List.of(fluidGauge(80, 17, "bar_wort", MachineJeiRecipe.GaugeMotion.STATIC,
                withLines(operationFluidTooltip(128, "wort"), GtUnits.alcoholLore("0–30"))))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "package/beer_bucket"),
            List.of(
                MachineJeiRecipe.item(INPUT, 26, 35, Items.BUCKET),
                MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.WORT.get(), 1_000),
                MachineJeiRecipe.item(OUTPUT, 44, 35, ModItems.BEER_BUCKET.get())),
            List.of(), List.of(fluidGauge(80, 17, "bar_wort", MachineJeiRecipe.GaugeMotion.STATIC,
                withLines(operationFluidTooltip(1_000, "wort"), GtUnits.alcoholLore("0–30"))))));
    }

    private static List<Component> distillerRawTooltip(Fluid rawFluid, String rawId) {
        List<Component> quality = rawFluid == ModFluids.MASH.get()
            ? List.of(GtUnits.alcoholLore("0–13"), GtUnits.rotLore("0–98"))
            : List.of(GtUnits.alcoholLore("0–30"), GtUnits.rotLore("0"));
        Component alcoholDecay = GtUnits.num(0.10, GtUnits.RECTIFICATE)
            .append(Component.literal("%/min").withColor(GtUnits.RECTIFICATE));
        Component rotGrowth = GtUnits.num(0.40, GtUnits.POISON_POTION)
            .append(Component.literal("%/min").withColor(GtUnits.POISON_POTION));
        Component storageDecay = Component.translatable("jei.gonzotech.gauge.distiller_storage_decay",
            alcoholDecay, rotGrowth).withStyle(ChatFormatting.GRAY);
        return withLines(activeTickFluidLimitTooltip(DistillerBlockEntity.PROCESS_RAW_RATE, rawId),
            quality.get(0), quality.get(1), storageDecay);
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
                    distillerRawTooltip(rawFluid, rawId)),
                fluidGauge(44, 17, net.minecraft.world.level.material.Fluids.WATER, MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidTooltip(DistillerBlockEntity.PROCESS_WATER_RATE, "water")),
                gauge(80, 17, 16, 52, barTexture("bar_hot_water"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.PRODUCE,
                    activeTickFluidTooltip(DistillerBlockEntity.PROCESS_WATER_RATE, "hot_water")));

            List<MachineJeiRecipe.Gauge> cleanGauges = new ArrayList<>(inputGauges);
            cleanGauges.add(fluidGauge(152, 17, ModFluids.DISTILLATE.get(), MachineJeiRecipe.GaugeMotion.PRODUCE,
                withLines(activeTickFluidLimitTooltip(DistillerBlockEntity.PROCESS_RAW_RATE, "distillate"),
                    GtUnits.alcoholLore("48"),
                    Component.translatable("jei.gonzotech.gauge.distiller_clean_condition")
                        .withStyle(ChatFormatting.GRAY))));
            target.get(MachineJeiCategories.THIRD_DISTILLER.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.THIRD_DISTILLER, "distill/clean/" + rawId),
                List.of(
                    inputs.get(0), inputs.get(1),
                    MachineJeiRecipe.fluid(OUTPUT, 152, 17, ModFluids.DISTILLATE.get(), DistillerBlockEntity.PROCESS_RAW_RATE)),
                List.of(), cleanGauges));

            int poisonMax = (DistillerBlockEntity.PROCESS_RAW_RATE + 1) / 2;
            List<MachineJeiRecipe.Gauge> contaminatedGauges = new ArrayList<>(inputGauges);
            contaminatedGauges.add(fluidGauge(152, 17, "bar_poison", MachineJeiRecipe.GaugeMotion.PRODUCE,
                withLines(activeTickFluidLimitTooltip(poisonMax, "poison_potion"),
                    Component.translatable("jei.gonzotech.gauge.distiller_poison_condition")
                        .withStyle(ChatFormatting.GRAY))));
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
            fluidGauge(62, 17, ModFluids.DISTILLATE.get(), MachineJeiRecipe.GaugeMotion.CONSUME,
                withLines(activeTickFluidTooltip(RectifierBlockEntity.PROCESS_DISTILLATE_COST, "distillate"),
                    GtUnits.alcoholLore("48"))),
            fluidGauge(98, 17, ModFluids.ETHANOL.get(), MachineJeiRecipe.GaugeMotion.PRODUCE,
                withLines(activeTickFluidTooltip(RectifierBlockEntity.PROCESS_RECTIFICATE_YIELD, "rectificate"),
                    GtUnits.alcoholLore("100"))));
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

    private static void addGeneratorRecipes(Map<String, List<MachineJeiRecipe>> target) {
        addFireboxGeneration(target);
        addNuclearFireboxGeneration(target);
        addBoilerGeneration(target);
        addStirlingGeneration(target);
        addSteamGenCycles(target);
        addTurbineCycle(target);
    }

    private static void addFireboxGeneration(Map<String, List<MachineJeiRecipe>> target) {
        List<Component> outputTooltip = List.of(
            energyAmountLine("generated_per_tick", MachineDefs.FIREBOX_GTH_PER_TICK, true),
            energyAmountLine("firebox_export_limit", MachineDefs.FIREBOX_GTH_OUTPUT, true),
            energyAmountLine("passive_loss_per_tick", MachineDefs.FIREBOX_GTH_LOSS, true));
        target.get(MachineJeiCategories.FIREBOX.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.FIREBOX, "fuel/coal"),
            List.of(MachineJeiRecipe.item(INPUT, 44, 53, Items.COAL)), List.of(),
            List.of(gauge(8, 17, 16, 52, barTexture("bar_gth"),
                MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                MachineJeiRecipe.GaugeMotion.PRODUCE, outputTooltip))));
    }

    private static void addNuclearFireboxGeneration(Map<String, List<MachineJeiRecipe>> target) {
        List<ItemStack> fuels = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (NuclearFireboxBlockEntity.isNuclearFuel(stack)) fuels.add(stack);
        }
        fuels.sort(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        if (fuels.isEmpty()) throw new IllegalStateException("Nuclear firebox has no JEI fuel variants");

        List<Component> outputTooltip = List.of(
            energyAmountLine("generated_per_tick", NuclearDefs.NUCLEAR_FIREBOX_GTH_PER_TICK, true),
            energyAmountLine("firebox_export_limit", NuclearDefs.NUCLEAR_FIREBOX_GTH_OUTPUT, true),
            energyAmountLine("passive_loss_per_tick", NuclearDefs.NUCLEAR_FIREBOX_GTH_LOSS, true),
            Component.translatable("jei.gonzotech.gauge.nuclear_firebox_acceleration",
                GtUnits.num(NuclearDefs.NUCLEAR_FIREBOX_ACCELERATION_START / MachineDefs.MILLI, GtUnits.GTH),
                GtUnits.num(NuclearDefs.NUCLEAR_FIREBOX_MAX_BURN_REDUCTION_PERMILLE / 10, GtUnits.ITEM)
                    .append(Component.literal("%").withColor(GtUnits.ITEM)))
                .withStyle(ChatFormatting.GRAY));
        target.get(MachineJeiCategories.SECOND_NUCLEAR_FIREBOX.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.SECOND_NUCLEAR_FIREBOX, "fuel/natural_uranium_thorium"),
            List.of(MachineJeiRecipe.items(INPUT, 80, 53, fuels)), List.of(),
            List.of(gauge(8, 17, 16, 52, barTexture("bar_gth"),
                MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                MachineJeiRecipe.GaugeMotion.PRODUCE, outputTooltip))));
    }

    private static void addBoilerGeneration(Map<String, List<MachineJeiRecipe>> target) {
        List<Component> steamTooltip = withLines(List.of(
            fluidAmountLine("generated_per_tick", MachineDefs.BOILER_STEAM_PER_TICK, GtUnits.STEAM)),
            fluidAmountLine("boiler_steam_condensation", MachineDefs.BOILER_STEAM_LOSS, GtUnits.STEAM),
            Component.translatable("jei.gonzotech.gauge.boiler_requires_firebox").withStyle(ChatFormatting.GRAY));
        target.get(MachineJeiCategories.BOILER.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.BOILER, "cycle/water_to_steam"),
            List.of(
                MachineJeiRecipe.items(INPUT, 8, 17, waterProviderItems()),
                MachineJeiRecipe.item(OUTPUT, 8, 53, Items.BUCKET),
                MachineJeiRecipe.fluid(INPUT, 116, 35, net.minecraft.world.level.material.Fluids.WATER,
                    MachineDefs.BOILER_WATER_PER_TICK)),
            List.of(), List.of(
                gauge(80, 17, 16, 52, barTexture("bar_gth"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME,
                    withLines(activeTickEnergyCostTooltip(MachineDefs.BOILER_GTH_PER_TICK, true),
                        energyAmountLine("passive_loss_per_tick", MachineDefs.BOILER_GTH_LOSS, true))),
                fluidGauge(116, 17, "bar_water", MachineJeiRecipe.GaugeMotion.CONSUME,
                    activeTickFluidTooltip(MachineDefs.BOILER_WATER_PER_TICK, "water")),
                gauge(152, 17, 16, 52, barTexture("bar_steam"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.PRODUCE, steamTooltip))));
    }

    private static void addStirlingGeneration(Map<String, List<MachineJeiRecipe>> target) {
        long minimumGtu = (long) MachineDefs.STIRLING_GTU_PER_TICK * MachineDefs.STIRLING_EFF_MIN_PERMILLE / 1_000L;
        long maximumGtu = (long) MachineDefs.STIRLING_GTU_PER_TICK * MachineDefs.STIRLING_EFF_MAX_PERMILLE / 1_000L;
        int maxCondensers = net.minecraft.core.Direction.values().length;
        int minWater = MachineDefs.stirlingWaterPerTick(0);
        int maxWater = MachineDefs.stirlingWaterPerTick(maxCondensers);
        List<Component> steamTooltip = withLines(
            activeTickFluidTooltip(MachineDefs.STIRLING_STEAM_PER_TICK, "steam"),
            fluidAmountLine("passive_condensate", MachineDefs.STIRLING_STEAM_LOSS, GtUnits.STEAM));
        List<Component> waterTooltip = withLines(List.of(
            fluidRangeAmountLine("generated_per_tick", minWater, maxWater, GtUnits.WATER)),
            fluidAmountLine("passive_condensate", MachineDefs.STIRLING_STEAM_LOSS, GtUnits.WATER),
            Component.translatable("jei.gonzotech.gauge.stirling_condensers", maxCondensers)
                .withStyle(ChatFormatting.GRAY));
        target.get(MachineJeiCategories.STIRLING_GENERATOR.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.STIRLING_GENERATOR, "cycle/steam_to_gtu"), List.of(), List.of(),
            List.of(
                gauge(80, 17, 16, 52, barTexture("bar_steam"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME, steamTooltip),
                gauge(116, 17, 16, 52, barTexture("bar_gtu"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.PRODUCE,
                    List.of(energyRangeLine("generated_per_tick", minimumGtu, maximumGtu, false),
                        Component.translatable("jei.gonzotech.gauge.stirling_condensate_efficiency")
                            .withStyle(ChatFormatting.GRAY))),
                fluidGauge(152, 17, "bar_water", MachineJeiRecipe.GaugeMotion.PRODUCE,
                    waterTooltip))));
    }

    private static void addSteamGenCycles(Map<String, List<MachineJeiRecipe>> target) {
        int[] exchangerCounts = {0, 2, 5, 12, 22, 26};
        for (int exchangers : exchangerCounts) {
            long gthMilli = SteamGenMath.gthPerCycleMilli(1, exchangers);
            int waterMb = SteamGenMath.waterPerCycle(1, exchangers);
            long steamMilli = SteamGenMath.steamPerCycleMilli(1, 150 * exchangers, exchangers);
            int steamMb = (int) (steamMilli / MachineDefs.MILLI);
            String path = "cycle/core_1_platinum_exchangers_" + exchangers;
            List<Component> steamTooltip = withLines(List.of(
                fluidAmountLine("per_cycle", steamMb, GtUnits.STEAM)),

                Component.translatable("jei.gonzotech.gauge.steamgen_profile", exchangers)
                    .withStyle(ChatFormatting.GRAY));
            target.get(MachineJeiCategories.SECOND_STEAMGEN_CORE.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.SECOND_STEAMGEN_CORE, path),
                List.of(MachineJeiRecipe.fluid(INPUT, 116, 35,
                    net.minecraft.world.level.material.Fluids.WATER, waterMb)), List.of(),
                List.of(
                    gauge(80, 17, 16, 52, barTexture("bar_gth"),
                        MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                        MachineJeiRecipe.GaugeMotion.CONSUME, cycleEnergyTooltip(gthMilli, true)),
                    fluidGauge(116, 17, "bar_water", MachineJeiRecipe.GaugeMotion.CONSUME,
                        cycleFluidTooltip(waterMb, GtUnits.WATER)),
                    gauge(152, 17, 16, 52, barTexture("bar_steam"),
                        MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                        MachineJeiRecipe.GaugeMotion.PRODUCE, steamTooltip))));
        }
    }

    private static void addTurbineCycle(Map<String, List<MachineJeiRecipe>> target) {
        int rotors = 1;
        int steamMb = TurbineMath.maxSteamConsumption(rotors);
        long gtuMilli = TurbineMath.gtuMilliForSteam(steamMb, 0);
        target.get(MachineJeiCategories.FIRST_TURBINE_ROTOR.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.FIRST_TURBINE_ROTOR, "cycle/one_rotor"), List.of(), List.of(),
            List.of(
                gauge(62, 17, 16, 52, barTexture("bar_gtu"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.PRODUCE,
                    List.of(energyAmountLine("generated_per_tick", gtuMilli, false),
                        Component.translatable("jei.gonzotech.gauge.turbine_profile", rotors)
                            .withStyle(ChatFormatting.GRAY))),
                gauge(98, 17, 16, 52, barTexture("bar_steam"),
                    MachineJeiRecipe.GaugeDirection.VERTICAL_BOTTOM_TO_TOP,
                    MachineJeiRecipe.GaugeMotion.CONSUME,
                    List.of(fluidAmountLine("active_tick", steamMb, GtUnits.STEAM),
                        GtUnits.turbineSteamPerGtu(String.format(Locale.ROOT, "%.2f",
                            TurbineMath.nominalSteamMbPerGtu())))))));
    }

    private static List<Component> cycleEnergyTooltip(long milliUnits, boolean heat) {
        return List.of(energyAmountLine("per_cycle", milliUnits, heat));
    }

    private static List<Component> cycleFluidTooltip(long milliBuckets, int color) {
        return List.of(fluidAmountLine("per_cycle", milliBuckets, color));
    }

    private static Component energyRangeLine(String labelKey, long minimumMilliUnits,
                                            long maximumMilliUnits, boolean heat) {
        int color = heat ? GtUnits.GTH : GtUnits.GTU;
        String unitKey = heat ? GtUnits.U_GTH : GtUnits.U_GTU;
        return Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge." + labelKey).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(unitKey, color))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(minimumMilliUnits / (double) MachineDefs.MILLI), color))
            .append(Component.literal("–").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(GtUnits.x1(maximumMilliUnits / (double) MachineDefs.MILLI), color));
    }

    private static Component fluidRangeAmountLine(String labelKey, long minimumMb, long maximumMb, int color) {
        return Component.empty()
            .append(Component.translatable("jei.gonzotech.gauge." + labelKey).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(": ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(minimumMb, color))
            .append(Component.literal("–").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.num(maximumMb, color))
            .append(Component.literal(" ").withStyle(ChatFormatting.WHITE))
            .append(GtUnits.key(GtUnits.U_MB, color));
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

    private static List<ItemStack> waterProviderItems() {
        List<ItemStack> providers = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (WaterProviders.isWaterProvider(stack)) providers.add(stack);
        }
        providers.sort(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        if (providers.isEmpty()) throw new IllegalStateException("Boiler has no JEI water-provider items");
        return List.copyOf(providers);
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
