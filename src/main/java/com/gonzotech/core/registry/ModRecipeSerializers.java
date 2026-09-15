package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.recipe.AlloyEquipmentRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe serializers for dynamic Gonzo Tech crafting-table results. */
public final class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, GonzoTechMod.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyEquipmentRecipe.Pickaxe>> ALLOY_PICKAXE =
        RECIPE_SERIALIZERS.register("alloy_pickaxe",
            () -> new CustomRecipe.Serializer<>(AlloyEquipmentRecipe.Pickaxe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyEquipmentRecipe.Sword>> ALLOY_SWORD =
        RECIPE_SERIALIZERS.register("alloy_sword",
            () -> new CustomRecipe.Serializer<>(AlloyEquipmentRecipe.Sword::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyEquipmentRecipe.Chestplate>> ALLOY_CHESTPLATE =
        RECIPE_SERIALIZERS.register("alloy_chestplate",
            () -> new CustomRecipe.Serializer<>(AlloyEquipmentRecipe.Chestplate::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyEquipmentRecipe.Helmet>> ALLOY_HELMET =
        RECIPE_SERIALIZERS.register("alloy_helmet",
            () -> new CustomRecipe.Serializer<>(AlloyEquipmentRecipe.Helmet::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyEquipmentRecipe.Leggings>> ALLOY_LEGGINGS =
        RECIPE_SERIALIZERS.register("alloy_leggings",
            () -> new CustomRecipe.Serializer<>(AlloyEquipmentRecipe.Leggings::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<AlloyEquipmentRecipe.Boots>> ALLOY_BOOTS =
        RECIPE_SERIALIZERS.register("alloy_boots",
            () -> new CustomRecipe.Serializer<>(AlloyEquipmentRecipe.Boots::new));

    public static void register(IEventBus modEventBus) {
        RECIPE_SERIALIZERS.register(modEventBus);
    }

    private ModRecipeSerializers() {
    }
}
