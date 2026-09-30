import com.gonzotech.cleanroom.DoorLeaks;
import com.gonzotech.cleanroom.RoomLedger;
import com.gonzotech.cleanroom.RoomTopology;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Физика открытых гермодверей (0.3.61, автор): дверь на улицу — -8 %/сек;
 * дверь между двумя контурами — выравнивание с сохранением суммы; дверь внутри
 * одной комнаты и дверь «улица-улица» — ничего. Одна дверь между двумя
 * контурами (скрин автора: K1|дверь|K2) обрабатывается один раз.
 */
public class DoorLeaksSelfTest {
    static final Map<RoomTopology.Pos, RoomTopology.Kind> WORLD = new HashMap<>();
    static final RoomLedger LEDGER = new RoomLedger(() -> { });
    static int checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(" #" + checks + ": " + what);
    }

    static RoomTopology.Kind read(RoomTopology.Pos p) {
        return WORLD.getOrDefault(p, RoomTopology.Kind.FORBIDDEN);
    }

    /** Две комнаты 3×3×3, разделённая стена с дверной клеткой-герметиком. */
    static RoomLedger.Room buildPair(RoomTopology.Pos doorCell) {
        Set<RoomTopology.Pos> cellsA = new HashSet<>(), shellA = new HashSet<>();
        Set<RoomTopology.Pos> cellsB = new HashSet<>(), shellB = new HashSet<>();
        for (int x = 0; x <= 6; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 2; z++) {
                    RoomTopology.Pos p = new RoomTopology.Pos(x, y, z);
                    boolean wall = x == 0 || x == 6 || y == 0 || y == 4 || z == 0 || z == 2 || x == 3;
                    boolean door = p.equals(doorCell);
                    WORLD.put(p, wall && !door ? RoomTopology.Kind.SEAL : RoomTopology.Kind.INTERIOR);
                    if (wall) {
                        if (x < 3) shellA.add(p);
                        else shellB.add(p);
                    } else {
                        if (x < 3) cellsA.add(p);
                        else cellsB.add(p);
                    }
                }
            }
        }
        return LEDGER.restore(cellsA, shellA, 100.0);
    }

    public static void main(String[] args) {
        // ── 1. Дверь между двумя контурами: выравнивание, сумма сохраняется ──
        RoomTopology.Pos door = new RoomTopology.Pos(3, 2, 1);
        RoomLedger.Room a = buildPair(door);
        Set<RoomTopology.Pos> cellsB = new HashSet<>(), shellB = new HashSet<>();
        for (int x = 4; x <= 5; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 1; z++) {
                    cellsB.add(new RoomTopology.Pos(x, y, z));
                    WORLD.put(new RoomTopology.Pos(x, y, z), RoomTopology.Kind.INTERIOR);
                }
            }
        }
        for (RoomTopology.Pos p : List.of(door)) shellB.add(p);
        RoomLedger.Room b = LEDGER.restore(cellsB, shellB, 0.0);
        double sum0 = a.quality() + b.quality();
        DoorLeaks.settle(LEDGER, List.of(new DoorLeaks.Door(a, b)));
        check(a.quality() < 100.0 && b.quality() > 0.0,
                "выравнивание: A=" + a.quality() + " B=" + b.quality());
        check(Math.abs((a.quality() + b.quality()) - sum0) < 1e-9,
                "сумма сохраняется: " + (a.quality() + b.quality()));
        double gap0 = 100.0;
        double gap1 = a.quality() - b.quality();
        check(Math.abs(gap1 - gap0 * (1 - 2 * DoorLeaks.EQUALIZE_PER_SECOND)) < 1e-9,
                "за секунду разница падает на 16% (по 8% с каждой стороны): " + gap1);
        for (int i = 0; i < 60; i++) DoorLeaks.settle(LEDGER, List.of(new DoorLeaks.Door(a, b)));
        check(Math.abs(a.quality() - b.quality()) < 1.0,
                "через минуту почти равны: " + a.quality() + "/" + b.quality());

        // ── 2. Дверь на улицу: -8 %/сек ──
        RoomLedger.Room solo = LEDGER.restore(
                Set.of(new RoomTopology.Pos(10, 1, 1)),
                Set.of(new RoomTopology.Pos(9, 1, 1), new RoomTopology.Pos(11, 1, 1),
                        new RoomTopology.Pos(10, 0, 1), new RoomTopology.Pos(10, 2, 1),
                        new RoomTopology.Pos(10, 1, 0), new RoomTopology.Pos(10, 1, 2)),
                50.0);
        DoorLeaks.settle(LEDGER, List.of(new DoorLeaks.Door(solo, null)));
        check(Math.abs(solo.quality() - 42.0) < 1e-9,
                "улица: 50 - 8 = 42, факт " + solo.quality());
        DoorLeaks.settle(LEDGER, List.of(new DoorLeaks.Door(null, solo)));
        check(Math.abs(solo.quality() - 34.0) < 1e-9, "стороны симметричны: " + solo.quality());

        // ── 3. Дверь внутри одной комнаты: нет-оп; улица-улица: нет-оп ──
        double before = solo.quality();
        DoorLeaks.settle(LEDGER, List.of(new DoorLeaks.Door(solo, solo)));
        check(Math.abs(solo.quality() - before) < 1e-9, "дверь внутри комнаты не теряет");
        DoorLeaks.settle(LEDGER, List.of(new DoorLeaks.Door(null, null)));
        check(Math.abs(solo.quality() - before) < 1e-9, "дверь улица-улица ничего не делает");

        // ── 4. Поверх клампов: утечка не уводит ниже 0 ──
        for (int i = 0; i < 10; i++) DoorLeaks.settle(LEDGER, List.of(new DoorLeaks.Door(solo, null)));
        check(solo.quality() == 0.0, "кламп в нуле: " + solo.quality());

        System.out.println("DoorLeaks: " + checks + " проверок: -8%/сек на улицу, "
                + "выравнивание контуров с сохранением суммы, нет-опы, кламп");
    }
}
