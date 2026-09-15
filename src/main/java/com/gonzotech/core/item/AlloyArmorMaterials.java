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
 * every resource pack), which points the humanoid passes at our two sheets —
 * {@code custom_alloy_layer_1} (helmet/chestplate/boots) and
 * {@code custom_alloy_layer_2} (leggings + inner torso, the lower pass) —
 * and makes them dyeable so the per-stack alloy tint flows through the
 * vanilla dye pipeline.
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
