import com.gonzotech.cleanroom.RoomLedger;
import com.gonzotech.cleanroom.RoomTopology;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Сценарий автора (0.3.50/0.3.51): телифон игрока показывает 100%, машина —
 * 0%/36%; в комнате работают 10 фильтров. Причина: filterRoom от позиции
 * контроллера видит только «шкаф» {резина, контроллер} внутри собственной
 * оболочки машины (сверху потолок, снизу пол, вокруг фарфор/стекло) — отдельную
 * комнату с качеством 0, недостижимую для фильтров. Фикс 0.3.51: сформированная
 * машина резолвит комнату по внешнему воздуху вокруг всей коробки (roomAround).
 * Реплицирует CleanRoomSystem/AirFilterBlockEntity/SiliconFactoryBlockEntity
 * без Minecraft-классов (формулы зафиксированы строковыми пинами в питон-сьюте;
 * чистота игрока — локальная реплика Cleanliness с тем же зажимом 0..100).
 */
public class LithoRoomLinkSelfTest {
    /** Реплика Cleanliness: зажим 0..100 (пин в питон-сьюте). */
    static final class Dirt {
        private double value;
        double v() { return value; }
        void set(double v) { value = Double.isFinite(v) ? Math.max(0.0, Math.min(100.0, v)) : 0.0; }
    }

    static final Map<RoomTopology.Pos, RoomTopology.Kind> WORLD = new HashMap<>();
    static final RoomLedger LEDGER = new RoomLedger(() -> { });
    static final Dirt PLAYER = new Dirt();

    static RoomTopology.Pos machineCtrl;   // контроллер машины (верхний центр коробки)
    static final Set<RoomTopology.Pos> MEMBERS = new HashSet<>();
    static RoomTopology.Pos filterWall;    // фильтр в стене комнаты (SEAL)
    static RoomTopology.Pos cleanerCell;   // клетка с потоком очистителя
    static RoomTopology.Pos feet;          // ноги игрока

    static RoomLedger.Room filterRoom;
    static boolean cleanerActive = true;
    static int beHundredths = -1;

    static int checks = 0;

    static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(" #" + checks + ": " + message);
        }
    }

    static RoomTopology.Kind read(RoomTopology.Pos pos) {
        return WORLD.getOrDefault(pos, RoomTopology.Kind.FORBIDDEN);
    }

    static RoomTopology.Pos[] neighborsOf(RoomTopology.Pos pos) {
        return new RoomTopology.Pos[]{
                pos.offset(0, -1, 0), pos.offset(0, 1, 0), pos.offset(0, 0, -1),
                pos.offset(0, 0, 1), pos.offset(-1, 0, 0), pos.offset(1, 0, 0)
        };
    }

    /** Реплика CleanRoomSystem.filterRoom (0.3.50 и раньше для машины). */
    static RoomLedger.Room oldRoomOf(RoomTopology.Pos pos, long tick) {
        RoomLedger.Room found = null;
        for (RoomTopology.Pos next : neighborsOf(pos)) {
            if (read(next) != RoomTopology.Kind.INTERIOR) continue;
            RoomLedger.Room candidate = LEDGER.find(LithoRoomLinkSelfTest::read, next, tick);
            if (candidate == null) continue;
            if (found != null && found != candidate) return null;
            found = candidate;
        }
        return found;
    }

    /**
     * Реплика SiliconFactoryBlockEntity.roomAround (0.3.55): заливка из клетки
     * контроллера; объём, не покинувший коробку, = замурован (ambient).
     */
    static RoomLedger.Room roomAround(long tick, java.util.Set<RoomTopology.Pos> members) {
        java.util.ArrayDeque<RoomTopology.Pos> queue = new java.util.ArrayDeque<>();
        java.util.HashSet<RoomTopology.Pos> seen = new java.util.HashSet<>();
        for (RoomTopology.Pos m : members) {
            if (read(m) == RoomTopology.Kind.INTERIOR) { seen.add(m); queue.add(m); }
        }
        RoomTopology.Pos entry = null;
        while (!queue.isEmpty()) {
            RoomTopology.Pos cur = queue.poll();
            for (RoomTopology.Pos next : neighborsOf(cur)) {
                if (!seen.add(next)) continue;
                RoomTopology.Kind kind = read(next);
                if (kind == RoomTopology.Kind.FORBIDDEN) return null; // открытый воздух
                if (kind != RoomTopology.Kind.INTERIOR) continue;
                if (!members.contains(next) && entry == null) entry = next;
                queue.add(next);
            }
        }
        if (entry == null) return null;
        return LEDGER.find(LithoRoomLinkSelfTest::read, entry, tick);
    }

    static RoomLedger.Room roomAround(long tick) {
        return roomAround(tick, MEMBERS);
    }

    /** Реплика 0.3.51-0.3.54: по-фасадная заливка всех внешних INTERIOR-граней. */
    static RoomLedger.Room oldFacesRoomOf(java.util.Set<RoomTopology.Pos> members, long tick) {
        RoomLedger.Room found = null;
        for (RoomTopology.Pos cell : members) {
            for (RoomTopology.Pos next : neighborsOf(cell)) {
                if (members.contains(next)) continue;
                if (read(next) != RoomTopology.Kind.INTERIOR) continue;
                RoomLedger.Room candidate = LEDGER.find(LithoRoomLinkSelfTest::read, next, tick);
                if (candidate == null) continue;
                if (found != null && found != candidate) return null;
                found = candidate;
            }
        }
        return found;
    }

    /** Реплика тика машины: чтение комнаты + сотые процента. */
    static void machineTick(long tick) {
        RoomLedger.Room room = roomAround(tick);
        beHundredths = room == null ? -1 : (int) Math.round(room.quality() * 100);
    }

    /** Реплика тика фильтра: +QUALITY_PER_TICK работающей комнате. */
    static void filterTick(long tick) {
        if (filterRoom != null || tick % 20 == 0) {
            filterRoom = oldRoomOf(filterWall, tick);
        }
        if (filterRoom != null) {
            LEDGER.adjust(filterRoom, 0.2 / 20);
        }
    }

    /** Реплика CleanRoomSystem.onLevelTick (раз в секунду, здесь каждый тик цикла). */
    static void playerTick(long tick) {
        if (isInCleanerStream()) {
            PLAYER.set(PLAYER.v() - 11.5);
            return;
        }
        RoomLedger.Room room = LEDGER.find(LithoRoomLinkSelfTest::read, feet, tick);
        if (room == null) {
            PLAYER.set(PLAYER.v() + 5.0);
        } else {
            double before = PLAYER.v();
            PLAYER.set(before - 1.0);
            LEDGER.adjust(room, -(before - PLAYER.v()) * 3.0);
        }
    }

    static boolean isInCleanerStream() {
        return cleanerActive && cleanerCell.equals(feet);
    }

    /** Формула из SiliconFactoryBlockEntity.rejectPercent (копия, пин в питоне). */
    static double rejectPercent(double qualityPercent) {
        if (qualityPercent < 0) return 26.0;
        if (qualityPercent >= 100) return 1.0;
        if (qualityPercent >= 50) return 9.0 + (qualityPercent - 50) * (1.0 - 9.0) / 50.0;
        if (qualityPercent >= 10) return 24.0 + (qualityPercent - 10) * (9.0 - 24.0) / 40.0;
        return 36.0 + qualityPercent * (24.0 - 36.0) / 10.0;
    }

    /** Строка тултипа шага: сотые → проценты один раз (/100f), брак из тех же сотых. */
    static String tooltip() {
        if (beHundredths < 0) {
            return "ambient/" + Math.round(rejectPercent(-1)) + "%";
        }
        int percent = Math.round(beHundredths / 100f);
        int chance = Math.round((float) rejectPercent(beHundredths / 100.0));
        return percent + "%/" + chance + "%";
    }

    static void simulate(long ticks) {
        for (long i = 0; i < ticks; i++) {
            long tick = i + 1;
            machineTick(tick);
            filterTick(tick);
            if (feet != null) playerTick(tick);
        }
    }

    public static void main(String[] args) {
        buildWorld();

        // ── 0. БАГ-РЕПОРТ АВТОРА: старый резолвер (filterRoom от контроллера) ──
        simulate(11_000); // фильтр (поиск комнаты раз в секунду, +0.2%/с) поднимает большую комнату до 100%
        RoomLedger.Room bigRoom = LEDGER.find(LithoRoomLinkSelfTest::read,
                new RoomTopology.Pos(1, 2, 1), 11_000);
        RoomLedger.Room closet = oldRoomOf(machineCtrl, 11_000);
        check(bigRoom != null && Math.round(bigRoom.quality() * 100) == 10_000,
                "большая комната (игрок/телифон/фильтры) обязана быть 100%, факт: "
                        + (bigRoom == null ? "null" : bigRoom.quality()));
        check(closet != null && closet != bigRoom && Math.round(closet.quality() * 100) == 0,
                "старый резолвер обязан находить отдельный «шкаф» с качеством 0 — "
                        + "вот почему машина показывала 0% при 100% у телифона");

        // ── 1. ФИКС: roomAround видит ту же комнату, что игрок и фильтры ──
        simulate(20);
        check("100%/1%".equals(tooltip()),
                "после фикса: тултип " + tooltip() + ", ожидался 100%/1%");
        check(Math.abs(rejectPercent(beHundredths / 100.0) - 1.0) < 1e-9,
                "брак по качеству 100 должен быть 1%");

        // ── 1б. Узел прямо на крыше станка (билд автора): комната сбрасывается
        // правилом чистых комнат (SEAL в воздухе), но машина ОБЯЗАНА видеть её же
        // и показывать рост качества, а не «обычный» (баг 0.3.54). ──
        RoomTopology.Pos nodeCell = machineCtrl.offset(0, 1, 0);
        WORLD.put(nodeCell, RoomTopology.Kind.SEAL);
        LEDGER.invalidate(nodeCell);
        simulate(3_000); // фильтры заново поднимают комнату (~55%)
        int mid = beHundredths;
        check(mid > 2_000 && mid < 9_000,
                "узел на крыше: машина обязана видеть растущую комнату, сотые " + mid);
        simulate(8_000);
        check("100%/1%".equals(tooltip()),
                "после восстановления: тултип " + tooltip() + ", ожидался 100%/1%");

        // ── 2. Игрок входит с уличной грязью 100: машина и комната падают ВМЕСТЕ ──
        feet = new RoomTopology.Pos(1, 2, 1);
        PLAYER.set(100.0);
        simulate(40); // слив -3.0/тик при грязи 100 -> 0 примерно за 34 тика
        check("0%/36%".equals(tooltip()),
                "стоя у GUI с грязью 100: тултип " + tooltip() + ", ожидался 0%/36% "
                        + "(слив -3.0 качества за единицу грязи за тик)");
        check(Math.abs(rejectPercent(beHundredths / 100.0) - 36.0) < 1e-9,
                "брак по качеству 0 должен быть 36% (опорная точка автора 0.3.42)");

        // ── 3. Игрок ушёл: фильтр поднимает комнату обратно (0.2%/с) ──
        feet = null;
        simulate(10_000);
        check("100%/1%".equals(tooltip()),
                "после ухода игрока: тултип " + tooltip() + ", ожидался 100%/1%");

        // ── 4. Вход через поток очистителя: грязь сгорает ДО подхода к машине ──
        cleanerActive = true;
        feet = cleanerCell;
        PLAYER.set(100.0);
        simulate(15); // в потоке грязь -11.5/тик: 100 -> 0 за 9 тиков
        check(PLAYER.v() == 0.0, "очиститель должен сжечь грязь в 0: " + PLAYER.v());
        feet = new RoomTopology.Pos(1, 2, 1);
        simulate(100);
        check(beHundredths > 9_000,
                "вход через очиститель сохраняет воздух: сотые " + beHundredths);

        // ── 4б. Станок замурован в стену: одна грань — в герметичный погреб 0%,
        // другая — на открытый воздух (баг 0.3.54: пофасадный резолвер показывал
        // 0% погреба; новый обязан сказать «обычный» — улица важнее кармана). ──
        java.util.Set<RoomTopology.Pos> streetMembers = new java.util.HashSet<>();
        String[] low = {"D", "R", "D", "R", "C", "R", "D", "R", "D"};
        String[] up = {"R", "B", "R", "B", "S", "B", "R", "B", "R"};
        for (int i = 0; i < 9; i++) {
            int dx = i % 3, dz = i / 3;
            putStreet(streetMembers, 20 + dx, 1, 2 + dz, low[i]);
            putStreet(streetMembers, 20 + dx, 2, 2 + dz, up[i]);
        }
        // Погреб: 2 клетки западнее угла D(20,1,2), стены явные (боковые стены
        // не прилегают к коробке — общий цикл стен их не достаёт).
        WORLD.put(new RoomTopology.Pos(19, 1, 2), RoomTopology.Kind.INTERIOR);
        WORLD.put(new RoomTopology.Pos(18, 1, 2), RoomTopology.Kind.INTERIOR);
        int[][] pocketWalls = {{18,0,2},{18,2,2},{18,1,1},{18,1,3},{19,0,2},{19,2,2},{19,1,1},{19,1,3},{17,1,2}};
        for (int[] w : pocketWalls) WORLD.put(new RoomTopology.Pos(w[0], w[1], w[2]), RoomTopology.Kind.SEAL);
        // Все не-членские клетки вокруг коробки — SEAL, КРОМЕ погреба (19,1,2)
        // и улицы (23,1,2) восточнее угла D(22,1,2).
        for (RoomTopology.Pos m : streetMembers) {
            for (RoomTopology.Pos n : neighborsOf(m)) {
                if (streetMembers.contains(n)) continue;
                if (n.equals(new RoomTopology.Pos(19, 1, 2))) continue;
                if (n.equals(new RoomTopology.Pos(23, 1, 2))) continue;
                if (read(n) == RoomTopology.Kind.FORBIDDEN) WORLD.put(n, RoomTopology.Kind.SEAL);
            }
        }
        RoomTopology.Pos p1 = new RoomTopology.Pos(19, 1, 2);
        for (RoomTopology.Pos n : neighborsOf(p1)) {
            System.out.println("DBG P1 neighbor " + n + " = " + read(n));
        }
        for (RoomTopology.Pos n : neighborsOf(new RoomTopology.Pos(18, 1, 2))) {
            System.out.println("DBG P2 neighbor " + n + " = " + read(n));
        }
        for (RoomTopology.Pos n : neighborsOf(new RoomTopology.Pos(20, 1, 2))) {
            System.out.println("DBG D20 neighbor " + n + " = " + read(n));
        }
        RoomLedger.Room probeP1 = LEDGER.find(LithoRoomLinkSelfTest::read, p1, 1);
        System.out.println("DBG find(P1)=" + (probeP1 == null ? "null" : ("q=" + probeP1.quality() + " cells=" + probeP1.cells().size())));
        for (RoomTopology.Pos m : streetMembers) {
            for (RoomTopology.Pos n : neighborsOf(m)) {
                if (streetMembers.contains(n)) continue;
                if (read(n) == RoomTopology.Kind.INTERIOR) {
                    RoomLedger.Room c = LEDGER.find(LithoRoomLinkSelfTest::read, n, 1);
                    System.out.println("DBG face " + n + " -> " + (c == null ? "null" : ("q=" + c.quality() + " cells=" + c.cells().size())));
                }
            }
        }
        RoomLedger.Room junk = oldFacesRoomOf(streetMembers, 1);
        check(junk != null && Math.round(junk.quality() * 100) == 0,
                "по-фасадный резолвер 0.3.54 обязан был показывать погреб 0% (баг), факт: "
                        + (junk == null ? "null" : junk.quality()));
        RoomLedger.Room streetRoom = roomAround(1, streetMembers);
        check(streetRoom == null,
                "станок улица+погреб обязан быть «обычный», а не "
                        + (streetRoom == null ? "null" : streetRoom.quality()));

        // ── 5. Замурованная машина: заливка не покинула коробку — честный «обычный» ──
        feet = null;
        sealAroundBox();
        simulate(20);
        check("ambient/26%".equals(tooltip()),
                "замурованная машина: тултип " + tooltip() + ", ожидался ambient/26%");

        System.out.println("LithoRoomLink: " + checks + " проверок: шкаф-баг воспроизведён, "
                + "узел на крыше не слепит машину, уличный станок у кармана = обычный, "
                + "замурованная машина = обычный; тултип=ledger=бросок брака");
    }

    /** Замуровываем машину: все не-членские клетки вокруг коробки становятся SEAL. */
    static void sealAroundBox() {
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                for (int y = 1; y <= 2; y++) {
                    sealIfNotMember(x, y, z);
                }
            }
        }
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                sealIfNotMember(x, 3, z); // верх коробки, кроме уже стоящей полки
            }
        }
    }

    static void sealIfNotMember(int x, int y, int z) {
        RoomTopology.Pos pos = new RoomTopology.Pos(x, y, z);
        if (MEMBERS.contains(pos)) return;
        WORLD.put(pos, RoomTopology.Kind.SEAL);
    }

    /**
     * Комната 5×3×5 (стены-фарфор SEAL), внутри — коробка машины 3×2×3
     * (раскладка варианта 1) и полка-потолок прямо над контроллером:
     * ровно билд автора со скриншота.
     */
    static void buildWorld() {
        int minX = 0, minY = 0, minZ = 0, maxX = 6, maxY = 4, maxZ = 6;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean wall = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    WORLD.put(new RoomTopology.Pos(x, y, z),
                            wall ? RoomTopology.Kind.SEAL : RoomTopology.Kind.INTERIOR);
                }
            }
        }
        // Коробка машины x2..4, z2..4, y1..2: нижний слой D R D / R C R / D R D,
        // верхний R B R / B S B / R B R. D/C/S — INTERIOR (оборудование), R/B — SEAL.
        String[] lower = {"D", "R", "D", "R", "C", "R", "D", "R", "D"};
        String[] upper = {"R", "B", "R", "B", "S", "B", "R", "B", "R"};
        for (int i = 0; i < 9; i++) {
            int dx = i % 3, dz = i / 3;
            place(2 + dx, 1, 2 + dz, lower[i]);
            place(2 + dx, 2, 2 + dz, upper[i]);
        }
        machineCtrl = new RoomTopology.Pos(3, 2, 3);
        // Полка-потолок прямо над контроллером (SEAL): билд автора со скриншота.
        WORLD.put(new RoomTopology.Pos(3, 3, 3), RoomTopology.Kind.SEAL);
        // Фильтр в стене (SEAL): соседи по интерьеру видны с одной стороны.
        filterWall = new RoomTopology.Pos(0, 2, 4);
        WORLD.put(filterWall, RoomTopology.Kind.SEAL);
        // Клетка потока очистителя в боковом проходе.
        cleanerCell = new RoomTopology.Pos(1, 1, 1);
    }

    static void putStreet(java.util.Set<RoomTopology.Pos> members, int x, int y, int z, String code) {
        RoomTopology.Pos pos = new RoomTopology.Pos(x, y, z);
        members.add(pos);
        WORLD.put(pos, "R".equals(code) || "B".equals(code)
                ? RoomTopology.Kind.SEAL
                : RoomTopology.Kind.INTERIOR);
    }

    static void place(int x, int y, int z, String code) {
        RoomTopology.Pos pos = new RoomTopology.Pos(x, y, z);
        MEMBERS.add(pos);
        WORLD.put(pos, "R".equals(code) || "B".equals(code)
                ? RoomTopology.Kind.SEAL
                : RoomTopology.Kind.INTERIOR);
    }
}
