package com.gonzotech.core.tooltip;

/** Describes a policy, not whether the current player has already fulfilled it. */
public record GateRequirement(int discovery, Extra extra) implements Comparable<GateRequirement> {
    public enum Extra { NONE, SUN_EVENT, PLAY_TIME_20_MINUTES }
    public static final GateRequirement NONE = new GateRequirement(0, Extra.NONE);

    public GateRequirement {
        if (discovery < 0 || discovery > 16 || extra == null) throw new IllegalArgumentException("Invalid gate");
    }

    public static GateRequirement discovery(int tier) { return new GateRequirement(tier, Extra.NONE); }

    @Override public int compareTo(GateRequirement other) {
        int tier = Integer.compare(discovery, other.discovery);
        return tier != 0 ? tier : extra.compareTo(other.extra);
    }

    public static boolean visible(boolean creative, boolean advanced, boolean enabled) {
        return creative && advanced && enabled;
    }
}
