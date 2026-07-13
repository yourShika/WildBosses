package com.yourshika.wildbosses.boss;

import org.bukkit.World;

import java.util.Set;

/**
 * Rules governing where/how often a boss may spawn from the random scheduler.
 *
 * @param environments      dimensions the boss may spawn in
 * @param weight            relative weight in weighted-random selection (higher = more common)
 * @param minPlayerDistance minimum distance from the anchoring player
 * @param minY              minimum spawn Y
 * @param maxY              maximum spawn Y
 * @param cooldownSeconds   minimum seconds between two spawns of this boss
 * @param maxConcurrent     maximum simultaneously active instances of this boss
 */
public record SpawnRules(
        Set<World.Environment> environments,
        int weight,
        double minPlayerDistance,
        int minY,
        int maxY,
        int cooldownSeconds,
        int maxConcurrent
) {
    public boolean allows(World.Environment env) {
        return environments.contains(env);
    }
}
