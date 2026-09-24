package com.gonzotech.machines.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Маленькие интерфейсы «приёмников» ресурсов, чтобы блоки могли толкать
 * ресурс соседу, не зная его конкретного класса.
 * <p>
 * Передача блок→сосед работает и сейчас (машины ставятся вплотную). С появлением
 * логистики она дополняет, а не заменяет трубы: труба/узел спрашивает тот же
 * {@link #push}, поэтому при переезде на NeoForge-capability логика не изменится.
 */
public final class Sinks {

    private Sinks() {
    }

    // Величины — long в базовых единицах ресурса: GTU/GTH в МИЛЛИ (1 ед.=1000),
    // вода/пар в mB. Поток за тик ограничен throughput → long хватает с запасом
    // (хранилище может быть больше — оно на BigInteger в GtBuffer).

    /** Блок умеет принимать GTH (тепло, mGTH) от соседа. */
    public interface GthSink {
        /** @return сколько mGTH реально принято */
        long receiveGth(long amount, boolean simulate);
    }

    /** Блок умеет принимать пар (mB) от соседа. */
    public interface SteamSink {
        /** @return сколько пара (mB) реально принято */
        long receiveSteam(long amount, boolean simulate);
    }

    /** Блок умеет принимать GTU («электричество», mGTU) от соседа. */
    public interface GtuSink {
        /** @return сколько mGTU реально принято */
        long receiveGtu(long amount, boolean simulate);
    }

    /** Блок умеет принимать воду (mB) от соседа (котёл — от генератора). */
    public interface WaterSink {
        /** @return сколько воды (mB) реально принято */
        long receiveWater(long amount, boolean simulate);
    }

    // ────────────────────── Жидкостные приёмники Эпохи III ──────────────────────

    /** Блок умеет принимать брагу (mB) от соседа или по трубе. */
    public interface MashSink {
        /**
         * @param amount         объём в mB
         * @param alcoholPercent процент спирта (0..13%)
         * @param rotPercent     процент гнили (0..98%)
         * @param simulate       проверка или реальный приём
         * @return сколько mB реально принято
         */
        long receiveMash(long amount, double alcoholPercent, double rotPercent, boolean simulate);
    }

    /** Блок умеет принимать сусло (mB) от соседа или по трубе. */
    public interface WortSink {
        /**
         * @param amount         объём в mB
         * @param alcoholPercent процент спирта (0..30%)
         * @param simulate       проверка или реальный приём
         * @return сколько mB реально принято
         */
        long receiveWort(long amount, double alcoholPercent, boolean simulate);
    }

    /** Блок умеет принимать дистиллят (mB) от соседа или по трубе (константный 48% спирт). */
    public interface DistillateSink {
        long receiveDistillate(long amount, boolean simulate);
    }

    /** Блок умеет принимать ретификат / чистый спирт (mB) от соседа или по трубе. */
    public interface RectificateSink {
        long receiveRectificate(long amount, boolean simulate);
    }

    /** Блок умеет принимать кипяток / горячую воду (mB) от соседа или по трубе. */
    public interface HotWaterSink {
        long receiveHotWater(long amount, boolean simulate);
    }

    /** Блок умеет принимать зелье отравления II (mB) от соседа или по трубе. */
    public interface PoisonPotionSink {
        long receivePoisonPotion(long amount, boolean simulate);
    }

    /** Блок умеет принимать серную кислоту (mB) от соседа или по трубе. */
    public interface SulfuricAcidSink {
        long receiveSulfuricAcid(long amount, boolean simulate);
    }

    /** Блок умеет принимать этилен (mB) от соседа или по трубе. */
    public interface EthyleneSink {
        long receiveEthylene(long amount, boolean simulate);
    }

    /** Блок умеет принимать аминоблейзатанол (mB) от соседа или по трубе. */
    public interface AminoblazeethanolSink {
        long receiveAminoblazeethanol(long amount, boolean simulate);
    }

    /** Блок умеет принимать формальдегид (mB) от соседа или по трубе. */
    public interface FormaldehydeSink {
        long receiveFormaldehyde(long amount, boolean simulate);
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
