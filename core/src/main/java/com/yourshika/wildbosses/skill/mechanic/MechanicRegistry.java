package com.yourshika.wildbosses.skill.mechanic;

import com.yourshika.wildbosses.skill.SkillContext;
import com.yourshika.wildbosses.skill.Target;
import com.yourshika.wildbosses.util.Effects;
import com.yourshika.wildbosses.util.Keys;
import com.yourshika.wildbosses.util.Params;
import com.yourshika.wildbosses.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.WitherSkull;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Built-in mechanics, keyed by the lower-case keyword used in YAML. Each mechanic is data-driven
 * via its {@link Params}. Registering a new keyword here makes it available to every boss.
 */
public final class MechanicRegistry {

    private final Map<String, Mechanic> mechanics = new HashMap<>();

    public MechanicRegistry() {
        register("message", MechanicRegistry::message);
        register("sound", MechanicRegistry::sound);
        register("particle", MechanicRegistry::particle);
        register("damage", MechanicRegistry::damage);
        register("aoe_damage", MechanicRegistry::aoeDamage);
        register("summon", MechanicRegistry::summon);
        register("potion", MechanicRegistry::potion);
        register("potion_cloud", MechanicRegistry::potionCloud);
        register("poison", MechanicRegistry::poison);
        register("knockback", MechanicRegistry::knockback);
        register("pull", MechanicRegistry::pull);
        register("leap", MechanicRegistry::leap);
        register("teleport", MechanicRegistry::teleport);
        register("teleport_target", MechanicRegistry::teleportTarget);
        register("lightning", MechanicRegistry::lightning);
        register("explode", MechanicRegistry::explode);
        register("arrow_volley", MechanicRegistry::arrowVolley);
        register("beam", MechanicRegistry::beam);
        register("charm", MechanicRegistry::charm);
        register("buff", MechanicRegistry::buff);
        register("heal", MechanicRegistry::heal);
        register("shield", MechanicRegistry::shield);
        register("command", MechanicRegistry::command);
        register("projectile", MechanicRegistry::projectile);
    }

    public void register(String key, Mechanic mechanic) {
        mechanics.put(key.toLowerCase(Locale.ROOT), mechanic);
    }

    public boolean has(String key) {
        return key != null && mechanics.containsKey(key.toLowerCase(Locale.ROOT));
    }

    public void run(String key, SkillContext ctx, List<Target> targets, Params params) {
        Mechanic m = mechanics.get(key == null ? "" : key.toLowerCase(Locale.ROOT));
        if (m == null) {
            ctx.plugin().getLogger().warning("Unknown mechanic: " + key);
            return;
        }
        try {
            m.run(ctx, targets, params);
        } catch (Exception ex) {
            ctx.plugin().getLogger().warning("Mechanic '" + key + "' failed for boss "
                    + ctx.boss().def().id() + ": " + ex.getMessage());
        }
    }

    // ---- mechanics ------------------------------------------------------------------------

    private static void message(SkillContext ctx, List<Target> targets, Params p) {
        var component = Text.mm(p.getString("message", ""));
        boolean any = false;
        for (Target t : targets) {
            if (t.entity() instanceof Player player) {
                player.sendMessage(component);
                any = true;
            }
        }
        if (!any && p.getBoolean("broadcast", false)) {
            Bukkit.broadcast(component);
        }
    }

    private static void sound(SkillContext ctx, List<Target> targets, Params p) {
        String sound = p.getString("sound", null);
        if (sound == null) {
            return;
        }
        float volume = (float) p.getDouble("volume", 1.0);
        float pitch = (float) p.getDouble("pitch", 1.0);
        for (Location loc : locations(ctx, targets)) {
            loc.getWorld().playSound(loc, sound.toLowerCase(Locale.ROOT), volume, pitch);
        }
    }

    private static void particle(SkillContext ctx, List<Target> targets, Params p) {
        Particle particle = enumOr(Particle.class, p.getString("particle", "CRIT"), Particle.CRIT);
        int count = p.getInt("count", 20);
        double spread = p.getDouble("spread", 0.5);
        double speed = p.getDouble("speed", 0.0);
        double offsetY = p.getDouble("offset-y", 1.0);
        for (Location loc : locations(ctx, targets)) {
            loc.getWorld().spawnParticle(particle, loc.clone().add(0, offsetY, 0), count, spread, spread, spread, speed);
        }
    }

    private static void damage(SkillContext ctx, List<Target> targets, Params p) {
        double amount = p.getDouble("amount", p.getDouble("damage", 4));
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                t.entity().damage(amount, ctx.self());
            }
        }
    }

    private static void aoeDamage(SkillContext ctx, List<Target> targets, Params p) {
        double radius = p.getDouble("radius", 6);
        double dmg = p.getDouble("damage", p.getDouble("amount", 6));
        double knockback = p.getDouble("knockback", 0);
        String particleName = p.getString("particle", null);
        Location center = ctx.location();
        if (particleName != null) {
            Particle particle = enumOr(Particle.class, particleName, Particle.EXPLOSION);
            center.getWorld().spawnParticle(particle, center.clone().add(0, 1, 0), 40, radius / 2, 1, radius / 2, 0.02);
        }
        double rSq = radius * radius;
        for (Player player : center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(center) <= rSq) {
                player.damage(dmg, ctx.self());
                if (knockback > 0) {
                    pushAway(player, center, knockback, 0.35);
                }
            }
        }
    }

    private static void summon(SkillContext ctx, List<Target> targets, Params p) {
        EntityType type = enumOr(EntityType.class, p.getString("type", "ZOMBIE"), EntityType.ZOMBIE);
        int amount = Math.max(1, p.getInt("amount", 3));
        double radius = p.getDouble("radius", 3);
        double health = p.getDouble("health", 0);
        boolean baby = p.getBoolean("baby", false);
        List<PotionEffect> effects = parseEffects(p.getStringList("effects"));
        Location base = targets.isEmpty() ? ctx.location() : targets.get(0).location();
        World world = base.getWorld();
        for (int i = 0; i < amount; i++) {
            Location loc = base.clone().add(rand(radius), 0, rand(radius));
            Entity e = world.spawnEntity(loc, type);
            e.getPersistentDataContainer().set(Keys.ENCOUNTER_ID, PersistentDataType.STRING, ctx.boss().encounterId());
            if (e instanceof LivingEntity le) {
                if (health > 0) {
                    AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) {
                        max.setBaseValue(health);
                        le.setHealth(health);
                    }
                }
                for (PotionEffect eff : effects) {
                    le.addPotionEffect(eff);
                }
            }
            if (baby && e instanceof org.bukkit.entity.Ageable ageable) {
                ageable.setBaby();
            }
            if (e instanceof Mob mob && ctx.boss().target() != null) {
                mob.setTarget(ctx.boss().target());
            }
        }
    }

    private static void potion(SkillContext ctx, List<Target> targets, Params p) {
        PotionEffectType type = Effects.type(p.getString("type", "SLOWNESS"));
        if (type == null) {
            return;
        }
        int duration = p.getInt("duration", 100);
        int amplifier = p.getInt("amplifier", 0);
        PotionEffect eff = new PotionEffect(type, duration, amplifier, false, true, true);
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                t.entity().addPotionEffect(eff);
            }
        }
    }

    private static void potionCloud(SkillContext ctx, List<Target> targets, Params p) {
        PotionEffectType type = Effects.type(p.getString("type", "POISON"));
        int duration = p.getInt("duration", 100);
        int amplifier = p.getInt("amplifier", 0);
        float radius = (float) p.getDouble("radius", 3);
        int cloudDuration = p.getInt("cloud-duration", 100);
        for (Location loc : locations(ctx, targets)) {
            org.bukkit.entity.AreaEffectCloud cloud =
                    loc.getWorld().spawn(loc, org.bukkit.entity.AreaEffectCloud.class);
            cloud.setRadius(radius);
            cloud.setDuration(cloudDuration);
            if (type != null) {
                cloud.addCustomEffect(new PotionEffect(type, duration, amplifier, false, true, true), true);
            }
        }
    }

    private static void poison(SkillContext ctx, List<Target> targets, Params p) {
        int duration = p.getInt("duration", 120);
        int amplifier = p.getInt("amplifier", 0);
        PotionEffect eff = new PotionEffect(PotionEffectType.POISON, duration, amplifier, false, true, true);
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                t.entity().addPotionEffect(eff);
            }
        }
    }

    private static void knockback(SkillContext ctx, List<Target> targets, Params p) {
        double strength = p.getDouble("strength", 1.0);
        double vertical = p.getDouble("vertical", 0.4);
        Location from = ctx.location();
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                pushAway(t.entity(), from, strength, vertical);
            }
        }
    }

    private static void pull(SkillContext ctx, List<Target> targets, Params p) {
        double strength = p.getDouble("strength", 1.0);
        Location to = ctx.location();
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                LivingEntity e = t.entity();
                Vector dir = to.toVector().subtract(e.getLocation().toVector());
                if (dir.lengthSquared() < 1.0E-4) {
                    continue;
                }
                e.setVelocity(dir.normalize().multiply(strength).setY(0.2));
            }
        }
    }

    private static void leap(SkillContext ctx, List<Target> targets, Params p) {
        double power = p.getDouble("power", 1.2);
        double vertical = p.getDouble("vertical", 0.5);
        Location from = ctx.location();
        Location to = targets.isEmpty() ? from.clone().add(from.getDirection().multiply(5)) : targets.get(0).location();
        Vector dir = to.toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1.0E-4) {
            dir = from.getDirection();
        }
        ctx.self().setVelocity(dir.normalize().multiply(power).setY(vertical));
    }

    private static void teleport(SkillContext ctx, List<Target> targets, Params p) {
        double radius = p.getDouble("radius", 0);
        Location dest = targets.isEmpty() ? ctx.location() : targets.get(0).location().clone();
        if (radius > 0) {
            dest.add(rand(radius), 0, rand(radius));
        }
        ctx.self().teleport(dest);
    }

    private static void teleportTarget(SkillContext ctx, List<Target> targets, Params p) {
        double radius = p.getDouble("radius", 2);
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                Location dest = ctx.location().clone().add(rand(radius), 0, rand(radius));
                t.entity().teleport(dest);
                break;
            }
        }
    }

    private static void lightning(SkillContext ctx, List<Target> targets, Params p) {
        boolean doDamage = p.getBoolean("damage", true);
        for (Location loc : locations(ctx, targets)) {
            if (doDamage) {
                loc.getWorld().strikeLightning(loc);
            } else {
                loc.getWorld().strikeLightningEffect(loc);
            }
        }
    }

    private static void explode(SkillContext ctx, List<Target> targets, Params p) {
        float power = (float) p.getDouble("power", 3.0);
        boolean fire = p.getBoolean("fire", false);
        // breakBlocks = false: never damage terrain or builds.
        for (Location loc : locations(ctx, targets)) {
            loc.getWorld().createExplosion(loc, power, fire, false, ctx.self());
        }
    }

    private static void arrowVolley(SkillContext ctx, List<Target> targets, Params p) {
        int count = Math.max(1, p.getInt("count", 8));
        double spread = p.getDouble("spread", 0.25);
        double velocity = p.getDouble("velocity", 1.6);
        boolean fire = p.getBoolean("fire", false);
        LivingEntity self = ctx.self();
        Location eye = self.getEyeLocation();
        Vector base = targets.isEmpty() ? eye.getDirection()
                : targets.get(0).location().toVector().subtract(eye.toVector()).normalize();
        for (int i = 0; i < count; i++) {
            Vector v = base.clone().add(new Vector(rand(spread), rand(spread), rand(spread))).normalize().multiply(velocity);
            Arrow arrow = self.launchProjectile(Arrow.class, v);
            arrow.setShooter(self);
            if (fire) {
                arrow.setFireTicks(200);
            }
        }
    }

    private static void beam(SkillContext ctx, List<Target> targets, Params p) {
        double dmg = p.getDouble("damage", 6);
        Particle particle = enumOr(Particle.class, p.getString("particle", "END_ROD"), Particle.END_ROD);
        Location origin = ctx.self().getEyeLocation();
        for (Target t : targets) {
            if (t.entity() == null || t.entity() == ctx.self()) {
                continue;
            }
            Location target = t.entity().getEyeLocation();
            Vector step = target.toVector().subtract(origin.toVector());
            double length = step.length();
            if (length < 0.1) {
                continue;
            }
            step.normalize().multiply(0.5);
            Location point = origin.clone();
            for (double d = 0; d < length; d += 0.5) {
                point.add(step);
                point.getWorld().spawnParticle(particle, point, 1, 0, 0, 0, 0);
            }
            t.entity().damage(dmg, ctx.self());
        }
    }

    private static void charm(SkillContext ctx, List<Target> targets, Params p) {
        int duration = p.getInt("duration", 100);
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                t.entity().addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0, false, true, true));
                t.entity().addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, false, true, true));
            }
        }
    }

    private static void buff(SkillContext ctx, List<Target> targets, Params p) {
        PotionEffectType type = Effects.type(p.getString("type", "STRENGTH"));
        if (type == null) {
            return;
        }
        ctx.self().addPotionEffect(new PotionEffect(type, p.getInt("duration", 200), p.getInt("amplifier", 0),
                false, true, true));
    }

    private static void heal(SkillContext ctx, List<Target> targets, Params p) {
        double amount = p.getDouble("amount", ctx.boss().maxHealth() * 0.1);
        LivingEntity self = ctx.self();
        double newHealth = Math.min(ctx.boss().maxHealth(), self.getHealth() + amount);
        self.setHealth(Math.max(0.5, newHealth));
    }

    private static void shield(SkillContext ctx, List<Target> targets, Params p) {
        int duration = p.getInt("duration", 100);
        int amplifier = p.getInt("amplifier", 1);
        ctx.self().addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, amplifier, false, true, true));
        ctx.self().addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier, false, true, true));
    }

    private static void command(SkillContext ctx, List<Target> targets, Params p) {
        String template = p.getString("command", "");
        if (template.isBlank()) {
            return;
        }
        String withBoss = template.replace("%boss%", ctx.boss().def().id());
        boolean ran = false;
        for (Target t : targets) {
            if (t.entity() instanceof Player player) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), withBoss.replace("%player%", player.getName()));
                ran = true;
            }
        }
        if (!ran) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), withBoss);
        }
    }

    private static void projectile(SkillContext ctx, List<Target> targets, Params p) {
        String type = p.getString("type", "FIREBALL").toUpperCase(Locale.ROOT);
        double velocity = p.getDouble("velocity", 1.4);
        LivingEntity self = ctx.self();
        Location eye = self.getEyeLocation();
        Vector dir = targets.isEmpty() ? eye.getDirection()
                : targets.get(0).location().toVector().subtract(eye.toVector()).normalize();
        Vector v = dir.multiply(velocity);
        Class<? extends Projectile> clazz = switch (type) {
            case "SMALL_FIREBALL" -> SmallFireball.class;
            case "WITHER_SKULL" -> WitherSkull.class;
            case "SNOWBALL" -> Snowball.class;
            case "ARROW" -> Arrow.class;
            default -> Fireball.class;
        };
        Projectile proj = self.launchProjectile(clazz, v);
        proj.setShooter(self);
        if (proj instanceof Fireball fb) {
            fb.setYield((float) p.getDouble("yield", 0));
            fb.setIsIncendiary(p.getBoolean("fire", false));
        }
    }

    // ---- helpers --------------------------------------------------------------------------

    private static List<Location> locations(SkillContext ctx, List<Target> targets) {
        if (targets.isEmpty()) {
            return List.of(ctx.location());
        }
        return targets.stream().map(Target::location).toList();
    }

    private static List<PotionEffect> parseEffects(List<String> tokens) {
        java.util.List<PotionEffect> out = new java.util.ArrayList<>();
        for (String token : tokens) {
            PotionEffect eff = Effects.parse(token);
            if (eff != null) {
                out.add(eff);
            }
        }
        return out;
    }

    private static void pushAway(LivingEntity entity, Location from, double strength, double vertical) {
        Vector dir = entity.getLocation().toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 1.0E-4) {
            dir = new Vector(0, 1, 0);
        }
        dir.setY(0).normalize().multiply(strength).setY(vertical);
        entity.setVelocity(dir);
    }

    private static double rand(double magnitude) {
        return magnitude <= 0 ? 0 : ThreadLocalRandom.current().nextDouble(-magnitude, magnitude);
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
