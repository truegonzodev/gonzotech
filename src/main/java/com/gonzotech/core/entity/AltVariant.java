package com.gonzotech.core.entity;

import com.gonzotech.GonzoTechMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Скины «альт-жителя» (автор 27.09.2026): один и тот же моб при создании
 * получает один из четырёх скинов — как вариации овец/аксолотлей.
 *
 * <p>Веса автора: alt 70 %, another_alt 15 %, not_alt 11 %, so_alt 4 %
 * ({@link #weightedPick} — честная дискретная рулетка, сумма 100). Вариант
 * выбирается в конструкторе сущности, поэтому применяется ВСЕГДА: яйцо
 * призыва, /summon, спавнер, будущий натуральный спавн — любой путь проходит
 * через конструктор. Дальше значение живёт в SynchedEntityData (автосинк на
 * клиент) и NBT ({@code AltVariant}), так что скин сохраняется при
 * перезаходе.</p>
 */
public enum AltVariant {

    ALT("alt_villager", 70),
    ANOTHER_ALT("another_alt_villager", 15),
    NOT_ALT("not_alt_villager", 11),
    SO_ALT("so_alt_villager", 4);

    public static final AltVariant[] VALUES = values();

    private final String textureName;
    private final int weight;

    AltVariant(String textureName, int weight) {
        this.textureName = textureName;
        this.weight = weight;
    }

    /** Порядковый номер для SynchedEntityData (синхронизируется как int). */
    public int id() {
        return ordinal();
    }

    /** Текстура скина: {@code textures/entity/alt/<имя>.png}. */
    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID,
            "textures/entity/alt/" + this.textureName + ".png");
    }

    /** По id из SynchedEntityData; вне диапазона — обычный альт. */
    public static AltVariant byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ALT;
    }

    /** По имени из NBT; неизвестное/отсутствующее — резервный план. */
    public static AltVariant byName(String name, AltVariant fallback) {
        for (AltVariant variant : VALUES) {
            if (variant.name().equals(name)) {
                return variant;
            }
        }
        return fallback;
    }

    /** Сумма весов (100) — даёт проценты автора напрямую. */
    public static int totalWeight() {
        int sum = 0;
        for (AltVariant variant : VALUES) {
            sum += variant.weight;
        }
        return sum;
    }

    /** Взвешенный выбор: alt 70 %, another_alt 15 %, not_alt 11 %, so_alt 4 %. */
    public static AltVariant weightedPick(RandomSource random) {
        int roll = random.nextInt(totalWeight());
        for (AltVariant variant : VALUES) {
            roll -= variant.weight;
            if (roll < 0) {
                return variant;
            }
        }
        return ALT;
    }
}
