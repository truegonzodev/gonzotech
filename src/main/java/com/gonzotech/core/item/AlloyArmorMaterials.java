package com.gonzotech.core.item;

import com.gonzotech.GonzoTechMod;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;

/**
 * Armor material of the custom-alloy set. A copy of the vanilla leather
 * material (same bare creative-tab stats) that only swaps the equipment
 * asset: the client reads {@code assets/gonzotech/equipment/custom_alloy.json}
 * (the {@code EquipmentAssetManager} scans {@code assets/<ns>/equipment/} of
 * every resource pack). Each layer's {@code texture} is a short name resolved
 * by the renderer to {@code textures/entity/equipment/<layer type>/<name>.png}
 * (the items atlas has a {@code entity/equipment} directory source), so our
 * sheets live at {@code textures/entity/equipment/humanoid/custom_alloy.png}
 * (helmet/chestplate/boots) and
 * {@code textures/entity/equipment/humanoid_leggings/custom_alloy.png}
 * (leggings + inner torso, the lower pass). Both layers are dyeable so the
 * per-stack alloy tint flows through the vanilla dye pipeline.
 */
public final class AlloyArmorMaterials {

    public static final ResourceKey<EquipmentAsset> CUSTOM_ALLOY_ASSET =
        ResourceKey.create(EquipmentAssets.ROOT_ID,
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "custom_alloy"));

    public static final ArmorMaterial CUSTOM_ALLOY = new ArmorMaterial(
        5,
        Util.make(new EnumMap<>(ArmorType.class), map -> {
            map.put(ArmorType.BOOTS, 1);
            map.put(ArmorType.LEGGINGS, 2);
            map.put(ArmorType.CHESTPLATE, 3);
            map.put(ArmorType.HELMET, 1);
            map.put(ArmorType.BODY, 3);
        }),
        15,
        SoundEvents.ARMOR_EQUIP_LEATHER,
        0.0F,
        0.0F,
        ItemTags.REPAIRS_LEATHER_ARMOR,
        CUSTOM_ALLOY_ASSET);

    private AlloyArmorMaterials() {
    }
}
