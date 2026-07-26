package com.yourshika.wildbosses.boss;

/**
 * A per-server-tick cumulative damage cap for a single boss. Ensures the total damage a boss takes in
 * one tick never exceeds a cap, no matter how many separate damage events land that tick - so a single
 * sword swing that triggers several bonus-damage procs (custom-enchant plugins, thorns stacks, ...)
 * can't sum past the cap and one-shot a full-HP boss. The window resets when the server tick advances.
 */
final class TickDamageCap {

    private int windowTick = Integer.MIN_VALUE;
    private double inWindow;

    /**
     * @param serverTick   the current server tick (window key)
     * @param capTotal     the most damage allowed in a single tick
     * @param incomingFinal the final damage this event wants to deal
     * @return how much of {@code incomingFinal} may actually be applied (the rest should be shaved off)
     */
    double allow(int serverTick, double capTotal, double incomingFinal) {
        if (serverTick != windowTick) {
            windowTick = serverTick;
            inWindow = 0;
        }
        double allowed = Math.max(0.0, Math.min(incomingFinal, capTotal - inWindow));
        inWindow += allowed;
        return allowed;
    }
}
