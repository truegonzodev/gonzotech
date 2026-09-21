package com.gonzotech.radiation;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Доза игрока и её категории — <b>единственный источник правды</b> для шкалы
 * «Облучение» ({@code PlayerPsyche.radiation}, тысячные: 0..1000 = 0..100%).
 *
 * <p>Категории — авторские, из отчёта дозиметра (спека 20.09, формат 21.09);
 * раньше они жили только в тексте дозиметра, теперь по ним же работают
 * последствия для здоровья ({@link RadSickness}) и лечение
 * ({@link AntiradinItem}):</p>
 * <pre>
 *   &lt; 5%   — фон в норме        (FINE)       последствий нет
 *   5–20%  — повышенное облучение (ELEVATED)
 *   20–50% — опасная доза        (DANGEROUS)
 *   50–80% — критическая доза    (CRITICAL)
 *   80–100%— смертельная доза    (LETHAL)     на 100% — смерть
 * </pre>
 */
public final class RadDose {

    /** Полная шкала = 100% (совпадает с {@code PlayerPsyche.MAX}). */
    public static final int MAX = 1000;

    /** Пороги начала категорий, в тысячных. */
    private static final int ELEVATED_AT = 50;
    private static final int DANGEROUS_AT = 200;
    private static final int CRITICAL_AT = 500;
    private static final int LETHAL_AT = 800;

    /** Ключ урона «лучевая болезнь» — тип лежит в датапаке мода ({@code gonzotech:radiation}). */
    private static final ResourceKey<DamageType> RADIATION =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "radiation"));

    /** Категория дозы; тексты — те же lang-ключи, что печатает дозиметр. */
    public enum Category {
        FINE("message.gonzotech.dosimeter.cat.fine"),
        ELEVATED("message.gonzotech.dosimeter.cat.elevated"),
        DANGEROUS("message.gonzotech.dosimeter.cat.dangerous"),
        CRITICAL("message.gonzotech.dosimeter.cat.critical"),
        LETHAL("message.gonzotech.dosimeter.cat.lethal");

        private final String langKey;

        Category(String langKey) {
            this.langKey = langKey;
        }

        public String langKey() {
            return langKey;
        }

        /** Подпись категории для чата/HUD. */
        public Component label() {
            return Component.translatable(langKey);
        }
    }

    private RadDose() {
    }

    /** Категория по текущей дозе (тысячные). */
    public static Category category(int permille) {
        if (permille < ELEVATED_AT) {
            return Category.FINE;
        }
        if (permille < DANGEROUS_AT) {
            return Category.ELEVATED;
        }
        if (permille < CRITICAL_AT) {
            return Category.DANGEROUS;
        }
        if (permille < LETHAL_AT) {
            return Category.CRITICAL;
        }
        return Category.LETHAL;
    }

    /** Доза в процентах (для текстов дозиметра/антирадина). */
    public static double percent(int permille) {
        return permille / 10.0;
    }

    /** Источник урона «лучевая болезнь» (сообщение о смерти — {@code death.attack.radiation}). */
    public static DamageSource damageSource(ServerLevel level) {
        Holder<DamageType> type = level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(RADIATION);
        return new DamageSource(type);
    }
}
