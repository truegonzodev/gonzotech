package com.gonzotech.machines.processing;

import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composition-driven Alloy Foundry matching.
 *
 * <p>The complete 5×5 grid is analysed as one material proportion. A named
 * preset matches only when every input material belongs to that preset and all
 * amounts are one common multiple of its ratio. Thus a corten load cannot be
 * misread as an invar batch plus ignored leftovers. If no preset matches, a
 * grid consisting exclusively of generic-eligible materials produces a
 * component-bearing {@code custom_alloy}.</p>
 */
public final class AlloyFoundryRecipes {

    private static final int MIN_CUSTOM_ALLOY_UNITS = AlloyMaterialCatalog.UNITS_PER_PORTION * 2;

    private static final List<Preset> PRESETS = List.of(
        preset("cast_iron_ingot", 1, parts("minecraft:iron", 10)),
        preset("steel_ingot", 3, parts("minecraft:iron", 30, "minecraft:coal", 10)),
        preset("stainless_steel_ingot", 7, parts("gonzotech:steel", 40, "gonzotech:chromium", 20, "gonzotech:nickel", 10)),
        preset("corten_steel_ingot", 25, parts("minecraft:iron", 190, "minecraft:copper", 30,
            "gonzotech:chromium", 10, "gonzotech:nickel", 20)),
        preset("pobedit_ingot", 6, parts("gonzotech:steel", 20, "gonzotech:tungsten", 30,
            "gonzotech:cobalt", 10, "minecraft:coal", 10)),
        preset("nitinol_ingot", 2, parts("gonzotech:nickel", 10, "gonzotech:titanium", 10)),
        preset("invar_ingot", 6, parts("minecraft:iron", 30, "gonzotech:nickel", 20, "gonzotech:stainless_steel", 10)),
        preset("ferromagnetic_ingot", 5, parts("minecraft:iron", 30, "gonzotech:cobalt", 10, "gonzotech:nickel", 10)),
        preset("cantor_ingot", 5, parts("gonzotech:cobalt", 10, "gonzotech:chromium", 10,
            "minecraft:iron", 10, "gonzotech:manganese", 10, "gonzotech:nickel", 10)),
        preset("vr20_ingot", 5, parts("gonzotech:tungsten", 40, "gonzotech:rhenium", 10)),
        preset("stellite_ingot", 11, parts("gonzotech:cobalt", 60, "gonzotech:chromium", 30,
            "gonzotech:tungsten", 10, "minecraft:coal", 10, "gonzotech:neodymium", 10)),
        preset("alnico_ingot", 10, parts("minecraft:iron", 50, "gonzotech:nickel", 10,
            "gonzotech:cobalt", 30, "gonzotech:aluminum", 10)),
        preset("telluride_ingot", 5, parts("gonzotech:bismuth", 20, "gonzotech:tellurium", 30)),
        preset("vitreloy_ingot", 9, parts("minecraft:diamond", 20, "gonzotech:nickel", 20,
            "gonzotech:ferromagnetic", 20, "gonzotech:zirconium", 30)),
        preset("semiconductor_ingot", 4, parts("gonzotech:neodymium", 10, "gonzotech:radium", 1,
            "gonzotech:silicon", 30, "minecraft:gold", 20, "minecraft:copper", 10, "minecraft:clay", 30))
    );

    private AlloyFoundryRecipes() {
    }

    /** Whether a stack is a legitimate input in either a preset or generic alloy. */
    public static boolean isSupportedInput(ItemStack stack) {
        return AlloyMaterialCatalog.ingredient(stack) != null;
    }

    /**
     * Returns one all-grid transaction, or {@code null} when the current mixture
     * is incomplete/unsupported. Named results always have priority over generic
     * output. All input forms are aggregated first, so dust is equal to its ingot
     * and ten nuggets equal one portion.
     */
    public static Batch find(Iterable<ItemStack> contents) {
        Input input = Input.collect(contents);
        if (input == null || input.materialUnits.isEmpty()) return null;

        for (Preset preset : PRESETS) {
            int multiple = preset.multipleFor(input.materialUnits);
            if (multiple > 0) {
                Item output = outputItem(preset.outputIngotId);
                return new Batch(new ItemStack(output, Math.multiplyExact(preset.outputCount, multiple)), input.byItem);
            }
        }

        if (!input.allGeneric || input.totalUnits < MIN_CUSTOM_ALLOY_UNITS
            || input.totalUnits % AlloyMaterialCatalog.UNITS_PER_PORTION != 0) {
            return null;
        }

        AlloyComposition composition = new AlloyComposition(input.materialUnits);
        AlloyProperties properties = AlloyProperties.from(composition).orElse(null);
        if (properties == null) return null;

        ItemStack output = new ItemStack(ModItems.CUSTOM_ALLOY.get(),
            input.totalUnits / AlloyMaterialCatalog.UNITS_PER_PORTION);
        output.set(ModDataComponents.ALLOY_COMPOSITION.get(), composition);
        output.set(ModDataComponents.ALLOY_TINT.get(), new AlloyTint(properties.argbTint()));
        return new Batch(output, input.byItem);
    }

    /** An already validated transaction; all listed input item forms are consumed. */
    public record Batch(ItemStack output, Map<Item, Integer> ingredients) {
        public Batch {
            if (output.isEmpty() || ingredients.isEmpty()) throw new IllegalArgumentException("Invalid alloy batch");
            output = output.copy();
            ingredients = Map.copyOf(ingredients);
        }
    }

    private record Preset(String outputIngotId, int outputCount, Map<ResourceLocation, Integer> ratio) {
        int multipleFor(Map<ResourceLocation, Integer> supplied) {
            if (supplied.size() != ratio.size()) return 0;
            int commonMultiple = 0;
            for (Map.Entry<ResourceLocation, Integer> required : ratio.entrySet()) {
                int amount = supplied.getOrDefault(required.getKey(), 0);
                if (amount == 0 || amount % required.getValue() != 0) return 0;
                int multiple = amount / required.getValue();
                if (commonMultiple == 0) commonMultiple = multiple;
                else if (commonMultiple != multiple) return 0;
            }
            return commonMultiple;
        }
    }

    private static Preset preset(String outputIngotId, int outputCount, Map<ResourceLocation, Integer> ratio) {
        return new Preset(outputIngotId, outputCount, ratio);
    }

    private static Item outputItem(String id) {
        // Pobedit is the new named preset output. It intentionally stays outside
        // the pre-existing 47-source-material catalog until it gets full forms.
        if (id.equals("pobedit_ingot")) return ModItems.POBEDIT_INGOT.get();
        var item = ModItems.INGOT_ITEMS.get(id);
        if (item == null) throw new IllegalStateException("Unregistered alloy output: " + id);
        return item.get();
    }

    /** Builds a material-unit map. Values are tenths of an ordinary portion. */
    private static Map<ResourceLocation, Integer> parts(Object... values) {
        if (values.length == 0 || values.length % 2 != 0) throw new IllegalArgumentException("Expected id/unit pairs");
        Map<ResourceLocation, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            ResourceLocation id = ResourceLocation.parse((String) values[index]);
            int units = (Integer) values[index + 1];
            if (units <= 0 || result.put(id, units) != null) throw new IllegalArgumentException("Invalid preset ratio");
        }
        return Map.copyOf(result);
    }

    private static final class Input {
        private final Map<ResourceLocation, Integer> materialUnits = new LinkedHashMap<>();
        private final Map<Item, Integer> byItem = new LinkedHashMap<>();
        private int totalUnits;
        private boolean allGeneric = true;

        static Input collect(Iterable<ItemStack> contents) {
            Input input = new Input();
            for (ItemStack stack : contents) {
                if (stack.isEmpty()) continue;
                AlloyMaterialCatalog.Ingredient ingredient = AlloyMaterialCatalog.ingredient(stack);
                if (ingredient == null) return null;
                int units = Math.multiplyExact(stack.getCount(), ingredient.units());
                input.materialUnits.merge(ingredient.material().id(), units, Math::addExact);
                input.byItem.merge(stack.getItem(), stack.getCount(), Math::addExact);
                input.totalUnits = Math.addExact(input.totalUnits, units);
                input.allGeneric &= ingredient.genericAllowed();
            }
            return input;
        }
    }
}
