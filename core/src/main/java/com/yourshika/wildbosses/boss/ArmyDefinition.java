package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.util.Enums;

import java.util.List;

/**
 * Definition of an army encounter: waves of minions that spawn until a kill threshold is reached,
 * after which an outcome resolves.
 *
 * @param minions              minion templates (weighted)
 * @param killThreshold        kills required to resolve the encounter
 * @param waveSize             minions spawned per reinforcement wave
 * @param maxAlive             cap on simultaneously alive minions
 * @param reinforceIntervalTicks ticks between reinforcement waves
 * @param radius               spawn radius around the anchor
 * @param outcome              what happens once the threshold is reached
 * @param endBossId            boss id to spawn when {@code outcome == SPAWN_BOSS} (nullable)
 * @param timeoutSeconds       encounter time limit (0 = none)
 */
public record ArmyDefinition(
        List<ArmyMinion> minions,
        int killThreshold,
        List<Integer> stages,
        int waveSize,
        int maxAlive,
        int reinforceIntervalTicks,
        double radius,
        Outcome outcome,
        String endBossId,
        int timeoutSeconds
) {
    /** Kill target for stage {@code index} (0-based). */
    public int stageTarget(int index) {
        if (index >= 0 && index < stages.size()) {
            return stages.get(index);
        }
        return killThreshold;
    }

    public int stageCount() {
        return Math.max(1, stages.size());
    }
    /** What happens when an army's kill threshold is reached. */
    public enum Outcome {
        /** Spawn a linked end-boss. */
        SPAWN_BOSS,
        /** Remaining minions flee and despawn. */
        FLEE,
        /** Encounter simply ends. */
        CLEARED;

        public static Outcome fromString(String s, Outcome fallback) {
            if (s == null || s.isBlank()) {
                return fallback;
            }
            try {
                return valueOf(Enums.constantCase(s));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }
}
