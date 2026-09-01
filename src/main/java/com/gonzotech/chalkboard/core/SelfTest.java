package com.gonzotech.chalkboard.core;

import java.util.Locale;

/**
 * Headless parity check for the ported engine — runs without Minecraft.
 * <pre>./gradlew compileJava &amp;&amp; java -cp build/classes/java/main com.gonzotech.chalkboard.core.SelfTest</pre>
 */
public final class SelfTest {

    private static int failures = 0;

    public static void main(String[] args) {
        System.out.println("── Resonance 7D · engine self-test ──\n");

        // 1. F / m = a  → dimensionally perfect
        Expr fma = Expr.Eq.of(
                Expr.Op.of(Expr.OpKind.DIV, Expr.Slot.of("force"), Expr.Slot.of("mass")),
                Expr.Slot.of("accel"));
        Analysis a1 = Evaluator.analyze(fma);
        report("F/m = a", a1);
        expect("S_D == 100", a1.sD != null && a1.sD > 99.9);

        // 2. F / m = 2a → 7D blind to the coefficient, numeric layer catches it
        Expr false2a = Expr.Eq.of(
                Expr.Op.of(Expr.OpKind.DIV, Expr.Slot.of("force"), Expr.Slot.of("mass")),
                Expr.Op.of(Expr.OpKind.MUL, Expr.Num.of(2, "2"), Expr.Slot.of("accel")));
        Analysis a2 = Evaluator.analyze(false2a);
        report("F/m = 2a", a2);
        expect("S_D == 100", a2.sD != null && a2.sD > 99.9);
        expect("S_N == 50", a2.sN != null && Math.abs(a2.sN - 50) < 0.01);

        // 3. Illegal addition: a + 2
        Expr broken = Expr.Eq.of(
                Expr.Op.of(Expr.OpKind.DIV, Expr.Slot.of("force"), Expr.Slot.of("mass")),
                Expr.Op.of(Expr.OpKind.ADD, Expr.Slot.of("accel"), Expr.Num.of(2, "2")));
        Analysis a3 = Evaluator.analyze(broken);
        report("F/m = a + 2", a3);
        expect("conflict registered", a3.conflicts.size() == 1);

        // 4. Anti-inflation: multiplying BOTH sides must not change the score
        Expr base = Expr.Eq.of(
                Expr.Slot.of("riemann"),
                Expr.Op.of(Expr.OpKind.MUL,
                        Expr.Op.of(Expr.OpKind.DIV,
                                Expr.Op.of(Expr.OpKind.MUL, Expr.Slot.of("pressure"), Expr.Slot.of("strain_tensor")),
                                Expr.Slot.of("work")),
                        Expr.Slot.of("area")));
        Analysis b0 = Evaluator.analyze(base);

        Expr inflated = base;
        for (int i = 0; i < 4; i++) {
            Expr.Eq eq = (Expr.Eq) inflated;
            inflated = Expr.Eq.of(
                    Expr.Op.of(Expr.OpKind.MUL, eq.left(), Expr.Slot.of("em_tensor")),
                    Expr.Op.of(Expr.OpKind.MUL, eq.right(), Expr.Slot.of("em_tensor")));
        }
        Analysis b4 = Evaluator.analyze(inflated);
        report("base  (Rpijk = …)", b0);
        report("×Fμν ⁴ both sides", b4);
        expect("S_D unchanged by inflation",
                b0.sD != null && b4.sD != null && Math.abs(b0.sD - b4.sD) < 0.001);
        expect("LHS penalty applied", b4.lhsPenalty == 4);
        expect("factors cancelled", b4.cancelledIds.size() == 4);

        // 5. Solver guarantees a solvable puzzle on every difficulty
        for (int level = 1; level <= 3; level++) {
            GameSolver.Puzzle p = GameSolver.generate(level);
            int locked = p.lockedSlotIds().size();
            System.out.printf(Locale.ROOT,
                    "  L%d  target=%-22s locked=%d  bestScore=%.1f  expansions=%d%n",
                    level, p.target().symbol() + " (w" + p.target().weight() + ")",
                    locked, p.bestScore(), p.expansionsDone());
            expect("L" + level + " solvable ≥ 90", p.bestScore() >= 90);
            if (level == 3) {
                boolean allHeavy = true;
                for (Manipulate.SlotInfo s : Manipulate.collectSlots(p.expr())) {
                    if (!s.locked() || s.quantityId() == null) continue;
                    Quantity q = Quantities.get(s.quantityId());
                    if (q != null && q.weight() < 2) allHeavy = false;
                }
                expect("L3 frozen nodes all weight ≥ 2", allHeavy);
            }
        }

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        if (failures > 0) System.exit(1);
    }

    private static void report(String name, Analysis a) {
        System.out.printf(Locale.ROOT, "  %-22s  S_D=%s  S_N=%s  S=%s  penalty=%d  cancelled=%s%n",
                name, fmt(a.sD), fmt(a.sN), fmt(a.sFinal), a.lhsPenalty,
                a.cancelledIds.isEmpty() ? "-" : String.join(",", a.cancelledIds));
    }

    private static String fmt(Double d) {
        return d == null ? "  n/a" : String.format(Locale.ROOT, "%5.1f", d);
    }

    private static void expect(String label, boolean ok) {
        System.out.println((ok ? "    ✓ " : "    ✗ ") + label);
        if (!ok) failures++;
    }

    private SelfTest() {
    }
}
