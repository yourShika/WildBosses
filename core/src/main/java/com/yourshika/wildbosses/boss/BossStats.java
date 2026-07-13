package com.yourshika.wildbosses.boss;

/** Core combat attributes applied to a boss entity on spawn. */
public record BossStats(
        double health,
        double armor,
        double armorToughness,
        double knockbackResistance,
        double attackDamage,
        double movementSpeed,
        double followRange,
        double scale
) {
    public static BossStats defaults() {
        return new BossStats(200, 0, 0, 0, 6, 0.25, 40, 1.0);
    }
}
