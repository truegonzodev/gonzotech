package com.gonzotech.cleanroom;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.chalkboard.progress.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** All instruments, players and filters address the same dimension-owned room ledger. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class CleanRoomSystem {
    // Only a lifecycle lookup for the mutation hook. Values do not hold a Level reference.
    private static final Map<ServerLevel, CleanRoomData> LOADED = new WeakHashMap<>();
    private CleanRoomSystem() {}

    private static CleanRoomData data(ServerLevel level) {
        return LOADED.computeIfAbsent(level, CleanRoomData::get);
    }

    @SubscribeEvent
    public static void onLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) data(level);
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LOADED.remove(level);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) { LOADED.clear(); }

    /** Called after an actual LevelChunk mutation, not a cancellable player attempt. */
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        if (!level.getServer().isSameThread()) {
            // Never read neighbouring chunks or mutate the ledger on a generation worker.
            // A deferred edit cannot reconstruct historical connectivity: invalidate safely.
            BlockPos changed = pos.immutable();
            level.getServer().execute(() -> {
                CleanRoomData loaded = LOADED.get(level);
                if (loaded != null && CleanRoomDetector.kind(before) != CleanRoomDetector.kind(after)) {
                    loaded.ledger.invalidate(CleanRoomDetector.pos(changed));
                }
            });
            return;
        }
        CleanRoomData data = LOADED.get(level);
        if (data != null) {
            data.ledger.blockChanged(p -> CleanRoomDetector.read(level, p), CleanRoomDetector.pos(pos),
                CleanRoomDetector.kindAt(level, pos, before), CleanRoomDetector.kindAt(level, pos, after));
        }
    }

    public static RoomLedger.Room room(ServerLevel level, BlockPos pos) {
        return data(level).ledger.find(p -> CleanRoomDetector.read(level, p),
                CleanRoomDetector.pos(pos), level.getGameTime());
    }

    public static double quality(ServerLevel level, BlockPos pos) {
        RoomLedger.Room room = room(level, pos);
        return room == null ? -1.0 : room.quality();
    }

    /** A wall-mounted filter must border exactly one room; never bridge two rooms. */
    public static RoomLedger.Room filterRoom(ServerLevel level, BlockPos pos) {
        RoomLedger.Room found = null;
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (CleanRoomDetector.read(level, CleanRoomDetector.pos(next)) != RoomTopology.Kind.INTERIOR) continue;
            RoomLedger.Room candidate = room(level, next);
            if (candidate == null) continue;
            if (found != null && found != candidate) return null;
            found = candidate;
        }
        return found;
    }

    public static void improve(ServerLevel level, RoomLedger.Room room, double amount) {
        data(level).ledger.adjust(room, amount);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        // Shared second boundary: a room is validated once for all players/filters this tick.
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            Cleanliness dirt = player.getData(ModAttachments.CLEANLINESS);
            RoomLedger.Room room = room(level, player.blockPosition());
            if (isInCleanerStream(level, player.getX(), player.getY(), player.getZ())) {
                dirt.setDirt(dirt.dirt() - 11.5);
            } else if (room == null) {
                dirt.setDirt(dirt.dirt() + 5.0);
            } else {
                double before = dirt.dirt();
                dirt.setDirt(before - 1.0);
                data(level).ledger.adjust(room, -(before - dirt.dirt()) * 3.0);
            }
            player.setData(ModAttachments.CLEANLINESS, dirt);
        }
        processDoorLeaks(level);
    }

    /**
     * Открытые гермодвери (0.3.61): утечка в улицу / выравнивание двух контуров.
     * Стороны двери — её горизонтальные соседи: воздух INTERIOR, входящий в
     * контур, даёт комнату (та же или другая); воздух INTERIOR ВНЕ контуров —
     * это улица (0.3.63: уличный воздух тоже INTERIOR — иначе утечка наружу
     * не срабатывала никогда). Стены/жидкости (FORBIDDEN) и герметичные блоки
     * (SEAL) стороной не считаются — дверь «в стену» не течёт. Дверь
     * обрабатывается один раз даже когда она в оболочке двух комнат
     * (см. скрин автора: одна дверь делит K1 и K2).
     */
    private static void processDoorLeaks(ServerLevel level) {
        RoomLedger ledger = data(level).ledger;
        long tick = level.getGameTime();
        java.util.List<DoorLeaks.Door> doors = new java.util.ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (RoomLedger.Room room : ledger.rooms()) {
            for (RoomTopology.Pos p : room.shell()) {
                BlockPos bp = CleanRoomDetector.blockPos(p);
                if (!seen.add(bp.asLong()) || !level.hasChunkAt(bp)) continue;
                if (!CleanRoomDetector.isOpenHermeticDoor(level.getBlockState(bp))) continue;
                RoomLedger.Room first = null, second = null;
                boolean outside = false;
                for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                    BlockPos next = bp.relative(dir);
                    if (!level.hasChunkAt(next)) continue;
                    RoomTopology.Kind kind = CleanRoomDetector.read(level, CleanRoomDetector.pos(next));
                    if (kind == RoomTopology.Kind.INTERIOR) {
                        RoomLedger.Room side = ledger.find(l -> CleanRoomDetector.read(level, l),
                                CleanRoomDetector.pos(next), tick);
                        if (side == null) { outside = true; continue; } // воздух вне контуров = улица
                        if (first == null) first = side;
                        else if (second == null && side != first) second = side;
                    } // FORBIDDEN (стены/жидкости) и SEAL: сторону не дают
                }
                if (first == null) continue; // обе стороны — улица/стены: терять нечему
                if (second != null) {
                    doors.add(new DoorLeaks.Door(first, second));   // дверь между двумя контурами
                } else {
                    // одна комната: улица по ту сторону — потеря, та же комната — нет-оп
                    doors.add(new DoorLeaks.Door(first, outside ? null : first));
                }
            }
        }
        DoorLeaks.settle(ledger, doors);
    }

    public static boolean isInCleanerStream(ServerLevel level, double x, double y, double z) {
        // Actual coordinates, including the neighbouring column within the 0.7 radius.
        for (int bx = (int) Math.floor(x - 0.7); bx <= (int) Math.floor(x + 0.7); bx++) {
            for (int bz = (int) Math.floor(z - 0.7); bz <= (int) Math.floor(z + 0.7); bz++) {
                if (Math.abs(x - (bx + 0.5)) > 0.7 || Math.abs(z - (bz + 0.5)) > 0.7) continue;
                for (int by = (int) Math.ceil(y - 3.0); by < y; by++) {
                    BlockPos base = new BlockPos(bx, by, bz);
                    if (!level.hasChunkAt(base) || !(level.getBlockEntity(base) instanceof AirCleanerBlockEntity cleaner)
                            || !cleaner.active()) continue;
                    boolean clear = true;
                    for (int above = by + 1; above <= (int) Math.floor(y); above++) {
                        BlockPos air = new BlockPos(bx, above, bz);
                        if (!level.hasChunkAt(air) || !level.getBlockState(air).isAir()) { clear = false; break; }
                    }
                    if (clear) return true;
                }
            }
        }
        return false;
    }
}
