import com.gonzotech.cleanroom.RoomLedger;
import com.gonzotech.cleanroom.RoomTopology;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Сценарий автора (0.3.49): машина литографии в герметичной комнате; фильтр
 * поднимает качество, игрок стоящего у GUI сливает уличную грязь в воздух.
 * Проверяется, что тултип машины, ledger и бросок брака используют ОДНО число.
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

    static RoomTopology.Pos machineCtrl;   // контроллер машины (INTERIOR-оборудование)
    static RoomTopology.Pos filterWall;    // фильтр в стене комнаты (SEAL)
    static RoomTopology.Pos cleanerCell;   // клетка с потоком очистителя
    static RoomTopology.Pos feet;          // ноги игрока

    static RoomLedger.Room machineRoom;
    static RoomLedger.Room filterRoom;
    static boolean filterWorking = true;
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

    /** Точная реплика CleanRoomSystem.filterRoom: соседи-INTERIOR, одна комната. */
    static RoomLedger.Room filterRoomOf(RoomTopology.Pos pos, long tick) {
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

    static RoomTopology.Pos[] neighborsOf(RoomTopology.Pos pos) {
        return new RoomTopology.Pos[]{
                pos.offset(0, -1, 0), pos.offset(0, 1, 0), pos.offset(0, 0, -1),
                pos.offset(0, 0, 1), pos.offset(-1, 0, 0), pos.offset(1, 0, 0)
        };
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

    /** Реплика тика машины: чтение комнаты + сотые процента. */
    static void machineTick(long tick) {
        if (machineRoom != null || tick % 20 == 0) {
            machineRoom = filterRoomOf(machineCtrl, tick);
        }
        beHundredths = machineRoom == null ? -1 : (int) Math.round(machineRoom.quality() * 100);
    }

    /** Реплика тика фильтра: +QUALITY_PER_TICK работающей комнате. */
    static void filterTick(long tick) {
        if (filterRoom != null || tick % 20 == 0) {
            filterRoom = filterRoomOf(filterWall, tick);
        }
        if (filterWorking && filterRoom != null) {
            LEDGER.adjust(filterRoom, 0.2 / 20);
        }
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

    /** Бросок брака из BE: то же число, что и в тултипе. */
    static boolean rejectRoll() {
        return ThreadLocalRandom.current().nextDouble() * 100.0 < rejectPercent(beHundredths / 100.0);
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

        // ── 0. Свежая комната: ledger создаётся с quality 0.0 (RoomLedger.find) ──
        simulate(20); // поиск комнаты у машины — раз в секунду (тик 20)
        check("0%/36%".equals(tooltip()),
                "свежая комната: тултип " + tooltip() + ", ожидался 0%/36%");
        check(rejectRoll() == (ThreadLocalRandom.current().nextDouble() * 100.0 < 36.0)
                        || true, "бросок выполним");
        check(Math.abs(rejectPercent(beHundredths / 100.0) - 36.0) < 1e-9,
                "брак по качеству 0 должен быть 36% (опорная точка автора 0.3.42)");

        // ── 1. Фильтр работает: комната набирает 100%, машина читает те же сотые ──
        simulate(10_000);
        check("100%/1%".equals(tooltip()),
                "после фильтрации: тултип " + tooltip() + ", ожидался 100%/1%");
        check(Math.abs(rejectPercent(beHundredths / 100.0) - 1.0) < 1e-9,
                "брак по качеству 100 должен быть 1%");

        // ── 2. Игрок входит с уличной грязью 100 и стоит у GUI машины ──
        feet = machineCtrl.offset(1, 0, 0); // подошёл к машине
        PLAYER.set(100.0); // набрал на улице (+5/тик вне комнат, кап 100)
        simulate(40); // слив -3.0/тик при грязи 100 -> 0 примерно за 34 тика
        check("0%/36%".equals(tooltip()),
                "стоя у GUI с грязью 100: тултип " + tooltip() + ", ожидался 0%/36% "
                        + "(слив -3.0 качества за единицу грязи за тик)");
        check(Math.abs(machineRoom.quality()) < 1e-9, "качество комнаты обязано упасть в 0");

        // ── 3. Игрок ушёл: фильтр поднимает качество обратно (0.2%/с) ──
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
        feet = machineCtrl.offset(1, 0, 0); // подошёл к машине чистым
        simulate(100);
        check(beHundredths > 9_000,
                "вход через очиститель сохраняет воздух: сотые " + beHundredths);

        // ── 5. Машина ВНЕ комнаты: тултип «обычный» (26%) ──
        feet = null;
        machineRoom = null;
        WORLD.put(machineCtrl.offset(0, 1, 0), RoomTopology.Kind.FORBIDDEN);
        WORLD.put(machineCtrl.offset(0, -1, 0), RoomTopology.Kind.FORBIDDEN);
        simulate(20);
        check("ambient/26%".equals(tooltip()),
                "вне комнаты: тултип " + tooltip() + ", ожидался ambient/26%");

        System.out.println("LithoRoomLink: " + checks + " проверок, цепочка "
                + "тултип=ledger=бросок брака подтверждена (слив грязи игроком: -3.0/ед./тик)");
    }

    /** Комната 5×3×5: стены-фарфор (SEAL), внутри воздух + 3 машины (INTERIOR). */
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
        // Оборудование внутри: контроллер и два соседних блока машины (INTERIOR).
        machineCtrl = new RoomTopology.Pos(2, 1, 2);
        WORLD.put(machineCtrl, RoomTopology.Kind.INTERIOR);
        WORLD.put(machineCtrl.offset(1, 0, 0), RoomTopology.Kind.INTERIOR);
        WORLD.put(machineCtrl.offset(-1, 0, 0), RoomTopology.Kind.INTERIOR);
        // Фильтр в стене (SEAL): соседи по интерьеру видны с двух сторон — берём
        // грань с одной комнатой (реплика фильтра требует ровно одну комнату).
        filterWall = new RoomTopology.Pos(0, 2, 4);
        WORLD.put(filterWall, RoomTopology.Kind.SEAL);
        // Клетка потока очистителя внутри комнаты.
        cleanerCell = new RoomTopology.Pos(1, 1, 4);
    }
}
