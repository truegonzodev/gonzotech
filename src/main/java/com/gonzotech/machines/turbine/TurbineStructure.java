package com.gonzotech.machines.turbine;

import com.gonzotech.machines.block.TurbineCasingBlock;
import com.gonzotech.machines.block.TurbinePartBlock;
import com.gonzotech.machines.block.TurbineRotorBlock;
import com.gonzotech.machines.block.entity.TurbineRotorBlockEntity;
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
 * Событийный валидатор и быстрый индекс прямоугольных паровых турбин.
 *
 * <p>Полный поиск выполняется только после изменения потенциальной части
 * многоблока или при однократном восстановлении после перезагрузки мира.
 * Обычный тик контроллера не сканирует соседей и не ищет форму.</p>
 */
public final class TurbineStructure {

    /** При N<100 максимальный допустимый объём заполненного параллелепипеда — 909. */
    private static final int MAX_CANDIDATE_COMPONENT = 1_024;

    /** Level -> позиция любой части -> детерминированный ротор-контроллер. */
    private static final Map<Level, Map<Long, BlockPos>> MEMBER_INDEX =
        Collections.synchronizedMap(new WeakHashMap<>());

    private TurbineStructure() {
    }

    /** Сброс transient-индекса при остановке сервера: он не хранит игрового состояния. */
    public static void clearAll() {
        MEMBER_INDEX.clear();
    }

    /** Ставится корпус/ротор: новая деталь может достроить либо сломать старый объём. */
    public static void partPlaced(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        // Замена существующей части уже вызвала partRemoved до постановки. Не
        // ломаем соседнюю независимую турбину только за то, что она касается
        // корпусом новой конструкции.
        tryFormFrom(server, pos);
    }

    /** Вызывается до фактического удаления корпуса/ротора. */
    public static void partRemoved(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        invalidateAt(server, pos);
    }

    /**
     * Все PipeBlock-ноды зовут этот метод при постановке. Обычные трубы отсекаются
     * первой дешёвой проверкой; service-нода может стать последним блоком формы.
     */
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
        if (server.getBlockEntity(root) instanceof TurbineRotorBlockEntity controller
            && controller.isFormedController()) {
            player.openMenu(controller, root);
            return true;
        }
        return false;
    }

    /** Быстрый ответ для PipeRouting: node является Steam-портом сформированной турбины. */
    public static Transfer.Receiver steamReceiverAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return null;
        BlockPos root = controllerAt(server, pos);
        if (root == null) return null;
        if (!(server.getBlockEntity(root) instanceof TurbineRotorBlockEntity controller)
            || !controller.isFormedController()) return null;
        return (amount, simulate) -> controller.receiveSteamFromPort(pos, amount, simulate);
    }

    /** Принадлежит ли позиция любому валидному многоблоку: защита выхода от захода в себя. */
    public static boolean isMember(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && controllerAt(server, pos) != null;
    }

    /** Реальный предел встроенного wire-порта (обычный wire=38, universal=34.2 GTU/t). */
    public static long outputPortLimit(Level level, BlockPos port) {
        BlockState state = level.getBlockState(port);
        if (!isWirePort(state) || !(state.getBlock() instanceof PipeCarrier carrier)) return 0;
        long base = PipeType.WIRE.maxThroughput();
        double factor = carrier.throughputFactor(state, PipeType.WIRE);
        return factor < 1.0D ? Math.max(1L, (long) Math.floor(base * factor)) : base;
    }

    /** Восстановление transient-индекса после загрузки мира; вызывается единожды контроллером. */
    public static boolean restoreController(ServerLevel level, BlockPos root, TurbineRotorBlockEntity controller) {
        if (!controller.isFormedController() || controller.min() == null || controller.max() == null) return false;
        // Не считаем отсутствующий дальний чанк «воздухом»: многоблок не должен
        // распадаться просто потому, что игрок отошёл от его половины.
        if (!allChunksLoaded(level, controller.min(), controller.max())) return false;
        Build build = validateBox(level, controller.min(), controller.max());
        if (build == null || !build.root.equals(root) || build.rotors != controller.rotors()) {
            invalidateController(level, root, controller);
            return false;
        }
        // Списки портов также проверяются: NBT не может тихо подменить соединения.
        if (!samePositions(build.steamPorts, controller.steamPorts())
            || !samePositions(build.wirePorts, controller.wirePorts())) {
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
        // Второе условие покрывает первый ПКМ сразу после перезапуска сервера,
        // когда transient MEMBER_INDEX ещё не успел восстановиться тикером.
        BlockPos existing = controllerAt(level, seed);
        if (existing != null && existing.equals(build.root)
            && level.getBlockEntity(existing) instanceof TurbineRotorBlockEntity controller
            && controller.isFormedController()) {
            return;
        }
        if (level.getBlockEntity(build.root) instanceof TurbineRotorBlockEntity controller
            && controller.isFormedController()
            && build.min.equals(controller.min())
            && build.max.equals(controller.max())
            && build.rotors == controller.rotors()
            && samePositions(build.steamPorts, controller.steamPorts())
            && samePositions(build.wirePorts, controller.wirePorts())) {
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

        if (!(level.getBlockEntity(build.root) instanceof TurbineRotorBlockEntity controller)) return;
        controller.configureStructure(build.min, build.max, build.rotors,
            toLongArray(build.steamPorts), toLongArray(build.wirePorts));

        applyFormVisuals(level, build);
        index(level, build);
    }

    /**
     * Запекает CTM-рамку ровно в blockstate при успешной сборке. Рендер получает
     * готовые флаги и не делает ни одного поиска соседей/границ каждый кадр.
     */
    private static void applyFormVisuals(ServerLevel level, Build build) {
        for (BlockPos pos : build.allParts) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof TurbineCasingBlock) {
                BlockState next = formedCasingState(level, build, pos, state);
                if (!next.equals(state)) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            } else if (state.getBlock() instanceof TurbineRotorBlock) {
                boolean controllerRotor = pos.equals(build.root);
                BlockState next = state
                    .setValue(TurbinePartBlock.FORMED, true)
                    .setValue(TurbineRotorBlock.CONTROLLER, controllerRotor);
                if (!next.equals(state)) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Получает готовый blockstate корпуса с профилем внешнего каркаса и каймой портов. */
    private static BlockState formedCasingState(ServerLevel level, Build build, BlockPos pos, BlockState state) {
        List<net.minecraft.core.Direction> boundary = boundaryDirections(build, pos);
        TurbineCasingBlock.FrameProfile profile = frameProfile(boundary);
        boolean[] portBorders = new boolean[4];
        boolean[] cornerCaps = diagonalCornerCaps(level, build, pos, boundary);

        if (boundary.size() == 1) {
            // Плоская грань: четыре направления в постоянном мировом порядке.
            // Порядок Direction.values() совпадает с генератором CTM-вариантов.
            int index = 0;
            net.minecraft.core.Direction normal = boundary.getFirst();
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                if (direction.getAxis() != normal.getAxis()) {
                    portBorders[index++] = isServicePortAt(level, build, pos.relative(direction));
                }
            }
        } else if (boundary.size() == 2) {
            // Ребро: порты могут быть не только на двух прилегающих плоских
            // гранях, но и с обоих концов вдоль самого ребра. В последнем случае
            // обводка рисуется на ОБЕИХ открытых гранях корпуса, огибая порт, а
            // не считая его ложным новым краем параллелепипеда.
            portBorders[0] = isServicePortAt(level, build, pos.relative(boundary.get(0).getOpposite()));
            portBorders[1] = isServicePortAt(level, build, pos.relative(boundary.get(1).getOpposite()));
            net.minecraft.core.Direction.Axis freeAxis = freeAxis(boundary);
            portBorders[2] = isServicePortAt(level, build, pos.relative(negativeDirection(freeAxis)));
            portBorders[3] = isServicePortAt(level, build, pos.relative(positiveDirection(freeAxis)));
        } else if (boundary.size() == 3) {
            // Угловая клетка оболочки тоже может примыкать к service-порту с
            // внутренней стороны по любой из трёх осей. Для такого порта должны
            // сохраниться две каймы на двух общих наружных гранях.
            for (int i = 0; i < 3; i++) {
                portBorders[i] = isServicePortAt(level, build, pos.relative(boundary.get(i).getOpposite()));
            }
        }

        return state
            .setValue(TurbinePartBlock.FORMED, true)
            .setValue(TurbineCasingBlock.FRAME, profile)
            .setValue(TurbineCasingBlock.PORT_0, portBorders[0])
            .setValue(TurbineCasingBlock.PORT_1, portBorders[1])
            .setValue(TurbineCasingBlock.PORT_2, portBorders[2])
            .setValue(TurbineCasingBlock.PORT_3, portBorders[3])
            .setValue(TurbineCasingBlock.CAP_0, cornerCaps[0])
            .setValue(TurbineCasingBlock.CAP_1, cornerCaps[1])
            .setValue(TurbineCasingBlock.CAP_2, cornerCaps[2])
            .setValue(TurbineCasingBlock.CAP_3, cornerCaps[3]);
    }

    /**
     * Запекает 2×2 corner caps в диагональных клетках вокруг каждого порта.
     *
     * <p>В отличие от {@code port_*}, которые принадлежат четырём прямым
     * соседям порта, эти флаги принадлежат именно диагональным клеткам из
     * схемы {@code [у][к][у] / [к][д][к] / [у][к][у]}. Для каждой видимой
     * грани перебираются две касательные оси. На плоской грани это даёт четыре
     * диагонали, на ребре — две на каждую из двух граней, в углу — по одной на
     * грань. Порядок полностью повторён в генераторе JSON-моделей.</p>
     */
    private static boolean[] diagonalCornerCaps(ServerLevel level, Build build, BlockPos pos,
                                                  List<net.minecraft.core.Direction> boundary) {
        boolean[] result = new boolean[4];
        int index = 0;
        for (net.minecraft.core.Direction normal : boundary) {
            net.minecraft.core.Direction.Axis firstAxis = null;
            net.minecraft.core.Direction.Axis secondAxis = null;
            for (net.minecraft.core.Direction.Axis axis : net.minecraft.core.Direction.Axis.values()) {
                if (axis == normal.getAxis()) continue;
                if (firstAxis == null) firstAxis = axis;
                else secondAxis = axis;
            }
            for (net.minecraft.core.Direction first : surfaceDirections(build, pos, firstAxis)) {
                for (net.minecraft.core.Direction second : surfaceDirections(build, pos, secondAxis)) {
                    if (index >= result.length) {
                        throw new IllegalStateException("Too many diagonal turbine corner caps");
                    }
                    result[index++] = isServicePortAt(level, build, pos.relative(first).relative(second));
                }
            }
        }
        return result;
    }

    /** Направления вдоль оси, которые ещё остаются в bounding parallelepiped. */
    private static List<net.minecraft.core.Direction> surfaceDirections(Build build, BlockPos pos,
                                                                          net.minecraft.core.Direction.Axis axis) {
        int coordinate = coordinate(pos, axis);
        if (coordinate == coordinate(build.min, axis)) return List.of(positiveDirection(axis));
        if (coordinate == coordinate(build.max, axis)) return List.of(negativeDirection(axis));
        return List.of(negativeDirection(axis), positiveDirection(axis));
    }

    private static int coordinate(BlockPos pos, net.minecraft.core.Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static List<net.minecraft.core.Direction> boundaryDirections(Build build, BlockPos pos) {
        List<net.minecraft.core.Direction> result = new ArrayList<>(3);
        if (pos.getX() == build.min.getX()) result.add(net.minecraft.core.Direction.WEST);
        else if (pos.getX() == build.max.getX()) result.add(net.minecraft.core.Direction.EAST);
        if (pos.getY() == build.min.getY()) result.add(net.minecraft.core.Direction.DOWN);
        else if (pos.getY() == build.max.getY()) result.add(net.minecraft.core.Direction.UP);
        if (pos.getZ() == build.min.getZ()) result.add(net.minecraft.core.Direction.NORTH);
        else if (pos.getZ() == build.max.getZ()) result.add(net.minecraft.core.Direction.SOUTH);
        return result;
    }

    /** Единственная ось, не входящая в геометрическое ребро оболочки. */
    private static net.minecraft.core.Direction.Axis freeAxis(List<net.minecraft.core.Direction> boundary) {
        for (net.minecraft.core.Direction.Axis axis : net.minecraft.core.Direction.Axis.values()) {
            boolean used = false;
            for (net.minecraft.core.Direction direction : boundary) {
                if (direction.getAxis() == axis) {
                    used = true;
                    break;
                }
            }
            if (!used) return axis;
        }
        throw new IllegalArgumentException("Corner has no free axis");
    }

    private static net.minecraft.core.Direction negativeDirection(net.minecraft.core.Direction.Axis axis) {
        return switch (axis) {
            case X -> net.minecraft.core.Direction.WEST;
            case Y -> net.minecraft.core.Direction.DOWN;
            case Z -> net.minecraft.core.Direction.NORTH;
        };
    }

    private static net.minecraft.core.Direction positiveDirection(net.minecraft.core.Direction.Axis axis) {
        return switch (axis) {
            case X -> net.minecraft.core.Direction.EAST;
            case Y -> net.minecraft.core.Direction.UP;
            case Z -> net.minecraft.core.Direction.SOUTH;
        };
    }

    private static boolean isServicePortAt(ServerLevel level, Build build, BlockPos pos) {
        if (pos.getX() < build.min.getX() || pos.getX() > build.max.getX()
            || pos.getY() < build.min.getY() || pos.getY() > build.max.getY()
            || pos.getZ() < build.min.getZ() || pos.getZ() > build.max.getZ()) return false;
        BlockState state = level.getBlockState(pos);
        return isShellBlock(state) && !(state.getBlock() instanceof TurbineCasingBlock);
    }

    private static TurbineCasingBlock.FrameProfile frameProfile(List<net.minecraft.core.Direction> boundary) {
        if (boundary.size() == 1) {
            return switch (boundary.getFirst()) {
                case DOWN -> TurbineCasingBlock.FrameProfile.FACE_DOWN;
                case UP -> TurbineCasingBlock.FrameProfile.FACE_UP;
                case NORTH -> TurbineCasingBlock.FrameProfile.FACE_NORTH;
                case SOUTH -> TurbineCasingBlock.FrameProfile.FACE_SOUTH;
                case WEST -> TurbineCasingBlock.FrameProfile.FACE_WEST;
                case EAST -> TurbineCasingBlock.FrameProfile.FACE_EAST;
            };
        }
        boolean west = boundary.contains(net.minecraft.core.Direction.WEST);
        boolean east = boundary.contains(net.minecraft.core.Direction.EAST);
        boolean down = boundary.contains(net.minecraft.core.Direction.DOWN);
        boolean up = boundary.contains(net.minecraft.core.Direction.UP);
        boolean north = boundary.contains(net.minecraft.core.Direction.NORTH);
        boolean south = boundary.contains(net.minecraft.core.Direction.SOUTH);

        if (boundary.size() == 2) {
            if (down && north) return TurbineCasingBlock.FrameProfile.EDGE_X_DOWN_NORTH;
            if (down && south) return TurbineCasingBlock.FrameProfile.EDGE_X_DOWN_SOUTH;
            if (up && north) return TurbineCasingBlock.FrameProfile.EDGE_X_UP_NORTH;
            if (up && south) return TurbineCasingBlock.FrameProfile.EDGE_X_UP_SOUTH;
            if (west && north) return TurbineCasingBlock.FrameProfile.EDGE_Y_WEST_NORTH;
            if (west && south) return TurbineCasingBlock.FrameProfile.EDGE_Y_WEST_SOUTH;
            if (east && north) return TurbineCasingBlock.FrameProfile.EDGE_Y_EAST_NORTH;
            if (east && south) return TurbineCasingBlock.FrameProfile.EDGE_Y_EAST_SOUTH;
            if (west && down) return TurbineCasingBlock.FrameProfile.EDGE_Z_WEST_DOWN;
            if (west && up) return TurbineCasingBlock.FrameProfile.EDGE_Z_WEST_UP;
            if (east && down) return TurbineCasingBlock.FrameProfile.EDGE_Z_EAST_DOWN;
            if (east && up) return TurbineCasingBlock.FrameProfile.EDGE_Z_EAST_UP;
        }
        if (west && down && north) return TurbineCasingBlock.FrameProfile.CORNER_WEST_DOWN_NORTH;
        if (west && down && south) return TurbineCasingBlock.FrameProfile.CORNER_WEST_DOWN_SOUTH;
        if (west && up && north) return TurbineCasingBlock.FrameProfile.CORNER_WEST_UP_NORTH;
        if (west && up && south) return TurbineCasingBlock.FrameProfile.CORNER_WEST_UP_SOUTH;
        if (east && down && north) return TurbineCasingBlock.FrameProfile.CORNER_EAST_DOWN_NORTH;
        if (east && down && south) return TurbineCasingBlock.FrameProfile.CORNER_EAST_DOWN_SOUTH;
        if (east && up && north) return TurbineCasingBlock.FrameProfile.CORNER_EAST_UP_NORTH;
        if (east && up && south) return TurbineCasingBlock.FrameProfile.CORNER_EAST_UP_SOUTH;
        return TurbineCasingBlock.FrameProfile.NONE;
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
                // Уже собранная соседняя турбина — самостоятельный объект, а не
                // продолжение строящейся формы. Так две турбины могут касаться
                // корпусами без взаимной инвалидизации.
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

    /** Полная строгая проверка каждого блока прямоугольника. */
    private static Build validateBox(ServerLevel level, BlockPos min, BlockPos max) {
        int xSize = max.getX() - min.getX() + 1;
        int ySize = max.getY() - min.getY() + 1;
        int zSize = max.getZ() - min.getZ() + 1;
        if (xSize < MachineDefs.TURBINE_MIN_DIMENSION
            || ySize < MachineDefs.TURBINE_MIN_DIMENSION
            || zSize < MachineDefs.TURBINE_MIN_DIMENSION) return null;

        long rotorLong = (long) (xSize - 2) * (ySize - 2) * (zSize - 2);
        if (rotorLong <= 0 || rotorLong >= MachineDefs.TURBINE_MAX_ROTORS_EXCLUSIVE) return null;
        long volume = (long) xSize * ySize * zSize;
        if (volume > MAX_CANDIDATE_COMPONENT) return null;

        List<BlockPos> all = new ArrayList<>((int) volume);
        List<BlockPos> steam = new ArrayList<>();
        List<BlockPos> wire = new ArrayList<>();
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
                        if (isWirePort(state)) wire.add(pos.immutable());
                    } else if (!state.is(ModMachines.TURBINE_ROTOR.get())) {
                        return null;
                    }
                    all.add(pos.immutable());
                }
            }
        }
        if (steam.isEmpty() || wire.isEmpty()) return null;
        BlockPos root = new BlockPos(min.getX() + 1, min.getY() + 1, min.getZ() + 1);
        if (!level.getBlockState(root).is(ModMachines.TURBINE_ROTOR.get())) return null;
        return new Build(min.immutable(), max.immutable(), root, (int) rotorLong, all, steam, wire);
    }

    private static boolean isShellBlock(BlockState state) {
        return state.is(ModMachines.TURBINE_CASING.get()) || isSteamPort(state) || isWirePort(state);
    }

    private static boolean isCandidateState(BlockState state) {
        return isCandidateBlock(state.getBlock());
    }

    public static boolean isCandidateBlock(Block block) {
        return block == ModMachines.TURBINE_CASING.get()
            || block == ModMachines.TURBINE_ROTOR.get()
            || block == ModMachines.STEAM_NODE.get()
            || block == ModMachines.UNIVERSAL_FLUID_NODE.get()
            || block == ModMachines.WIRE_NODE.get()
            || block == ModMachines.UNIVERSAL_NODE.get();
    }

    private static boolean isSteamPort(BlockState state) {
        Block block = state.getBlock();
        return block == ModMachines.STEAM_NODE.get()
            || block == ModMachines.UNIVERSAL_FLUID_NODE.get()
            || block == ModMachines.UNIVERSAL_NODE.get();
    }

    private static boolean isWirePort(BlockState state) {
        Block block = state.getBlock();
        return block == ModMachines.WIRE_NODE.get() || block == ModMachines.UNIVERSAL_NODE.get();
    }

    private static long[] toLongArray(List<BlockPos> positions) {
        return positions.stream().mapToLong(BlockPos::asLong).toArray();
    }

    private static void invalidateAt(ServerLevel level, BlockPos part) {
        BlockPos root = controllerAt(level, part);
        if (root == null) return;
        if (level.getBlockEntity(root) instanceof TurbineRotorBlockEntity controller) {
            invalidateController(level, root, controller);
        } else {
            removeIndexForRoot(level, root);
        }
    }

    private static void invalidateController(ServerLevel level, BlockPos root, TurbineRotorBlockEntity controller) {
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
                    if (state.getBlock() instanceof TurbineCasingBlock && state.getValue(TurbinePartBlock.FORMED)) {
                        BlockState next = state
                            .setValue(TurbinePartBlock.FORMED, false)
                            .setValue(TurbineCasingBlock.FRAME, TurbineCasingBlock.FrameProfile.NONE)
                            .setValue(TurbineCasingBlock.PORT_0, false)
                            .setValue(TurbineCasingBlock.PORT_1, false)
                            .setValue(TurbineCasingBlock.PORT_2, false)
                            .setValue(TurbineCasingBlock.PORT_3, false)
                            .setValue(TurbineCasingBlock.CAP_0, false)
                            .setValue(TurbineCasingBlock.CAP_1, false)
                            .setValue(TurbineCasingBlock.CAP_2, false)
                            .setValue(TurbineCasingBlock.CAP_3, false);
                        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                    } else if (state.getBlock() instanceof TurbineRotorBlock) {
                        BlockState next = state
                            .setValue(TurbinePartBlock.FORMED, false)
                            .setValue(TurbineRotorBlock.CONTROLLER, false);
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

    private record Build(BlockPos min, BlockPos max, BlockPos root, int rotors,
                         List<BlockPos> allParts, List<BlockPos> steamPorts, List<BlockPos> wirePorts) {
    }

}
