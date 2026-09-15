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

    /**
     * Worn sheets of the custom alloy armor (final art, vanilla leather-UV
     * layout): {@code layer_1} carries the helmet / chestplate / boots
     * regions, {@code layer_2} carries the leggings — the lower pass that
     * sits under the chestplate and boots, exactly like vanilla's
     * leather_layer_1 / leather_layer_2.
     */
    private static final ResourceLocation CUSTOM_ALLOY_LAYER_1 =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/models/armor/custom_alloy_layer_1");
    private static final ResourceLocation CUSTOM_ALLOY_LAYER_2 =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/models/armor/custom_alloy_layer_2");

    private AlloyClient() {
    }

    public static void onRegisterItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "alloy_tint"), AlloyTintSource.MAP_CODEC);
    }

    /**
     * Надетая броня из custom_alloy: вместо ванильных кожаных слоёв
     * рендерятся собственные листы того же leather-UV расклада —
     * {@code custom_alloy_layer_1} (шлем/нагрудник/ботинки) на первом
     * проходе и {@code custom_alloy_layer_2} (поножи, нижний слой под
     * нагрудником и ботинками + внутренний торс) на втором, как у ванильной
     * кожаной брони. Цвет — тот же взвешенный тинт сплава, что и у предмета
     * в инвентаре (см. {@link AlloyTintSource}); без компонента — запасной
     * DYED_COLOR стека. Humanoid-модель сама выбирает UV-регион по слоту.
     */
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions alloyArmor = new IClientItemExtensions() {
            @Nullable
            @Override
            public ResourceLocation getArmorTexture(ItemStack stack, EquipmentClientInfo.LayerType type,
                                                    EquipmentClientInfo.Layer layer, ResourceLocation _default) {
                if (type != EquipmentClientInfo.LayerType.HUMANOID) return null;
                // _default — ванильная текстура этого прохода (leather_layer_1
                // / leather_layer_2): по её имени выбираем свой лист.
                if (_default.getPath().endsWith("layer_2")) return CUSTOM_ALLOY_LAYER_2;
                return CUSTOM_ALLOY_LAYER_1;
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
