package com.gonzotech.core.component;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical proportional composition of one {@code custom_alloy} stack.
 *
 * <p>Values are relative material units, not a batch size. The constructor
 * divides every entry by their greatest common divisor and sorts keys, so a
 * 1:2 copper/nickel mix is exactly equal to a 10:20 mix. Consequently vanilla
 * ItemStack component equality provides the required stacking behaviour.</p>
 */
public record AlloyComposition(Map<ResourceLocation, Integer> parts) {

    private static final int MAX_PART = 100_000;
    private static final int UNITS_PER_PORTION = 10;
    private static final int MIN_GENERIC_BATCH_UNITS = 20;
    private static final Codec<Map<ResourceLocation, Integer>> PARTS_CODEC =
        Codec.unboundedMap(ResourceLocation.CODEC, Codec.intRange(1, MAX_PART));
    public static final Codec<AlloyComposition> CODEC = PARTS_CODEC.xmap(AlloyComposition::new, AlloyComposition::parts);

    public AlloyComposition {
        Objects.requireNonNull(parts, "parts");
        if (parts.isEmpty()) throw new IllegalArgumentException("Alloy composition cannot be empty");

        Map<ResourceLocation, Integer> ordered = new LinkedHashMap<>();
        parts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
            .forEach(entry -> {
                ResourceLocation material = Objects.requireNonNull(entry.getKey(), "material");
                int value = Objects.requireNonNull(entry.getValue(), "part count");
                if (value <= 0 || value > MAX_PART) {
                    throw new IllegalArgumentException("Invalid alloy part count: " + value);
                }
                ordered.put(material, value);
            });

        int gcd = 0;
        for (int value : ordered.values()) gcd = gcd(gcd, value);
        if (gcd > 1) {
            for (Map.Entry<ResourceLocation, Integer> entry : ordered.entrySet()) {
                entry.setValue(entry.getValue() / gcd);
            }
        }
        parts = Collections.unmodifiableMap(ordered);
    }

    /**
     * Multiplier that turns the reduced ratio into the smallest valid generic
     * foundry batch: at least two portions and a total divisible by one portion
     * (ten material units). It lets lore state its normalized recipe in real
     * portions even for compositions that were made with individual nuggets.
     */
    public int minimumFoundryBatchMultiplier() {
        long total = 0;
        for (int value : parts.values()) total += value;

        int multiplier = 1;
        while (total * multiplier < MIN_GENERIC_BATCH_UNITS || total * multiplier % UNITS_PER_PORTION != 0) {
            multiplier++;
        }
        return multiplier;
    }

    private static int gcd(int left, int right) {
        while (right != 0) {
            int next = left % right;
            left = right;
            right = next;
        }
        return Math.abs(left);
    }
}
