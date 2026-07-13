package com.yourshika.wildbosses.skill;

import com.yourshika.wildbosses.boss.ActiveBoss;
import org.bukkit.entity.Entity;

/**
 * Fires boss skills in response to runtime events. The default implementation lives in
 * {@code DefaultSkillEngine}; a {@link #NOOP} is used until the engine is wired.
 */
public interface SkillEngine {

    void onSpawn(ActiveBoss boss);

    void onTick(ActiveBoss boss, long tick);

    void onDamaged(ActiveBoss boss, Entity damager, double amount);

    void onDealDamage(ActiveBoss boss, Entity victim, double amount);

    void onPhaseChange(ActiveBoss boss, int newPhaseIndex);

    void onDeath(ActiveBoss boss);

    SkillEngine NOOP = new SkillEngine() {
        @Override
        public void onSpawn(ActiveBoss boss) {
        }

        @Override
        public void onTick(ActiveBoss boss, long tick) {
        }

        @Override
        public void onDamaged(ActiveBoss boss, Entity damager, double amount) {
        }

        @Override
        public void onDealDamage(ActiveBoss boss, Entity victim, double amount) {
        }

        @Override
        public void onPhaseChange(ActiveBoss boss, int newPhaseIndex) {
        }

        @Override
        public void onDeath(ActiveBoss boss) {
        }
    };
}
