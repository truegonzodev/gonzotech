package com.gonzotech.cleanroom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Hidden player dust load used by the clean-room layer. */
public final class Cleanliness {
    public static final Codec<Cleanliness> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("dirt", 0.0).forGetter(Cleanliness::dirt)
    ).apply(instance, Cleanliness::new));

    private double dirt;

    public Cleanliness() {
        this(0.0);
    }

    public Cleanliness(double dirt) {
        this.dirt = clamp(dirt);
    }

    public double dirt() { return dirt; }

    public void setDirt(double value) { dirt = clamp(value); }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
