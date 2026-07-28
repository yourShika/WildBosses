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
    /** Hard floor between two fires of the SAME timed skill, even if it's configured faster (anti-spam). */
    private static final int MIN_TIMER_TICKS = 3;
    /** Hard floor between two fires of the same EVENT skill (onDamaged/onDealDamage/...), even with no
     *  configured cooldown - so a flood of damage/events can't make it fire every tick. */
    private static final int MIN_EVENT_GAP = 8;

    /** Rate-limits the "ability spam throttled" warning to at most once per 5s per boss. */
    private final java.util.Map<java.util.UUID, Long> lastThrottleWarn = new java.util.HashMap<>();

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
                        // Floor the interval so a mis-set "interval: 1" can never spam every tick.
                        int interval = Math.max(MIN_TIMER_TICKS, s.triggerParams().getInt("interval", 100));
                        fire(boss, s, null, 0);
                        // Optional "interval-max" makes the skill fire at a random cadence in
                        // [interval, interval-max] instead of a robotic fixed rhythm.
                        int intervalMax = Math.max(interval, s.triggerParams().getInt("interval-max", interval));
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
                            int cd = Math.max(MIN_EVENT_GAP, s.cooldownTicks() > 0 ? s.cooldownTicks() : 40);
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
        lastThrottleWarn.remove(boss.entity().getUniqueId());
    }

    @Override
    public void onKillPlayer(ActiveBoss boss, Player victim) {
        fireByTrigger(boss, TriggerType.ON_KILL_PLAYER, victim, 0);
    }

    /**
     * Resolve targets and run a single mechanic right now with a fresh context - used by the {@code cast}
     * mechanic to fire its payload when a channel completes.
     */
    public void runMechanicNow(ActiveBoss boss, String mechanic, String targeter, com.yourshika.wildbosses.util.Params params) {
        SkillContext ctx = new SkillContext(plugin, boss, plugin.bossManager().currentTick());
        List<Target> targets = targeters.resolve(targeter, ctx, params);
        mechanics.run(mechanic, ctx, targets, params);
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
            // Every event-triggered skill is gated by a minimum gap, even with no configured cooldown,
            // so a flood of damage/events (e.g. a custom-enchant plugin's many hits) can't fire it every
            // tick. A larger configured cooldown still wins.
            if (now < boss.nextTick(i)) {
                continue;
            }
            if (fire(boss, s, trigger, amount)) {
                boss.setNextTick(i, now + Math.max(MIN_EVENT_GAP, s.cooldownTicks()));
            }
        }
    }

    private boolean fire(ActiveBoss boss, SkillDefinition s, Entity trigger, double amount) {
        long now = plugin.bossManager().currentTick();
        // Anti-spam safety net: no boss may cast more than the configured abilities per second, whatever
        // drove the trigger (a bad interval, an edited skill, an external plugin flooding it). Excess
        // casts are refused and a throttle warning is logged so it's visible.
        if (!boss.allowCast(now, plugin.config().maxAbilityCastsPerSecond())) {
            warnThrottled(boss, now);
            return false;
        }
        SkillContext ctx = new SkillContext(plugin, boss, now).withTrigger(trigger, amount);
        if (!conditions.allPass(s.conditions(), ctx)) {
            return false;
        }
        List<Target> targets = targeters.resolve(s.targeter(), ctx, s.params());
        mechanics.run(s.mechanic(), ctx, targets, s.params());
        return true;
    }

    /** Log (at most once per 5s per boss) that a boss hit the anti-spam cap - so runaway casting shows. */
    private void warnThrottled(ActiveBoss boss, long now) {
        java.util.UUID id = boss.entity().getUniqueId();
        Long last = lastThrottleWarn.get(id);
        if (last == null || now - last >= 100) {
            lastThrottleWarn.put(id, now);
            plugin.getLogger().warning("Boss '" + boss.def().id() + "' hit the ability-cast rate limit ("
                    + plugin.config().maxAbilityCastsPerSecond() + "/s) - throttling it to prevent spam."
                    + " If this repeats, check that boss' skills (a too-short interval) or an external"
                    + " plugin flooding it with damage/events.");
        }
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
