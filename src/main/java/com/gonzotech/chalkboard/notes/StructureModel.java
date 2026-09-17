package com.gonzotech.chalkboard.notes;

import java.util.ArrayList;
import java.util.List;

/**
 * Статическая модель структуры для иллюстраций «Заметок учёного»
 * (вариант 3 — подстраницы; изометрия ОТМЕНЕНА).
 *
 * <p>Модель — список заполненных клеток параллелепипепа {@code sizeX×sizeY×sizeZ}
 * (воздух = отсутствующая клетка). Оси как в MC: x → вправо (восток), y → вверх,
 * z → «на зрителя» (юг).
 *
 * <p>Подстраницы:
 * <ul>
 *   <li>0 — <b>ВИД СПЕРЕДИ</b> (анфас, z=0-плоскость): передний блок каждой
 *       колонки (x, y) — плоская сетка 16px, ВПРИТИРКУ (без гэпов),
 *       «просто как оно выглядит собранное»;</li>
 *   <li>1..sizeY — <b>слои «вид сверху»</b> (только если {@link #layers()}) —
 *       снизу вверх, сетка 16px с гэпом 2px (гэпы подчёркивают структурность).</li>
 * </ul>
 * Клик по блоку — скрыть/показать (общее состояние на все подстраницы);
 * ПКМ по панели — показать всё.
 */
public record StructureModel(int sizeX, int sizeY, int sizeZ, boolean layers, List<StructureBlock> blocks) {

    /** Ключ ячейки: x*4096 + y*64 + z (размеры до 16 блоков). */
    public static long key(int x, int y, int z) {
        return (long) x * 4096L + (long) y * 64L + z;
    }

    /** Число подстраниц: 1 (вид спереди) + слои, если {@link #layers()}. */
    public int subpageCount() {
        return 1 + (layers ? sizeY : 0);
    }

    /** Клетка (x, y, z) модели, или null — воздух. (record не может иметь
     *  доп. инстанс-полей — ищем по списку; блоков ≤125, вызовы только в GUI). */
    public StructureBlock at(int x, int y, int z) {
        for (StructureBlock b : blocks) {
            if (b.x() == x && b.y() == y && b.z() == z) return b;
        }
        return null;
    }

    /** Топка внизу + котёл на ней (стр. «Топка и котёл»).
     *  ОДНА подстраница (без слоёв): вид спереди — [котёл] над [топкой]. */
    public static StructureModel fireboxBoiler() {
        return new StructureModel(1, 2, 1, false, List.of(
                new StructureBlock(0, 0, 0, "gonzotech:firebox"),
                new StructureBlock(0, 1, 0, "gonzotech:boiler")));
    }

    /**
     * Минимальная турбина 3×3×3 (стр. после турбины) — ровно по валидации
     * {@code TurbineStructure} (внутри — только ротор, оболочка — корпус/порты,
     * порт каждого типа ≥1) и схеме автора. Вид спереди (z=0-плоскость):
     *   [К][К][К]
     *   [УЗ1][К][УЗ2]   (УЗ1 = паровой узел, УЗ2 = узел провода — на передней
     *   [К][К][К]        грани, средний ряд; оба порта — внешние клетки)
     * Слои: 1 и 3 — чисто корпуса; 2 — [УЗ1][К][УЗ2] / [К][ротор][К] / [К][К][К].
     * 4 подстраницы: спереди + 3 слоя.
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
        return new StructureModel(3, 3, 3, true, List.copyOf(out));
    }
}
