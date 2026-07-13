package com.yourshika.wildbosses.listener;

import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.boss.BossManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Bridges Bukkit combat/death events into the boss runtime. */
public final class BossListener implements Listener {

    private final BossManager manager;

    public BossListener(BossManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        ActiveBoss attacker = manager.get(event.getDamager());
        if (attacker != null) {
            manager.onBossDealtDamage(attacker, event.getEntity(), event.getFinalDamage());
        }
        if (event.getEntity() instanceof LivingEntity) {
            ActiveBoss victim = manager.get(event.getEntity());
            if (victim != null) {
                manager.onBossDamaged(victim, event.getDamager(), event.getFinalDamage());
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        ActiveBoss boss = manager.get(event.getEntity());
        if (boss != null) {
            manager.handleDeath(boss, event);
        }
    }
}
