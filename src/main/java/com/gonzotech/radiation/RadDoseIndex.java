package com.gonzotech.radiation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Живой индекс наведённых доз (0.3.294, корневой ремонт дюпа по лог-разбору
 * автора 25.10: 193 «воскрешения» стаков в креативной сетке, +105 шт по
 * [LEDGER], 85 расхождений stateId).
 *
 * <p><b>Проблема, которую это решает на корню.</b> Радиационный проход каждые
 * ~20 тиков дописывал компонент {@code gonzo_rad} прямо в ItemStack'ы живого
 * инвентаря. Сервер становился единственным источником изменений, о которых
 * клиент не знает до delivery: stateId инвентарного меню уезжал вперёд
 * (нормальные клики — rejection'ом, а креативный канал {@code
 * handleSetCreativeModeSlot} в 1.21.4 не проверяет НИ stateId, НИ прежнее
 * содержимое слота), и весь client-side сортировочный софт (ClientSort,
 * ChestPreview, SmoothSwapping) получал легитимный канал записать серверу
 * устаревшую сетку целиком — увезённый стак «воскресал» на старом адресе
 * рядом со своей новой копией. Физический дюп, переживающий rejoin. Любой
 * guard на входе (отмена пакетов по эвристикам) был бы костылём: он лечил бы
 * следствие. Здесь лечится причина: <b>пока инвентарь «горячий», сервер не
 * меняет предметы вообще</b> — сетка клиента и сервера совпадают по
 * построению, и ни один канал записи не может resurrect'нуть то, чего
 * клиент никогда не терял.</p>
 *
 * <p><b>Модель.</b> Значение дозы и тик последнего шага живут в записи этого
 * класса, ключ — сам объект стака (WeakHashMap: запись умирает вместе со
 * стаком, тряски GC-задержек нет; стек-объект переезжает между слотами/
 * игроками по ссылке, поэтому доза следует за предметом, а не за адресом).
 * Компонент {@code gonzo_rad} на стаке превращается в холодный кэш: он
 * актуален на момент последнего flush и служит persistence/тултипам/
 * офлайн-чтению. Flush — только в безопасное окно: игрок «тих» ≥
 * {@value #QUIET_NS} наносекунд (печать активности ставят миксины на клик и
 * креативный пакет) и серверное меню по умолчанию; плюс принудительно при
 * logout. Ценность физически ограничена (кап + tanh-отдача в nextInduced),
 * поэтому «сидеть в GUI часами» не опасен ни переполнением, ни потерей при
 * краше: теряется ровно дрейф с последнего flush, который затухание само
 * съедает за минуты. Пауза прохода НЕ делается (урок 0.3.275: ленивый догон
 * сжигает наведёнку за паузу) — расчёт идёт каждый тик, просто пишет в индекс.</p>
 *
 * <p><b>Тред-модель.</b> Все вызовы — с серверного потока (проход, logout)
 * либо из отложенного обработчика пакетов; синхронизация на map всё равно
 * стоит: отладочные чтения допускаются с netty-потока, а WeakHashMap при
 * конкурентном resize умеет терять данные — дешевле страховки нет.</p>
 */
public final class RadDoseIndex {
    /** Тишина, после которой инвентарь считается безопасным для flush (нс). */
    static final long QUIET_NS = 5_000_000_000L;

    /** Запись индекса на один стак-объект. */
    static final class Entry {
        double value;
        long lastTick;
        boolean dirty;
        boolean clearPending;

        Entry(double value, long lastTick) {
            this.value = value;
            this.lastTick = lastTick;
        }
    }

    private static final WeakHashMap<ItemStack, Entry> LIVE = new WeakHashMap<>();
    /** Кого выгружать при flush: слабые ссылки на принятые стаки игрока. */
    private static final Map<UUID, List<WeakReference<ItemStack>>> ADOPTED = new HashMap<>();
    /** Наносекунды последней инвентарной активности игрока (клик/креатив). */
    private static final Map<UUID, Long> LAST_INTERACTION_NS = new HashMap<>();

    private RadDoseIndex() {
    }

    /** Печать активности: любое действие по инвентарю закрывает окно flush. */
    public static synchronized void noteInteraction(ServerPlayer player) {
        LAST_INTERACTION_NS.put(player.getUUID(), System.nanoTime());
    }

    /**
     * True, если игрок тих ≥ {@link #QUIET_NS} и у него нет открытого
     * серверного контейнера — тогда допустимы запись компонентов и flush.
     */
    public static synchronized boolean quietFor(ServerPlayer player) {
        Long last = LAST_INTERACTION_NS.get(player.getUUID());
        if (last != null && System.nanoTime() - last < QUIET_NS) {
            return false;
        }
        return player.containerMenu == player.inventoryMenu;
    }

    static synchronized Entry peek(ItemStack stack) {
        return LIVE.get(stack);
    }

    static synchronized boolean isLive(ItemStack stack) {
        return LIVE.containsKey(stack);
    }

    /** Принять стак в индекс (из горячего инвентаря). Повторный adopt — no-op. */
    static synchronized void adopt(ServerPlayer player, ItemStack stack) {
        if (LIVE.containsKey(stack)) {
            return;
        }
        double comp = ItemRadioactivity.readComponentValue(stack);
        long tick = ItemRadioactivity.readComponentLastTick(stack);
        LIVE.put(stack, new Entry(comp, tick));
        List<WeakReference<ItemStack>> list =
                ADOPTED.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        list.add(new WeakReference<>(stack));
    }

    /** Запись значения в индекс, если стак живой; false — писать в компонент. */
    static synchronized boolean setValue(ItemStack stack, double value) {
        Entry e = LIVE.get(stack);
        if (e == null) {
            return false;
        }
        double next = Math.max(0.0, value);
        if (Double.compare(e.value, next) != 0) {
            e.value = next;
            e.dirty = true;
            if (next < ItemRadioactivity.CLEANUP_FLOOR) {
                e.clearPending = true;
            }
        }
        return true;
    }

    /** Сброс последнего шага в индекс; false — писать в компонент. */
    static synchronized boolean markTick(ItemStack stack, long nowTick) {
        Entry e = LIVE.get(stack);
        if (e == null) {
            return false;
        }
        if (e.lastTick != nowTick) {
            e.lastTick = nowTick;
            e.dirty = true;
        }
        return true;
    }

    /** Последний шаг из индекса; Long.MIN_VALUE — в индексе нет (читать компонент). */
    static synchronized long tickOf(ItemStack stack) {
        Entry e = LIVE.get(stack);
        return e == null ? Long.MIN_VALUE : e.lastTick;
    }

    /** Изъятие gonzo_rad «на потом»: живой стак не должен трогать компонент. */
    static synchronized boolean markCleared(ItemStack stack) {
        Entry e = LIVE.get(stack);
        if (e == null) {
            return false;
        }
        if (e.value != 0.0 || !e.clearPending) {
            e.value = 0.0;
            e.clearPending = true;
            e.dirty = true;
        }
        return true;
    }

    /** Выгрузить один стак: индексное состояние — в компонент, запись — только если отличается. */
    private static void flushStack(ItemStack stack) {
        Entry e = LIVE.remove(stack);
        if (e == null) {
            return;
        }
        if (!e.dirty) {
            return;
        }
        long realTick = ItemRadioactivity.readComponentLastTick(stack);
        if (e.clearPending || e.value < ItemRadioactivity.CLEANUP_FLOOR) {
            ItemRadioactivity.clearComponent(stack);
            return;
        }
        ItemRadioactivity.writeComponent(stack, e.value);
        if (realTick != e.lastTick && e.lastTick != Long.MIN_VALUE) {
            ItemRadioactivity.writeComponentLastTick(stack, e.lastTick);
        }
    }

    /**
     * Принудительный flush игрока: все принятые стаки (включая уже уехавшие в
     * хранилище/на землю — доза едет за объектом) выгружаются в компоненты.
     */
    public static synchronized void flushPlayer(ServerPlayer player) {
        List<WeakReference<ItemStack>> list = ADOPTED.remove(player.getUUID());
        if (list == null) {
            return;
        }
        for (Iterator<WeakReference<ItemStack>> it = list.iterator(); it.hasNext(); ) {
            ItemStack stack = it.next().get();
            if (stack == null) {
                it.remove();
                continue;
            }
            flushStack(stack);
        }
    }

    /** Logout/stop: flush и полное забытье. */
    public static synchronized void forgetPlayer(UUID uuid) {
        LAST_INTERACTION_NS.remove(uuid);
        ADOPTED.remove(uuid);
    }

    /** Отладка 291+: число живых записей индекса. */
    public static synchronized int liveCount() {
        return LIVE.size();
    }
}
