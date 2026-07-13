package com.yourshika.wildbosses.listener;

import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.boss.BossManager;
import com.yourshika.wildbosses.util.Keys;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.persistence.PersistentDataType;

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

    /**
     * Prevent WildBosses entities from transforming (e.g. a Piglin Brute boss zombifying when struck
     * by lightning, or an infected minion turning into a Drowned).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        var pdc = event.getEntity().getPersistentDataContainer();
        if (pdc.has(Keys.BOSS_ID, PersistentDataType.STRING)
                || pdc.has(Keys.ARMY_ID, PersistentDataType.STRING)
                || pdc.has(Keys.ENCOUNTER_ID, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}

