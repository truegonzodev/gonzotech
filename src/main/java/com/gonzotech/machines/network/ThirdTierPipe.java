package com.gonzotech.machines.network;

/**
 * Marker for the THIRD-opening (epoch 3) shielded transport carriers.
 * <p>
 * Extends {@link SecondTierPipe}: network logic (clamps, throughput tables,
 * tier separation) treats them as tier II hosts, while {@link #STAT_FACTOR}
 * applies the shielded stats nerf. Radiation-wise these blocks close the
 * contour (tag {@code gonzotech:contour_seal}) and shield 89% as items/blocks
 * (RadMaterials exact factors).
 */
public interface ThirdTierPipe extends SecondTierPipe {

    /** 0.3.110: все статы экранированной семьи ×0.88 (автор). */
    double STAT_FACTOR = 0.88D;
}
