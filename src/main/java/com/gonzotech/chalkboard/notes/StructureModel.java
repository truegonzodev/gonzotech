package com.gonzotech.chalkboard.notes;

import java.util.ArrayList;
import java.util.List;

/**
 * Статическая модель структуры для иллюстраций «Заметок учёного»
 * (только слои; изометрия и «вид спереди» отменены — автор, 2-й заход).
 *
 * <p>Модель — список заполненных клеток параллелепипепа {@code sizeX×sizeY×sizeZ}
 * (воздух = отсутствующая клетка). Оси как в MC: x → вправо (восток), y → вверх,
 * z → «на зрителя» (юг).
 *
 * <p>Подстраницы:
 * <ul>
 *   <li>{@code assembly=true} — ОДНА подстраница «Сборка (1)»: вид спереди модели
 *       (передний блок каждой колонки (x, y)) на общей сетке С ГЭПОМ;</li>
 *   <li>иначе — {@code sizeY} подстраниц-СЛОЁВ «вид сверху», снизу вверх:
 *       «Нижний слой (1)», «Средний слой (2)», «Верхний слой (3)» — сетка
 *       с гэпом (гэпы подчёркивают структурность).</li>
 * </ul>
 * Иконки — предметы, размер = GUI-масштаб + 1 (автор: «увеличить на +1 от
 * размера интерфейса»), тултипы на ховере. Скрытие/показ блоков УБРАН —
 * видим только единичные слои.
 */
public record StructureModel(int sizeX, int sizeY, int sizeZ, boolean assembly, List<StructureBlock> blocks) {

    /** Ключ ячейки: x*4096 + y*64 + z (размеры до 16 блоков). */
    public static long key(int x, int y, int z) {
        return (long) x * 4096L + (long) y * 64L + z;
    }

    /** Число подстраниц: 1 («Сборка») или sizeY (слои). */
    public int subpageCount() {
        return assembly ? 1 : sizeY;
    }

    /** Клетка (x, y, z) модели, или null — воздух. (record не может иметь
     *  доп. инстанс-полей — ищем по списку; блоков ≤125, вызовы только в GUI). */
    public StructureBlock at(int x, int y, int z) {
        for (StructureBlock b : blocks) {
            if (b.x() == x && b.y() == y && b.z() == z) return b;
        }
        return null;
    }

    /** Передний (минимальный z) блок колонки (x, y) — для вида «Сборка». */
    public StructureBlock front(int x, int y) {
        for (int z = 0; z < sizeZ; z++) {
            StructureBlock b = at(x, y, z);
            if (b != null) return b;
        }
        return null;
    }

    /** Топка внизу + котёл на ней (стр. «Топка и котёл»).
     *  Одна подстраница «Сборка (1)» — котёл над топкой на общей сетке
     *  с гэпом, как слои турбины. */
    public static StructureModel fireboxBoiler() {
        return new StructureModel(1, 2, 1, true, List.of(
                new StructureBlock(0, 0, 0, "gonzotech:firebox"),
                new StructureBlock(0, 1, 0, "gonzotech:boiler")));
    }

    /**
     * Минимальная турбина 3×3×3 (стр. после турбины) — 3 подстраницы-СЛОЯ
     * (снизу вверх), ровно по валидации {@code TurbineStructure} (внутри —
     * только ротор, оболочка — корпус/порты, порт каждого типа ≥1):
     * <pre>
     *   Нижний слой (1):  [К][К][К]
     *                     [К][К][К]
     *                     [К][К][К]
     *   Средний слой (2): [УЗ пара][К][УЗ провода]   (узлы — на передней
     *                     [К][ротор][К]               грани z=0, внешние клетки)
     *                     [К][К][К]
     *   Верхний слой (3): [К][К][К]
     *                     [К][К][К]
     *                     [К][К][К]
     * </pre>
     */
    public static StructureModel turbineMinimum() {
        List<StructureBlock> out = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    boolean outer = x == 0 || x == 2 || y == 0 || y == 2 || z == 0 || z == 2;
                    String id;
                    if (!outer) {
                        id = "gonzotech:turbine_rotor";
                    } else if (z == 0 && y == 1 && x == 0) {
                        id = "gonzotech:first_steam_node";
                    } else if (z == 0 && y == 1 && x == 2) {
                        id = "gonzotech:first_wire_node";
                    } else {
                        id = "gonzotech:turbine_casing";
                    }
                    out.add(new StructureBlock(x, y, z, id));
                }
            }
        }
        return new StructureModel(3, 3, 3, false, List.copyOf(out));
    }
}
