package com.gonzotech.machines;

import com.gonzotech.machines.blastfurnace.BlastFurnaceLayout;
import com.gonzotech.machines.blastfurnace.BlastFurnaceStructure;
import com.gonzotech.machines.block.entity.FireboxBlockEntity;
import com.gonzotech.machines.litho.SiliconFactoryBlockEntity;
import com.gonzotech.machines.litho.SiliconFactoryStructure;
import com.gonzotech.machines.network.PipeCarrier;
import com.gonzotech.machines.reactor.ReactorStructure;
import com.gonzotech.machines.steamgen.SteamGenStructure;
import com.gonzotech.machines.turbine.TurbineStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.3.335 (автор): «вшитый корпусный порт» — узел/труба, которая стоит в
 * ОБОЛОЧКЕ сформированного мультиблока. Такой блок работает точкой приёма/
 * выдачи самой структуры, а не самостоятельным узлом сети: он не сшивается
 * ни с соседними портами корпуса, ни с узлами снаружи.
 *
 * <p>Проверка строго серверная (индексы членов живут в ServerLevel) и
 * вызывается только из ответа на адресный запрос HUD ключа. Клиент сам
 * решает, какой заголовок показать, по полученному ownerId: до ответа
 * классическая строка не рисуется вовсе.</p>
 *
 * <p>Политика САМИХ РЕЖИМОВ вшитого порта (AUTO/PULL/PUSH, «обе стороны
 * делят общую проводимость порта») в 0.3.335 НЕ реализована: см.
 * {@code D-PORTMODE1} в CURRENT-STATE (активная работа после приёмки)
 * и заглушки вызовов в PipeRouting. Здесь
 * определяется только факт принадлежности порта к сборке.</p>
 */
public final class FormedPorts {

    /** 0 = обычный узел; 1..5 — хозяин вшитого порта. */
    public static final int OWNER_NONE = 0;
    public static final int OWNER_REACTOR = 1;
    public static final int OWNER_TURBINE = 2;
    public static final int OWNER_STEAMGEN = 3;
    public static final int OWNER_BLAST_FURNACE = 4;
    public static final int OWNER_SILICON_FACTORY = 5;

    private FormedPorts() {
    }

    /** Позиция — порт одной из сформированных структур? */
    public static boolean isEmbeddedPort(ServerLevel level, BlockPos pos, BlockState state) {
        return ownerId(level, pos, state) != OWNER_NONE;
    }

    /**
     * 0.3.335 (приёмка автора): сервер отдаёт НЕ только флаг «вшитый/обычный»,
     * но и владельца сборки. Это позволяет клиенту один раз закэшировать
     * ГОСТ-подсказку по позиции и больше не подменять классическую строку
     * «после проверок».
     */
    public static int ownerId(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PipeCarrier)) return OWNER_NONE;
        if (ReactorStructure.isMember(level, pos)) return OWNER_REACTOR;
        if (TurbineStructure.isMember(level, pos)) return OWNER_TURBINE;
        if (SteamGenStructure.isMember(level, pos)) return OWNER_STEAMGEN;
        // Доменная печь: узлы вывода стоят только в среднем слое (dy=0) и
        // только в четырёх ячейках isNodeCell; перебираем ровно 4 кандидата
        // топки, а не все 27 клеток куба (запрос HUD приходит каждые 5 тиков).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!BlastFurnaceLayout.isNodeCell(dx, 0, dz)) continue;
                BlockPos fireboxPos = pos.offset(-dx, 0, -dz);
                if (level.getBlockEntity(fireboxPos) instanceof FireboxBlockEntity firebox
                    && firebox.isBlastFormed()
                    && BlastFurnaceStructure.nodePositions(level, fireboxPos).contains(pos)) {
                    return OWNER_BLAST_FURNACE;
                }
            }
        }
        // Литографическая фабрика: позиция в индексе сформированного контроллера.
        BlockPos root = SiliconFactoryStructure.controllerAt(level, pos);
        if (root != null
            && level.getBlockEntity(root) instanceof SiliconFactoryBlockEntity controller
            && controller.isFormed()) {
            return OWNER_SILICON_FACTORY;
        }
        return OWNER_NONE;
    }
}
