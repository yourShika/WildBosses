package com.yourshika.wildbosses.skill.target;

import com.yourshika.wildbosses.skill.SkillContext;
import com.yourshika.wildbosses.skill.Target;
import com.yourshika.wildbosses.util.Params;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
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
        // Positional targeters: positioning now matters. min-dot 1 = dead ahead, 0.5 = ~60deg cone.
        register("frontal_cone", (ctx, p) -> frontalCone(ctx, p.getDouble("radius", 8), p.getDouble("min-dot", 0.5)));
        register("lowest_health", (ctx, p) -> byHealth(ctx, p.getDouble("radius", 32), true));
        register("highest_health", (ctx, p) -> byHealth(ctx, p.getDouble("radius", 32), false));
        register("farthest_player", (ctx, p) -> farthestPlayer(ctx, p.getDouble("radius", 48)));
        register("highest_threat", (ctx, p) -> highestThreat(ctx, p.getDouble("radius", 48)));
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

    private static boolean eligible(Player p, Location loc, double rSq) {
        return !p.getGameMode().name().equals("SPECTATOR")
                && !p.getGameMode().name().equals("CREATIVE")
                && p.getLocation().distanceSquared(loc) <= rSq;
    }

    /** Players roughly in front of the boss (their bearing aligns with its facing). */
    private static List<Target> frontalCone(SkillContext ctx, double radius, double minDot) {
        Location eye = ctx.self().getEyeLocation();
        Vector facing = eye.getDirection();
        double rSq = radius * radius;
        List<Target> out = new ArrayList<>();
        for (Player p : ctx.world().getPlayers()) {
            if (!eligible(p, eye, rSq)) {
                continue;
            }
            Vector to = p.getEyeLocation().toVector().subtract(eye.toVector());
            if (to.lengthSquared() < 1.0E-4 || facing.dot(to.normalize()) >= minDot) {
                out.add(Target.of(p));
            }
        }
        return out;
    }

    /** The single lowest- (or highest-) health player in radius. */
    private static List<Target> byHealth(SkillContext ctx, double radius, boolean lowest) {
        Location loc = ctx.location();
        double rSq = radius * radius;
        Player best = null;
        double bestHp = lowest ? Double.MAX_VALUE : -1;
        for (Player p : ctx.world().getPlayers()) {
            if (!eligible(p, loc, rSq)) {
                continue;
            }
            double hp = p.getHealth();
            if (lowest ? hp < bestHp : hp > bestHp) {
                bestHp = hp;
                best = p;
            }
        }
        return best == null ? List.of() : List.of(Target.of(best));
    }

    /** The farthest player still within radius (backline punish). */
    private static List<Target> farthestPlayer(SkillContext ctx, double radius) {
        Location loc = ctx.location();
        double rSq = radius * radius;
        Player best = null;
        double bestDist = -1;
        for (Player p : ctx.world().getPlayers()) {
            if (!eligible(p, loc, rSq)) {
                continue;
            }
            double d = p.getLocation().distanceSquared(loc);
            if (d > bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best == null ? List.of() : List.of(Target.of(best));
    }

    /** The player who has dealt the most damage to this boss (falls back to nearest). */
    private static List<Target> highestThreat(SkillContext ctx, double radius) {
        Map<UUID, Double> dmg = ctx.boss().damageByPlayer();
        Location loc = ctx.location();
        double rSq = radius * radius;
        Player best = null;
        double bestDmg = -1;
        for (Player p : ctx.world().getPlayers()) {
            if (!eligible(p, loc, rSq)) {
                continue;
            }
            double d = dmg.getOrDefault(p.getUniqueId(), 0.0);
            if (d > bestDmg) {
                bestDmg = d;
                best = p;
            }
        }
        return best != null && bestDmg > 0 ? List.of(Target.of(best)) : nearestPlayer(ctx, radius);
    }
}
