package com.gonzotech.core.psyche;

import net.minecraft.network.syncher.EntityDataAccessor;

/** Synced presentation flags, implemented by PlayerFaintingMixin on both logical sides. */
public interface FaintingPlayer {
    boolean gonzotech$isFainting();
    float gonzotech$faintFloorOffset();
    boolean gonzotech$isFaintData(EntityDataAccessor<?> key);
    void gonzotech$beginFaint(float floorOffset);
    void gonzotech$endFaint();
}
