package com.gonzotech.integration.jei;

import com.gonzotech.core.item.AlloyEquipmentStats;
import com.gonzotech.core.recipe.AlloyGreatswordRecipe;
import com.gonzotech.core.recipe.HeatExchangerCellRecipe;
import com.gonzotech.core.registry.GreatswordContent;
import com.gonzotech.core.registry.ModItems;
import mezz.jei.api.recipe.vanilla.IJeiShapedRecipeBuilder;
import mezz.jei.api.recipe.vanilla.IVanillaRecipeFactory;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Eight JEI-only shape previews for special composition-preserving crafting.
 * No fabricated composition, RecipeManager entry, serializer or server recipe.
 * Plain display stacks deliberately show the form, not an alloy/stat example.
 * This uses JEI 20.0.0's MC 1.21.4 display-aware vanilla factory. */
final class AlloyCraftingJeiRecipes {
    private AlloyCraftingJeiRecipes() {}
    static List<RecipeHolder<CraftingRecipe>> build(IVanillaRecipeFactory factory) {
        List<RecipeHolder<CraftingRecipe>> result=new ArrayList<>();
        Item alloy=ModItems.CUSTOM_ALLOY.get();
        add(result,factory,"heat_exchanger_cell",ModItems.HEAT_EXCHANGER_CELL.get(),
            HeatExchangerCellRecipe.pattern(),Map.of('A',alloy));
        for(AlloyEquipmentStats.Kind kind:AlloyEquipmentStats.Kind.values()) {
            Map<Character,Item> ingredients=switch(kind) {
                case PICKAXE,SWORD -> Map.of('A',alloy,'S',Items.STICK);
                default -> Map.of('A',alloy);
            };
            add(result,factory,"alloy_"+kind.name().toLowerCase(Locale.ROOT),kind.outputItem(),kind.pattern(),ingredients);
        }
        add(result,factory,"alloy_greatsword",GreatswordContent.ALLOY_GREATSWORD.get(),AlloyGreatswordRecipe.pattern(),
            Map.of('I',alloy,'B',ModItems.METAL_BLOCK_ITEMS.get("titanium_block").get(),'R',ModItems.REBAR.get()));
        return List.copyOf(result);
    }
    private static void add(List<RecipeHolder<CraftingRecipe>> result,IVanillaRecipeFactory factory,
                            String id,Item output,String[] pattern,Map<Character,Item> ingredients) {
        IJeiShapedRecipeBuilder builder=factory.createShapedRecipeBuilder(CraftingBookCategory.MISC,display(output));
        ingredients.forEach((symbol,item)->builder.define(symbol,Ingredient.of(item),display(item)));
        for(String row:pattern) builder.pattern(row);
        result.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE,
            ResourceLocation.fromNamespaceAndPath("gonzotech","jei_preview/"+id)),builder.build()));
    }
    private static SlotDisplay display(Item item) { return new SlotDisplay.ItemStackSlotDisplay(new ItemStack(item)); }
}
