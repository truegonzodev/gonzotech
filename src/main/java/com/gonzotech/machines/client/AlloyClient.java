package com.gonzotech.machines.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.component.AlloyTint;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.Nullable;

/** Client-only registrations for custom-alloy items: model tint source + worn armor layer. */
public final class AlloyClient {

    /** Leather-UV worn sheet of the custom alloy chestplate (placeholder until final art). */
    private static final ResourceLocation CUSTOM_ALLOY_LAYER_1 =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/models/armor/custom_alloy_layer_1");

    private AlloyClient() {
    }

    public static void onRegisterItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "alloy_tint"), AlloyTintSource.MAP_CODEC);
    }

    /**
     * Надетая броня из custom_alloy: вместо ванильных кожаных слоёв рендерится
     * собственная развёртка {@code custom_alloy_layer_1} (полный 64×32
     * leather-UV лист: голова/грудь/ноги/ступни), а цвет — тот же взвешенный
     * тинт сплава, что и у предмета в инвентаре (см. {@link AlloyTintSource}).
     * Без компонента — запасной DYED_COLOR стека. Humanoid-модель сама
     * выбирает UV-регион по слоту, которым надета броня, поэтому один лист
     * покрывает все четыре предмета.
     */
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions alloyArmor = new IClientItemExtensions() {
            @Nullable
            @Override
            public ResourceLocation getArmorTexture(ItemStack stack, EquipmentClientInfo.LayerType type,
                                                    EquipmentClientInfo.Layer layer, ResourceLocation _default) {
                return type == EquipmentClientInfo.LayerType.HUMANOID ? CUSTOM_ALLOY_LAYER_1 : null;
            }

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
