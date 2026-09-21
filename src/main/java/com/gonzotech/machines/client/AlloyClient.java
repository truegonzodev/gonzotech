package com.gonzotech.machines.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-only registrations for custom-alloy items: model tint source +
 * worn-armor dye color.
 * <p>
 * The worn sheets themselves need no client code: the equipment asset
 * ({@code assets/gonzotech/equipment/custom_alloy.json}) points each layer at
 * a short texture name that the 1.21.4 renderer resolves to
 * {@code textures/entity/equipment/<layer type>/<name>.png} inside the items
 * atlas, and our PNGs sit exactly there. Only the per-stack tint color needs
 * a hook, because the alloy items are not in {@code #minecraft:dyeable}.
 */
public final class AlloyClient {

    private AlloyClient() {
    }

    public static void onRegisterItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "alloy_tint"), AlloyTintSource.MAP_CODEC);
    }

    /**
     * Надетая броня из custom_alloy: текстуры слоёв даёт equipment-ассет
     * (см. {@link AlloyArmorMaterials}); здесь лишь подставляем точный
     * взвешенный тинт состава вместо ванильного «неокрашенный» цвета —
     * предметы не входят в {@code #minecraft:dyeable}, поэтому vanilla-по
     * умолчанию дал бы 0. Цвет тот же, что у предмета в инвентаре
     * (см. {@link AlloyTintSource}); без компонента — запасной DYED_COLOR стека.
     */
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions alloyArmor = new IClientItemExtensions() {
            @Override
            public int getDefaultDyeColor(ItemStack stack) {
                AlloyTint tint = stack.get(ModDataComponents.ALLOY_TINT.get());
                int argb = tint != null ? tint.argb() : DyedItemColor.getOrDefault(stack, 0xFFC0C0C0);
                return AlloyTintSource.colorFilter(argb);
            }
        };
        event.registerItem(alloyArmor, ModItems.ALLOY_CHESTPLATE.get());
        event.registerItem(alloyArmor, ModItems.ALLOY_HELMET.get());
        event.registerItem(alloyArmor, ModItems.ALLOY_LEGGINGS.get());
        event.registerItem(alloyArmor, ModItems.ALLOY_BOOTS.get());
    }
}
