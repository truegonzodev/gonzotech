package com.gonzotech.machines.litho;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Литографическая фабрика (автор 28.09.2026) — событийный валидатор многоблока
 * 3×3(XZ)×2(Y) с единой UV-развёрткой 96×80: «дно» 48×48, «бока» 48×32,
 * «верх» 48×48 (раскладка листа — по макету автора).
 *
 * <p>Под каждый вид чипа — своя раскладка ({@link #LAYOUTS}, авторские матрицы):
 * D — алюминиевый блок / нержавеющий блок / алюминиевый корпус, R — фарфор,
 * C — резиновый блок, B — боросиликатное стекло, S — литографическая фабрика
 * (верхний центр). При формировании 17 блоков-участников заменяются
 * технической оболочкой ({ каждая рендерит свой срез развёртки}), оригиналы
 * запоминаются в BE контроллера и возвращаются при распаде; сломанная
 * оболочка выпадает оригинальным блоком через его loot-таблицу.</p>
 *
 * <p>Как турбина/парогенератор: полный поиск только после изменения
 * потенциального участника; обычный тик соседей не сканирует.</p>
 */
public final class SiliconFactoryStructure {

    public static final int SLOTS = 18;
    /** Верхний центр (x1, z1, слой 2) — контроллер. */
    public static final int SLOT_ROOT = 13;

    /**
     * Авторские раскладки: LAYOUTS[вариант-1][слот], слот = x + 3*z + 9*слой
     * (слой 0 — нижний). Вариант 1 → chip_1, 2 → chip_2, 3 → chip_3.
     */
    public static final char[][] LAYOUTS = {
        // Вариант 1: слой 1 (низ) D R D / R C R / D R D; слой 2 R B R / B S B / R B R.
        {'D', 'R', 'D', 'R', 'C', 'R', 'D', 'R', 'D', 'R', 'B', 'R', 'B', 'S', 'B', 'R', 'B', 'R'},
        // Вариант 2: слой 1 R D R / D C D / R D R; слой 2 B R B / B S B / B R B.
        {'R', 'D', 'R', 'D', 'C', 'D', 'R', 'D', 'R', 'B', 'R', 'B', 'B', 'S', 'B', 'B', 'R', 'B'},
        // Вариант 3: слой 1 D D D / D C D / D D D; слой 2 R B R / B S B / R B R.
        {'D', 'D', 'D', 'D', 'C', 'D', 'D', 'D', 'D', 'R', 'B', 'R', 'B', 'S', 'B', 'R', 'B', 'R'},
    };

    /** Level -> позиция любой части -> контроллер (верхний центр). */
    private static final Map<Level, Map<Long, BlockPos>> MEMBER_INDEX =
        Collections.synchronizedMap(new WeakHashMap<>());

    /** Гашение tryForm во время программной перестановки блоков (form/restore). */
    private static final Set<Long> SUPPRESSED = new HashSet<>();

    private SiliconFactoryStructure() {
    }

    /** Сброс transient-индекса при остановке сервера. */
    public static void clearAll() {
        MEMBER_INDEX.clear();
        SUPPRESSED.clear();
    }

    /** Смещение слота внутри коробки: x + 3*z + 9*слой (слой 0 — нижний). */
    public static BlockPos offsetOf(int slot) {
        return new BlockPos(slot % 3, slot / 9, (slot / 3) % 3);
    }

    /**
     * Соответствие символа раскладки блоку мира. Оболочка — «джокер» для любого
     * не-S слота: она стоит на месте участника сформированной структуры.
     */
    static boolean matches(char code, Block block) {
        if (block == ModBlocks.THIRD_SILICON_FACTORY_SHELL.get()) return code != 'S';
        return switch (code) {
            case 'D' -> block == ModBlocks.METAL_BLOCKS.get("aluminum_block").get()
                || block == ModBlocks.METAL_BLOCKS.get("stainless_steel_block").get()
                || block == ModBlocks.ALUMINUM_HOUSING.get();
            case 'R' -> block == ModBlocks.PORCELAIN.get();
            case 'C' -> block == ModBlocks.RUBBER_BLOCK.get();
            case 'B' -> block == ModBlocks.BORE_STAINED_GLASS.get();
            case 'S' -> block == ModBlocks.THIRD_SILICON_FACTORY.get();
            default -> false;
        };
    }

    /** Участник структуры? (для обработчика постановки блока.) */
    public static boolean isMember(Block block) {
        return block == ModBlocks.THIRD_SILICON_FACTORY.get()
            || block == ModBlocks.THIRD_SILICON_FACTORY_SHELL.get()
            || matches('D', block) || matches('R', block) || matches('C', block) || matches('B', block);
    }

    /** Игрок поставил блок-участник — попытка достроить структуру. */
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if (isMember(event.getPlacedBlock().getBlock())) tryFormFrom(server, event.getPos());
    }

    /** Ставится сама фабрика (блок-контроллер). */
    static void factoryPlaced(ServerLevel level, BlockPos pos) {
        tryFormFrom(level, pos);
    }

    /** Удаляется фабрика или оболочка (skip = удалённая позиция: её не восстанавливаем). */
    static void partRemoved(ServerLevel level, BlockPos brokenPos) {
        BlockPos root = controllerAt(level, brokenPos);
        if (root == null) return;
        if (level.getBlockEntity(root) instanceof SiliconFactoryBlockEntity controller) {
            invalidate(level, controller, brokenPos);
        }
    }

    /** Сломана оболочка: структура распадается, оригинал позиции выпадает лутом. */
    static void shellBroken(ServerLevel level, BlockPos pos) {
        BlockPos root = controllerAt(level, pos);
        if (root == null) return;
        BlockState original = null;
        if (level.getBlockEntity(root) instanceof SiliconFactoryBlockEntity controller) {
            original = controller.originalAt(pos);
        }
        partRemoved(level, pos);
        if (original != null) {
            // Через loot-таблицу оригинала: стекло без шёлка не выпадает, фарфор — выпадает.
            Block.dropResources(original, level, pos);
        }
    }

    /** ПКМ по любой части сформированной структуры. */
    public static boolean openMenu(Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockPos root = controllerAt(server, pos);
        if (root == null) return false;
        if (server.getBlockEntity(root) instanceof SiliconFactoryBlockEntity controller
            && controller.isFormed()) {
            player.openMenu(controller, root);
            return true;
        }
        return false;
    }

    /** Контроллер структуры, содержащей позицию (null — вне структур). */
    public static BlockPos controllerAt(Level level, BlockPos pos) {
        Map<Long, BlockPos> idx = MEMBER_INDEX.get(level);
        if (idx == null) return null;
        return idx.get(pos.asLong());
    }

    private static boolean anyIndexed(ServerLevel level, BlockPos origin) {
        Map<Long, BlockPos> idx = MEMBER_INDEX.get(level);
        if (idx == null) return false;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (idx.containsKey(origin.offset(offsetOf(slot)).asLong())) return true;
        }
        return false;
    }

    private static boolean allChunksLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        return level.hasChunkAt(new BlockPos(min.getX(), 0, min.getZ()))
            && level.hasChunkAt(new BlockPos(max.getX(), 0, max.getZ()))
            && level.hasChunkAt(new BlockPos(min.getX(), 0, max.getZ()))
            && level.hasChunkAt(new BlockPos(max.getX(), 0, min.getZ()));
    }

    private record Build(BlockPos origin, int variant) {
    }

    /** Полная проверка коробки; origin — угол (minX, minY, minZ). */
    private static Build validateBox(ServerLevel level, BlockPos origin) {
        if (!allChunksLoaded(level, origin, origin.offset(2, 1, 2))) return null;
        for (int variant = 1; variant <= 3; variant++) {
            char[] layout = LAYOUTS[variant - 1];
            boolean ok = true;
            for (int slot = 0; slot < SLOTS && ok; slot++) {
                Block block = level.getBlockState(origin.offset(offsetOf(slot))).getBlock();
                if (!matches(layout[slot], block)) ok = false;
            }
            if (ok) return new Build(origin, variant);
        }
        return null;
    }

    private static void tryFormFrom(ServerLevel level, BlockPos seed) {
        if (SUPPRESSED.contains(seed.asLong())) return;
        for (int slot = 0; slot < SLOTS; slot++) {
            BlockPos origin = seed.subtract(offsetOf(slot));
            if (anyIndexed(level, origin)) continue;
            Build build = validateBox(level, origin);
            if (build == null) continue;
            form(level, build);
            return;
        }
    }

    private static void form(ServerLevel level, Build build) {
        BlockPos root = build.origin().offset(offsetOf(SLOT_ROOT));
        if (!(level.getBlockEntity(root) instanceof SiliconFactoryBlockEntity controller)) return;
        if (controller.isFormed()) {
            // Повторная проверка уже собранной структуры (до восстановления индекса
            // после перезапуска): оригиналы нельзя переписать состояниями оболочек.
            return;
        }
        BlockPos[] memberPos = new BlockPos[SLOTS - 1];
        BlockState[] originalStates = new BlockState[SLOTS - 1];
        int n = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (slot == SLOT_ROOT) continue;
            memberPos[n] = build.origin().offset(offsetOf(slot));
            originalStates[n] = level.getBlockState(memberPos[n]);
            n++;
        }

        SUPPRESSED.add(root.asLong());
        for (BlockPos p : memberPos) SUPPRESSED.add(p.asLong());
        for (int i = 0; i < memberPos.length; i++) {
            BlockState shell = ModBlocks.THIRD_SILICON_FACTORY_SHELL.get().defaultBlockState()
                .setValue(SiliconFactoryShellBlock.SLICE, slotOf(memberPos[i], build.origin()))
                .setValue(SiliconFactoryShellBlock.VARIANT, build.variant());
            level.setBlock(memberPos[i], shell, Block.UPDATE_CLIENTS);
        }
        BlockState factoryState = level.getBlockState(root)
            .setValue(SiliconFactoryBlock.FORMED, true)
            .setValue(SiliconFactoryBlock.VARIANT, build.variant());
        level.setBlock(root, factoryState, Block.UPDATE_CLIENTS);
        SUPPRESSED.clear();

        controller.setFormed(build.origin(), build.variant(), memberPos, originalStates);
        index(level, build.origin(), root);
    }

    private static int slotOf(BlockPos pos, BlockPos origin) {
        return (pos.getX() - origin.getX()) + 3 * (pos.getZ() - origin.getZ())
            + 9 * (pos.getY() - origin.getY());
    }

    private static void index(ServerLevel level, BlockPos origin, BlockPos root) {
        Map<Long, BlockPos> idx = MEMBER_INDEX.computeIfAbsent(level, l -> new HashMap<>());
        for (int slot = 0; slot < SLOTS; slot++) {
            idx.put(origin.offset(offsetOf(slot)).asLong(), root);
        }
    }

    private static void unindex(ServerLevel level, BlockPos origin) {
        Map<Long, BlockPos> idx = MEMBER_INDEX.get(level);
        if (idx == null) return;
        for (int slot = 0; slot < SLOTS; slot++) {
            idx.remove(origin.offset(offsetOf(slot)).asLong());
        }
    }

    /** Распад структуры: вернуть оригиналы (кроме skipPos — он уже сломан), погасить фабрику. */
    private static void invalidate(ServerLevel level, SiliconFactoryBlockEntity controller, BlockPos skipPos) {
        BlockPos origin = controller.origin();
        if (origin == null) {
            controller.clearFormed();
            return;
        }
        BlockPos root = origin.offset(offsetOf(SLOT_ROOT));
        unindex(level, origin);
        SUPPRESSED.add(root.asLong());
        for (int i = 0; i < controller.memberCount(); i++) {
            SUPPRESSED.add(controller.memberPos(i).asLong());
        }
        for (int i = 0; i < controller.memberCount(); i++) {
            BlockPos p = controller.memberPos(i);
            if (skipPos != null && p.equals(skipPos)) continue;
            level.setBlock(p, controller.originalState(i), Block.UPDATE_CLIENTS);
        }
        if (!root.equals(skipPos)) {
            BlockState st = level.getBlockState(root);
            if (st.getBlock() == ModBlocks.THIRD_SILICON_FACTORY.get()
                && st.getValue(SiliconFactoryBlock.FORMED)) {
                level.setBlock(root, st.setValue(SiliconFactoryBlock.FORMED, false), Block.UPDATE_CLIENTS);
            }
        }
        SUPPRESSED.clear();
        controller.clearFormed();
    }

    /**
     * Восстановление transient-индекса после загрузки мира (вызывается тиком BE).
     * Коробка обязана совпасть раскладкой; иначе структура распадается.
     */
    static boolean restoreController(ServerLevel level, SiliconFactoryBlockEntity controller) {
        if (!controller.isFormed() || controller.origin() == null) return false;
        BlockPos origin = controller.origin();
        if (!allChunksLoaded(level, origin, origin.offset(2, 1, 2))) return false;
        Build build = validateBox(level, origin);
        if (build == null || build.variant() != controller.variant()) {
            invalidate(level, controller, null);
            return false;
        }
        index(level, origin, origin.offset(offsetOf(SLOT_ROOT)));
        controller.onIndexRestored();
        return true;
    }
}
