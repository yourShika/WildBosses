package com.yourshika.wildbosses.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-boss anti-spam cap: a boss may cast at most N abilities per one-second window, no matter how
 * often something drives it to fire. Guards the "boss spammed an ability" report.
 */
class CastRateLimiterTest {

    @Test
    void allowsUpToTheCapThenRefusesWithinAWindow() {
        CastRateLimiter cap = new CastRateLimiter();
        int allowed = 0;
        for (int i = 0; i < 100; i++) {
            if (cap.allow(5, 12)) { // all in the same tick -> same window
                allowed++;
            }
        }
        assertEquals(12, allowed, "no more than the cap may fire within one window");
    }

    @Test
    void spreadOverAWindowStillCapsTheTotal() {
        CastRateLimiter cap = new CastRateLimiter();
        int allowed = 0;
        for (long t = 0; t < CastRateLimiter.WINDOW_TICKS; t++) { // ticks 0..19 = one window
            if (cap.allow(t, 12)) {
                allowed++;
            }
        }
        assertEquals(12, allowed);
    }

    @Test
    void budgetResetsWhenTheWindowAdvances() {
        CastRateLimiter cap = new CastRateLimiter();
        for (int i = 0; i < 12; i++) {
            assertTrue(cap.allow(0, 12));
        }
        assertFalse(cap.allow(0, 12));                       // window full at tick 0
        assertTrue(cap.allow(CastRateLimiter.WINDOW_TICKS, 12)); // next window -> fresh budget
    }

    @Test
    void zeroCapMeansUnlimited() {
        CastRateLimiter cap = new CastRateLimiter();
        for (int i = 0; i < 1000; i++) {
            assertTrue(cap.allow(5, 0));
        }
    }

    @Test
    void tickGoingBackwardsResetsRatherThanLocking() {
        CastRateLimiter cap = new CastRateLimiter();
        for (int i = 0; i < 12; i++) {
            cap.allow(1000, 12);
        }
        assertFalse(cap.allow(1000, 12));
        assertTrue(cap.allow(5, 12)); // earlier tick (e.g. after a reset) -> new window, not stuck
    }
}
