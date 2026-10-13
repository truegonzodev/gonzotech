package com.gonzotech.integration.jei;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.registry.ModMachines;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.level.ItemLike;

import java.util.List;
import java.util.function.Supplier;

/** Single source of truth for the supported machines, icons, ids, and category sizes. */
public final class MachineJeiCategories {
    private static final int CATEGORY_WIDTH = 176;
    private static final int CATEGORY_HEIGHT = 167;

    public static final Definition FIRST_ELECTRIC_FURNACE = define(
        "first_electric_furnace", "electric_furnace", () -> ModMachines.FIRST_ELECTRIC_FURNACE_ITEM.get());
    public static final Definition SECOND_ELECTRIC_FURNACE = define(
        "second_electric_furnace", "second_electric_furnace", () -> ModMachines.SECOND_ELECTRIC_FURNACE_ITEM.get());
    public static final Definition SECOND_CRUSHER = define(
        "second_crusher", "crusher", () -> ModMachines.SECOND_CRUSHER_ITEM.get());
    public static final Definition SECOND_CENTRIFUGE = define(
        "second_centrifuge", "centrifuge", () -> ModMachines.SECOND_CENTRIFUGE_ITEM.get());
    public static final Definition THIRD_PCFSOZ = define(
        "third_pcfsoz", "pcfsoz", () -> ModMachines.PCFSOZ_ITEM.get());
    public static final Definition SECOND_GRINDER = define(
        "second_grinder", "second_grinder", () -> ModMachines.SECOND_GRINDER_ITEM.get());
    public static final Definition SECOND_PRESS = define(
        "second_press", "second_press", () -> ModMachines.SECOND_PRESS_ITEM.get());
    public static final Definition SECOND_ALLOY_FOUNDRY = define(
        "second_alloy_foundry", "alloy_foundry", () -> ModMachines.SECOND_ALLOY_FOUNDRY_ITEM.get(), 128, 128, 166);
    public static final Definition THIRD_CHEMICAL_PLANT = define(
        "third_chemical_plant", "third_chemical_plant", () -> ModMachines.THIRD_CHEMICAL_PLANT_ITEM.get());
    public static final Definition THIRD_FILLER = define(
        "third_filler", "third_filler", () -> ModMachines.THIRD_FILLER_ITEM.get());
    public static final Definition THIRD_SILICON_FACTORY = define(
        "third_silicon_factory", "silicon_factory", () -> ModItems.THIRD_SILICON_FACTORY.get());
    public static final Definition THIRD_FERMENTATION_VAT = define(
        "third_fermentation_vat", "third_fermentation_vat", () -> ModMachines.THIRD_FERMENTATION_VAT_ITEM.get());
    public static final Definition THIRD_WORT_KETTLE = define(
        "third_wort_kettle", "third_wort_kettle", () -> ModMachines.THIRD_WORT_KETTLE_ITEM.get());
    public static final Definition THIRD_DISTILLER = define(
        "third_distiller", "third_distiller", () -> ModMachines.THIRD_DISTILLER_ITEM.get());
    public static final Definition THIRD_RECTIFIER = define(
        "third_rectifier", "third_rectifier", () -> ModMachines.THIRD_RECTIFIER_ITEM.get());
    public static final Definition THIRD_DISPENSING_TAP = define(
        "third_dispensing_tap", "pump", () -> ModMachines.THIRD_DISPENSING_TAP_ITEM.get());
    public static final Definition CRIMSON_OBSIDIAN = defineTextOnly(
        "crimson_obsidian", () -> ModItems.CRIMSON_OBSIDIAN_ITEM.get());

    public static final List<Definition> ALL = List.of(
        FIRST_ELECTRIC_FURNACE,
        SECOND_ELECTRIC_FURNACE,
        SECOND_CRUSHER,
        SECOND_CENTRIFUGE,
        THIRD_PCFSOZ,
        SECOND_GRINDER,
        SECOND_PRESS,
        SECOND_ALLOY_FOUNDRY,
        THIRD_CHEMICAL_PLANT,
        THIRD_FILLER,
        THIRD_SILICON_FACTORY,
        THIRD_FERMENTATION_VAT,
        THIRD_WORT_KETTLE,
        THIRD_DISTILLER,
        THIRD_RECTIFIER,
        THIRD_DISPENSING_TAP,
        CRIMSON_OBSIDIAN
    );

    private MachineJeiCategories() {
    }

    private static Definition define(String id, String texture, Supplier<? extends ItemLike> station) {
        return define(id, texture, station, 128, 127, CATEGORY_HEIGHT);
    }

    private static Definition define(String id, String texture, Supplier<? extends ItemLike> station,
                                     int textureX, int textureY, int height) {
        return new Definition(IRecipeType.create("gonzotech", id, MachineJeiRecipe.class),
            id, texture, station, CATEGORY_WIDTH, height, textureX, textureY, false);
    }

    private static Definition defineTextOnly(String id, Supplier<? extends ItemLike> icon) {
        return new Definition(IRecipeType.create("gonzotech", id, MachineJeiRecipe.class),
            id, null, icon, CATEGORY_WIDTH, CATEGORY_HEIGHT, 128, 127, true);
    }

    public record Definition(IRecipeType<MachineJeiRecipe> type, String id, String texture,
                             Supplier<? extends ItemLike> station, int width, int height,
                             int textureX, int textureY, boolean textOnly) {
    }
}
