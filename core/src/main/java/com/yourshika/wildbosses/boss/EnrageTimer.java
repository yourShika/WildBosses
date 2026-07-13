package com.yourshika.wildbosses.boss;

/**
 * Soft-enrage: once a boss has been alive for {@code afterSeconds}, every {@code intervalSeconds} its
 * attack damage and movement speed are multiplied again (a stacking ramp) so fights can't drag on.
 */
public record EnrageTimer(
        boolean enabled,
        int afterSeconds,
        int intervalSeconds,
        double damageMult,
        double speedMult
) {
    public static EnrageTimer disabled() {
        return new EnrageTimer(false, 300, 30, 1.1, 1.05);
    }
}
