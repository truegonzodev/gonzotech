package com.gonzotech.chalkboard.notes;

import java.util.ArrayList;
import java.util.List;

/**
 * Статическая модель структуры для иллюстраций «Заметок учёного»
 * (вариант 3 — подстраницы: изо-вид с торца + горизонтальные слои «вид сверху»).
 *
 * <p>Модель — это список заполненных клеток параллелепипепа {@code sizeX×sizeY×sizeZ}
 * (воздух = отсутствующая клетка). Иконки — предметы; {@link #iconScale()} —
 * ЦЕЛОЧИСЛЕННЫЙ масштаб иконки в изо-виде (16, 32, 48 px — без «мыла»: GUI-сэмплинг
 * нейр-нейр, только целые кратные). Шаг сетки при этом GUI выбирает сам, чтобы
 * вся конструкция влезла в панель структуры.
 *
 * <p>Подстраницы: 0 — изо-вид с торца (северо-запад), 1..sizeY — слои снизу вверх
 * (порядок сборки). Клик по блоку — скрыть/показать (общее состояние на все
 * подстраницы); ПКМ по панели — показать всё.
 */
public record StructureModel(int sizeX, int sizeY, int sizeZ, int iconScale, List<StructureBlock> blocks) {

    /** Ключ ячейки: x*4096 + y*64 + z (размеры до 16 блоков). */
    public static long key(int x, int y, int z) {
        return (long) x * 4096L + (long) y * 64L + z;
    }

    /** Число подстраниц: 1 (изо) + по одной на горизонтальный слой. */
    public int subpageCount() {
        return 1 + sizeY;
    }

    /** Клетка (x, y, z) модели, или null — воздух. (record не может иметь
     *  доп. инстанс-полей — ищем по списку; блоков ≤125, вызовы только в GUI). */
    public StructureBlock at(int x, int y, int z) {
        for (StructureBlock b : blocks) {
            if (b.x() == x && b.y() == y && b.z() == z) return b;
        }
        return null;
    }

    /** Топка внизу + котёл на ней (стр. «Топка и котёл»). 3 подстраницы: изо + 2 слоя. */
    public static StructureModel fireboxBoiler() {
        return new StructureModel(1, 2, 1, 3, List.of(
                new StructureBlock(0, 0, 0, "gonzotech:firebox"),
                new StructureBlock(0, 1, 0, "gonzotech:boiler")));
    }

    /**
     * Минимальная турбина 3×3×3 (стр. после турбины) — ровно по валидации
     * {@code TurbineStructure} (внутри — только ротор, оболочка — корпус/порты,
     * порт каждого типа ≥1) и схеме автора: слои снизу вверх —
     *   слой 1: чисто корпуса;
     *   слой 2: [корпус][корпус][узел пара] / [корпус][ротор][корпус] /
     *           [корпус][корпус][узел провода];
     *   слой 3: чисто корпуса.
     * 4 подстраницы: изо + 3 слоя.
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
                    } else if (y == 1 && x == 2 && z == 0) {
                        id = "gonzotech:first_steam_node";
                    } else if (y == 1 && x == 2 && z == 2) {
                        id = "gonzotech:first_wire_node";
                    } else {
                        id = "gonzotech:turbine_casing";
                    }
                    out.add(new StructureBlock(x, y, z, id));
                }
            }
        }
        return new StructureModel(3, 3, 3, 2, List.copyOf(out));
    }
}
