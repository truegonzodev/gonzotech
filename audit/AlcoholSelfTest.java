import com.gonzotech.core.psyche.AlcoholDose;

public final class AlcoholSelfTest {
    private static int checks;
    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }
    public static void main(String[] args) {
        var mug = AlcoholDose.BEER_MUG;
        var bucket = AlcoholDose.BEER_BUCKET;
        var vodka = AlcoholDose.VODKA;
        check(mug.addiction == 4000 && mug.addiction / 10000.0 == .4, "mug is 0.4%, not 400 points");
        check(mug.stress == -3000 && mug.crisis == 0 && mug.food == 2 && mug.saturation == -2, "mug dose");
        check(mug.clueChance == 1 && mug.poisonChance == 0 && mug.faintChance == 0, "mug probabilities");
        check(bucket.addiction == 4000 && bucket.stress == -9000 && bucket.crisis == 0, "bucket points");
        check(bucket.food == 6 && bucket.saturation == -8 && bucket.clueChance == 3, "bucket food and clue");
        check(bucket.slownessTicks == 30*20 && bucket.poisonChance == 50 && bucket.poisonTicks == 6*20, "bucket effects");
        check(vodka.addiction == 80000 && vodka.stress == -40000 && vodka.crisis == 1000, "vodka points");
        check(vodka.food == -3 && vodka.saturation == -6 && vodka.clueChance == 9, "vodka food and clue");
        check(vodka.poisonChance == 100 && vodka.poisonTicks == 13*20 && vodka.nauseaTicks == 20*20, "vodka effects");
        check(vodka.faintChance == 25 && java.util.List.of(AlcoholDose.FAINT_TICKS, AlcoholDose.BLINDNESS_TICKS).equals(java.util.List.of(80,60)), "approved faint chance and durations");
        for (int chance : new int[]{0,1,3,9,25,50,100}) {
            int hits = 0;
            for (int roll=0; roll<100; roll++) if (AlcoholDose.succeeds(roll,chance)) hits++;
            check(hits == chance, "exact percentage " + chance);
            check(!AlcoholDose.succeeds(-1,chance) && !AlcoholDose.succeeds(100,chance), "out of range rolls rejected");
        }
        for (var dose : AlcoholDose.values()) {
            for (int food=0; food<=20; food++) {
                int next = dose.foodAfter(food);
                check(next >= 0 && next <= 20, "food clamp");
                for (int quarter=0; quarter<=food*4; quarter++) {
                    float saturation = dose.saturationAfter(quarter/4f, next);
                    check(saturation >= 0 && saturation <= next, "saturation within new food bounds");
                    check(saturation <= quarter/4f, "drinking cannot silently add saturation");
                }
            }
        }
        check(mug.foodAfter(19) == 20 && mug.saturationAfter(5,20) == 3, "mug near full hunger");
        check(bucket.foodAfter(0) == 6 && bucket.saturationAfter(2,6) == 0, "bucket saturation cannot go negative");
        check(vodka.foodAfter(2) == 0 && vodka.saturationAfter(2,0) == 0, "vodka at near-starvation");
        check(AlcoholDose.withinClueRadius(0) && AlcoholDose.withinClueRadius(256), "radius includes boundary");
        check(!AlcoholDose.withinClueRadius(256.0001) && !AlcoholDose.withinClueRadius(16*16+16*16), "no cube-corner clues");
        check(!AlcoholDose.withinClueRadius(Double.NaN) && !AlcoholDose.withinClueRadius(-1), "invalid distances rejected");
        System.out.println("Alcohol production-dose checks passed: " + checks);
    }
}
