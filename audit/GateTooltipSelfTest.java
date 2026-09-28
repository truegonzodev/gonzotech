import com.gonzotech.core.tooltip.GateRequirement;
import com.gonzotech.core.tooltip.RecipeGateIndex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Headless checks of production visibility/aggregation code; fixtures come from repository recipes. */
public final class GateTooltipSelfTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        for (int mask = 0; mask < 8; mask++) {
            check(GateRequirement.visible((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0)
                    == (mask == 7), "creative / advanced / config visibility " + mask);
        }
        RecipeGateIndex<String> index = new RecipeGateIndex<>();
        for (String line : Files.readAllLines(Path.of(args[0]))) {
            String[] fields = line.split("\\t");
            index.add(fields[0], new GateRequirement(Integer.parseInt(fields[1]), GateRequirement.Extra.valueOf(fields[2])));
        }
        Map<String, List<GateRequirement>> result = index.snapshot();
        for (String id : List.of("first_pump", "wrench", "boiler", "condenser", "firebox", "stirling_generator")) {
            check(result.get("gonzotech:" + id).equals(List.of(GateRequirement.discovery(1))), id + " book gate 1");
        }
        for (String id : List.of("electric_motor", "energy_module", "sheathing")) {
            check(result.get("gonzotech:" + id).equals(List.of(GateRequirement.discovery(3))), id + " book gate 3 via actual output");
        }
        check(result.get("gonzotech:superdense_ice").equals(List.of(GateRequirement.discovery(6))), "discovery 6");
        check(result.get("gonzotech:solar_watch").equals(List.of(new GateRequirement(2, GateRequirement.Extra.SUN_EVENT))), "compound gate");
        check(result.get("minecraft:charcoal").contains(new GateRequirement(0, GateRequirement.Extra.PLAY_TIME_20_MINUTES)), "timed recipe with different id/output");
        check(!result.containsKey("gonzotech:motor_copper"), "recipe id is NOT item id");
        // 0.3.30: the air filter recipe now EXISTS, so its gate must follow the real
        // recipe instead of the old "gate without recipe" guard inversion.
        check(result.get("gonzotech:third_air_filter").equals(List.of(GateRequirement.discovery(3))),
                "air filter gate 3 follows its real recipe");
        check(result.get("gonzotech:speedometer").equals(List.of(GateRequirement.discovery(2))),
                "speedometer gate 2 follows its real recipe");
        check(result.get("gonzotech:canister").equals(List.of(GateRequirement.discovery(3))),
                "both canister routes aggregate into one distinct gate 3");
        check(result.get("gonzotech:dust_meter").equals(List.of(GateRequirement.discovery(3)))
                && result.get("gonzotech:dosimeter").equals(List.of(GateRequirement.discovery(3)))
                && result.get("gonzotech:uv_meter").equals(List.of(GateRequirement.discovery(3))),
                "instrument gates 3 follow their real recipes");
        // 0.3.40: lithography line — alternates aggregate into one distinct gate 3.
        check(result.get("gonzotech:uv_lamp").equals(List.of(GateRequirement.discovery(3))), "uv lamp gate 3");
        check(result.get("gonzotech:chip_soup").equals(List.of(GateRequirement.discovery(3))), "chip soup gate 3");
        check(result.get("gonzotech:rubber_block").equals(List.of(GateRequirement.discovery(3))), "rubber block gate 3");
        check(result.get("gonzotech:third_silicon_factory").equals(List.of(GateRequirement.discovery(3))),
                "silicon factory gate 3");
        // Chips have NO JSON recipes yet (lithography machine will provide them):
        // the craft gate must NOT invent tooltip payloads for recipe-less outputs.
        check(!result.containsKey("gonzotech:chip_1") && !result.containsKey("gonzotech:chip_2")
                && !result.containsKey("gonzotech:chip_3"), "no invented gates for recipe-less chips");

        // Alternate recipes: an ungated route must not disappear, nor duplicate lines proliferate.
        RecipeGateIndex<String> alternatives = new RecipeGateIndex<>();
        alternatives.add("output", GateRequirement.discovery(3));
        alternatives.add("output", GateRequirement.discovery(1));
        alternatives.add("output", GateRequirement.discovery(3));
        alternatives.add("output", GateRequirement.NONE);
        alternatives.add("free", GateRequirement.NONE);
        var snapshot = alternatives.snapshot();
        check(snapshot.get("output").equals(List.of(GateRequirement.NONE, GateRequirement.discovery(1), GateRequirement.discovery(3))), "sorted distinct alternatives including none");
        check(!snapshot.containsKey("free"), "ungated-only outputs need no payload");
        alternatives.add("output", GateRequirement.discovery(2));
        check(snapshot.get("output").size() == 3, "snapshot detached from builder");
        try { snapshot.clear(); throw new AssertionError("mutable map"); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { snapshot.get("output").clear(); throw new AssertionError("mutable rules"); }
        catch (UnsupportedOperationException expected) { checks++; }
        check(new RecipeGateIndex<String>().snapshot().isEmpty(), "fresh reload index does not retain removed recipes");
        System.out.println("Gate tooltip production-core checks passed: " + checks);
    }
}
