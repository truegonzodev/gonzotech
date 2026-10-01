package com.gonzotech.machines.blastfurnace;

import com.gonzotech.machines.block.FireclayBlock;
import com.gonzotech.machines.block.entity.FireboxBlockEntity;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Доменная печь (0.3.65): событийная сборка/проверка структуры 3×3×3.
 * <p>
 * Геометрия — {@link BlastFurnaceLayout}: фундамент 9× шамот, в середине —
 * топка (контроллер) с узлами теплотруб по серединам рёбер, сверху — котёл.
 * Узлы принимаются любого тепло-типа: T1/T2 или универсальные T1/T2.
 * <p>
 * При сборке шамотные блоки получают флаг {@code FORMED} — клиентский
 * Smart CTM рисует бесшовную обшивку (как у турбины и парогена). Контроллер —
 * сама топка: её BlockEntity в сформированном режиме работает как доменная
 * печь (5 слотов топлива, 34 GTH/t, жжение ×4, вывод GTH через узлы).
 */
public final class BlastFurnaceStructure {

    private BlastFurnaceStructure() {
    }

    /** Совпадает ли блок в ячейке с ожидаемой ролью раскладки. */
    private static boolean matches(BlockState state, BlastFurnaceLayout.Role role) {
        return switch (role) {
            case FIRECLAY -> state.getBlock() instanceof FireclayBlock;
            case HEAT_NODE -> state.is(ModMachines.HEAT_NODE.get())
                || state.is(ModMachines.SECOND_HEAT_NODE.get())
                || state.is(ModMachines.UNIVERSAL_NODE.get())
                || state.is(ModMachines.SECOND_UNIVERSAL_NODE.get());
            case FIREBOX -> state.is(ModMachines.FIREBOX.get());
            case CAULDRON -> state.is(Blocks.CAULDRON);
            case OUTSIDE -> false;
        };
    }

    /**
     * Все чанки под кубом структуры загружены? Без этой проверки
     * {@code getBlockState} силой генерирует соседние чанки — на входе в мир
     * это встаёт в очередь генерации и подвешивает загрузку мира.
     */
    public static boolean chunksLoaded(ServerLevel level, BlockPos fireboxPos) {
        // Угловые BlockPos куба покрывают все 1-4 чанка под 3×3 структурой
        // (как у турбины/парогена: hasChunkAt(BlockPos) с блок-координатами).
        return level.hasChunkAt(fireboxPos.offset(-1, 0, -1))
            && level.hasChunkAt(fireboxPos.offset(1, 0, -1))
            && level.hasChunkAt(fireboxPos.offset(-1, 0, 1))
            && level.hasChunkAt(fireboxPos.offset(1, 0, 1));
    }

    /** Пустой ИЛИ заполненный (вода) котёл — заполнять его не обязательно. */
    private static boolean isCauldronFamily(BlockState state) {
        return state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON);
    }

    /**
     * Полная проверка структуры вокруг топки (вызывать только при загруженных
     * чанках). Каулдрон принимается в центре ЛЮБОГО из двух слоёв (чертёж
     * автора: «слой 3 с каулдроном» — либо верх при чтении снизу вверх, либо
     * низ при чтении сверху вниз; принимаем оба, шамот — в противоположном
     * центре), минимум один каулдрон обязателен.
     */
    public static boolean isFormed(ServerLevel level, BlockPos fireboxPos) {
        boolean cauldronSeen = false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockState state = level.getBlockState(fireboxPos.offset(dx, dy, dz));
                    if (BlastFurnaceLayout.isLayerCenter(dx, dy, dz)) {
                        if (isCauldronFamily(state)) { cauldronSeen = true; continue; }
                        if (state.getBlock() instanceof FireclayBlock) continue;
                        return false;
                    }
                    if (!matches(state, BlastFurnaceLayout.roleAt(dx, dy, dz))) return false;
                }
            }
        }
        return cauldronSeen;
    }

    /** Позиции 4 узлов вывода GTH (середины рёбер среднего слоя). */
    public static List<BlockPos> nodePositions(ServerLevel level, BlockPos fireboxPos) {
        List<BlockPos> nodes = new ArrayList<>(4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (BlastFurnaceLayout.isNodeCell(dx, 0, dz)) {
                    nodes.add(fireboxPos.offset(dx, 0, dz));
                }
            }
        }
        return nodes;
    }

    /** Выставить/снять флаг FORMED на всех шамотных блоках структуры. */
    public static void applyFormedFlags(ServerLevel level, BlockPos fireboxPos, boolean formed) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = fireboxPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    // Весь шамот куба, включая центр-слой без каулдрона
                    // (ориентация чертежа не важна).
                    if (!(state.getBlock() instanceof FireclayBlock)) continue;
                    BlockState next = state.setValue(FireclayBlock.FORMED, formed);
                    if (!next.equals(state)) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /**
     * Открыть меню доменной печи кликом по любой шамотной части СОБРАННОЙ
     * структуры: ищет топку в кубе ±1 от места клика. Открытие — строго
     * перегрузкой с BlockPos: она пишет позицию в extra-data буфер, без неё
     * клиентский конструктор меню получает null и роняет соединение
     * (0.3.66: fix ClientboundOpenScreenPacket NPE).
     */
    public static boolean openMenu(Level level, BlockPos clicked, net.minecraft.world.entity.player.Player player) {
        if (level.isClientSide()) return false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = clicked.offset(dx, dy, dz);
                    if (level.getBlockEntity(pos) instanceof FireboxBlockEntity firebox
                        && firebox.isBlastFormed()) {
                        player.openMenu(firebox, firebox.getBlockPos());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Изменилась потенциальная часть структуры (шамот поставлен/сломан и т.п.):
     * сбрасывает кэш сформированности всех топок в кубе ±1 — они перепроверятся
     * в ближайший тик и соберут/разберут структуру (флаги FORMED, режим работы).
     */
    public static void partChanged(Level level, BlockPos changed) {
        if (level.isClientSide() || !(level instanceof ServerLevel)) return;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = changed.offset(dx, dy, dz);
                    if (level.getBlockEntity(pos) instanceof FireboxBlockEntity firebox) {
                        firebox.invalidateBlastCache();
                    }
                }
            }
        }
    }
}
