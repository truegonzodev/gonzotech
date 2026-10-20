package com.gonzotech.machines.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Журнал меток-вымпелов автошахтера (0.3.256, автор). Per-измерение,
 * персистентно; TTL — для тестовых меток (команда debug marker: 20 секунд).
 * Изменения рассылаются клиентам {@link MarkerNetwork}.
 */
public final class MarkerService extends SavedData {

    private static final String NAME = "gonzotech_miner_markers";

    private final Map<Long, MinerMarker> markers = new LinkedHashMap<>();
    private long nextId = 1;

    public static MarkerService get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new Factory<>(MarkerService::new, (tag, provider) -> load(tag), null), NAME);
    }

    private static MarkerService load(CompoundTag tag) {
        MarkerService data = new MarkerService();
        data.nextId = Math.max(1, tag.getLong("NextId"));
        ListTag list = tag.getList("Markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long id = entry.getLong("Id");
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            MinerMarker.Kind kind = switch (entry.getInt("Kind")) {
                case 0 -> MinerMarker.Kind.STATION;
                case 1 -> MinerMarker.Kind.ORE;
                default -> MinerMarker.Kind.TEST;
            };
            long expires = entry.getLong("Expires");
            List<Item> ores = new ArrayList<>();
            LongArrayTag oreIds = null;
            long[] raw = entry.getLongArray("Ores");
            for (long oreRaw : raw) {
                ores.add(Item.byId((int) oreRaw));
            }
            data.markers.put(id, new MinerMarker(id, pos, kind, expires, ores));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("NextId", nextId);
        ListTag list = new ListTag();
        for (MinerMarker marker : markers.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Id", marker.id());
            entry.putLong("Pos", marker.pos().asLong());
            entry.putLong("Expires", marker.expiresAtTick());
            long[] oreIds = new long[marker.ores().size()];
            for (int i = 0; i < oreIds.length; i++) {
                oreIds[i] = Item.getId(marker.ores().get(i));
            }
            entry.putLongArray("Ores", oreIds);
            entry.putInt("Kind", marker.kind().ordinal());
            list.add(entry);
        }
        tag.put("Markers", list);
        return tag;
    }

    public Collection<MinerMarker> markers() {
        return markers.values();
    }

    /** Добавить метку; ttl=0 — бессрочно. Возвращает созданную метку. */
    public MinerMarker add(ServerLevel level, BlockPos pos, MinerMarker.Kind kind, long ttl, List<Item> ores) {
        long id = nextId++;
        MinerMarker marker = new MinerMarker(id, pos, kind,
            ttl > 0 ? level.getGameTime() + ttl : 0, ores);
        markers.put(id, marker);
        setDirty();
        MarkerNetwork.broadcast(level, this);
        return marker;
    }

    /** Истечение TTL (только для меток с дедлайном). */
    public void tick(ServerLevel level) {
        if (markers.isEmpty()) return;
        long now = level.getGameTime();
        boolean changed = markers.values().removeIf(
            marker -> marker.expiresAtTick() > 0 && now >= marker.expiresAtTick());
        if (changed) {
            setDirty();
            MarkerNetwork.broadcast(level, this);
        }
    }

    public void sync(ServerPlayer player) {
        MarkerNetwork.sendTo(player, this);
    }
}
