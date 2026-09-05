package com.gonzotech.machines.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Железное правило передачи ресурсов Gonzo Tech: РАВНОМЕРНОЕ (не приоритетное)
 * распределение бюджета отдачи между всеми соседями-приёмниками.
 * <p>
 * Бюджет {@code B} делится между {@code N} приёмниками поровну: каждому
 * {@code B/N}. Неделимый остаток {@code B%N} раздаётся по одной единице, но НЕ
 * всегда первым соседям, а начиная со сдвига {@code rotation % N} — так за
 * несколько тиков остаток честно «обходит» всех соседей по кругу.
 * <p>
 * Пример честности: 1 воды/т на 4 котла. За тик уходит 1 котлу, но благодаря
 * ротации по gameTime за 4 тика каждый котёл получит по 1 → в среднем 0.25/т,
 * как и должно быть при делении 1 на 4. Так решается проблема «неделимой
 * единицы» без перехода на дробные (milli-) единицы.
 * <p>
 * <b>NB на будущее (по замечанию про 1.6 GTU/t):</b> когда понадобится честное
 * деление МЕНЬШЕ единицы за тик по-настоящему (а не в среднем), стоит перейти на
 * фиксированную точку — хранить ресурсы в milli-единицах (×1000). Тогда 1.6 GTU/t
 * = 1600 milli/t, и деление на N соседей будет точным каждый тик. Точка
 * концентрации для такого перехода — {@link ResourceBuffer} + этот класс.
 */
public final class Transfer {

    private Transfer() {
    }

    /** Приёмник одной порции ресурса. */
    @FunctionalInterface
    public interface Receiver {
        /** @return сколько единиц реально принято */
        int receive(int amount, boolean simulate);
    }

    /**
     * Равномерно (с ротацией остатка) раздать {@code budget} единиц соседям.
     *
     * @param level    мир
     * @param pos      позиция источника
     * @param budget   сколько единиц источник готов отдать за этот тик
     * @param rotation сдвиг ротации остатка (обычно {@code level.getGameTime()})
     * @param mapper   для соседнего BE возвращает {@link Receiver} или {@code null}
     * @return сколько единиц суммарно принято (столько же списать из источника)
     */
    public static int distribute(Level level, BlockPos pos, int budget, long rotation,
                                 Function<BlockEntity, Receiver> mapper) {
        if (budget <= 0) return 0;

        List<Receiver> receivers = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be == null) continue;
            Receiver r = mapper.apply(be);
            if (r != null) receivers.add(r);
        }
        return distributeAmong(receivers, budget, rotation);
    }

    /**
     * То же равномерное деление с ротацией остатка, но по ПРОИЗВОЛЬНОМУ списку
     * приёмников (не обязательно соседей одного блока). Используется энергосетью
     * (машины-приёмники слива через PipeRouting).
     *
     * @param receivers приёмники (порядок должен быть стабильным между тиками
     *                  для честной ротации остатка)
     * @param budget    сколько единиц раздать
     * @param rotation  сдвиг ротации остатка (обычно {@code level.getGameTime()})
     * @return сколько единиц суммарно принято
     */
    public static int distributeAmong(List<Receiver> receivers, int budget, long rotation) {
        if (budget <= 0) return 0;
        int n = receivers.size();
        if (n == 0) return 0;

        int base = budget / n;
        int extra = budget % n;
        int start = (int) Math.floorMod(rotation, n);

        int moved = 0;
        for (int i = 0; i < n; i++) {
            int rot = Math.floorMod(i - start, n);
            int share = base + (rot < extra ? 1 : 0);
            if (share <= 0) continue;
            moved += receivers.get(i).receive(share, false);
        }
        return moved;
    }
}
