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
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.List;

/**
 * Доза игрока и её категории — <b>единственный источник правды</b> для шкалы
 * «Облучение» ({@code PlayerPsyche.radiation}, тысячные: 0..1000 = 0..100%).
 *
 * <p>Числа — ровно из спеки автора 22.09.2026 (правка захода 1 + некроз/очищение).
 * Здесь же лежат все ставки лечения, чтобы «шкала», «эффекты» и «лечение»
 * не разъезжались.</p>
 *
 * <p><b>Категории и эффекты.</b> «Постоянные» висят всё время категории и
 * обновляются ежесекундно; «вспышки» — отдельный пул: раз в секунду с шансом
 * {@code flashChance} игрок получает 1–3 эффекта из пула на случайную
 * длительность (см. {@link RadSickness}). Уровни записаны как в игре:
 * «голод I» = {@code effect.hunger.level.0}.</p>
 * <pre>
 *   повышенное (5–20 %)   постоянно: голод I
 *                         вспышки (0.54 %/с): слабость I 5–15 с, замедление I 5–15 с
 *   опасная (20–50 %)     постоянно: голод II, слабость I
 *                         вспышки (0.67 %/с): усталость I 5–30 с, замедление I 5–15 с, отравление I 3–6 с
 *   критическая (50–80 %) постоянно: голод II, слабость II, усталость I, отравление I
 *                         вспышки (0.88 %/с): замедление I 5–35 с, тошнота I 3–10 с
 *   смертельная (80–100 %)постоянно: голод III, слабость III, усталость II, замедление I, отравление II
 *                         вспышки (0.98 %/с): замедление II 5–45 с, тошнота II 3–15 с, иссушение I 5–10 с
 * </pre>
 *
 * <p><b>Смерть на 100 %</b> — свой тип урона {@code gonzotech:radiation} в теге
 * {@code minecraft:bypasses_armor}. Периодического урона у категорий больше нет
 * (в новой таблице автора его не было).</p>
 */
public final class RadDose {

    /** Полная шкала = 100 % (совпадает с {@code PlayerPsyche.MAX}). */
    public static final int MAX = 1000;

    /** Пороги категорий, в тысячных. */
    private static final int ELEVATED_AT = 50;
    private static final int DANGEROUS_AT = 200;
    private static final int CRITICAL_AT = 500;
    private static final int LETHAL_AT = 800;

    /** Некроз начинает набираться выше 30 % (0.1 %/с), выше 70 % — сразу II уровень. */
    public static final int NECROSIS_AT = 300;
    public static final int NECROSIS_LEVEL2_AT = 700;
    public static final double NECROSIS_CHANCE_PER_SECOND = 0.001;

    /** Антирадиновый абсорбент: 20 % дозы за 30 с на уровне I (+20 % за уровень). */
    public static final int CLEANSE_PER_LEVEL_PERMILLE = 200;
    public static final int CLEANSE_SECONDS = 30;

    /** Ключ урона «лучевая болезнь» — тип лежит в датапаке мода ({@code gonzotech:radiation}). */
    private static final ResourceKey<DamageType> RADIATION =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "radiation"));

    /** Постоянный («фоновый») эффект категории: держится, пока доза в категории. */
    public record Steady(Holder<MobEffect> effect, int amplifier) {
    }

    /** Вариант вспышки: эффект с уровнем и случайной длительностью в диапазоне. */
    public record Flavor(Holder<MobEffect> effect, int amplifier, int minSeconds, int maxSeconds) {
    }

    /** Категория дозы: порог, шанс вспышки за секунду, постоянные эффекты, пул вспышек. */
    public record Tier(int threshold, double flashChance, List<Steady> steady, List<Flavor> pool) {
    }

    private static final Tier FINE = new Tier(0, 0.0, List.of(), List.of());

    private static final Tier ELEVATED = new Tier(ELEVATED_AT, 0.0054,
            List.of(new Steady(MobEffects.HUNGER, 0)),
            List.of(new Flavor(MobEffects.WEAKNESS, 0, 5, 15),
                    new Flavor(MobEffects.MOVEMENT_SLOWDOWN, 0, 5, 15)));

    private static final Tier DANGEROUS = new Tier(DANGEROUS_AT, 0.0067,
            List.of(new Steady(MobEffects.HUNGER, 1),
                    new Steady(MobEffects.WEAKNESS, 0)),
            List.of(new Flavor(MobEffects.DIG_SLOWDOWN, 0, 5, 30),
                    new Flavor(MobEffects.MOVEMENT_SLOWDOWN, 0, 5, 15),
                    new Flavor(MobEffects.POISON, 0, 3, 6)));

    private static final Tier CRITICAL = new Tier(CRITICAL_AT, 0.0088,
            List.of(new Steady(MobEffects.HUNGER, 1),
                    new Steady(MobEffects.WEAKNESS, 1),
                    new Steady(MobEffects.DIG_SLOWDOWN, 0),
                    new Steady(MobEffects.POISON, 0)),
            List.of(new Flavor(MobEffects.MOVEMENT_SLOWDOWN, 0, 5, 35),
                    new Flavor(MobEffects.CONFUSION, 0, 3, 10)));

    private static final Tier LETHAL = new Tier(LETHAL_AT, 0.0098,
            List.of(new Steady(MobEffects.HUNGER, 2),
                    new Steady(MobEffects.WEAKNESS, 2),
                    new Steady(MobEffects.DIG_SLOWDOWN, 1),
                    new Steady(MobEffects.MOVEMENT_SLOWDOWN, 0),
                    new Steady(MobEffects.POISON, 1)),
            List.of(new Flavor(MobEffects.MOVEMENT_SLOWDOWN, 1, 5, 45),
                    new Flavor(MobEffects.CONFUSION, 1, 3, 15),
                    new Flavor(MobEffects.WITHER, 0, 5, 10)));

    /** Отображаемая категория для дозиметра (пять текстов, как раньше). */
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

        public Component label() {
            return Component.translatable(langKey);
        }
    }

    private RadDose() {
    }

    /** Категория по текущей дозе (тысячные). */
    public static Category category(int permille) {
        Tier t = tier(permille);
        if (t == FINE) return Category.FINE;
        if (t == ELEVATED) return Category.ELEVATED;
        if (t == DANGEROUS) return Category.DANGEROUS;
        if (t == CRITICAL) return Category.CRITICAL;
        return Category.LETHAL;
    }

    /** Полное описание категории (эффекты, шансы) — по текущей дозе. */
    public static Tier tier(int permille) {
        if (permille < ELEVATED_AT) {
            return FINE;
        }
        if (permille < DANGEROUS_AT) {
            return ELEVATED;
        }
        if (permille < CRITICAL_AT) {
            return DANGEROUS;
        }
        if (permille < LETHAL_AT) {
            return CRITICAL;
        }
        return LETHAL;
    }

    /** Доза в процентах (для текстов дозиметра/абсорбента). */
    public static double percent(int permille) {
        return permille / 10.0;
    }

    /**
     * Сколько дозы (в тысячных) выводит «Очищение» за одну секунду.
     * Дробную часть копит вызывающий ({@link RadCleanse}).
     */
    public static double cleansePerSecond(int dose, int amplifier) {
        return (double) dose * (CLEANSE_PER_LEVEL_PERMILLE * (amplifier + 1))
                / (1000.0 * CLEANSE_SECONDS);
    }

    /** Источник урона «лучевая болезнь» (сообщение о смерти — {@code death.attack.radiation}). */
    public static DamageSource damageSource(ServerLevel level) {
        Holder<DamageType> type = level.registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(RADIATION);
        return new DamageSource(type);
    }
}
