package com.gonzotech.machines.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Метка-вымпел автошахтера (0.3.256, автор). Не блок — waypoint-рендер:
 * квад 1,0×1,75 блока (текстура 20×35), «слот» дисплея 14×14 на канве в пикселе (3,3).
 * Дисплей — красный флажок (marker_flag.png) либо изометрия блока руды;
 * несколько руд листаются по 3 секунды на руду.
 *
 * @param id             идентификатор метки (внутри {@link MarkerService})
 * @param pos            блок, в который «воткнут» вымпел (остриё снизу квада)
 * @param kind           назначение метки
 * @param expiresAtTick  gameTime истечения; 0 — бессрочно
 * @param ores           найденные руды (листание по 3 с); пусто — флажок
 */
public record MinerMarker(long id, BlockPos pos, Kind kind, long expiresAtTick, List<Item> ores) {

    public enum Kind {
        /** Площадка сейсмической установки (разметка Плотника). */
        STATION,
        /** Точка бурения / найденная руда. */
        ORE,
        /** Тестовая метка команды /gonzotech debug marker. */
        TEST
    }

    public MinerMarker {
        ores = List.copyOf(ores);
    }
}
