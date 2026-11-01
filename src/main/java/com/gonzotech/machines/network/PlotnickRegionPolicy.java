package com.gonzotech.machines.network;

/** Ownerless machine policy, NOT an impersonated player's WorldGuard build permission. */
public final class PlotnickRegionPolicy {
    public enum State { ALLOW, DENY, UNSET }
    public static boolean allows(State build, State place, State broken, State pistons) {
        if (build == State.DENY || place == State.DENY || broken == State.DENY) return false;
        return pistons == State.ALLOW
            && (place == State.ALLOW || build == State.ALLOW)
            && (broken == State.ALLOW || build == State.ALLOW);
    }
    private PlotnickRegionPolicy() {}
}
