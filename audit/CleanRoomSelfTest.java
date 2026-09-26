import com.gonzotech.cleanroom.CleanerPulse;
import com.gonzotech.cleanroom.FilterCycle;
import com.gonzotech.cleanroom.RoomLedger;
import com.gonzotech.cleanroom.RoomTopology;
import com.gonzotech.cleanroom.RoomTopology.Kind;
import com.gonzotech.cleanroom.RoomTopology.Pos;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Executes production algorithms, not a Python reimplementation. No Minecraft needed. */
public final class CleanRoomSelfTest {
    private static int checks;
    private static final class World {
        final Map<Pos, Kind> blocks = new HashMap<>();
        Kind get(Pos p) { return blocks.getOrDefault(p, Kind.FORBIDDEN); }
        void box(int x0, int y0, int z0, int sx, int sy, int sz) {
            for (int x = -1; x <= sx; x++) for (int y = -1; y <= sy; y++) for (int z = -1; z <= sz; z++) {
                boolean shell = x == -1 || x == sx || y == -1 || y == sy || z == -1 || z == sz;
                blocks.put(new Pos(x0 + x, y0 + y, z0 + z), shell ? Kind.SEAL : Kind.INTERIOR);
            }
        }
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 0.0000001, message + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        World world = new World();
        world.box(-2, 64, -2, 3, 3, 3);
        Pos a = new Pos(-2, 64, -2), b = new Pos(0, 66, 0);
        var topology = RoomTopology.find(world::get, a);
        check(topology.valid() && topology.cells().size() == 27, "closed 3x3x3 volume");
        check(topology.cells().equals(RoomTopology.find(world::get, b).cells()), "origin-independent geometry");
        AtomicInteger writes = new AtomicInteger();
        RoomLedger ledger = new RoomLedger(writes::incrementAndGet);
        var room = ledger.find(world::get, a, 1);
        near(room.quality(), 0, "new room starts at zero");
        ledger.adjust(room, 50);
        for (Pos p : topology.cells()) {
            check(ledger.find(world::get, p, 1) == room, "every interior cell shares one object");
            near(ledger.find(world::get, p, 1).quality(), 50, "uniform quality");
        }
        // Two players lose 1 dirt each; a filter restores 0.2 to this same room.
        ledger.adjust(ledger.find(world::get, a, 20), -3);
        ledger.adjust(ledger.find(world::get, b, 20), -3);
        ledger.adjust(ledger.find(world::get, new Pos(-1, 65, -1), 20), 0.2);
        near(room.quality(), 44.2, "shared player pollution and filter improvement");
        check(ledger.rooms().size() == 1, "no per-position states");
        check(ledger.find(world::get, new Pos(100, 64, 100), 20) == null, "outside is not a room");

        // Air and allowed equipment both classify as INTERIOR, never as a seal.
        world.blocks.put(new Pos(-1, 65, -1), Kind.INTERIOR);
        check(ledger.find(world::get, b, 21) == room, "interior equipment preserves quality");
        Pos floor = new Pos(-1, 63, -1);
        world.blocks.put(floor, Kind.INTERIOR); ledger.invalidate(floor);
        check(ledger.find(world::get, a, 21) == null, "hole/pipe/machine in floor opens room");
        check(ledger.rooms().isEmpty(), "breached state deleted");
        world.blocks.put(floor, Kind.SEAL); ledger.invalidate(floor);
        room = ledger.find(world::get, a, 21);
        near(room.quality(), 0, "floor restored in same tick resets quality");
        ledger.adjust(room, 37);
        // A brief breach with no quality query must also reset the room.
        world.blocks.put(floor, Kind.INTERIOR); ledger.invalidate(floor);
        world.blocks.put(floor, Kind.SEAL); ledger.invalidate(floor);
        room = ledger.find(world::get, a, 21);
        near(room.quality(), 0, "unobserved break-and-replace cannot recover old quality");

        // Check every boundary face, not only walls/ceiling. SEAL models a legal node.
        for (Pos p : topology.shell()) {
            world.blocks.put(p, Kind.INTERIOR);
            check(!RoomTopology.find(world::get, a).valid(), "all six faces must seal: " + p);
            world.blocks.put(p, Kind.SEAL);
        }
        Pos furniture = new Pos(-1, 65, -1);
        world.blocks.put(furniture, Kind.FORBIDDEN); ledger.invalidate(furniture);
        check(ledger.find(world::get, a, 22) == null, "dirt/forbidden furniture invalidates room");
        world.blocks.put(furniture, Kind.INTERIOR);
        room = ledger.find(world::get, a, 23); ledger.adjust(room, 37);
        world.blocks.put(floor, Kind.UNLOADED);
        check(ledger.find(world::get, a, 24) == null, "unloaded boundary suspends operation");
        check(ledger.rooms().size() == 1, "unload is not a breach");
        world.blocks.put(floor, Kind.SEAL);
        near(ledger.find(world::get, a, 25).quality(), 37, "chunk reload retains quality");

        RoomLedger restored = new RoomLedger(() -> {});
        for (var entry : ledger.rooms()) restored.restore(entry.cells(), entry.shell(), entry.quality());
        near(restored.find(world::get, b, 26).quality(), 37, "SavedData snapshot round trip");
        RoomLedger otherWorld = new RoomLedger(() -> {});
        near(otherWorld.find(world::get, b, 26).quality(), 0, "same coordinates in different world do not leak");
        // Even without mutation callbacks, saved geometry is checked at first query.
        world.blocks.put(floor, Kind.FORBIDDEN);
        RoomLedger stale = new RoomLedger(() -> {});
        stale.restore(room.cells(), room.shell(), 90);
        check(stale.find(world::get, a, 27) == null && stale.rooms().isEmpty(), "stale saved shell rejected");
        world.blocks.put(floor, Kind.SEAL);
        ledger.adjust(room, 1000); near(room.quality(), 100, "quality upper bound");
        ledger.adjust(room, -1000); near(room.quality(), 0, "quality lower bound");
        check(writes.get() > 0, "quality/topology changes mark SavedData dirty");

        World doors = new World();
        doors.box(0, 64, 0, 1, 2, 1); doors.box(2, 64, 0, 1, 2, 1);
        RoomLedger divided = new RoomLedger(() -> {});
        var left = divided.find(doors::get, new Pos(0, 64, 0), 1);
        var right = divided.find(doors::get, new Pos(2, 64, 0), 1);
        check(left != null && right != null && left != right, "hermetic door partitions volumes");
        divided.adjust(left, 80); near(right.quality(), 0, "neighbour room independent");
        // Opening a hermetic door leaves Kind.SEAL unchanged: no invalidation is sent.
        check(divided.find(doors::get, new Pos(0, 64, 0), 2) == left, "hermetic door state does not merge rooms");
        divided.invalidate(new Pos(1, 64, 0));
        check(divided.rooms().isEmpty(), "removing shared shell invalidates both sides");

        World maximum = new World(); maximum.box(0, -64, 0, 16, 8, 16);
        check(RoomTopology.find(maximum::get, new Pos(0, -64, 0)).valid(), "exact 2048-cell limit accepted");
        var open = RoomTopology.find(p -> Kind.INTERIOR, new Pos(0, 0, 0));
        check(open.status() == RoomTopology.Status.TOO_LARGE && open.cells().size() == 2049, "open air scan bounded");
        check(RoomTopology.find(p -> Kind.UNLOADED, a).status() == RoomTopology.Status.UNLOADED, "never treat unloaded as air");

        RoomLedger outdoor = new RoomLedger(() -> {});
        AtomicInteger reads = new AtomicInteger();
        java.util.function.Function<Pos, Kind> sky = p -> { reads.incrementAndGet(); return Kind.INTERIOR; };
        check(outdoor.find(sky, a, 40) == null, "large outdoor volume rejected");
        int firstScan = reads.get();
        for (int tick = 41; tick < 60; tick++) {
            check(outdoor.find(sky, a, tick) == null, "outdoor miss cached");
        }
        check(reads.get() == firstScan, "no 20x outdoor flood-fill regression for tick-based filters");
        outdoor.find(sky, a, 60);
        check(reads.get() > firstScan, "outdoor miss expires next second");
        World sealed = new World(); sealed.box(a.x(), a.y(), a.z(), 1, 1, 1);
        outdoor.invalidate(a.offset(1, 0, 0));
        check(outdoor.find(sealed::get, a, 60) != null, "block edit immediately clears outdoor miss cache");

        FilterCycle cycle = new FilterCycle(0, 0);
        int coal = 0, catalyst = 0;
        for (int second = 1; second <= 360; second++) {
            if (cycle.needsCoal()) coal++;
            if (cycle.needsCatalyst()) catalyst++;
            cycle.workedSecond();
            if (second == 60) check(coal == 1 && catalyst == 1 && cycle.needsCoal(), "60s = one coal");
            if (second == 180) check(coal == 3 && catalyst == 1 && cycle.needsCatalyst(), "180s = one catalyst");
        }
        check(coal == 6 && catalyst == 2, "360 operating seconds cost 6 coal + 2 catalyst");
        cycle.workedSecond();
        FilterCycle loaded = new FilterCycle(cycle.coal(), cycle.catalyst());
        check(loaded.coal() == 59 && loaded.catalyst() == 179, "paid reserve survives save/reload");
        check(!loaded.needsCoal() && !loaded.needsCatalyst(), "paused operation retains credit");
        // Every possible second-phase offset, including a pulse after the BE tick.
        for (int start = 0; start < 20; start++) {
            CleanerPulse pulse = new CleanerPulse(0, false);
            pulse.signal(true);
            check(!pulse.active(start), "no treatment before first processed BE tick");
            int active = 0, seconds = 0;
            for (int time = start + 1; time <= start + 180; time++) {
                check(!pulse.signal(true), "held power / neighbour updates do not retrigger");
                if (pulse.tick(time)) active++;
                if (time % 20 == 0 && pulse.active(time)) seconds++;
            }
            check(active == 160 && seconds == 8, "one pulse = 160 ticks / 8 treatments / 92 dirt");
            pulse.signal(false); pulse.signal(true);
            check(pulse.remaining() == 160, "new rising edge starts new pulse");
        }
        CleanerPulse running = new CleanerPulse(80, true);
        running.signal(true);
        check(running.remaining() == 80, "reload under held power does not restart pulse");
        check(new CleanerPulse(999, true).remaining() == 160, "corrupt pulse duration clamped");
        int energy = 100_000;
        near((energy & 0xffff) | (((energy >>> 16) & 0xffff) << 16), energy, "GTU short-pair sync does not wrap");
        System.out.println("Clean-room core: " + checks + " checks passed");
    }
}
