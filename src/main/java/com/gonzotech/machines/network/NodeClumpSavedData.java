package com.gonzotech.machines.network;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Персист сшитых узлов (0.3.114, раунд 12). Индекс клампов — серверная
 * статика {@link NodeClumpIndex}, которая чистится на остановке сервера:
 * после перезахода в мир клампы исчезали, «сшито узлов: N» пропадало, а
 * поршневые системы роняли TPS до ручного перетыкания узла (репорт автора).
 * Теперь каждый кламп пишется в dimension-data ({@link SavedData}) и
 * восстанавливается при старте сервера как есть (порты мультиблоков уже
 * вычтены на момент записи — состояние консистентно по построению).
 *
 * <p>Хранятся {корень, род, члены}: плоская потеря НЕ пишется, а
 * пересчитывается из рода — правила потерь применяются к старым мирам.</p>
 */
public final class NodeClumpSavedData extends SavedData {

    private static final String DATA_NAME = "gonzotech_node_clumps";

    /** Загруженные (живые) данные по измерениям — цель write-through setDirty. */
    private static final WeakHashMap<ServerLevel, NodeClumpSavedData> LOADED = new WeakHashMap<>();

    private final List<NodeClumpIndex.ClumpSave> clumps = new ArrayList<>();

    /** Измерение, к которому привязаны данные (нужно для снимка при сохранении). */
    private ServerLevel level;

    private NodeClumpSavedData() {
    }

    public static NodeClumpSavedData get(ServerLevel level) {
        NodeClumpSavedData data = level.getDataStorage().computeIfAbsent(
            new Factory<>(NodeClumpSavedData::new,
                (tag, provider) -> load(tag), null),
            DATA_NAME);
        data.level = level;
        LOADED.put(level, data);
        return data;
    }

    /** Восстановить индекс из сохранения (вызов на старте сервера, до игроков). */
    public static void restore(ServerLevel level) {
        NodeClumpSavedData data = get(level);
        NodeClumpIndex.restoreAll(level, data.clumps);
    }

    /** Write-through: любой реестр клампов помечает измерение грязным. */
    static void markDirty(ServerLevel level) {
        NodeClumpSavedData data = LOADED.get(level);
        if (data != null) data.setDirty();
    }

    /** Сброс живых ссылок на остановке сервера (вместе с NodeClumpIndex.clearAll). */
    public static void forgetLoaded() {
        LOADED.clear();
    }

    /** Снимок клампов уровня для записи (пусто — измерение без сшивок). */
    static List<NodeClumpIndex.ClumpSave> snapshot(ServerLevel level) {
        return NodeClumpIndex.clumpsOf(level);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // Снимок берётся В МОМЕНТ сохранения: индекс живёт в статике NodeClumpIndex.
        clumps.clear();
        if (level != null) clumps.addAll(snapshot(level));
        ListTag list = new ListTag();
        for (NodeClumpIndex.ClumpSave clump : clumps) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Root", clump.root());
            entry.putString("Kind", clump.kind());
            entry.put("Members", new LongArrayTag(clump.members()));
            list.add(entry);
        }
        tag.put("Clumps", list);
        return tag;
    }

    private static NodeClumpSavedData load(CompoundTag tag) {
        NodeClumpSavedData data = new NodeClumpSavedData();
        ListTag list = tag.getList("Clumps", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long root = entry.getLong("Root");
            String kind = entry.getString("Kind");
            long[] members = entry.getLongArray("Members");
            if (members.length < 2) continue; // одиночки не индексируются
            data.clumps.add(new NodeClumpIndex.ClumpSave(root, kind, members));
        }
        return data;
    }
}
