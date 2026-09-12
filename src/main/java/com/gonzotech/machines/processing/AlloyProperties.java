package com.gonzotech.machines.processing;

import com.gonzotech.core.component.AlloyComposition;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/** Weighted property vector calculated from a canonical custom-alloy composition. */
public record AlloyProperties(
    int strength,
    int brittleness,
    int inertness,
    int conductivity,
    int heatResistance,
    int plasticity,
    int weight,
    int argbTint,
    AlloyMaterialCatalog.ToolTier toolTier
) {
    /**
     * Calculates all continuous axes as a material-unit weighted mean. Plasticity
     * reduces the displayed effective brittleness by half its value; the raw
     * material tables retain both independent values for future tool formulas.
     */
    public static Optional<AlloyProperties> from(AlloyComposition composition) {
        long total = 0;
        long strength = 0, brittleness = 0, inertness = 0, conductivity = 0;
        long heat = 0, plasticity = 0, weight = 0, red = 0, green = 0, blue = 0;
        int greatestPart = -1;
        AlloyMaterialCatalog.ToolTier tier = AlloyMaterialCatalog.ToolTier.STONE;

        for (Map.Entry<ResourceLocation, Integer> entry : composition.parts().entrySet()) {
            AlloyMaterialCatalog.Material material = AlloyMaterialCatalog.material(entry.getKey());
            if (material == null) continue; // Robust tooltip handling for manually malformed stacks.
            int units = entry.getValue();
            total += units;
            strength += (long) material.strength() * units;
            brittleness += (long) material.brittleness() * units;
            inertness += (long) material.inertness() * units;
            conductivity += (long) material.conductivity() * units;
            heat += (long) material.heatResistance() * units;
            plasticity += (long) material.plasticity() * units;
            weight += (long) material.weight() * units;
            red += (long) ((material.rgb() >>> 16) & 0xFF) * units;
            green += (long) ((material.rgb() >>> 8) & 0xFF) * units;
            blue += (long) (material.rgb() & 0xFF) * units;

            // "Dominant inclusion" governs U. Exact ties deliberately select the
            // higher tier, so a 1:1 mix is never weakened by arbitrary map order.
            if (units > greatestPart || (units == greatestPart && material.toolTier().isAtLeast(tier))) {
                greatestPart = units;
                tier = material.toolTier();
            }
        }
        if (total == 0) return Optional.empty();

        int averagePlasticity = divideRound(plasticity, total);
        int effectiveBrittleness = clamp(divideRound(brittleness, total) - averagePlasticity / 2);
        int rgb = (clamp(divideRound(red, total)) << 16)
            | (clamp(divideRound(green, total)) << 8)
            | clamp(divideRound(blue, total));
        return Optional.of(new AlloyProperties(
            clamp(divideRound(strength, total)),
            effectiveBrittleness,
            clamp(divideRound(inertness, total)),
            clamp(divideRound(conductivity, total)),
            clamp(divideRound(heat, total)),
            averagePlasticity,
            clamp(divideRound(weight, total)),
            0xFF000000 | rgb,
            tier
        ));
    }

    private static int divideRound(long numerator, long denominator) {
        return (int) ((numerator + denominator / 2) / denominator);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
