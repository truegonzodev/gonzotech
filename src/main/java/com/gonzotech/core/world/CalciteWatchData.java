package com.gonzotech.core.world;

import com.gonzotech.core.event.VanillaPolishEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Наблюдение за костными блоками в контакте с водой (0.3.254, автор):
 * через {@value VanillaPolishEvents#CALCITE_DELAY_SECONDS} секунд блок становится
 * кальцитом. Таймеры персистентны (SavedData измерения), дедлайн — от первого
 * касания воды; при утере контакта до срока наблюдение снимается, повторный
 * контакт запускает новый отсчёт.
 */
public final class CalciteWatchData extends SavedData {

    private static final String NAME = "gonzotech_calcite_watch";

    /** pos.asLong() → gameTime дедлайна. */
    private final Map<Long, Long> deadlines = new HashMap<>();

    public static CalciteWatchData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(CalciteWatchData::new, (tag, provider) -> load(tag), null), NAME);
    }

    private static CalciteWatchData load(CompoundTag tag) {
        CalciteWatchData data = new CalciteWatchData();
        long[] pending = tag.getLongArray("Pending");
        for (int i = 0; i + 1 < pending.length; i += 2) {
            data.deadlines.put(pending[i], pending[i + 1]);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        long[] pending = new long[deadlines.size() * 2];
        int index = 0;
        for (Map.Entry<Long, Long> entry : deadlines.entrySet()) {
            pending[index++] = entry.getKey();
            pending[index++] = entry.getValue();
        }
        tag.putLongArray("Pending", pending);
        return tag;
    }

    /** Первый контакт с водой: дедлайн фиксируется и не сдвигается. */
    public void watch(BlockPos pos, long deadlineGameTime) {
        if (deadlines.putIfAbsent(pos.asLong(), deadlineGameTime) == null) {
            setDirty();
        }
    }

    /** Отсчёт таймеров; конвертация — только при живом контакте с водой. */
    public void advance(ServerLevel level) {
        if (deadlines.isEmpty()) return;
        long now = level.getGameTime();
        List<Long> finished = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : deadlines.entrySet()) {
            if (now < entry.getValue()) continue;
            BlockPos pos = BlockPos.of(entry.getKey());
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.BONE_BLOCK) && VanillaPolishEvents.hasWaterNeighbor(level, pos)) {
                level.setBlock(pos, Blocks.CALCITE.defaultBlockState(), 3);
                // Эффект «шипения» как у затвердевания камня.
                level.levelEvent(2001, pos, Block.getId(Blocks.CALCITE.defaultBlockState()));
            }
            finished.add(entry.getKey());
        }
        if (!finished.isEmpty()) {
            finished.forEach(deadlines::remove);
            setDirty();
        }
    }
}
