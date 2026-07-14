package com.yourshika.wildbosses.listener;

import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.boss.BossDefinition;
import com.yourshika.wildbosses.boss.BossManager;
import com.yourshika.wildbosses.util.Keys;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.persistence.PersistentDataType;

/** Bridges Bukkit combat/death events into the boss runtime. */
public final class BossListener implements Listener {

    private final BossManager manager;

    public BossListener(BossManager manager) {
        this.manager = manager;
    }

    /** Cancel damage a boss is immune to. Runs early (LOW) so immune hits are ignored downstream. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageCause(EntityDamageEvent event) {
        ActiveBoss boss = manager.get(event.getEntity());
        if (boss == null || boss.def().immunities().isEmpty()) {
            return;
        }
        if (isImmune(boss.def(), event.getCause().name())) {
            event.setCancelled(true);
        }
    }

    private static boolean isImmune(BossDefinition def, String cause) {
        return switch (cause) {
            case "PROJECTILE" -> def.immuneTo("PROJECTILE");
            case "FIRE", "FIRE_TICK", "LAVA", "HOT_FLOOR" -> def.immuneTo("FIRE");
            case "FALL" -> def.immuneTo("FALL");
            case "DROWNING" -> def.immuneTo("DROWNING");
            case "BLOCK_EXPLOSION", "ENTITY_EXPLOSION" -> def.immuneTo("EXPLOSION");
            case "WITHER" -> def.immuneTo("WITHER");
            case "MAGIC" -> def.immuneTo("MAGIC");
            case "POISON" -> def.immuneTo("POISON");
            default -> false;
        };
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
     * Stop a boss Creeper from self-detonating (and dying) when a player gets close. The boss only
     * "explodes" through its scripted skills; its own vanilla ignition is cancelled so the fight lasts.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (manager.get(event.getEntity()) != null) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Creeper creeper) {
                creeper.setIgnited(false);
                creeper.setFuseTicks(creeper.getMaxFuseTicks());
            }
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

