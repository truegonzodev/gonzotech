import com.gonzotech.radiation.RadiationContour;
import com.gonzotech.radiation.RadiationContour.Cell;
import com.gonzotech.radiation.RadiationContour.Kind;
import com.gonzotech.radiation.RadiationContour.Pos;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises the actual production geometry, not a second implementation of its rules. */
public final class RadiationContourSelfTest {
    private static int checks;
    private static final Cell LEAD = new Cell(Kind.WALL, .02, 0);
    private static final Cell BARIUM = new Cell(Kind.WALL, .10, 0);
    private static final Cell STONE = new Cell(Kind.WALL, 1, 0);
    private static final Cell PURE_SEAL = new Cell(Kind.SEAL, 1, 0);
    private static final Cell SOURCE = new Cell(Kind.WALL, .72, 675_000_000);
    private static final Pos CENTER = new Pos(0, 0, 0);
    private static void check(boolean ok, String name) {
        checks++;
        if (!ok) throw new AssertionError(name);
    }
    private static void near(double actual, double expected, String name) {
        check(Math.abs(actual - expected) <= Math.max(1e-12, Math.abs(expected) * 1e-10), name + ": " + actual + " != " + expected);
    }
    private static final class World {
        final Map<Pos, Cell> cells = new HashMap<>();
        Cell read(Pos p) { return cells.getOrDefault(p, RadiationContour.AIR); }
        void layer(int halfSize, Cell material) {
            for (int x = -halfSize; x <= halfSize; x++)
                for (int y = -halfSize; y <= halfSize; y++)
                    for (int z = -halfSize; z <= halfSize; z++)
                        if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) == halfSize)
                            cells.put(new Pos(x, y, z), material);
        }
        RadiationContour.Result probe() { return RadiationContour.probe(this::read, CENTER); }
    }
    public static void main(String[] args) {
        World box = new World(); box.layer(1, LEAD);
        near(box.probe().factor(), .02, "one lead layer");
        box.cells.put(new Pos(2, 0, 0), BARIUM);
        near(box.probe().factor(), (5 * .02 + .02 * .10) / 6, "one outside block only affects ONE of six wall rays");
        check(box.probe().factor() > .002, "one patch is not a complete second shell");
        box.layer(2, BARIUM);
        near(box.probe().factor(), .002, "complete contiguous lead+barium layers multiply");
        box.layer(2, LEAD);
        near(box.probe().factor(), 0, "two lead layers pass below zero threshold");

        Pos[] patches = {new Pos(2,0,0), new Pos(-2,0,0), new Pos(0,2,0), new Pos(0,-2,0), new Pos(0,0,2), new Pos(0,0,-2)};
        for (int mask = 0; mask < 64; mask++) {
            World patched = new World(); patched.layer(1, LEAD);
            for (int side = 0; side < 6; side++) if ((mask & (1 << side)) != 0) patched.cells.put(patches[side], BARIUM);
            int covered = Integer.bitCount(mask);
            near(patched.probe().factor(), (.02 * (6-covered) + .002 * covered) / 6, "partial outer coverage mask " + mask);
            check(mask == 63 || patched.probe().factor() > .002, "only full coverage grants whole second-layer factor " + mask);
        }
        World gap = new World(); gap.layer(1, LEAD); gap.layer(3, BARIUM);
        near(gap.probe().factor(), .02, "air gap stops layer trace");
        World stone = new World(); stone.layer(1, STONE); stone.layer(2, BARIUM);
        near(stone.probe().factor(), .10, "solid neutral cladding allows subsequent layers");
        World mixed = new World(); mixed.layer(1, LEAD); mixed.cells.put(new Pos(1, 0, 0), BARIUM);
        near(mixed.probe().factor(), (5 * .02 + .10) / 6, "mixed first-layer material is area averaged");
        mixed.cells.put(new Pos(1, 0, 0), PURE_SEAL);
        near(mixed.probe().factor(), .02, "one pure closed seal excluded");
        for (Pos p : new Pos[]{new Pos(-1,0,0), new Pos(0,1,0), new Pos(0,-1,0)}) mixed.cells.put(p, PURE_SEAL);
        near(mixed.probe().factor(), (4 + 2 * .02) / 6, "majority-seal exploit prevented");
        mixed.cells.put(new Pos(1,0,0), RadiationContour.AIR);
        check(!mixed.probe().enclosed(), "open door makes enclosure open");

        World thickness = new World();
        Cell iron = new Cell(Kind.WALL, .90, 0);
        for (int layer = 1; layer <= 9; layer++) thickness.layer(layer, iron);
        near(thickness.probe().factor(), Math.pow(.9, 8), "eight-layer limit, not nine");
        box = new World(); box.layer(2, LEAD); box.cells.put(new Pos(1, 0, 0), SOURCE);
        var inside = box.probe();
        check(inside.enclosed(), "player and source inside enclosure");
        near(inside.insideDose(), 270_000_000, "one radium block: 675mZt/s * 40% = 270mZt/s");
        near(inside.factor(), .02, "source does not replace actual wall with a self-shielding cell");
        box.cells.put(new Pos(1, 0, 1), SOURCE);
        near(box.probe().insideDose(), 540_000_000, "adjacent radioactive blocks both count exactly once");
        check(box.probe().interior().contains(new Pos(1,0,1)), "sources included in cavity cache aliases");
        near(RadiationContour.probe(box::read, new Pos(4,0,0)).insideDose(), 0, "outside observer not exposed through whole shell");
        box.layer(3, LEAD);
        near(box.probe().factor(), 0, "chunk can be fully shielded");
        near(box.probe().insideDose(), 540_000_000, "zero exterior leakage never cancels internal dose");

        World split = new World(); split.layer(3, LEAD);
        for (int y=-2; y<=2; y++) for (int z=-2; z<=2; z++) split.cells.put(new Pos(0,y,z), LEAD);
        split.cells.put(new Pos(1,0,0), SOURCE);
        near(RadiationContour.probe(split::read, new Pos(-1,0,0)).insideDose(), 0, "sealed partition blocks exposure from another room");
        near(RadiationContour.probe(split::read, new Pos(2,0,0)).insideDose(), 270_000_000, "same room exposes its occupant");
        split.cells.put(new Pos(0,0,0), RadiationContour.AIR);
        near(RadiationContour.probe(split::read, new Pos(-1,0,0)).insideDose(), 270_000_000, "opening internal door joins source cavity");

        World many = new World(); many.layer(3, LEAD);
        int count = 0;
        for (int x=-2;x<=2;x++) for(int y=-2;y<=2;y++) for(int z=-2;z<=2;z++) {
            Pos p = new Pos(x,y,z);
            if (!p.equals(CENTER)) { many.cells.put(p, SOURCE); count++; }
        }
        near(many.probe().insideDose(), count * 270_000_000.0, "all 124 piled sources counted, not just 16 or visible surface blocks");
        check(many.probe().interior().size() == 125, "one snapshot reusable for every interior source/player");
        many.cells.put(new Pos(3,0,0), RadiationContour.UNLOADED);
        check(!many.probe().enclosed() && many.probe().insideDose() == 0, "unknown boundary cannot prove containment or internal dose");
        AtomicInteger reads = new AtomicInteger();
        var open = RadiationContour.probe(p -> { reads.incrementAndGet(); return RadiationContour.AIR; }, CENTER);
        check(!open.enclosed() && reads.get() <= RadiationContour.VISIT_CAP, "open-world traversal is bounded");
        System.out.println("Radiation contour production-core checks passed: " + checks);
    }
}
