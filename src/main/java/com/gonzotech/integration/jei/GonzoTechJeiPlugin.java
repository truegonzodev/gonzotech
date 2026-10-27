package com.gonzotech.integration.jei;

import mezz.jei.api.JeiPlugin;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.List;
import java.util.Map;

/** JEI integration for all registered machine processing categories. */
@JeiPlugin
public final class GonzoTechJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        for (MachineJeiCategories.Definition definition : MachineJeiCategories.ALL) {
            registration.addRecipeCategories(new MachineJeiRecipeCategory(guiHelper, definition));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(RecipeTypes.CRAFTING, AlloyCraftingJeiRecipes.build(registration.getVanillaRecipeFactory()));
        Map<String, List<MachineJeiRecipe>> recipes = MachineJeiRecipes.build();
        for (MachineJeiCategories.Definition definition : MachineJeiCategories.ALL) {
            registration.addRecipes(definition.type(), recipes.getOrDefault(definition.id(), List.of()));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineJeiCategories.Definition definition : MachineJeiCategories.ALL) {
            registration.addCraftingStation(definition.type(), definition.station().get());
        }
        registration.addCraftingStation(MachineJeiCategories.CRIMSON_OBSIDIAN.type(),
            Items.LAVA_BUCKET, Items.REDSTONE_BLOCK);
        registration.addCraftingStation(RecipeTypes.SMELTING,
            com.gonzotech.machines.registry.ModMachines.FIRST_ELECTRIC_FURNACE_ITEM.get(),
            com.gonzotech.machines.registry.ModMachines.SECOND_ELECTRIC_FURNACE_ITEM.get());
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        // 0.3.295 (debug2.log, ×3 на перелогинах): реестр расширений JEI живёт
        // дольше пересоздания интеграции — при rejoin хук вызывается повторно и
        // второй addExtension(CraftingRecipe.class) бросает IAE «already been
        // registered», унося остальную жизненную фазу плагина. Идемпотентный
        // skip: расширение уже стоит в реестре, повторная установка не нужна.
        try {
            registration.getCraftingCategory().addExtension(
                CraftingRecipe.class, new GatedCraftingJeiExtension());
        } catch (IllegalArgumentException alreadyRegistered) {
            // идемпотентно
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<RecipeHolder<SmeltingRecipe>> vanillaSmelting = runtime.getRecipeManager()
            .createRecipeLookup(RecipeTypes.SMELTING)
            .includeHidden()
            .get()
            .toList();
        runtime.getRecipeManager().addRecipes(MachineJeiCategories.FIRST_ELECTRIC_FURNACE.type(),
            MachineJeiRecipes.vanillaSmelting(vanillaSmelting.stream(), false));
        runtime.getRecipeManager().addRecipes(MachineJeiCategories.SECOND_ELECTRIC_FURNACE.type(),
            MachineJeiRecipes.vanillaSmelting(vanillaSmelting.stream(), true));
    }
}
