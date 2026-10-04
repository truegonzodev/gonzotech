package com.gonzotech.radiation;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.block.ThirdPistonBlock;
import com.gonzotech.machines.block.ThirdPistonHeadBlock;
import com.gonzotech.machines.block.ThirdStickyPistonBlock;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Radiation enclosure, independent of clean-room material rules.
 * Boundary cells are area-averaged; up to eight contiguous blocks on EACH outward
 * normal multiply their factors. One external barium block improves only that ray.
 * Pure seals are excluded unless they occupy over half the boundary. Below 0.001
 * transmission is rounded to zero. See RadiationContour for the tested policy.
 *
 * A closed room's actual radioactive blocks also expose occupants at 40% emission/s,
 * independently of the attenuation protecting the outside chunk. Inventory sources
 * remain direct; container contents are NOT added to this new block-only exposure.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class Containment {
    public static final TagKey<Block> CONTOUR_SEAL = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "contour_seal"));
    private static final long CACHE_TTL_TICKS = 400;
    private static final int CACHE_MAX = 4096;
    private static final int DEPENDENCY_RANGE = RadiationContour.RANGE + RadiationContour.MAX_LAYERS;

    private record Entry(RadiationContour.Result result, BlockPos origin, Set<Long> chunks, long tick) {}
    // No cross-dimension or cross-world reuse of identical block coordinates.
    // Values contain positions/results only, never a strong reference to the level.
    private static final Map<ServerLevel, Map<Long, Entry>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private Containment() {}

    public static RadiationContour.Result cachedResult(ServerLevel level, BlockPos source) {
        Map<Long, Entry> cache = CACHE.get(level);
        if (cache == null) return null;
        Entry entry = cache.get(source.asLong());
        if (entry == null || level.getGameTime() - entry.tick() < 0
                || level.getGameTime() - entry.tick() >= CACHE_TTL_TICKS) return null;
        for (long chunk : entry.chunks()) {
            if (!level.hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) return null;
        }
        return entry.result();
    }

    public static RadiationContour.Result result(ServerLevel level, BlockPos source) {
        var cached = cachedResult(level, source);
        if (cached != null) return cached;
        Set<Long> chunks = new HashSet<>();
        boolean[] fullyLoaded = {true};
        var result = RadiationContour.probe(pos -> {
            BlockPos block = new BlockPos(pos.x(), pos.y(), pos.z());
            if (level.isOutsideBuildHeight(block)) return RadiationContour.UNLOADED;
            chunks.add(ChunkPos.asLong(pos.x() >> 4, pos.z() >> 4));
            if (!level.hasChunkAt(block)) {
                fullyLoaded[0] = false;
                return RadiationContour.UNLOADED;
            }
            return cell(level, block);
        }, new RadiationContour.Pos(source.getX(), source.getY(), source.getZ()));
        if (!fullyLoaded[0]) return result;
        Map<Long, Entry> cache = CACHE.computeIfAbsent(level, ignored -> new HashMap<>());
        if (cache.size() + result.interior().size() + 1 > CACHE_MAX) cache.clear();
        Entry entry = new Entry(result, source.immutable(), Set.copyOf(chunks), level.getGameTime());
        cache.put(source.asLong(), entry);
        // Reuse the same closed-cavity snapshot for moving players and every source
        // inside it. A solid container forced as the probe origin must not alias rooms
        // on opposite sides of that container (it is not generally traversable).
        var start = cell(level.getBlockState(source));
        if (result.enclosed() && (!start.barrier() || start.emission() > 0)) {
            for (var pos : result.interior()) {
                cache.put(BlockPos.asLong(pos.x(), pos.y(), pos.z()), entry);
            }
        }
        return result;
    }

    public static double factor(ServerLevel level, BlockPos source) { return result(level, source).factor(); }
    public static boolean isEnclosed(ServerLevel level, BlockPos source) { return result(level, source).enclosed(); }
    public static double insideDose(ServerLevel level, BlockPos observer) {
        if (level.isOutsideBuildHeight(observer) || !level.hasChunkAt(observer)) return 0;
        var occupancy = cell(level, observer);
        // Do not apply the source/container "ignore my own solid block" exception
        // to an observer overlapping a closed wall or door.
        if (occupancy.barrier() && occupancy.emission() <= 0) return 0;
        return result(level, observer).insideDose();
    }

    /** A moving piston cell normally exposes only minecraft:moving_piston. For our
     * lead piston, resolve its source head back to the shielded technical block
     * during the two animation ticks, so the transient state remains a wall. */
    private static RadiationContour.Cell cell(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.MOVING_PISTON)
                && level.getBlockEntity(pos) instanceof PistonMovingBlockEntity moving) {
            BlockState shield = gonzotech$movingLeadPistonState(level, pos, moving);
            if (shield != null) state = shield;
        }
        return cell(state);
    }

    private static BlockState gonzotech$movingLeadPistonState(ServerLevel level, BlockPos pos,
                                                               PistonMovingBlockEntity moving) {
        BlockState moved = moving.getMovedState();
        if (moved.getBlock() instanceof ThirdPistonHeadBlock
                || gonzotech$isThirdLeadPiston(moved)) return moved;
        if (!moving.isSourcePiston()) return null;

        Direction facing = moving.getDirection();
        if (facing == null) return null;
        BlockPos back = pos.relative(facing.getOpposite());
        if (level.hasChunkAt(back) && gonzotech$isThirdLeadPiston(level.getBlockState(back))) {
            return ModMachines.THIRD_LEAD_PISTON_HEAD.get().defaultBlockState();
        }
        BlockPos front = pos.relative(facing);
        if (level.hasChunkAt(front) && gonzotech$isThirdLeadPiston(level.getBlockState(front))) {
            return ModMachines.THIRD_LEAD_PISTON_HEAD.get().defaultBlockState();
        }
        return null;
    }

    private static boolean gonzotech$isThirdLeadPiston(BlockState state) {
        return state.getBlock() instanceof ThirdPistonBlock
                || state.getBlock() instanceof ThirdStickyPistonBlock;
    }

    private static RadiationContour.Cell cell(BlockState state) {
        if (state.isAir()) return RadiationContour.AIR;
        // An OPEN metal door must not become a wall again through its material factor.
        boolean aperture = state.is(CONTOUR_SEAL)
                || state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock;
        if (aperture && state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN)) return RadiationContour.AIR;
        double emission = RadSources.blockEmission(state);
        double factor = RadMaterials.blockFactor(state);
        var kind = state.is(CONTOUR_SEAL) ? RadiationContour.Kind.SEAL
                : factor < 1 || state.canOcclude() ? RadiationContour.Kind.WALL : RadiationContour.Kind.OPEN;
        return new RadiationContour.Cell(kind, factor, emission);
    }

    /** Called AFTER actual world mutation: doors, outer layers, commands and explosions count. */
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState before, BlockState after) {
        Map<Long, Entry> cache = CACHE.get(level);
        if (cache == null) return;
        if (!level.getServer().isSameThread()) {
            BlockPos changed = pos.immutable();
            level.getServer().execute(() -> blockChanged(level, changed, before, after));
            return;
        }
        if (cache.isEmpty() || cell(before).equals(cell(after))) return;
        cache.entrySet().removeIf(e -> {
            BlockPos origin = e.getValue().origin();
            return Math.abs((long) origin.getX() - pos.getX()) <= DEPENDENCY_RANGE
                    && Math.abs((long) origin.getY() - pos.getY()) <= DEPENDENCY_RANGE
                    && Math.abs((long) origin.getZ() - pos.getZ()) <= DEPENDENCY_RANGE;
        });
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) CACHE.remove(level);
    }
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) CACHE.remove(level);
    }
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) CACHE.remove(level);
    }
}
