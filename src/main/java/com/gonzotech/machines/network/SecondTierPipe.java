package com.gonzotech.machines.network;

/**
 * Marker for second-opening transport carriers.
 *
 * <p>It keeps first- and second-tier pipe bundles separate: mixing them would
 * otherwise silently turn a level-II segment into a first-tier composite block.
 * Both tiers may still connect in a resource network, as required by the current
 * routing model.</p>
 */
public interface SecondTierPipe {
}
