package com.yourshika.wildbosses.skill;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.ActiveBoss;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Per-activation context passed to targeters, conditions and mechanics. */
public final class SkillContext {

    private final WildBossesPlugin plugin;
    private final ActiveBoss boss;
    private final long tick;

    private Entity triggerEntity;
    private double amount;

    public SkillContext(WildBossesPlugin plugin, ActiveBoss boss, long tick) {
        this.plugin = plugin;
        this.boss = boss;
        this.tick = tick;
    }

    public WildBossesPlugin plugin() {
        return plugin;
    }

    public ActiveBoss boss() {
        return boss;
    }

    public LivingEntity self() {
        return boss.entity();
    }

    public Location location() {
        return boss.location();
    }

    public World world() {
        return boss.entity().getWorld();
    }

    public long tick() {
        return tick;
    }

    /** The entity that triggered this skill (damager/victim), if any. */
    public Entity triggerEntity() {
        return triggerEntity;
    }

    public double amount() {
        return amount;
    }

    public SkillContext withTrigger(Entity triggerEntity, double amount) {
        this.triggerEntity = triggerEntity;
        this.amount = amount;
        return this;
    }
}
