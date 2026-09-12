package com.gonzotech.core.component;

import com.mojang.serialization.Codec;

/** Opaque ARGB tint precomputed by the server when a custom alloy is cast. */
public record AlloyTint(int argb) {

    public static final Codec<AlloyTint> CODEC = Codec.INT.xmap(AlloyTint::new, AlloyTint::argb);

    public AlloyTint {
        // Custom alloy sprites always use an opaque tint. This also makes manually
        // malformed component input harmless rather than invisibly transparent.
        argb = 0xFF000000 | (argb & 0x00FFFFFF);
    }
}
