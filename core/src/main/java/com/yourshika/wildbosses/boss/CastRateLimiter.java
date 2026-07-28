package com.yourshika.wildbosses.boss;

/**
 * A per-boss anti-spam guard: allows at most {@code maxPerWindow} ability casts within a rolling
 * one-second (20-tick) window. Whatever drives a boss to fire abilities - a misconfigured interval,
 * an edited skill, an external plugin flooding damage/events - it can never run more than the cap per
 * second; excess casts are refused. Kept as its own class so the logic is unit-testable without a live
 * entity.
 */
final class CastRateLimiter {

    static final int WINDOW_TICKS = 20; // one second

    private long windowStart = Long.MIN_VALUE;
    private int countInWindow;

    /**
     * @param tick          the current server tick
     * @param maxPerWindow  the most casts allowed per {@link #WINDOW_TICKS}-tick window
     * @return true if a cast is allowed now (and counts it); false if the cap for this window is hit
     */
    boolean allow(long tick, int maxPerWindow) {
        if (maxPerWindow <= 0) {
            return true; // limiter disabled
        }
        if (windowStart == Long.MIN_VALUE || tick - windowStart >= WINDOW_TICKS || tick < windowStart) {
            windowStart = tick;
            countInWindow = 0;
        }
        if (countInWindow >= maxPerWindow) {
            return false;
        }
        countInWindow++;
        return true;
    }
}
