package com.yourshika.wildbosses.skill;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.skill.condition.ConditionRegistry;
import com.yourshika.wildbosses.skill.mechanic.MechanicRegistry;
import com.yourshika.wildbosses.skill.target.TargeterRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Default skill engine: matches a boss' skills against runtime triggers, checks conditions,
 * resolves targeters and runs mechanics. Timer/health/range triggers are driven from the tick loop;
 * combat/phase/death triggers are driven from events.
 */
public final class DefaultSkillEngine implements SkillEngine {

    private static final long ONCE = Long.MAX_VALUE;

    private final WildBossesPlugin plugin;
    private final TargeterRegistry targeters = new TargeterRegistry();
    private final ConditionRegistry conditions = new ConditionRegistry();
    private final MechanicRegistry mechanics = new MechanicRegistry();

    public DefaultSkillEngine(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public TargeterRegistry targeters() {
        return targeters;
    }

    public ConditionRegistry conditions() {
        return conditions;
    }

    public MechanicRegistry mechanics() {
        return mechanics;
    }

    @Override
    public void onSpawn(ActiveBoss boss) {
        fireByTrigger(boss, TriggerType.ON_SPAWN, null, 0);
    }

    @Override
    public void onTick(ActiveBoss boss, long tick) {
        List<SkillDefinition> skills = boss.def().skills();
        for (int i = 0; i < skills.size(); i++) {
            SkillDefinition s = skills.get(i);
            switch (s.trigger()) {
                case ON_TIMER -> {
                    if (tick >= boss.nextTick(i)) {
                        int interval = Math.max(1, s.triggerParams().getInt("interval", 100));
                        fire(boss, s, null, 0);
                        // Optional "interval-max" makes the skill fire at a random cadence in
                        // [interval, interval-max] instead of a robotic fixed rhythm.
                        int intervalMax = s.triggerParams().getInt("interval-max", interval);
                        int next = intervalMax > interval
                                ? interval + java.util.concurrent.ThreadLocalRandom.current().nextInt(intervalMax - interval + 1)
                                : interval;
                        boss.setNextTick(i, tick + next);
                    }
                }
                case ON_HEALTH_BELOW -> {
                    double value = s.triggerParams().getDouble("value", 50);
                    if (boss.nextTick(i) != ONCE && boss.healthPercent() <= value) {
                        fire(boss, s, null, 0);
                        boss.setNextTick(i, ONCE);
                    }
                }
                case ON_TARGET_IN_RANGE -> {
                    if (tick >= boss.nextTick(i)) {
                        double radius = s.triggerParams().getDouble("radius", 12);
                        if (anyPlayerInRange(boss, radius)) {
                            fire(boss, s, null, 0);
                            int cd = s.cooldownTicks() > 0 ? s.cooldownTicks() : 40;
                            boss.setNextTick(i, tick + cd);
                        }
                    }
                }
                default -> {
                    // combat/phase/death triggers handled elsewhere
                }
            }
        }
    }

    @Override
    public void onDamaged(ActiveBoss boss, Entity damager, double amount) {
        fireByTrigger(boss, TriggerType.ON_DAMAGED, damager, amount);
    }

    @Override
    public void onDealDamage(ActiveBoss boss, Entity victim, double amount) {
        fireByTrigger(boss, TriggerType.ON_DEAL_DAMAGE, victim, amount);
    }

    @Override
    public void onPhaseChange(ActiveBoss boss, int newPhaseIndex) {
        List<SkillDefinition> skills = boss.def().skills();
        for (SkillDefinition s : skills) {
            if (s.trigger() != TriggerType.ON_PHASE_CHANGE) {
                continue;
            }
            if (s.triggerParams().has("phase") && s.triggerParams().getInt("phase", -999) != newPhaseIndex) {
                continue;
            }
            fire(boss, s, null, 0);
        }
    }

    @Override
    public void onDeath(ActiveBoss boss) {
        fireByTrigger(boss, TriggerType.ON_DEATH, null, 0);
    }

    // ---- internals ------------------------------------------------------------------------

    private void fireByTrigger(ActiveBoss boss, TriggerType type, Entity trigger, double amount) {
        long now = plugin.bossManager().currentTick();
        List<SkillDefinition> skills = boss.def().skills();
        for (int i = 0; i < skills.size(); i++) {
            SkillDefinition s = skills.get(i);
            if (s.trigger() != type) {
                continue;
            }
            if (s.cooldownTicks() > 0 && now < boss.nextTick(i)) {
                continue;
            }
            if (fire(boss, s, trigger, amount) && s.cooldownTicks() > 0) {
                boss.setNextTick(i, now + s.cooldownTicks());
            }
        }
    }

    private boolean fire(ActiveBoss boss, SkillDefinition s, Entity trigger, double amount) {
        SkillContext ctx = new SkillContext(plugin, boss, plugin.bossManager().currentTick())
                .withTrigger(trigger, amount);
        if (!conditions.allPass(s.conditions(), ctx)) {
            return false;
        }
        List<Target> targets = targeters.resolve(s.targeter(), ctx, s.params());
        mechanics.run(s.mechanic(), ctx, targets, s.params());
        return true;
    }

    private boolean anyPlayerInRange(ActiveBoss boss, double radius) {
        double rSq = radius * radius;
        Location loc = boss.location();
        for (Player p : boss.entity().getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rSq) {
                return true;
            }
        }
        return false;
    }
}
