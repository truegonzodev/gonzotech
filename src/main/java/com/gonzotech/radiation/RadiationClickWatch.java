package com.gonzotech.radiation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (0.3.271, автор 23.10) Стража кликов по GUI: перераспределение наведёнки
 * переписывает стаки прямо в слотах, и если запись попадает в окно между
 * предсказанием клиента и обработкой клика сервером — слот обновляется «своим»
 * стаком, а результат drag'а лежит в carried. Дюп. Пока игрок кликает,
 * радиация считает дозы и эмиссии как считала, но стаки не трогает: физическая
 * запись замораживается на FREEZE_TICKS, а пропущенные шаги догорят лениво по
 * метке lastDecayTick — расхождения не накапливается.
 */
public final class RadiationClickWatch {

    /**
     * Окно заморозки записей, игровые тики (1 с при 20 tps, 0.2 с при 200).
     * 0.3.273: 30, не 20. Проход инвентаря идёт раз в 20 тиков, и окно ровно
     * в период оставляло фазовую дыру: клик за тик до прохода замораживал его,
     * клик за тик ПОСЛЕ — не замораживал следующий (проход приходился на
     * границу &lt;20). Тик-стресс автора 23.10 (frozen=false при непрерывных
     * кликах) маскировался ещё и этим; локализация по просмотру (0.3.274)
     * заменит окно целиком.
     */
    public static final int FREEZE_TICKS = 30;

    private static final Map<UUID, Long> LAST_CLICK = new ConcurrentHashMap<>();

    /** Реалтаймовый шлюз логов клика: не чаще одной строки в 150 мс на JVM. */
    private static final java.util.concurrent.atomic.AtomicLong LAST_CLICK_LOG_NS =
            new java.util.concurrent.atomic.AtomicLong();

    private RadiationClickWatch() {}

    /** true, если строку клика в лог пора писать (anti-flood при спам-ЛКМ). */
    public static boolean allowClickLog() {
        long now = System.nanoTime();
        long prev = LAST_CLICK_LOG_NS.get();
        return now - prev > 150_000_000L && LAST_CLICK_LOG_NS.compareAndSet(prev, now);
    }

    /** Вызывается из миксина обработки пакета клика, до исполнения клика меню. */
    public static void markClick(ServerPlayer player) {
        long now = player.level() instanceof ServerLevel level ? level.getServer().getTickCount() : 0L;
        if (LAST_CLICK.size() > 4096) {
            LAST_CLICK.entrySet().removeIf(entry -> now - entry.getValue() > 20L * 60L * 5L);
        }
        LAST_CLICK.put(player.getUUID(), now);
    }

    /** Игрок кликал контейнер (в т.ч. свой инвентарь) в последние FREEZE_TICKS. */
    public static boolean recentlyClicked(ServerPlayer player, long nowTick) {
        Long mark = LAST_CLICK.get(player.getUUID());
        return mark != null && nowTick - mark < FREEZE_TICKS;
    }
}
