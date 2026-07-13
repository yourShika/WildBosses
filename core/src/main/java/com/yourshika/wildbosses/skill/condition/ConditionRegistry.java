package com.yourshika.wildbosses.skill.condition;

import com.yourshika.wildbosses.skill.ConditionDefinition;
import com.yourshika.wildbosses.skill.SkillContext;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Built-in skill conditions. */
public final class ConditionRegistry {

    private final Map<String, Condition> conditions = new HashMap<>();

    public ConditionRegistry() {
        register("health_below", (ctx, p) -> ctx.boss().healthPercent() <= p.getDouble("value", 50));
        register("health_above", (ctx, p) -> ctx.boss().healthPercent() >= p.getDouble("value", 50));
        register("phase_equals", (ctx, p) -> ctx.boss().phaseIndex() == p.getInt("phase", 0));
        register("phase_at_least", (ctx, p) -> ctx.boss().phaseIndex() >= p.getInt("phase", 0));
        register("chance", (ctx, p) -> ThreadLocalRandom.current().nextDouble() <= p.getDouble("value", 1.0));
        register("world_is", (ctx, p) -> {
            String env = ctx.world().getEnvironment().name();
            for (String want : p.getStringList("value")) {
                String w = want.trim().toUpperCase(Locale.ROOT);
                if (w.equals(env) || (w.equals("OVERWORLD") && env.equals("NORMAL"))
                        || (w.equals("END") && env.equals("THE_END"))) {
                    return true;
                }
            }
            return false;
        });
        register("players_in_radius", (ctx, p) -> countPlayers(ctx, p.getDouble("radius", 16)) >= p.getInt("count", 1));
    }

    public void register(String key, Condition condition) {
        conditions.put(key.toLowerCase(Locale.ROOT), condition);
    }

    /** All conditions must pass. Unknown condition types are treated as failing (fail-safe). */
    public boolean allPass(List<ConditionDefinition> defs, SkillContext ctx) {
        for (ConditionDefinition def : defs) {
            Condition c = conditions.get(def.type());
            if (c == null) {
                ctx.plugin().getLogger().warning("Unknown skill condition: " + def.type());
                return false;
            }
            if (!c.test(ctx, def.params())) {
                return false;
            }
        }
        return true;
    }

    private static int countPlayers(SkillContext ctx, double radius) {
        Location loc = ctx.location();
        double rSq = radius * radius;
        int n = 0;
        for (Player p : ctx.world().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rSq) {
                n++;
            }
        }
        return n;
    }
}
