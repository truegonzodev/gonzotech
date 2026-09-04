package com.gonzotech.machines.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Маленькие интерфейсы «приёмников» ресурсов, чтобы блоки могли толкать
 * ресурс соседу, не зная его конкретного класса.
 * <p>
 * Пока это прямая передача блок→сосед (мы ставим блоки вплотную вместо
 * проводов). Позже, когда появятся трубы/провода, здесь же можно будет
 * повесить NeoForge-capability, а логика толкания в {@link #push} не изменится.
 */
public final class Sinks {

    private Sinks() {
    }

    /** Блок умеет принимать GTH (тепло) от соседа. */
    public interface GthSink {
        /** @return сколько GTH реально принято */
        int receiveGth(int amount, boolean simulate);
    }

    /** Блок умеет принимать пар (mB) от соседа. */
    public interface SteamSink {
        /** @return сколько пара (mB) реально принято */
        int receiveSteam(int amount, boolean simulate);
    }

    /** Блок умеет принимать GTU («электричество») от соседа. */
    public interface GtuSink {
        /** @return сколько GTU реально принято */
        int receiveGtu(int amount, boolean simulate);
    }

    /** Блок умеет принимать воду (mB) от соседа (котёл — от генератора). */
    public interface WaterSink {
        /** @return сколько воды (mB) реально принято */
        int receiveWater(int amount, boolean simulate);
    }

    // ─────────────────────── источники (для труб/сетей) ───────────────────────
    // Зеркала к *Sink: их реализуют блоки-генераторы, из которых СЕТЬ может
    // ВЫКАЧАТЬ ресурс. Прямая передача «сосед→сосед» (Фаза 2) их не использует —
    // она пушит через Transfer#distribute. Эти интерфейсы нужны трубам, чтобы
    // тянуть ресурс из машины на конце цепочки.

    /** Из блока можно ВЫКАЧАТЬ GTH (тепло) — реализует топка. */
    public interface GthSource {
        /** @return сколько GTH реально отдано */
        int extractGth(int amount, boolean simulate);
    }

    /** Из блока можно ВЫКАЧАТЬ GTU («электричество») — реализует стирлинг. */
    public interface GtuSource {
        /** @return сколько GTU реально отдано */
        int extractGtu(int amount, boolean simulate);
    }

    /**
     * Есть ли среди 6 соседей блок-сущность заданного класса.
     * Используется для проверки «вижу ли я нужного соседа в цепочке».
     */
    public static boolean hasNeighbor(Level level, BlockPos pos, Class<?> type) {
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be != null && type.isInstance(be)) {
                return true;
            }
        }
        return false;
    }
}
