package com.yourshika.wildbosses.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bosses must never spawn on or past the world border. Example: a 50 000-wide border centred at 0,0
 * (the user's "25k + and -"): valid spawns stay within +/-25 000 minus the safety margin.
 */
class SpawnBorderTest {

    // 50 000 side length centred at origin => edges at +/-25 000.
    private static final double SIZE = 50_000;
    private static final int MARGIN = 16;

    @Test
    void spotsWellInsideAreAllowed() {
        assertTrue(SpawnScheduler.insideBorder(0, 0, SIZE, 0, 0, MARGIN));
        assertTrue(SpawnScheduler.insideBorder(0, 0, SIZE, 24_000, -24_000, MARGIN));
    }

    @Test
    void spotsPastTheBorderAreRejected() {
        assertFalse(SpawnScheduler.insideBorder(0, 0, SIZE, 25_001, 0, MARGIN)); // past +X edge
        assertFalse(SpawnScheduler.insideBorder(0, 0, SIZE, 0, -30_000, MARGIN)); // well past -Z edge
        assertFalse(SpawnScheduler.insideBorder(0, 0, SIZE, 40_000, 40_000, MARGIN));
    }

    @Test
    void theMarginKeepsSpawnsOffTheVeryEdge() {
        assertFalse(SpawnScheduler.insideBorder(0, 0, SIZE, 24_990, 0, MARGIN)); // inside edge but within margin
        assertTrue(SpawnScheduler.insideBorder(0, 0, SIZE, 24_980, 0, MARGIN));  // just past the margin -> ok
    }

    @Test
    void offCentreBorderIsHandled() {
        // Border centred at 1000,2000.
        assertTrue(SpawnScheduler.insideBorder(1000, 2000, SIZE, 1000, 2000, MARGIN));
        assertFalse(SpawnScheduler.insideBorder(1000, 2000, SIZE, 1000 + 25_001, 2000, MARGIN));
    }

    @Test
    void borderSmallerThanMarginAllowsNothing() {
        assertFalse(SpawnScheduler.insideBorder(0, 0, 20, 0, 0, MARGIN)); // 10-block half < 16 margin
    }
}
