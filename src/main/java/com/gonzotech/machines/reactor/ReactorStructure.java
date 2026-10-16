package com.gonzotech.machines.reactor;

import com.gonzotech.core.block.ReactorCasingBlock;
import com.gonzotech.core.block.ReactorControlPanelBlock;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Event-driven validator and formed-visual initializer for the first uranium reactor.
 *
 * <p>The 4..16 limit is measured on the outside of the cubic casing. The shell is
 * one block thick. Its only permitted parts are casing, one control panel, and
 * shielded third-tier output nodes. The filled interior is deliberately a strict
 * whitelist; air and unknown blocks prevent formation.</p>
 *
 * <p>This is structure initialization only. It does not implement fuel slots,
 * burnup, neutron transport, heat, or GTH generation.</p>
 */
public final class ReactorStructure {

    public static final int MIN_SIZE = 4;
    public static final int MAX_SIZE = 16;
    private static final int MAX_SHELL_PARTS = 1_600;

    /** Loaded controller panels only; persisted bounds live in the panel BE. */
    private static final Map<Level, Set<Long>> PANEL_POSITIONS =
        Collections.synchronizedMap(new WeakHashMap<>());

    private ReactorStructure() {
    }

    /** A player placed any block; revalidate only reactors within the fixed 16-block limit. */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            partChanged(level, event.getPos());
        }
    }

    /** A player broke any block; treat the broken position as air before the game removes it. */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !event.isCanceled()) {
            partRemoved(level, event.getPos());
        }
    }

    /** Called by the panel BE on load and after its initial block placement. */
    public static void panelLoaded(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        PANEL_POSITIONS.computeIfAbsent(level, ignored -> new HashSet<>())
            .add(panel.getBlockPos().asLong());
        restorePanel(level, panel);
    }

    public static void panelUnloaded(Level level, BlockPos pos) {
        Set<Long> panels = PANEL_POSITIONS.get(level);
        if (panels != null) {
            panels.remove(pos.asLong());
            if (panels.isEmpty()) PANEL_POSITIONS.remove(level);
        }
    }

    /** One transient index per loaded level; saved structure bounds remain in the panel BE. */
    public static void clearAll() {
        PANEL_POSITIONS.clear();
    }

    /** Called for shell/interior block callbacks and placement events. */
    public static void partChanged(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        registerPanelIfPresent(server, pos);
        revalidateNearbyPanels(server, pos, null);
    }

    /** Called before removal; {@code pos} is validated as air to avoid stale formed visuals. */
    public static void partRemoved(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        revalidateNearbyPanels(server, pos, pos);
    }

    /**
     * Validate a panel after its chunk loads or when a pending shell check is
     * retried. If any required chunk is not loaded yet, its lightweight ticker
     * retries later rather than force-loading chunks or treating them as air.
     */
    public static void restorePanel(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        if (panel.isFormedController() && !chunksLoaded(level, panel.min(), panel.max())) {
            panel.markValidationPending();
            return;
        }
        // Unformed panels can also have a pending check when shell discovery
        // reached an unloaded neighbour; retry them after that chunk is loaded.
        revalidatePanel(level, panel, null);
    }

    /** Right-click on the panel runs a full diagnostic and ensures the formed state is current. */
    public static String debugReport(Level level, BlockPos panelPos) {
        if (!(level instanceof ServerLevel server)) {
            return "Реактор: диагностика доступна на серверной стороне.";
        }
        if (!server.hasChunkAt(panelPos)
            || !(server.getBlockState(panelPos).getBlock() instanceof ReactorControlPanelBlock)
            || !(server.getBlockEntity(panelPos) instanceof ReactorControlPanelBlockEntity panel)) {
            return "Реактор не собран: на позиции панели нет её BlockEntity.";
        }

        PANEL_POSITIONS.computeIfAbsent(server, ignored -> new HashSet<>()).add(panelPos.asLong());
        Validation validation = revalidatePanel(server, panel, null);
        if (validation.incomplete()) {
            return "Реактор: проверка отложена — не все чанки куба загружены; принудительная загрузка не выполнялась.";
        }
        if (validation.structure() == null) {
            return "Реактор не сформирован: " + validation.failure()
                + (panel.isFormedController() ? " (сохранённая форма снята)." : ".");
        }
        StructureInfo info = validation.structure();
        return "Реактор I поколения сформирован: внешний куб " + info.size() + "³"
            + ", обшивок=" + info.casingBlocks()
            + ", ТВЭЛ-чехлов=" + info.sheaths()
            + ", узел ГТХ=" + info.heatNodes()
            + ", узел мультижидкости=" + info.fluidNodes()
            + ", универсальных узлов=" + info.universalNodes()
            + ", графита=" + info.graphite()
            + ", бора=" + info.boron()
            + ", бария=" + info.barium()
            + ", воды=" + info.water()
            + ", свинцовых поршней=" + info.leadPistons() + "."
            + " Работающий топливный/тепловой цикл ещё не реализован.";
    }

    private static void registerPanelIfPresent(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof ReactorControlPanelBlock
            && level.getBlockEntity(pos) instanceof ReactorControlPanelBlockEntity) {
            PANEL_POSITIONS.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos.asLong());
        }
    }

    private static void revalidateNearbyPanels(ServerLevel level, BlockPos changed, BlockPos ignoredPos) {
        Set<Long> indexed = PANEL_POSITIONS.get(level);
        if (indexed == null || indexed.isEmpty()) return;

        // Copy before validation: de-forming a panel can mutate the loaded index.
        for (long packedPos : Set.copyOf(indexed)) {
            BlockPos panelPos = BlockPos.of(packedPos);
            if (!near(changed, panelPos)) continue;
            if (!level.hasChunkAt(panelPos)
                || !(level.getBlockState(panelPos).getBlock() instanceof ReactorControlPanelBlock)) {
                panelUnloaded(level, panelPos);
                continue;
            }
            if (level.getBlockEntity(panelPos) instanceof ReactorControlPanelBlockEntity panel) {
                revalidatePanel(level, panel, ignoredPos);
            }
        }
    }

    private static boolean near(BlockPos a, BlockPos b) {
        int reach = MAX_SIZE - 1;
        return Math.abs((long) a.getX() - b.getX()) <= reach
            && Math.abs((long) a.getY() - b.getY()) <= reach
            && Math.abs((long) a.getZ() - b.getZ()) <= reach;
    }

    private static Validation revalidatePanel(ServerLevel level,
                                               ReactorControlPanelBlockEntity panel,
                                               BlockPos ignoredPos) {
        if (panel.isFormedController() && !chunksLoaded(level, panel.min(), panel.max())) {
            panel.markValidationPending();
            return Validation.incompleteResult();
        }

        Validation validation = inspectFromPanel(level, panel.getBlockPos(), ignoredPos);
        if (validation.incomplete()) {
            panel.markValidationPending();
            return validation;
        }
        panel.clearValidationPending();
        if (validation.structure() != null) {
            applyFormedVisuals(level, panel, validation.structure());
        } else if (panel.isFormedController()) {
            clearFormedVisuals(level, panel, ignoredPos);
        }
        return validation;
    }

    private static Validation inspectFromPanel(ServerLevel level, BlockPos panelPos, BlockPos ignoredPos) {
        if (panelPos.equals(ignoredPos)) {
            return Validation.failure("панель управления вынута из оболочки");
        }
        if (!level.hasChunkAt(panelPos)) {
            return Validation.incompleteResult();
        }
        if (!(level.getBlockState(panelPos).getBlock() instanceof ReactorControlPanelBlock)) {
            return Validation.failure("панель управления должна находиться на внешней оболочке");
        }

        ComponentResult component = collectShellComponent(level, panelPos, ignoredPos);
        if (component.tooLarge()) {
            return Validation.failure("связная оболочка превышает защитный лимит деталей");
        }
        if (component.incomplete()) {
            return Validation.incompleteResult();
        }
        if (component.positions().isEmpty()) {
            return Validation.failure("не найдена связная оболочка из обшивки, панели и экранированных узлов");
        }
        Bounds bounds = boundsOf(component.positions());
        int sizeX = bounds.max().getX() - bounds.min().getX() + 1;
        int sizeY = bounds.max().getY() - bounds.min().getY() + 1;
        int sizeZ = bounds.max().getZ() - bounds.min().getZ() + 1;
        if (sizeX != sizeY || sizeX != sizeZ) {
            return Validation.failure("корпус должен быть кубическим, найдено "
                + sizeX + "×" + sizeY + "×" + sizeZ);
        }
        if (sizeX < MIN_SIZE || sizeX > MAX_SIZE) {
            return Validation.failure("внешняя сторона куба должна быть от "
                + MIN_SIZE + " до " + MAX_SIZE + ", найдено " + sizeX);
        }
        if (!onBoundary(panelPos, bounds.min(), bounds.max())) {
            return Validation.failure("панель управления должна стоять во внешнем слое оболочки");
        }
        if (!chunksLoaded(level, bounds.min(), bounds.max())) {
            return Validation.incompleteResult();
        }
        return validateBox(level, panelPos, bounds, ignoredPos);
    }

    private static ComponentResult collectShellComponent(ServerLevel level,
                                                         BlockPos seed,
                                                         BlockPos ignoredPos) {
        if (seed.equals(ignoredPos) || !isShellPart(level.getBlockState(seed))) {
            return new ComponentResult(List.of(), false, false);
        }

        List<BlockPos> positions = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<BlockPos> pending = new ArrayDeque<>();
        boolean incomplete = false;
        pending.add(seed.immutable());
        seen.add(seed.asLong());

        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            positions.add(pos);
            if (positions.size() > MAX_SHELL_PARTS) {
                return new ComponentResult(List.of(), true, false);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                long packed = next.asLong();
                if ((ignoredPos != null && next.equals(ignoredPos)) || seen.contains(packed)) {
                    continue;
                }
                if (!level.hasChunkAt(next)) {
                    incomplete = true;
                    continue;
                }
                if (!isShellPart(level.getBlockState(next))) continue;
                seen.add(packed);
                pending.addLast(next.immutable());
            }
        }
        return new ComponentResult(List.copyOf(positions), false, incomplete);
    }

    private static Validation validateBox(ServerLevel level,
                                          BlockPos panelPos,
                                          Bounds bounds,
                                          BlockPos ignoredPos) {
        int size = bounds.max().getX() - bounds.min().getX() + 1;
        if (!chunksLoaded(level, bounds.min(), bounds.max())) return Validation.incompleteResult();

        int casing = 0;
        int panels = 0;
        int heatNodes = 0;
        int fluidNodes = 0;
        int universalNodes = 0;
        int sheaths = 0;
        int graphite = 0;
        int boron = 0;
        int barium = 0;
        int water = 0;
        int leadPistons = 0;

        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = stateAt(level, pos, ignoredPos);
                    Block block = state.getBlock();
                    boolean shell = onBoundary(pos, bounds.min(), bounds.max());
                    if (shell) {
                        if (block instanceof ReactorCasingBlock) {
                            casing++;
                        } else if (block instanceof ReactorControlPanelBlock) {
                            panels++;
                        } else if (block == ModMachines.THIRD_HEAT_NODE.get()) {
                            heatNodes++;
                        } else if (block == ModMachines.THIRD_UNIVERSAL_FLUID_NODE.get()) {
                            fluidNodes++;
                        } else if (block == ModMachines.THIRD_UNIVERSAL_NODE.get()) {
                            universalNodes++;
                        } else {
                            return Validation.failure("оболочка не заполнена обшивкой, панелью и разрешёнными экранированными узлами: "
                                + pos.toShortString());
                        }
                    } else {
                        if (state.isAir()) {
                            return Validation.failure("внутри оболочки есть воздух: " + pos.toShortString());
                        }
                        if (!isAllowedInterior(block)) {
                            return Validation.failure("недозволенный блок внутри оболочки: "
                                + BuiltInRegistries.BLOCK.getKey(block) + " в " + pos.toShortString());
                        }
                        if (block == ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get()) sheaths++;
                        else if (block == ModBlocks.GRAPHITE_BLOCK.get()) graphite++;
                        else if (isMetalBlock(block, "boron_block")) boron++;
                        else if (isMetalBlock(block, "barium_block")) barium++;
                        else if (block == Blocks.WATER) water++;
                        else if (isLeadPiston(block)) leadPistons++;
                    }
                }
            }
        }

        if (panels != 1 || !panelPos.equals(findPanelPosition(level, bounds, ignoredPos))) {
            return Validation.failure("во внешней оболочке должна быть ровно одна панель управления");
        }
        if (universalNodes < 1 && (heatNodes < 1 || fluidNodes < 1)) {
            return Validation.failure("нужен один экранированный универсальный узел либо одновременно узел ГТХ и узел мультижидкости");
        }

        return Validation.success(new StructureInfo(bounds.min(), bounds.max(), size,
            casing, sheaths, heatNodes, fluidNodes, universalNodes,
            graphite, boron, barium, water, leadPistons));
    }

    private static BlockPos findPanelPosition(ServerLevel level, Bounds bounds, BlockPos ignoredPos) {
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int y = bounds.min().getY(); y <= bounds.max().getY(); y++) {
                for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!pos.equals(ignoredPos) && onBoundary(pos, bounds.min(), bounds.max())
                        && level.getBlockState(pos).getBlock() instanceof ReactorControlPanelBlock) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isShellPart(BlockState state) {
        Block block = state.getBlock();
        return block instanceof ReactorCasingBlock
            || block instanceof ReactorControlPanelBlock
            || block == ModMachines.THIRD_HEAT_NODE.get()
            || block == ModMachines.THIRD_UNIVERSAL_FLUID_NODE.get()
            || block == ModMachines.THIRD_UNIVERSAL_NODE.get();
    }

    private static boolean isAllowedInterior(Block block) {
        return block == ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get()
            || block == ModBlocks.GRAPHITE_BLOCK.get()
            || isMetalBlock(block, "boron_block")
            || isMetalBlock(block, "barium_block")
            || block == Blocks.WATER
            || isLeadPiston(block);
    }

    private static boolean isLeadPiston(Block block) {
        return block == ModMachines.THIRD_LEAD_PISTON.get()
            || block == ModMachines.THIRD_STICKY_LEAD_PISTON.get()
            || block == ModMachines.THIRD_LEAD_PISTON_HEAD.get();
    }

    private static boolean isMetalBlock(Block block, String path) {
        var key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && "gonzotech".equals(key.getNamespace()) && path.equals(key.getPath());
    }

    private static BlockState stateAt(ServerLevel level, BlockPos pos, BlockPos ignoredPos) {
        return pos.equals(ignoredPos) ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
    }

    private static boolean onBoundary(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() == min.getX() || pos.getX() == max.getX()
            || pos.getY() == min.getY() || pos.getY() == max.getY()
            || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
    }

    private static Bounds boundsOf(List<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new Bounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static boolean chunksLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        int minChunkX = min.getX() >> 4;
        int maxChunkX = max.getX() >> 4;
        int minChunkZ = min.getZ() >> 4;
        int maxChunkZ = max.getZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                BlockPos check = new BlockPos((chunkX << 4) + 8, min.getY(), (chunkZ << 4) + 8);
                if (!level.hasChunkAt(check)) return false;
            }
        }
        return true;
    }

    private static void applyFormedVisuals(ServerLevel level,
                                           ReactorControlPanelBlockEntity panel,
                                           StructureInfo info) {
        panel.configureStructure(info.min(), info.max());
        for (int x = info.min().getX(); x <= info.max().getX(); x++) {
            for (int y = info.min().getY(); y <= info.max().getY(); y++) {
                for (int z = info.min().getZ(); z <= info.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!onBoundary(pos, info.min(), info.max())) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof ReactorCasingBlock
                        && !state.getValue(ReactorCasingBlock.FORMED)) {
                        level.setBlock(pos, state.setValue(ReactorCasingBlock.FORMED, true), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void clearFormedVisuals(ServerLevel level,
                                           ReactorControlPanelBlockEntity panel,
                                           BlockPos ignoredPos) {
        if (!panel.isFormedController()) return;
        BlockPos min = panel.min();
        BlockPos max = panel.max();
        if (!chunksLoaded(level, min, max)) {
            panel.markValidationPending();
            return;
        }
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(ignoredPos) || !onBoundary(pos, min, max)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof ReactorCasingBlock
                        && state.getValue(ReactorCasingBlock.FORMED)) {
                        level.setBlock(pos, state.setValue(ReactorCasingBlock.FORMED, false), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        panel.clearStructure();
    }

    private record Bounds(BlockPos min, BlockPos max) {
    }

    private record ComponentResult(List<BlockPos> positions, boolean tooLarge, boolean incomplete) {
    }

    private record StructureInfo(BlockPos min, BlockPos max, int size,
                                 int casingBlocks, int sheaths,
                                 int heatNodes, int fluidNodes, int universalNodes,
                                 int graphite, int boron, int barium, int water,
                                 int leadPistons) {
    }

    private record Validation(StructureInfo structure, String failure, boolean incomplete) {
        static Validation success(StructureInfo structure) {
            return new Validation(structure, null, false);
        }

        static Validation failure(String reason) {
            return new Validation(null, reason, false);
        }

        static Validation incompleteResult() {
            return new Validation(null, null, true);
        }
    }
}
