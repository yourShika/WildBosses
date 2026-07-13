package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.model.ModelHandle;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state for a single spawned boss: its entity, boss bar, model handle, current phase and
 * per-skill timers. Also manages which nearby players see the boss bar.
 */
public final class ActiveBoss {

    /** Range (blocks) at which players are shown the boss bar. */
    private static final double BOSS_BAR_RANGE = 64.0;

    private final BossDefinition def;
    private final LivingEntity entity;
    private final BossBar bossBar;
    private final ModelHandle model;
    private final double maxHealth;
    private final long spawnTick;
    private final String encounterId;

    private final Map<Integer, Long> skillNextTick = new HashMap<>();
    private final Set<UUID> viewers = new HashSet<>();

    private int phaseIndex = -1;
    private LivingEntity target;
    private boolean removed;

    private long fleeAtTick;
    private String currentAnimState = "";
    private long attackHoldUntil;

    public ActiveBoss(BossDefinition def, LivingEntity entity, BossBar bossBar, ModelHandle model,
                      double maxHealth, long spawnTick, String encounterId) {
        this.def = def;
        this.entity = entity;
        this.bossBar = bossBar;
        this.model = model;
        this.maxHealth = maxHealth;
        this.spawnTick = spawnTick;
        this.encounterId = encounterId;
    }

    public BossDefinition def() {
        return def;
    }

    public LivingEntity entity() {
        return entity;
    }

    public BossBar bossBar() {
        return bossBar;
    }

    public ModelHandle model() {
        return model;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public long spawnTick() {
        return spawnTick;
    }

    public String encounterId() {
        return encounterId;
    }

    public Location location() {
        return entity.getLocation();
    }

    public boolean isValid() {
        return !removed && entity.isValid() && !entity.isDead();
    }

    public double healthPercent() {
        if (maxHealth <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (entity.getHealth() / maxHealth) * 100.0));
    }

    public int phaseIndex() {
        return phaseIndex;
    }

    public void setPhaseIndex(int phaseIndex) {
        this.phaseIndex = phaseIndex;
    }

    public LivingEntity target() {
        return target;
    }

    public void setTarget(LivingEntity target) {
        this.target = target;
    }

    public long fleeAtTick() {
        return fleeAtTick;
    }

    public void setFleeAtTick(long fleeAtTick) {
        this.fleeAtTick = fleeAtTick;
    }

    public String currentAnimState() {
        return currentAnimState;
    }

    public void setCurrentAnimState(String currentAnimState) {
        this.currentAnimState = currentAnimState;
    }

    public long attackHoldUntil() {
        return attackHoldUntil;
    }

    public void setAttackHoldUntil(long attackHoldUntil) {
        this.attackHoldUntil = attackHoldUntil;
    }

    // ---- skill timers ---------------------------------------------------------------------

    public long nextTick(int skillIndex) {
        return skillNextTick.getOrDefault(skillIndex, 0L);
    }

    public void setNextTick(int skillIndex, long tick) {
        skillNextTick.put(skillIndex, tick);
    }

    // ---- boss bar -------------------------------------------------------------------------

    /** Recompute the bar progress/title and show/hide it based on nearby players. */
    public void updateBossBar() {
        bossBar.progress((float) Math.max(0, Math.min(1, entity.getHealth() / maxHealth)));

        World world = entity.getWorld();
        Location loc = entity.getLocation();
        double rangeSq = BOSS_BAR_RANGE * BOSS_BAR_RANGE;

        Set<UUID> near = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rangeSq) {
                near.add(p.getUniqueId());
                if (viewers.add(p.getUniqueId())) {
                    p.showBossBar(bossBar);
                }
            }
        }
        Iterator<UUID> it = viewers.iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!near.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(bossBar);
                }
                it.remove();
            }
        }
    }

    private void hideFromAll() {
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bossBar);
            }
        }
        viewers.clear();
    }

    /** Remove the boss bar and model. Optionally removes the entity too. */
    public void cleanup(boolean removeEntity) {
        if (removed) {
            return;
        }
        removed = true;
        hideFromAll();
        try {
            model.remove();
        } catch (Throwable ignored) {
        }
        if (removeEntity && entity.isValid()) {
            entity.remove();
        }
    }

    /** The display name used on the bar and nametag (difficulty is intentionally not shown here). */
    public Component displayName() {
        return Text.mm(def.name());
    }
}
