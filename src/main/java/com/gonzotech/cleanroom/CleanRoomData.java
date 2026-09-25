package com.gonzotech.cleanroom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** Dimension-owned data; never share air quality between servers or worlds. */
public final class CleanRoomData extends SavedData {
    private static final String NAME = "gonzotech_clean_rooms";
    final RoomLedger ledger = new RoomLedger(this::setDirty);

    public static CleanRoomData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new Factory<>(CleanRoomData::new, (tag, provider) -> load(tag), null), NAME);
    }

    private static CleanRoomData load(CompoundTag tag) {
        CleanRoomData data = new CleanRoomData();
        ListTag entries = tag.getList("Rooms", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag room = entries.getCompound(i);
            long[] cells = room.getLongArray("Cells");
            long[] shell = room.getLongArray("Shell");
            if (cells.length == 0 || cells.length > RoomTopology.MAX_VOLUME
                    || shell.length == 0 || shell.length > 6 * RoomTopology.MAX_VOLUME) continue;
            Set<RoomTopology.Pos> inside = unpack(cells);
            Set<RoomTopology.Pos> boundary = unpack(shell);
            if (java.util.Collections.disjoint(inside, boundary)) {
                data.ledger.restore(inside, boundary, room.getDouble("Quality"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        for (RoomLedger.Room room : ledger.rooms()) {
            CompoundTag entry = new CompoundTag();
            entry.putLongArray("Cells", pack(room.cells()));
            entry.putLongArray("Shell", pack(room.shell()));
            entry.putDouble("Quality", room.quality());
            entries.add(entry);
        }
        tag.putInt("Version", 1);
        tag.put("Rooms", entries);
        return tag;
    }

    private static Set<RoomTopology.Pos> unpack(long[] values) {
        Set<RoomTopology.Pos> out = new HashSet<>();
        for (long value : values) out.add(CleanRoomDetector.pos(BlockPos.of(value)));
        return out;
    }

    private static long[] pack(Set<RoomTopology.Pos> positions) {
        return positions.stream().mapToLong(p -> CleanRoomDetector.blockPos(p).asLong()).sorted().toArray();
    }
}
