import com.gonzotech.core.fluid.FoamPopulation;
import com.gonzotech.core.fluid.FoamSurface;
import com.gonzotech.core.fluid.FoamSurface.Pos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Tests the production sampler/grouping/population, not a parallel implementation. */
public final class FoamSelfTest {
    private static int checks;
    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
    }
    private static Map<Pos,Integer> rectangle(int x0, int z0, int width, int depth, int y, int kind) {
        var cells = new LinkedHashMap<Pos,Integer>();
        for (int x=x0; x<x0+width; x++) for (int z=z0; z<z0+depth; z++) cells.put(new Pos(x,y,z), kind);
        return cells;
    }
    private static final class Life implements FoamPopulation.Life {
        final int lifetime;
        int remaining;
        boolean removed;
        Life(int lifetime) { this.lifetime = lifetime; remaining = lifetime; }
        public boolean alive() { return !removed && remaining > 0; }
        public int lifetime() { return lifetime; }
        public void remove() { removed = true; }
    }
    private static final class Simulation {
        final FoamPopulation population = new FoamPopulation();
        final ArrayList<Life> particles = new ArrayList<>();
        final Map<Pos,Integer> histogram = new HashMap<>();
        final Random random = new Random(521);
        int births, fixedLifetime;
        Simulation(int fixedLifetime) { this.fixedLifetime = fixedLifetime; }
        void tick(FoamSurface.Snapshot surface, double density) {
            for (Life life : particles) life.remaining--;
            particles.removeIf(life -> !life.alive());
            int before = births;
            population.tick(surface, FoamPopulation.budgets(surface, density), random, (pos, kind, scale) -> {
                check(scale >= 0.8F && scale <= 2.0F, "approved scale bounds");
                check(surface.membership().containsKey(pos), "birthpoint belongs to surface");
                check(surface.pools().get(surface.membership().get(pos)).kind() == kind, "colour/type preserved");
                // Variable lifetimes exercise engine-owned expiry, not controller-owned fixed TTL.
                Life life = new Life(fixedLifetime > 0 ? fixedLifetime : 8 + random.nextInt(65));
                particles.add(life); births++;
                histogram.merge(pos, 1, Integer::sum);
                return life;
            });
            check(births - before <= FoamPopulation.MAX_BIRTHS_PER_TICK, "bounded births each tick");
            check(population.size() <= FoamPopulation.MAX_LIVE, "bounded live handles");
        }
    }

    public static void main(String[] args) {
        int[] ns = {0,1,4,9,16,25,100};
        int[] expected = {0,8,12,19,28,40,139};
        for (int i=0; i<ns.length; i++) check(FoamSurface.target(ns[i]) == expected[i], "population formula " + ns[i]);
        var square = rectangle(14,0,5,5,64,0); // Crosses a chunk boundary: still one puddle.
        var one = FoamSurface.group(square);
        check(one.pools().size() == 1 && one.pools().getFirst().cells().size() == 25, "cross-chunk 5x5 is one surface");
        var topology = new LinkedHashMap<>(square);
        topology.put(new Pos(19,64,5),0); // Diagonal-only contact with (18,64,4).
        topology.put(new Pos(14,65,0),0);
        topology.put(new Pos(13,64,0),1);
        check(FoamSurface.group(topology).pools().size() == 4, "diagonal/vertical/different-kind separation");
        check(FoamSurface.group(Map.of()).pools().isEmpty(), "empty surface has no base seven");
        check(FoamPopulation.budgets(one,1)[0] == 40, "one shared +7, not per cell/chunk");
        check(FoamPopulation.budgets(one,.5)[0] == 20, "decreased particle setting");
        check(FoamPopulation.budgets(one,0)[0] == 0, "minimal setting");

        var scan = new FoamSurface.Scan(new Pos(0,64,0));
        var visited = new HashSet<Pos>();
        int steps = 0;
        while (!scan.done()) {
            int reads = scan.advance(p -> { check(visited.add(p), "scan visits each cell once"); return square.getOrDefault(p,-1); });
            check(reads <= FoamSurface.READS_PER_TICK, "world-read budget");
            steps++;
        }
        check(steps == 37 && visited.size() == FoamSurface.SCAN_VOLUME, "bounded 33x17x33 rolling scan");
        check(scan.snapshot().pools().getFirst().cells().size() == 15, "scan clips unseen portion, no forced traversal");
        check(visited.contains(new Pos(-16,56,-16)) && visited.contains(new Pos(16,72,16)), "scan bounds inclusive");
        try { new FoamSurface.Scan(new Pos(0,0,0)).snapshot(); throw new AssertionError("partial published"); }
        catch (IllegalStateException expectedFailure) { checks++; }

        Simulation steady = new Simulation(0);
        double sum = 0;
        for (int tick=0; tick<5000; tick++) {
            steady.tick(one,1);
            check(steady.population.size() <= 40, "never overfill stable puddle");
            if (tick >= 200) sum += steady.population.size();
        }
        double average = sum / 4800;
        check(average >= 38 && average <= 40, "steady mean close to formula: " + average);
        double perCell = steady.births / 25.0, chiSquared = 0;
        for (Pos p : square.keySet()) chiSquared += Math.pow(steady.histogram.getOrDefault(p,0)-perCell,2)/perCell;
        check(chiSquared < 70, "uniform random sampling over whole surface: chi2=" + chiSquared);

        Simulation shortLived = new Simulation(20), longLived = new Simulation(80);
        for (int i=0;i<500;i++) { shortLived.tick(one,1); longLived.tick(one,1); }
        check(shortLived.births > 2 * longLived.births, "longer life reduces replacements instead of accumulating foam");
        check(longLived.population.size() == 40, "same live target with long-lived particles");
        steady.population.clear();
        check(steady.population.size() == 0 && steady.particles.stream().noneMatch(Life::alive), "world change clears only owned particles");

        var pairs = rectangle(0,0,10,1,64,0);
        pairs.putAll(rectangle(11,0,10,1,64,0));
        var separated = FoamSurface.group(pairs);
        Simulation transitions = new Simulation(1000);
        for (int i=0;i<30;i++) transitions.tick(separated,1);
        check(transitions.population.size() == 40, "two ten-block puddles have two budgets");
        var joinedCells = new LinkedHashMap<>(pairs); joinedCells.put(new Pos(10,64,0),0);
        var joined = FoamSurface.group(joinedCells);
        check(joined.pools().size() == 1 && FoamPopulation.budgets(joined,1)[0] == 35, "connector merges groups and removes extra base seven");
        int beforeMerge = transitions.births;
        transitions.tick(joined,1);
        check(transitions.births == beforeMerge && transitions.population.size() == 40, "merge reuses old particles without burst or abrupt killing");
        transitions.tick(separated,1);
        check(transitions.births == beforeMerge, "split maps existing particles back to their own side");
        transitions.tick(FoamSurface.EMPTY,1);
        check(transitions.births == beforeMerge, "removed surface cannot emit");

        var islands = new LinkedHashMap<Pos,Integer>();
        for (int i=0;i<500;i++) islands.put(new Pos(i*2,64,0),0);
        var crowded = FoamSurface.group(islands);
        int budget = java.util.Arrays.stream(FoamPopulation.budgets(crowded,1)).sum();
        check(budget == 512, "proportional total cap across many puddles");
        Simulation capped = new Simulation(1000);
        for(int i=0;i<50;i++) capped.tick(crowded,1);
        check(capped.population.size() == 512, "global cap attainable without a first-frame burst");
        var rejection = new FoamPopulation();
        int[] calls = {0};
        rejection.tick(crowded, FoamPopulation.budgets(crowded,1), new Random(1), (p,k,s) -> { calls[0]++; return null; });
        check(calls[0] <= 64 && rejection.size() == 0, "invalid/culled points cannot cause an unbounded refill loop");

        var evicted = new FoamPopulation();
        int[] removed = {0};
        for(int i=0;i<30;i++) evicted.tick(one, FoamPopulation.budgets(one,1), new Random(i), (p,k,s) -> new FoamPopulation.Life() {
            public boolean alive() { return true; } // Simulate ParticleEngine eviction without remove().
            public int lifetime() { return 4; }
            public void remove() { removed[0]++; }
        });
        check(removed[0] > 0, "evicted engine handles cannot occupy the budget forever");
        System.out.println("Foam production-core checks passed: " + checks + "; steady mean=" + average + "/40");
    }
}
