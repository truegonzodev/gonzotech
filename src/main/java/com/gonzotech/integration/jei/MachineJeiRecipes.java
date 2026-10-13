package com.gonzotech.integration.jei;

import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.block.entity.FermentationVatBlockEntity;
import com.gonzotech.machines.block.entity.RectifierBlockEntity;
import com.gonzotech.machines.block.entity.DistillerBlockEntity;
import com.gonzotech.machines.block.entity.WortKettleBlockEntity;
import com.gonzotech.machines.processing.AlloyFoundryRecipes;
import com.gonzotech.machines.processing.CentrifugeRecipes;
import com.gonzotech.machines.processing.ChemicalPlantRecipes;
import com.gonzotech.machines.processing.CrusherRecipes;
import com.gonzotech.machines.processing.GrinderRecipes;
import com.gonzotech.machines.processing.PCFSOZRecipes;
import com.gonzotech.machines.processing.PressRecipes;
import com.gonzotech.machines.processing.ProcessingRecipeDisplay;
import mezz.jei.api.constants.RecipeTypes;
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
import net.minecraft.world.item.crafting.display.SlotDisplay;

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
    private static final int FLUID_RENDERER_CAPACITY = 1_000;

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
            List<Component> notes = new ArrayList<>();
            if (furnace.duration() > 0) {
                notes.add(Component.translatable("jei.gonzotech.smelting_time", furnace.duration()));
            }
            if (furnace.experience() > 0) {
                notes.add(Component.translatable("jei.gonzotech.smelting_experience", furnace.experience()));
            }
            if (secondFurnace) {
                notes.add(Component.translatable("jei.gonzotech.note.second_furnace_independent_lanes"));
            }
            String sourceId = holder.id().location().toString().replace(':', '_');
            result.add(new MachineJeiRecipe(
                recipeId(definition, "smelting/" + sourceId),
                List.of(
                    MachineJeiRecipe.display(INPUT, inputX, inputY, furnace.ingredient()),
                    MachineJeiRecipe.display(OUTPUT, outputX, outputY, furnace.result())
                ),
                notes));
        });
        result.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        return List.copyOf(result);
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
                recipeId(definition, display.id()), slots, notes));
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
            String noteKey = consumesForm
                ? "jei.gonzotech.note.press_tvel_form_consumed"
                : display.id().startsWith("wire/")
                    ? "jei.gonzotech.note.press_wire_form_empty"
                    : "jei.gonzotech.note.press_tools_are_reusable";
            target.get(MachineJeiCategories.SECOND_PRESS.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.SECOND_PRESS, display.id()), slots,
                List.of(Component.translatable(noteKey))));
        }
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
                List.of(Component.translatable("jei.gonzotech.note.alloy_preset"))));
        }
        target.get(MachineJeiCategories.SECOND_ALLOY_FOUNDRY.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.SECOND_ALLOY_FOUNDRY, "information/custom_alloy"), List.of(),
            List.of(Component.translatable("jei.gonzotech.note.alloy_custom_output"))));
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
                        new ItemStack(palladium, recipe.catalystRequired())))
                    .withTooltip(Component.translatable("jei.gonzotech.note.chemical_catalyst_slots",
                        recipe.catalystRequired())));
            }
            slots.add(MachineJeiRecipe.item(OUTPUT, 152, 35, recipe.output()));

            List<Component> notes = new ArrayList<>();
            notes.add(Component.translatable("jei.gonzotech.note.chemical_reaction_ticks", recipe.reactionTicks()));
            if (recipe.catalystRequired() > 0) {
                notes.add(Component.translatable(recipe.softCatalysts()
                    ? "jei.gonzotech.note.chemical_soft_catalyst_consumption"
                    : "jei.gonzotech.note.chemical_catalyst_consumption"));
            }
            target.get(MachineJeiCategories.THIRD_CHEMICAL_PLANT.id()).add(new MachineJeiRecipe(
                recipeId(MachineJeiCategories.THIRD_CHEMICAL_PLANT, recipe.id()), slots, notes));
        }
    }

    private static void addFillerRecipes(Map<String, List<MachineJeiRecipe>> target) {
        var recipes = target.get(MachineJeiCategories.THIRD_FILLER.id());

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/sulfuric_acid"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, net.minecraft.world.level.material.Fluids.WATER, 23),
                MachineJeiRecipe.items(INPUT, 134, 17, sulfurItems(1)),
                MachineJeiRecipe.fluid(OUTPUT, 105, 35, ModFluids.SULFURIC_ACID.get(), 17)),
            List.of(Component.translatable("jei.gonzotech.note.filler_sulfuric_acid"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/ethylene"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, ModFluids.ETHANOL.get(), 19),
                MachineJeiRecipe.items(INPUT, 134, 17, sulfurItems(2)),
                MachineJeiRecipe.items(INPUT, 134, 35,
                    List.of(new ItemStack(item(ModItems.DUST_ITEMS, "aluminum_dust"))))),
            List.of(Component.translatable("jei.gonzotech.note.filler_ethylene"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/calcite_dissolution"),
            List.of(
                MachineJeiRecipe.fluid(CRAFTING_STATION, 55, 35, net.minecraft.world.level.material.Fluids.WATER, 1)
                    .withTooltip(Component.translatable("jei.gonzotech.note.filler_water_is_retained")),
                MachineJeiRecipe.item(INPUT, 134, 17, Items.CALCITE)),
            List.of(Component.translatable("jei.gonzotech.note.filler_calcite_dissolution"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/salt_evaporation"),
            List.of(
                MachineJeiRecipe.fluid(CRAFTING_STATION, 55, 35, net.minecraft.world.level.material.Fluids.WATER, 1)
                    .withTooltip(Component.translatable("jei.gonzotech.note.filler_salt_threshold")),
                MachineJeiRecipe.item(OUTPUT, 62, 53, ModItems.SALT.get())),
            List.of(Component.translatable("jei.gonzotech.note.filler_salt_evaporation"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/aminoblazeethanol"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, ModFluids.ETHANOL.get(), 19),
                MachineJeiRecipe.item(INPUT, 134, 17, new ItemStack(Items.BLAZE_POWDER, 2))),
            List.of(Component.translatable("jei.gonzotech.note.filler_aminoblazeethanol"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/formaldehyde"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, ModFluids.ETHANOL.get(), 19),
                MachineJeiRecipe.item(INPUT, 134, 17, ModItems.SALT.get()),
                MachineJeiRecipe.item(INPUT, 134, 35, Items.NETHER_WART),
                MachineJeiRecipe.items(INPUT, 134, 53,
                    List.of(new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL))),
                MachineJeiRecipe.fluid(OUTPUT, 105, 35, ModFluids.FORMALDEHYDE.get(), 16)),
            List.of(Component.translatable("jei.gonzotech.note.filler_formaldehyde"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/calcium_chloride"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, net.minecraft.world.level.material.Fluids.WATER, 12),
                MachineJeiRecipe.item(INPUT, 134, 17, ModItems.SALT.get()),
                MachineJeiRecipe.item(INPUT, 134, 35, Items.CALCITE),
                MachineJeiRecipe.items(INPUT, 134, 53, calciumItems()),
                MachineJeiRecipe.item(OUTPUT, 62, 53, ModItems.CALCIUM_CHLORIDE.get())),
            List.of(Component.translatable("jei.gonzotech.note.filler_calcium_chloride"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FILLER, "reaction/yellow_cake"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 55, 35, ModFluids.SULFURIC_ACID.get(), 512),
                MachineJeiRecipe.items(INPUT, 134, 17, uraniumItems()),
                MachineJeiRecipe.item(OUTPUT, 62, 53, ModItems.YELLOW_CAKE.get())),
            List.of(Component.translatable("jei.gonzotech.note.filler_yellow_cake"))));
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
                List.of(Component.translatable("jei.gonzotech.note.silicon_factory_process"),
                    Component.translatable("jei.gonzotech.note.silicon_factory_rejects"))));
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
            List.of(Component.translatable("jei.gonzotech.note.fermentation_general"))));
        addFermentationSpecial(recipes, "proto_mash", protoMash, 0.9);
        addFermentationSpecial(recipes, "fruit_mash", fruitMash, 0.9);
        addFermentationSpecial(recipes, "golden_apple", Items.GOLDEN_APPLE, 2.7);
        addFermentationSpecial(recipes, "golden_carrot", Items.GOLDEN_CARROT, 2.7);
        addFermentationSpecial(recipes, "enchanted_golden_apple", Items.ENCHANTED_GOLDEN_APPLE, 13.0);
    }

    private static void addFermentationSpecial(List<MachineJeiRecipe> recipes, String id, Item input, double alcohol) {
        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_FERMENTATION_VAT, "organic/" + id),
            List.of(
                MachineJeiRecipe.item(INPUT, 80, 35, input),
                MachineJeiRecipe.fluid(OUTPUT, 152, 35, ModFluids.MASH.get(), 128)),
            List.of(Component.translatable("jei.gonzotech.note.fermentation_special", alcohol))));
    }

    private static void addWortKettleRecipes(Map<String, List<MachineJeiRecipe>> target) {
        var recipes = target.get(MachineJeiCategories.THIRD_WORT_KETTLE.id());
        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "boil/mash_to_wort"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.MASH.get(), WortKettleBlockEntity.BOIL_RATE),
                MachineJeiRecipe.fluid(OUTPUT, 116, 35, ModFluids.WORT.get(), WortKettleBlockEntity.BOIL_RATE)),
            List.of(Component.translatable("jei.gonzotech.note.wort_boiling"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "evaporate/wort"),
            List.of(MachineJeiRecipe.fluid(INPUT, 116, 35, ModFluids.WORT.get(), WortKettleBlockEntity.EVAPORATE_RATE)),
            List.of(Component.translatable("jei.gonzotech.note.wort_evaporation", WortKettleBlockEntity.MAX_WORT_ALCOHOL))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "package/beer_mug"),
            List.of(
                MachineJeiRecipe.item(INPUT, 26, 35, Items.GLASS_BOTTLE),
                MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.WORT.get(), 128),
                MachineJeiRecipe.item(OUTPUT, 44, 35, ModItems.BEER_MUG.get())),
            List.of(Component.translatable("jei.gonzotech.note.wort_packaging"))));

        recipes.add(new MachineJeiRecipe(recipeId(MachineJeiCategories.THIRD_WORT_KETTLE, "package/beer_bucket"),
            List.of(
                MachineJeiRecipe.item(INPUT, 26, 35, Items.BUCKET),
                MachineJeiRecipe.fluid(INPUT, 80, 35, ModFluids.WORT.get(), 1_000),
                MachineJeiRecipe.item(OUTPUT, 44, 35, ModItems.BEER_BUCKET.get())),
            List.of(Component.translatable("jei.gonzotech.note.wort_packaging"))));
    }

    private static void addDistillerRecipes(Map<String, List<MachineJeiRecipe>> target) {
        List<MachineJeiRecipe.FluidAmount> raw = List.of(
            new MachineJeiRecipe.FluidAmount(ModFluids.MASH.get(), DistillerBlockEntity.PROCESS_RAW_RATE),
            new MachineJeiRecipe.FluidAmount(ModFluids.WORT.get(), DistillerBlockEntity.PROCESS_RAW_RATE));
        List<MachineJeiRecipe.Slot> inputs = List.of(
            MachineJeiRecipe.fluids(INPUT, 116, 17, raw, FLUID_RENDERER_CAPACITY),
            MachineJeiRecipe.fluid(INPUT, 44, 17, net.minecraft.world.level.material.Fluids.WATER,
                DistillerBlockEntity.PROCESS_WATER_RATE));

        target.get(MachineJeiCategories.THIRD_DISTILLER.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.THIRD_DISTILLER, "distill/clean"),
            List.of(
                inputs.get(0), inputs.get(1),
                MachineJeiRecipe.fluid(OUTPUT, 152, 17, ModFluids.DISTILLATE.get(), DistillerBlockEntity.PROCESS_RAW_RATE)),
            List.of(Component.translatable("jei.gonzotech.note.distiller_clean"))));
        target.get(MachineJeiCategories.THIRD_DISTILLER.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.THIRD_DISTILLER, "distill/contaminated"), inputs,
            List.of(Component.translatable("jei.gonzotech.note.distiller_contaminated"))));
    }

    private static void addRectifierRecipe(Map<String, List<MachineJeiRecipe>> target) {
        target.get(MachineJeiCategories.THIRD_RECTIFIER.id()).add(new MachineJeiRecipe(
            recipeId(MachineJeiCategories.THIRD_RECTIFIER, "rectify/distillate"),
            List.of(
                MachineJeiRecipe.fluid(INPUT, 62, 35, ModFluids.DISTILLATE.get(), RectifierBlockEntity.PROCESS_DISTILLATE_COST),
                MachineJeiRecipe.fluid(OUTPUT, 98, 35, ModFluids.ETHANOL.get(), RectifierBlockEntity.PROCESS_RECTIFICATE_YIELD)),
            List.of(Component.translatable("jei.gonzotech.note.rectifier_energy",
                RectifierBlockEntity.PROCESS_GTH_COST, RectifierBlockEntity.PROCESS_GTU_COST))));
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
