package com.yourshika.wildbosses.boss;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;

/** Called when a tracked boss dies, so the reward system can grant loot without tight coupling. */
public interface BossDeathListener {

    void onBossDeath(ActiveBoss boss, Player killer, EntityDeathEvent event);

    BossDeathListener NOOP = (boss, killer, event) -> {
    };
}
