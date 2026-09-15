package com.gonzotech.machines.steamgen;

import com.gonzotech.machines.block.SteamGenCasingBlock;
import com.gonzotech.machines.block.SteamGenCoreBlock;
import com.gonzotech.machines.block.entity.SteamGenCoreBlockEntity;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.network.PipeCarrier;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Событийный валидатор и быстрый индекс продвинутого парогенератора.
 *
 * <p>Форма фиксирована: 5×5×5, внешний слой — корпус (или портовые ноды),
 * внутренность 3×3×3 — ядра, драгоценные блоки-теплообменники и воздух.
 * Контроллер — ядро в углу {@code (minX+1, minY+1, minZ+1)}.</p>
 *
 * <p>Как и турбина: полный поиск только после изменения потенциальной части
 * или при однократном восстановлении после перезагрузки мира.</p>
 */
public final class SteamGenStructure {

    /** Защита от случайной стены корпусов: максимум блоков в связной компоненте. */
    private static final int MAX_CANDIDATE_COMPONENT = 1_024;

    /** Level -> позиция любой части -> детерминированное ядро-контроллер. */
    private static final Map<Level, Map<Long, BlockPos>> MEMBER_INDEX =
        Collections.synchronizedMap(new WeakHashMap<>());

    private SteamGenStructure() {
    }

    /** Сброс transient-индекса при остановке сервера: он не хранит игрового состояния. */
    public static void clearAll() {
        MEMBER_INDEX.clear();
    }

    /** Ставится корпус/ядро: новая деталь может достроить либо сломать объём. */
    public static void partPlaced(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        tryFormFrom(server, pos);
    }

    /** Вызывается до фактического удаления корпуса/ядра. */
    public static void partRemoved(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        invalidateAt(server, pos);
    }

    /** Все портовые ноды зовут этот метод при постановке. */
    public static void portPlaced(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !isCandidateState(server.getBlockState(pos))) return;
        tryFormFrom(server, pos);
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            tryFormFrom(server, pos.relative(direction));
        }
    }

    /** Точная позиция порта ещё индексирована во время onRemove, поэтому форма снимается сразу. */
    public static void portRemoved(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) invalidateAt(server, pos);
    }

    /** Открывает одно меню контроллера с любой части сформированной конструкции. */
    public static boolean openMenu(Level level, BlockPos clicked, Player player) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockPos root = controllerAt(server, clicked);
        if (root == null && isCandidateState(server.getBlockState(clicked))) {
            tryFormFrom(server, clicked);
            root = controllerAt(server, clicked);
        }
        if (root == null) return false;
        if (server.getBlockEntity(root) instanceof SteamGenCoreBlockEntity controller
            && controller.isFormedController()) {
            player.openMenu(controller, root);
            return true;
        }
        return false;
    }

    /** Виртуальный приёмник воды: node является водным портом сформированной установки. */
    public static Transfer.Receiver waterReceiverAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        BlockPos root = controllerAt(server, pos);
        if (root == null) return null;
        if (!(server.getBlockEntity(root) instanceof SteamGenCoreBlockEntity controller)
            || !controller.isFormedController()) return null;
        return (amount, simulate) -> controller.receiveWaterFromPort(pos, amount, simulate);
    }

    /** Виртуальный приёмник GTH: node является тепловым портом сформированной установки. */
    public static Transfer.Receiver gthReceiverAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        BlockPos root = controllerAt(server, pos);
        if (root == null) return null;
        if (!(server.getBlockEntity(root) instanceof SteamGenCoreBlockEntity controller)
            || !controller.isFormedController()) return null;
        return (amount, simulate) -> controller.receiveGthFromPort(pos, amount, simulate);
    }

    /** Принадлежит ли позиция любому валидному многоблоку: защита выхода от захода в себя. */
    public static boolean isMember(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && controllerAt(server, pos) != null;
    }

    /** Реальный предел встроенного парового порта (норма 1000 mB/t, универсальный узел — ×0.9). */
    public static long steamPortLimit(Level level, BlockPos port) {
        BlockState state = level.getBlockState(port);
        if (!isSteamPort(state) || !(state.getBlock() instanceof PipeCarrier carrier)) return 0;
        long base = carrier.throughputLimit(state, PipeType.STEAM);
        double factor = carrier.throughputFactor(state, PipeType.STEAM);
        return factor < 1.0D ? Math.max(1L, (long) Math.floor(base * factor)) : base;
    }

    /** Восстановление transient-индекса после загрузки мира; вызывается единожды контроллером. */
    public static boolean restoreController(ServerLevel level, BlockPos root, SteamGenCoreBlockEntity controller) {
        if (!controller.isFormedController() || controller.min() == null || controller.max() == null) return false;
        // Не считаем отсутствующий дальний чанк «воздухом».
        if (!allChunksLoaded(level, controller.min(), controller.max())) return false;
        Build build = validateBox(level, controller.min(), controller.max());
        if (build == null || !build.root.equals(root) || build.cores != controller.cores()
            || build.sumCH != controller.sumCH() || build.precious != controller.precious()) {
            invalidateController(level, root, controller);
            return false;
        }
        if (!samePositions(build.steamPorts, controller.steamPorts())
            || !samePositions(build.waterPorts, controller.waterPorts())
            || !samePositions(build.heatPorts, controller.heatPorts())) {
            invalidateController(level, root, controller);
            return false;
        }
        index(level, build);
        applyFormVisuals(level, build);
        controller.markIndexRestored();
        return true;
    }

    private static boolean samePositions(List<BlockPos> expected, long[] actual) {
        if (expected.size() != actual.length) return false;
        long[] a = expected.stream().mapToLong(BlockPos::asLong).sorted().toArray();
        long[] b = actual.clone();
        Arrays.sort(b);
        return Arrays.equals(a, b);
    }

    private static void tryFormFrom(ServerLevel level, BlockPos seed) {
        if (!isCandidateState(level.getBlockState(seed)) || controllerAt(level, seed) != null) return;
        List<BlockPos> component = collectComponent(level, seed);
        if (component == null || component.isEmpty()) return;

        Bounds bounds = boundsOf(component);
        if (bounds.volume() > MAX_CANDIDATE_COMPONENT) return;
        Build build = validateBox(level, bounds.min, bounds.max);
        if (build == null) return;

        // Если это уже та же сформированная структура, повторно не очищаем баки.
        // Второе условие покрывает первый ПКМ сразу после перезапуска сервера.
        BlockPos existing = controllerAt(level, seed);
        if (existing != null && existing.equals(build.root)
            && level.getBlockEntity(existing) instanceof SteamGenCoreBlockEntity controller
            && controller.isFormedController()) {
            return;
        }
        if (level.getBlockEntity(build.root) instanceof SteamGenCoreBlockEntity controller
            && controller.isFormedController()
            && build.min.equals(controller.min())
            && build.max.equals(controller.max())
            && build.cores == controller.cores()
            && build.sumCH == controller.sumCH()
            && build.precious == controller.precious()
            && samePositions(build.steamPorts, controller.steamPorts())
            && samePositions(build.waterPorts, controller.waterPorts())
            && samePositions(build.heatPorts, controller.heatPorts())) {
            index(level, build);
            applyFormVisuals(level, build);
            controller.markIndexRestored();
            return;
        }

        // Соседняя новая деталь могла попасть в candidate component до того, как
        // neighbourChanged успел снять старую форму.
        for (BlockPos pos : build.allParts) {
            BlockPos oldRoot = controllerAt(level, pos);
            if (oldRoot != null && !oldRoot.equals(build.root)) invalidateAt(level, pos);
        }

        if (!(level.getBlockEntity(build.root) instanceof SteamGenCoreBlockEntity controller)) return;
        controller.configureStructure(build.min, build.max, build.cores, build.sumCH, build.precious,
            toLongArray(build.steamPorts), toLongArray(build.waterPorts), toLongArray(build.heatPorts));

        applyFormVisuals(level, build);
        index(level, build);
    }

    /** Помечает части как сформированные (визуал собирает клиентский Smart CTM). */
    private static void applyFormVisuals(ServerLevel level, Build build) {
        for (BlockPos pos : build.allParts) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SteamGenCasingBlock) {
                BlockState next = state.setValue(SteamGenCoreBlock.FORMED, true);
                if (!next.equals(state)) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            } else if (state.getBlock() instanceof SteamGenCoreBlock core) {
                boolean controllerCore = pos.equals(build.root);
                BlockState next = state
                    .setValue(SteamGenCoreBlock.FORMED, true)
                    .setValue(SteamGenCoreBlock.CONTROLLER, controllerCore);
                if (!next.equals(state)) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Связная компонента допустимых деталей; ограничение защищает от случайной стены корпусов. */
    private static List<BlockPos> collectComponent(ServerLevel level, BlockPos seed) {
        List<BlockPos> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed.immutable());
        seen.add(seed.asLong());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            result.add(current);
            if (result.size() > MAX_CANDIDATE_COMPONENT) return null;
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!seen.add(next.asLong()) || !isCandidateState(level.getBlockState(next))) continue;
                // Уже собранная соседняя установка — самостоятельный объект.
                if (!next.equals(seed) && controllerAt(level, next) != null) continue;
                queue.addLast(next.immutable());
            }
        }
        return result;
    }

    private static Bounds boundsOf(List<BlockPos> positions) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
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

    /** Проверяет по одному BlockPos на чанк, не загружая новые чанки ради валидации. */
    private static boolean allChunksLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        int firstX = Math.floorDiv(min.getX(), 16) * 16;
        int firstZ = Math.floorDiv(min.getZ(), 16) * 16;
        for (int x = firstX; x <= max.getX(); x += 16) {
            for (int z = firstZ; z <= max.getZ(); z += 16) {
                if (!level.hasChunkAt(new BlockPos(x, min.getY(), z))) return false;
            }
        }
        return true;
    }

    /**
     * Полная строгая проверка: ровно 5×5×5; снаружи — корпус или порты, внутри —
     * ядра/теплообменники/воздух; контроллер-ядро в углу; хотя бы по одному
     * GTH-, водяному и паровому порту.
     */
    private static Build validateBox(ServerLevel level, BlockPos min, BlockPos max) {
        int size = MachineDefs.STEAMGEN_SIZE;
        if (max.getX() - min.getX() != size - 1
            || max.getY() - min.getY() != size - 1
            || max.getZ() - min.getZ() != size - 1) return null;

        int cores = 0;
        int sumCH = 0;
        int precious = 0;
        List<BlockPos> all = new ArrayList<>(size * size * size);
        List<BlockPos> steam = new ArrayList<>();
        List<BlockPos> water = new ArrayList<>();
        List<BlockPos> heat = new ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    boolean outer = x == min.getX() || x == max.getX()
                        || y == min.getY() || y == max.getY()
                        || z == min.getZ() || z == max.getZ();
                    if (outer) {
                        if (!isShellBlock(state)) return null;
                        if (isSteamPort(state)) steam.add(pos.immutable());
                        if (isWaterPort(state)) water.add(pos.immutable());
                        if (isHeatPort(state)) heat.add(pos.immutable());
                    } else {
                        Block block = state.getBlock();
                        if (block == ModMachines.STEAMGEN_CORE.get()) {
                            cores++;
                        } else if (SteamGenHeatExchangers.isHeatExchanger(block)) {
                            precious++;
                            sumCH += SteamGenHeatExchangers.of(block).sum();
                        } else if (!state.isAir()) {
                            return null;
                        }
                    }
                    all.add(pos.immutable());
                }
            }
        }
        if (cores < 1 || steam.isEmpty() || water.isEmpty() || heat.isEmpty()) return null;
        BlockPos root = new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ() + 1);
        if (!level.getBlockState(root).is(ModMachines.STEAMGEN_CORE.get())) return null;
        return new Build(min.immutable(), max.immutable(), root, cores, sumCH, precious,
            all, steam, water, heat);
    }

    private static boolean isShellBlock(BlockState state) {
        return state.is(ModMachines.STEAMGEN_CASING.get())
            || isSteamPort(state) || isWaterPort(state) || isHeatPort(state);
    }

    private static boolean isCandidateState(BlockState state) {
        return isCandidateBlock(state.getBlock());
    }

    public static boolean isCandidateBlock(Block block) {
        return block == ModMachines.STEAMGEN_CASING.get()
            || block == ModMachines.STEAMGEN_CORE.get()
            || isSteamPortBlock(block)
            || isWaterPortBlock(block)
            || isHeatPortBlock(block);
    }

    /** Паровой выход: паровая нода, универсальная жидкостная нода или универсальная нода. */
    private static boolean isSteamPort(BlockState state) {
        return isSteamPortBlock(state.getBlock());
    }

    private static boolean isSteamPortBlock(Block block) {
        return block == ModMachines.STEAM_NODE.get()
            || block == ModMachines.SECOND_STEAM_NODE.get()
            || block == ModMachines.UNIVERSAL_FLUID_NODE.get()
            || block == ModMachines.SECOND_UNIVERSAL_FLUID_NODE.get()
            || block == ModMachines.UNIVERSAL_NODE.get()
            || block == ModMachines.SECOND_UNIVERSAL_NODE.get();
    }

    /** Водный вход: водная нода, универсальная жидкостная нода или универсальная нода. */
    private static boolean isWaterPort(BlockState state) {
        return isWaterPortBlock(state.getBlock());
    }

    private static boolean isWaterPortBlock(Block block) {
        return block == ModMachines.WATER_NODE.get()
            || block == ModMachines.SECOND_WATER_NODE.get()
            || block == ModMachines.UNIVERSAL_FLUID_NODE.get()
            || block == ModMachines.SECOND_UNIVERSAL_FLUID_NODE.get()
            || block == ModMachines.UNIVERSAL_NODE.get()
            || block == ModMachines.SECOND_UNIVERSAL_NODE.get();
    }

    /** Тепловой вход (GTH): тепловая нода или универсальная нода. */
    private static boolean isHeatPort(BlockState state) {
        return isHeatPortBlock(state.getBlock());
    }

    private static boolean isHeatPortBlock(Block block) {
        return block == ModMachines.HEAT_NODE.get()
            || block == ModMachines.SECOND_HEAT_NODE.get()
            || block == ModMachines.UNIVERSAL_NODE.get()
            || block == ModMachines.SECOND_UNIVERSAL_NODE.get();
    }

    private static long[] toLongArray(List<BlockPos> positions) {
        return positions.stream().mapToLong(BlockPos::asLong).toArray();
    }

    private static void invalidateAt(ServerLevel level, BlockPos part) {
        BlockPos root = controllerAt(level, part);
        if (root == null) return;
        if (level.getBlockEntity(root) instanceof SteamGenCoreBlockEntity controller) {
            invalidateController(level, root, controller);
        } else {
            removeIndexForRoot(level, root);
        }
    }

    private static void invalidateController(ServerLevel level, BlockPos root, SteamGenCoreBlockEntity controller) {
        BlockPos min = controller.min();
        BlockPos max = controller.max();
        removeIndexForRoot(level, root);
        controller.clearStructure();
        if (min == null || max == null) return;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof SteamGenCasingBlock && state.getValue(SteamGenCoreBlock.FORMED)) {
                        level.setBlock(pos, state.setValue(SteamGenCoreBlock.FORMED, false), Block.UPDATE_CLIENTS);
                    } else if (state.getBlock() instanceof SteamGenCoreBlock) {
                        BlockState next = state
                            .setValue(SteamGenCoreBlock.FORMED, false)
                            .setValue(SteamGenCoreBlock.CONTROLLER, false);
                        if (!next.equals(state)) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void index(ServerLevel level, Build build) {
        Map<Long, BlockPos> index = MEMBER_INDEX.computeIfAbsent(level, ignored -> new HashMap<>());
        for (BlockPos pos : build.allParts) index.put(pos.asLong(), build.root);
    }

    private static BlockPos controllerAt(ServerLevel level, BlockPos pos) {
        Map<Long, BlockPos> index = MEMBER_INDEX.get(level);
        return index == null ? null : index.get(pos.asLong());
    }

    private static void removeIndexForRoot(ServerLevel level, BlockPos root) {
        Map<Long, BlockPos> index = MEMBER_INDEX.get(level);
        if (index == null) return;
        index.entrySet().removeIf(entry -> root.equals(entry.getValue()));
        if (index.isEmpty()) MEMBER_INDEX.remove(level);
    }

    private record Bounds(BlockPos min, BlockPos max) {
        long volume() {
            return (long) (max.getX() - min.getX() + 1)
                * (max.getY() - min.getY() + 1)
                * (max.getZ() - min.getZ() + 1);
        }
    }

    private record Build(BlockPos min, BlockPos max, BlockPos root, int cores, int sumCH, int precious,
                         List<BlockPos> allParts, List<BlockPos> steamPorts,
                         List<BlockPos> waterPorts, List<BlockPos> heatPorts) {
    }

}
