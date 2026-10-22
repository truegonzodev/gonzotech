package com.gonzotech.core.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * «Последний запрос» на игрока и тип пэйлоада (автор #41, 0.3.267): дорогие
 * C2S-обработчики (доска резонанса, записки, трубы, цезий) валидируют
 * содержимое, но не частоту — клиент может гонять сканеры/солверыв секунду. Окно —
 * 20 тиков сервера; сверх лимита пакет молча отбрасывается: честный клиент
 *порог не заметит, а читеру и не должно быть ответа.
 */
public final class PayloadThrottle {

    public static final int WINDOW_TICKS = 20;

    private static final Map<UUID, Map<String, long[]>> STATE = new ConcurrentHashMap<>();

    private PayloadThrottle() {}

    /** @param maxPerWindow сколько запросов в окне 20 тиков. */
    public static boolean allow(ServerPlayer player, String key, int maxPerWindow) {
        long tick = player.level().getGameTime();
        Map<String, long[]> perKey =
            STATE.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
        long[] st = perKey.computeIfAbsent(key, k -> new long[] {tick, 0});
        if (tick - st[0] >= WINDOW_TICKS) {
            st[0] = tick;
            st[1] = 0;
        }
        if (st[1] >= maxPerWindow) return false;
        st[1]++;
        return true;
    }

    /** Игроков выписываем — статика не должна растить память сессиями. */
    public static void forget(UUID player) {
        STATE.remove(player);
    }
}
