package com.gonzotech.machines.reactor;

import com.gonzotech.radiation.ChunkRadiationData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Очередь активных аварий реакторов уровня (0.3.246). Живёт вне панели и
 * структуры: последовательность T1..T3 и пыль T1..T5 доезжают до конца даже
 * после разрушения реактора. При постановке в очередь чанк пролома получает
 * разовую дозу +5 mZt.
 */
public final class ReactorAccidentData extends SavedData {

    private static final String DATA_NAME = "gonzotech_reactor_accidents";

    private final List<ReactorAccident> accidents = new ArrayList<>();

    public static ReactorAccidentData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(ReactorAccidentData::new,
                (tag, provider) -> load(tag), null),
            DATA_NAME);
    }

    private static ReactorAccidentData load(CompoundTag tag) {
        ReactorAccidentData data = new ReactorAccidentData();
        ListTag list = tag.getList("Accidents", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.accidents.add(ReactorAccident.load(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (ReactorAccident accident : accidents) {
            list.add(accident.save());
        }
        tag.put("Accidents", list);
        return tag;
    }

    /** Поставить аварию в очередь: разовая доза чанка +5 mZt при аварии. */
    public void enqueue(ServerLevel level, ReactorAccident accident) {
        accidents.add(accident);
        setDirty();
        BlockPos breach = accident.breachPos();
        ChunkRadiationData.get(level).addContamination(level,
            ChunkPos.asLong(breach.getX() >> 4, breach.getZ() >> 4),
            ReactorAccident.ACCIDENT_DOSE_NZT, "авария реактора");
    }

    public void tickAccidents(ServerLevel level, long gameTime) {
        if (accidents.isEmpty()) return;
        accidents.removeIf(accident -> {
            accident.tick(level, gameTime);
            return accident.finished(gameTime);
        });
        setDirty();
    }

    public boolean hasActive() {
        return !accidents.isEmpty();
    }
}
