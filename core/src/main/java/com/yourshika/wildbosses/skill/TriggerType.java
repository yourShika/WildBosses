package com.yourshika.wildbosses.skill;

import com.yourshika.wildbosses.util.Enums;

/**
 * When a boss skill fires. Parsed leniently from config ({@code onTimer}, {@code on_timer}, ...).
 */
public enum TriggerType {

    /** Once, when the boss spawns. */
    ON_SPAWN,
    /** Repeatedly on a fixed tick interval (param {@code interval}). */
    ON_TIMER,
    /** When the boss takes damage. */
    ON_DAMAGED,
    /** When the boss deals melee damage. */
    ON_DEAL_DAMAGE,
    /** When the boss changes phase. */
    ON_PHASE_CHANGE,
    /** Once, when the boss first drops below {@code value} percent health. */
    ON_HEALTH_BELOW,
    /** When a player enters {@code radius} of the boss (rate-limited by cooldown). */
    ON_TARGET_IN_RANGE,
    /** When the boss dies. */
    ON_DEATH;

    public static TriggerType fromString(String s, TriggerType fallback) {
        if (s == null || s.isBlank()) {
            return fallback;
        }
        String c = Enums.constantCase(s);
        if (!c.startsWith("ON_")) {
            c = "ON_" + c;
        }
        try {
            return valueOf(c);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
