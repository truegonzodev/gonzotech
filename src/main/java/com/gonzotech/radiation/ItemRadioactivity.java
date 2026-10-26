package com.gonzotech.radiation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;


/** Per-item induced rate in the external owner-persisted engine.
 * Item components and item serialization never contain dynamic radiation. */
public final class ItemRadioactivity {
    public static final int DECAY_STEP_TICKS = 20;
    public static final double GROWTH = 1.25;
    public static final double INDUCED_CAP = 5.0 * RadUnits.UNIT;
    /** (автор 22.10, баланс) Стоимость нагрева: множитель приёма
     *  (1 − 0.00097)^L для дозы L в mZt — каждый 1 mZt снимает 0.097 %
     *  получения наведёнки ОТ ТЕКУЩЕГО, не от потолка. При 1 Zt остаётся
     *  ~38 % скорости (нагрев в 2.6 раза затратнее). */
    public static final double UPTAKE_COST = 0.00097;
    /** (автор 22.10) Полная скорость отдачи: на большой дозе предмет теряет
     *  SELF_DISCHARGE (25 %) в секунду — авторский ряд 5→3.7→2.7→1.9. Зеркалит
     *  скорость роста: что логистика накручивает, отдача уносит. Заменяет жёсткое
     *  плато 0.3.249 (всё сверх 10 mZt теряло половину) — плато-обрушения нет,
     *  отдача плавная и пропорциональна текущему. */
    public static final double SELF_DISCHARGE = GROWTH - 1.0;
    /** Масштаб насыщения отдачи: «среднее плато инвентаря» автора ~33 mZt
     *  (0.3.298: ×MILLI — формула живёт в nZt, таблица автора в mZt; до правки
     *  равновесие замирало на ~30 nZt — в миллион раз ниже задания) —
     *  там отдача достигает 76 % полной скорости, ниже — спадает линейно
     *  (~0.76 %/с на каждый 1 mZt у нуля). Наведёнка НЕ может самоподдерживающе
     *  стоять выше ~40–70 mZt ни у какого источника: «жутко радиоактивным»
     *  делают пресеты (стоп радия), а не накопленный фон — дюп-петли
     *  «инвентарь ↔ инвентарь» закрываются сами, без плато-ножа. */
    public static final double DISCHARGE_ANCHOR = 33.0 * RadUnits.MILLI;  // 0.3.298
    public static final double CLEANUP_FLOOR = RadUnits.MILLI;  // 1 mZt (0.3.298)

    private ItemRadioactivity() {
    }

    public static boolean isLeadImmune(ItemStack stack) {
        if (stack.isEmpty()) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath().contains("lead");
    }

    /** Runtime rate. Dynamic radiation is never read from item components. */
    public static double getInduced(ItemStack stack) {
        return com.gonzotech.radiation.engine.ExternalRadiation.read(stack).rate();
    }

    public static void setInduced(ItemStack stack, double value) {
        com.gonzotech.radiation.engine.ExternalRadiation.rate(stack,
                value >= CLEANUP_FLOOR ? value : 0.0);
    }

    private static void clear(ItemStack stack, String cause) {
        com.gonzotech.radiation.engine.ExternalRadiation.rate(stack, 0);
    }

    /**
     * Рассчитать следующий per-item уровень без изменения ItemStack.
     *
     * Рост идёт на предмет; перенос и взвешенное объединение обслуживает внешний движок.
     */
    public static double nextInduced(double current, ItemStack stack,
                                     boolean hasSource, double sourceNzt, double factor) {
        // Preset emission is a permanent floor, not an inducible part. A
        // source may only raise a target above its own intrinsic baseline —
        // в per-item деньгах: минус собственный пресет ПРЕДМЕТА, не стака.
        // Носитель (щипцы/ковш, 0.3.83): собственного «фундамента» не имеет —
        // наведёнка растёт от ЧУЖИХ источников инвентаря/чанка; содержимое
        // свою тару не облучает (sourceLevel уже исключает сам стак).
        double intrinsic = stack.getItem() instanceof CarrierItem
                ? 0.0 : RadSources.emissionPerItem(stack);
        double target = Math.min(Math.max(0.0, sourceNzt - intrinsic), INDUCED_CAP);
        double next;
        if (hasSource && target >= CLEANUP_FLOOR && factor > 0.0) {  // 0.3.298: порог — 1 mZt
            if (current > target) {
                next = selfDischarge(current);
            } else {
                // Старт — 1 mZt на предмет (тот же CLEANUP_FLOOR: меньшие
                // значения setInduced съедает, поэтому сид не может быть
                // дробным на стаке — это и был корень #27).
                double seeded = Math.max(current, CLEANUP_FLOOR);
                // (автор 22.10) «Затратность» нагрева: текущая доза душит
                // приём по геометрической (1−0.00097)^L. Равновесие ниже
                // цели всегда — mutual-hold дюпа нет даже без жёсткого плато.
                // 0.3.298: показатель степени — в mZt (строка автора «на каждый
                // 1 mZt −0.097 %»), значение — в nZt: делим на масштаб.
                double uptake = Math.pow(1.0 - UPTAKE_COST, seeded / RadUnits.MILLI);
                // Отдача действует и на ветке роста, иначе равновесие = цель и
                // «предметы держат друг друга» возвращается (дюп 0.3.249).
                next = selfDischarge(Math.min(
                    seeded + seeded * (GROWTH - 1.0) * (1.0 - seeded / target) * factor * uptake,
                    target));
            }
        } else {
            next = current > 0.0 ? selfDischarge(current) : 0.0;
        }
        return next;
    }

    /**
     * Отдача «от текущего» (0.3.268, баланс автора): доля потерь за секунду
     * = SELF_DISCHARGE × tanh(L / DISCHARGE_ANCHOR). 5 Zt → ровно ×0.75 в
     * первую секунду (ряд 5→3.75→2.81→2.11 ≈ авторских 3.7→2.7→1.9), у нуля
     * — мягкое стекание без обрезки, плато-ножа 0.3.249 больше нет.
     */
    private static double selfDischarge(double value) {
        return value * (1.0 - SELF_DISCHARGE * Math.tanh(value / DISCHARGE_ANCHOR));
    }

    /**
     * Спад в покое за {@code steps} секунд (без источника: контейнеры, пол,
     * рамки, оффлайн). Ряд сходится к floor(1 mZt) менее чем за ~800 шагов с
     * любой высоты, поэтому длинный простой считается циклом с ограничением,
     * а не формулой — хвостов «вечных» предметов закон tanh не оставляет.
     */
    public static double restDecay(double value, long steps) {
        if (value <= 0.0D || steps <= 0L) return value;
        long n = Math.min(steps, 1024L);
        for (long i = 0; i < n && value >= CLEANUP_FLOOR; i++) {
            value = selfDischarge(value);
        }
        if (steps > n && value >= CLEANUP_FLOOR) value = 0.0D;
        return value;
    }

    /**
     * Шаг роста одного предмета. {@code sourceNzt} — суммарный уровень
     * локального источника (для стоков — уже со скидкой ре-излучения,
     * {@link #transferEmission}); деления на размер стака нет с 0.3.268:
     * каждый предмет догоняет цель как одиночный.
     */
    public static void tickInduced(ItemStack stack, boolean hasSource, double sourceNzt, double factor) {
        double next = nextInduced(getInduced(stack), stack, hasSource, sourceNzt, factor);
        if (next > 0.0) setInduced(stack, next);
        else clear(stack, "tick-floor");
    }

    /**
     * Обычный секундный тик инвентаря с ленивым наверстыванием после контейнера
     * или ItemEntity. Пропущенные интервалы считаются покоем (без взаимного
     * облучения); на текущем шаге применяется только реальный источник инвентаря.
     */
    public static double tickInducedAt(ItemStack stack, boolean hasSource, double sourceNzt,
                                       double factor, long nowTick) {
        if (stack.isEmpty()) return 0.0D;
        long last = lastDecayTick(stack);
        if (last == Long.MIN_VALUE) {
            tickInduced(stack, hasSource, sourceNzt, factor);
            markDecayTick(stack, nowTick);
            decayInducedDeepLazily(stack, nowTick);
            return getInduced(stack);
        }
        long elapsed = nowTick - last;
        if (elapsed < DECAY_STEP_TICKS && elapsed >= 0L) {
            decayInducedDeepLazily(stack, nowTick);
            return getInduced(stack);
        }
        long steps = elapsed < 0L ? 1L : Math.max(1L, elapsed / DECAY_STEP_TICKS);
        if (steps > 1L) decayInducedSteps(stack, steps - 1L);
        tickInduced(stack, hasSource, sourceNzt, factor);
        markDecayTick(stack, nowTick);
        decayInducedDeepLazily(stack, nowTick);
        return getInduced(stack);
    }

    /** Пропущенные пассивные интервалы перед текущим шагом источника — без записи NBT. */
    public static double inducedBeforeSourceStep(ItemStack stack, long nowTick) {
        if (stack.isEmpty()) return 0.0D;
        double current = getInduced(stack);
        long last = lastDecayTick(stack);
        if (current <= 0.0D || last == Long.MIN_VALUE) return current;
        long elapsed = nowTick - last;
        if (elapsed < 0L) return current;
        long steps = elapsed / DECAY_STEP_TICKS;
        if (steps > 1L) current = restDecay(current, steps - 1L);
        return current >= CLEANUP_FLOOR ? current : 0.0D;
    }

    /**
     * Ленивый no-source decay для вещей в контейнерах, на полу и в рамках.
     * Возвращает true, если внешнее состояние радиации изменилось. Пропущенные интервалы
     * применяются степенями, поэтому обновление не требует тика каждой вещи.
     */
    public static boolean decayInducedLazily(ItemStack stack, long nowTick) {
        if (stack.isEmpty()) return false;
        double before = getInduced(stack);
        if (before <= 0.0D) return false;
        long last = lastDecayTick(stack);
        if (last == Long.MIN_VALUE) {
            markDecayTick(stack, nowTick);
            return true;
        }
        long elapsed = nowTick - last;
        if (elapsed < DECAY_STEP_TICKS && elapsed >= 0L) return false;
        long steps = elapsed < 0L ? 1L : Math.max(1L, elapsed / DECAY_STEP_TICKS);
        decayInducedSteps(stack, steps);
        if (getInduced(stack) > 0.0D) {
            markDecayTick(stack, elapsed < 0L ? nowTick : last + steps * DECAY_STEP_TICKS);
        }
        return getInduced(stack) != before;
    }

    /**
     * Ленивое затухание контейнерного предмета и вложенных слотов (например,
     * шалкера-предмета). Вложенные вещи не облучают друг друга; их собственная
     * наведёнка лишь теряет активность.
     */
    public static boolean decayInducedDeepLazily(ItemStack stack, long nowTick) {
        return decayInducedDeepLazily(stack, nowTick, 0);
    }

    private static boolean decayInducedDeepLazily(ItemStack stack, long nowTick, int depth) {
        if (stack.isEmpty()) return false;
        boolean changed = decayInducedLazily(stack, nowTick);
        if (stack.getItem() instanceof CarrierItem carrier) {
            ItemStack content = carrier.previewCarried(stack);
            changed |= decayInducedLazily(content, nowTick);
            com.gonzotech.radiation.engine.ExternalRadiation.writeContents(stack,
                com.gonzotech.radiation.engine.ExternalRadiation.read(content));
        }
        if (depth >= 4) return changed;
        // Read the immutable contents, update ONLY the external radiation views.
        for (ItemStack inner : RadSources.contentsOf(stack)) {
            changed |= decayInducedDeepLazily(inner, nowTick, depth + 1);
        }
        return changed;
    }

    /** Отметить уже обработанный инвентарный шаг, в том числе виртуальный held-stack. */
    public static void markDecayTick(ItemStack stack, long nowTick) {
        com.gonzotech.radiation.engine.ExternalRadiation.tick(stack, nowTick);
    }

    private static long lastDecayTick(ItemStack stack) {
        return com.gonzotech.radiation.engine.ExternalRadiation.read(stack).tick();
    }

    private static void decayInducedSteps(ItemStack stack, long steps) {
        double current = getInduced(stack);
        if (current <= 0.0D || steps <= 0L) return;
        double next = restDecay(current, steps);
        if (next >= CLEANUP_FLOOR) setInduced(stack, next);
        else clear(stack, "rest-catchup steps=" + steps);
    }

    /**
     * (автор 22.10) Наведёнка переизлучается «на предмет» на 75 % мощности:
     * уран (пресет, бесконечный) зарядит мандарин на 1.0, а мандарин с тем же
     * номиналом наведёнки — на 0.75 от того же темпа. Только перенос
     * «наведёнка → предмет»: дозы игроку и фон чанка считаются полной
     * эмиссией.
     */
    public static final double INDUCED_REEMIT = 0.75;

    /** Эмиссия стака в передаче «на предмет»: пресет полной мощностью,
     *  наведёнка — с {@link #INDUCED_REEMIT}. Носитель (щипцы/ковш, автор 22.10
     *  п.4) — двойной фильтр содержимого: пресет проходит ×0.6, наведёнка
     *  ×0.75×0.6 — зарядившийся сток мандаринов в щипцах светит другим
     *  предметам на 0.45 номинала, урановый слиток — на ×0.6, как и прежде. */
    public static double transferEmission(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        double sum = RadSources.emissionOfStack(stack)   // пресет (у носителя уже −40 %)
            + INDUCED_REEMIT * getInduced(stack) * Math.max(1, stack.getCount());
        if (stack.getItem() instanceof CarrierItem carrier) {
            // carriedEmission (0.3.79) проносит сквозь экран только пресет
            // содержимого; его наведёнку добавляем здесь — оба фильтра подряд.
            ItemStack content = carrier.previewCarried(stack);
            if (!content.isEmpty()) {
                sum += CarrierItem.RADIOACTIVITY_FACTOR * INDUCED_REEMIT
                    * getInduced(content) * Math.max(1, content.getCount());
            }
        }
        return sum;
    }

    /** Полная эмиссия стака: preset per-item × count + induced per-item × count. */
    public static double totalEmission(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        double sum = RadSources.emissionOfStack(stack) + getInduced(stack) * stack.getCount();
        if (stack.getItem() instanceof CarrierItem carrier) {
            ItemStack content = carrier.previewCarried(stack);
            sum += CarrierItem.RADIOACTIVITY_FACTOR * getInduced(content) * content.getCount();
        }
        return sum;
    }

}
