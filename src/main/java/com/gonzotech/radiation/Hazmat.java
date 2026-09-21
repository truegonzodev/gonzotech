package com.gonzotech.radiation;

import com.gonzotech.GonzoTechMod;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;

/**
 * Хазмат I — защитный костюм (спека автора 22.09.2026). Четыре части:
 * маска (шлем), фартук (нагрудник), трико (штаны), шуфли (ботинки).
 *
 * <p><b>Защита от облучения:</b> полный сет режет {@link #FULL_SHIELDING} (60 %)
 * входящей дозы, но только пока доза/сек не выше {@link #SOFT_DOSE_MILLI} (20 mZt);
 * дальше защита падает линейно на 1 % за каждый лишний mZt/с — горячий источник
 * пробивает костюм (при 80 mZt/с и выше он бесполезен).</p>
 *
 * <p>Неполный сет режет пропорционально частям (по {@value #SHIELDING_PER_PIECE} %
 * за часть — 4 части = 60 %). Это моя интерпретация: в спеке было только «фулл сет
 * режет 60 %». Меняется одной константой.</p>
 *
 * <p>Материал брони — копия кожаного (как у сплава): защита тела 1/3/2/1, чинится
 * кожей; текстуры — плейсхолдер (копия слоя сплава), арт за автором.</p>
 */
public final class Hazmat {

    public static final ResourceKey<EquipmentAsset> HAZMAT_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID,
                    ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "hazmat"));

    /** Костюм: те же слоты защиты, что у кожаного/сплавного набора. */
    public static final ArmorMaterial HAZMAT_MATERIAL = new ArmorMaterial(
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
            HAZMAT_ASSET);

    /** Сколько режет полный сет (доля дозы). */
    private static final double FULL_SHIELDING = 0.60;
    /** Прибавка за каждую надетую часть (4 × 0.15 = 0.60). */
    private static final double SHIELDING_PER_PIECE = 0.15;
    /** До этой дозы/сек (mZt) защита полная. */
    private static final double SOFT_DOSE_MILLI = 20.0;
    /** Падение защиты: 1 % на каждый mZt/с сверх мягкой дозы. */
    private static final double FALLOFF_PER_MILLI = 0.01;

    private Hazmat() {
    }

    /** Части комплекта (в порядке слотов), которые считает костюм. */
    private static final EquipmentSlot[] PIECES = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Сколько частей хазмата надето (0..4). Сверяем сами предметы: не зависим от API ArmorItem. */
    public static int pieces(LivingEntity entity) {
        int worn = 0;
        for (int i = 0; i < PIECES.length; i++) {
            if (entity.getItemBySlot(PIECES[i]).is(com.gonzotech.core.registry.ModItems.HAZMAT_PIECES.get(i).get())) {
                worn++;
            }
        }
        return worn;
    }

    /**
     * Множитель входящей дозы (1.0 — костюма нет; 0.4 — полный сет под слабой дозой).
     *
     * @param entity         игрок
     * @param dosePerSecond  доза/сек в nZt (инвентарь + фон чанка)
     */
    public static double factor(LivingEntity entity, double dosePerSecond) {
        int worn = pieces(entity);
        if (worn == 0) {
            return 1.0;
        }
        double shielding = Math.min(FULL_SHIELDING, SHIELDING_PER_PIECE * worn);
        double milli = dosePerSecond / RadUnits.MILLI;
        if (milli > SOFT_DOSE_MILLI) {
            shielding -= FALLOFF_PER_MILLI * (milli - SOFT_DOSE_MILLI);
        }
        if (shielding <= 0.0) {
            return 1.0;
        }
        return 1.0 - Math.min(FULL_SHIELDING, shielding);
    }
}
