package com.yourshika.wildbosses.boss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The cumulative one-shot cap. This guards the "instant death during a fight" bug: a single swing that
 * fires several bonus-damage procs (e.g. a custom-enchant plugin) produces multiple damage events in
 * ONE tick; capping each event individually still let them sum past a full-HP boss's health. The tick
 * total must never exceed the cap.
 */
class TickDamageCapTest {

    private static final double CAP = 230; // e.g. 50% of a 460 HP boss

    @Test
    void multipleProcsInOneTickCannotExceedTheCap() {
        TickDamageCap cap = new TickDamageCap();
        // Four 200-damage procs land in the same tick; total allowed must be exactly the cap.
        double total = 0;
        for (int i = 0; i < 4; i++) {
            total += cap.allow(100, CAP, 200);
        }
        assertEquals(CAP, total, 1e-9, "one tick's total damage must be clamped to the cap");
    }

    @Test
    void firstProcGetsFullValueUpToTheCap() {
        TickDamageCap cap = new TickDamageCap();
        assertEquals(200, cap.allow(100, CAP, 200), 1e-9); // under the cap: full
        assertEquals(30, cap.allow(100, CAP, 200), 1e-9);  // only 30 left before hitting 230
        assertEquals(0, cap.allow(100, CAP, 200), 1e-9);   // cap reached: nothing more this tick
    }

    @Test
    void windowResetsWhenTheServerTickAdvances() {
        TickDamageCap cap = new TickDamageCap();
        assertEquals(CAP, cap.allow(100, CAP, 999), 1e-9); // tick 100: clamp to cap
        assertEquals(0, cap.allow(100, CAP, 999), 1e-9);   // still tick 100: exhausted
        assertEquals(CAP, cap.allow(101, CAP, 999), 1e-9); // tick 101: fresh budget
    }

    @Test
    void neverReturnsNegativeWhenAlreadyOverCap() {
        TickDamageCap cap = new TickDamageCap();
        cap.allow(5, CAP, 500); // clamps to 230, window now full
        assertEquals(0, cap.allow(5, CAP, 500), 1e-9); // no negative "credit"
    }
}
