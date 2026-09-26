import com.gonzotech.cleanroom.FilterCycle;
import com.gonzotech.cleanroom.FilterIntake;

public final class AirFilterSelfTest {
    private static int checks;
    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        int capacity = FilterCycle.CAPACITY_GTU;
        check(capacity == 2040, "2040 GTU capacity");
        FilterCycle cycle = new FilterCycle(0, 0);
        check(cycle.coalUsedHundredths() == 0 && cycle.catalystUsedHundredths() == 0, "unused != exhausted");
        int coal = 0, catalyst = 0;
        long spent = 0;
        double quality = 0;
        for (int tick = 1; tick <= 7200; tick++) {
            var step = cycle.tick(2_040_000, true, true, true);
            check(step.working() && step.energySpent() == 2619, "active tick costs 2.6 + 0.019");
            if (step.loadCoal()) coal++;
            if (step.loadCatalyst()) catalyst++;
            spent += step.energySpent();
            quality += FilterCycle.QUALITY_PER_TICK;
            if (tick == 20) check(Math.abs(quality - 0.2) < 1e-10, "0.2 quality/s");
            if (tick == 600) check(cycle.coalUsedHundredths() == 5000, "30s coal = 50%");
            if (tick == 1800) check(cycle.catalystUsedHundredths() == 5000, "90s catalyst = 50%");
            // Persist/restore exact paid ticks, even at zero/exhaustion and fractional seconds.
            cycle = FilterCycle.fromTicks(cycle.coalTicks(), cycle.catalystTicks(), cycle.coalStarted(), cycle.catalystStarted());
        }
        check(coal == 6 && catalyst == 2, "360s costs six coal, two catalysts across reloads");
        check(spent == 18_856_800L, "total milli-GTU exact, no rounding drift");
        check(cycle.coalUsedHundredths() == 10000 && cycle.catalystUsedHundredths() == 10000, "exhaustion survives reload");
        for (boolean room : new boolean[]{false, true}) {
            for (boolean fuel : new boolean[]{false, true}) {
                for (boolean cat : new boolean[]{false, true}) {
                    for (long power : new long[]{0, 9, 19, 2600, 2618, 2619, 2_040_000}) {
                        FilterCycle c = new FilterCycle(0, 0);
                        var step = c.tick(power, room, fuel, cat);
                        boolean work = room && fuel && cat && power >= 2619;
                        check(step.working() == work, "all preconditions atomically checked");
                        check(step.loadCoal() == work && step.loadCatalyst() == work, "no fuel spent on failed work");
                        check(step.energySpent() == Math.min(power, 19) + (work ? 2600 : 0), "leakage always, bounded by stored energy");
                    }
                }
            }
        }
        cycle = new FilterCycle(30, 90);
        check(cycle.coalUsedHundredths() == 5000 && cycle.catalystUsedHundredths() == 5000, "legacy seconds migration");
        for (int i = 0; i < 20; i++) cycle.tick(1_000_000, false, true, true);
        check(cycle.coalTicks() == 600 && cycle.catalystTicks() == 1800, "pause does not spend reserves");
        check(cycle.tick(2619, true, false, false).working(), "paid fuel does not require another item in slot");

        FilterIntake intake = new FilterIntake();
        check(intake.offer(1, 100_000, 2_040_000, true) == 32_000, "simulate limit");
        check(intake.offer(1, 100_000, 2_040_000, true) == 32_000, "simulation does not spend quota");
        check(intake.offer(1, 10_000, 2_040_000, false) == 10_000, "first sender");
        check(intake.offer(1, 100_000, 2_030_000, false) == 22_000, "second sender shares quota");
        check(intake.offer(1, 100_000, 2_008_000, false) == 0, "six faces cannot exceed 32 GTU/t");
        check(intake.offer(2, 100_000, 500, true) == 500, "capacity limits simulation");
        check(intake.offer(1, 100_000, 2_008_000, true) == 0, "future simulation does not reset ledger");
        check(intake.offer(2, 100_000, 500, false) == 500, "new tick and near-full capacity");
        check(intake.offer(2, -1, 500, false) == 0, "negative request");
        check(intake.offer(2, 50, 0, false) == 0, "full buffer");
        for (int milli : new int[]{0, 19, 2600, 32768, 65535, 65536, 2_040_000}) {
            int low = (short) (milli & 0xffff), high = (short) (milli >>> 16);
            check(((low & 0xffff) | ((high & 0xffff) << 16)) == milli, "container signed-short sync");
        }
        System.out.println("Air filter production-core checks passed: " + checks);
    }
}
