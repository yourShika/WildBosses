package com.yourshika.wildbosses.skill.target;

import com.yourshika.wildbosses.skill.SkillContext;
import com.yourshika.wildbosses.skill.Target;
import com.yourshika.wildbosses.util.Params;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Built-in targeters, keyed by the lower-case keyword used in YAML. */
public final class TargeterRegistry {

    private final Map<String, Targeter> targeters = new HashMap<>();

    public TargeterRegistry() {
        register("self", (ctx, p) -> List.of(Target.of(ctx.self())));
        register("self_location", (ctx, p) -> List.of(Target.of(ctx.location())));
        register("current_target", (ctx, p) -> {
            LivingEntity t = ctx.boss().target();
            if (t != null && t.isValid()) {
                return List.of(Target.of(t));
            }
            return nearestPlayer(ctx, p.getDouble("radius", 32));
        });
        register("nearest_player", (ctx, p) -> nearestPlayer(ctx, p.getDouble("radius", 32)));
        register("players_in_radius", (ctx, p) -> playersInRadius(ctx, p.getDouble("radius", 12)));
        register("all_players_in_radius", (ctx, p) -> playersInRadius(ctx, p.getDouble("radius", 12)));
        register("random_nearby", (ctx, p) -> {
            List<Target> all = playersInRadius(ctx, p.getDouble("radius", 16));
            if (all.isEmpty()) {
                return all;
            }
            return List.of(all.get(ThreadLocalRandom.current().nextInt(all.size())));
        });
    }

    public void register(String key, Targeter targeter) {
        targeters.put(key.toLowerCase(Locale.ROOT), targeter);
    }

    /** Resolve by keyword; unknown keywords default to {@code self}. */
    public List<Target> resolve(String key, SkillContext ctx, Params params) {
        Targeter t = targeters.getOrDefault(key == null ? "self" : key.toLowerCase(Locale.ROOT), null);
        if (t == null) {
            return List.of(Target.of(ctx.self()));
        }
        return t.resolve(ctx, params);
    }

    // ---- helpers --------------------------------------------------------------------------

    private static List<Target> playersInRadius(SkillContext ctx, double radius) {
        List<Target> out = new ArrayList<>();
        World world = ctx.world();
        Location loc = ctx.location();
        double rSq = radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.getGameMode().name().equals("SPECTATOR")) {
                continue;
            }
            if (p.getLocation().distanceSquared(loc) <= rSq) {
                out.add(Target.of(p));
            }
        }
        return out;
    }

    private static List<Target> nearestPlayer(SkillContext ctx, double radius) {
        World world = ctx.world();
        Location loc = ctx.location();
        double rSq = radius * radius;
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            if (p.getGameMode().name().equals("SPECTATOR")) {
                continue;
            }
            double d = p.getLocation().distanceSquared(loc);
            if (d <= rSq && d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best == null ? List.of() : List.of(Target.of(best));
    }
}
