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
            long owner = entry.getLong("Owner");
            data.markers.put(id, new MinerMarker(id, pos, kind, expires, ores, owner));
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
            if (marker.owner() != 0L) entry.putLong("Owner", marker.owner());
            list.add(entry);
        }
        tag.put("Markers", list);
        return tag;
    }

    public Collection<MinerMarker> markers() {
        return markers.values();
    }

    /** Удалить метки по позиции (слом Плотника / сброс кнопкой внутри Плотника). */
    public boolean remove(ServerLevel level, BlockPos pos) {
        boolean changed = markers.values().removeIf(marker -> marker.pos().equals(pos));
        if (changed) {
            setDirty();
            MarkerNetwork.broadcast(level, this);
        }
        return changed;
    }

    /** Удалить метку по id. */
    public boolean remove(ServerLevel level, long id) {
        MinerMarker removed = markers.remove(id);
        if (removed != null) {
            setDirty();
            MarkerNetwork.broadcast(level, this);
            return true;
        }
        return false;
    }

    /** Удалить набор меток одним снимком клиентам. */
    public boolean removeAll(ServerLevel level, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return false;
        boolean changed = false;
        for (Long id : ids) {
            if (id != null && markers.remove(id) != null) {
                changed = true;
            }
        }
        if (changed) {
            setDirty();
            MarkerNetwork.broadcast(level, this);
        }
        return changed;
    }

    /**
     * Удалить STATION-метки сектора 3×3 чанка вокруг якоря. Нужно как
     * совместимый путь для меток первого выпуска Плотника, где BE ещё не
     * хранил id своих меток.
     */
    public boolean removeStationMarkersInSector(ServerLevel level, BlockPos sectorAnchor) {
        int minX = ((sectorAnchor.getX() >> 4) - 1) << 4;
        int minZ = ((sectorAnchor.getZ() >> 4) - 1) << 4;
        int maxXExclusive = minX + 48;
        int maxZExclusive = minZ + 48;
        boolean changed = markers.values().removeIf(marker ->
            marker.kind() == MinerMarker.Kind.STATION
                && marker.pos().getX() >= minX && marker.pos().getX() < maxXExclusive
                && marker.pos().getZ() >= minZ && marker.pos().getZ() < maxZExclusive);
        if (changed) {
            setDirty();
            MarkerNetwork.broadcast(level, this);
        }
        return changed;
    }

    /** Добавить метку; ttl=0 — бессрочно. Возвращает созданную метку. */
    public MinerMarker add(ServerLevel level, BlockPos pos, MinerMarker.Kind kind, long ttl, List<Item> ores) {
        return add(level, pos, kind, ttl, ores, 0L);
    }

    /** Метка с владельцем: Плотник, который её поставил (для самочистки). */
    public MinerMarker add(ServerLevel level, BlockPos pos, MinerMarker.Kind kind, long ttl,
                           List<Item> ores, long owner) {
        long id = nextId++;
        MinerMarker marker = new MinerMarker(id, pos, kind,
            ttl > 0 ? level.getGameTime() + ttl : 0, ores, owner);
        markers.put(id, marker);
        setDirty();
        MarkerNetwork.broadcast(level, this);
        return marker;
    }

    /**
     * Самочистка осиротевших меток (0.3.261): помеченные Плотником метки
     * живут, пока жив их владелец. Раньше метка переживала слом Плотника,
     * если BE не успело сохраниться (краш сейва), и оставалась навсегда —
     * вместе с перерисовкой на каждый кадр.
     */
    public void sweepOrphans(ServerLevel level) {
        if (markers.isEmpty()) return;
        boolean changed = markers.values().removeIf(marker -> {
            if (marker.owner() == 0L) return false;
            BlockPos ownerPos = BlockPos.of(marker.owner());
            // Незагруженный чанк владельца — не сиротство: ждём выгрузки обратно.
            if (!level.hasChunk(ownerPos.getX() >> 4, ownerPos.getZ() >> 4)) return false;
            return !(level.getBlockEntity(ownerPos)
                instanceof com.gonzotech.machines.block.entity.PlotnickBlockEntity);
        });
        if (changed) {
            setDirty();
            MarkerNetwork.broadcast(level, this);
        }
    }

    /** Снести все метки измерения (команда /gonzotech debug marker clear). */
    public int clearAll(ServerLevel level) {
        int n = markers.size();
        if (n == 0) return 0;
        markers.clear();
        setDirty();
        MarkerNetwork.broadcast(level, this);
        return n;
    }

    /** Истечение TTL (только для меток с дедлайном) + чистка сирот раз в 5 с. */
    public void tick(ServerLevel level) {
        if (markers.isEmpty()) return;
        if (level.getGameTime() % 100 == 0) sweepOrphans(level);
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
