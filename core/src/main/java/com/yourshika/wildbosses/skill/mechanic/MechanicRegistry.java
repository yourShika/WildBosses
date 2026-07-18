package com.yourshika.wildbosses.skill.mechanic;

import com.yourshika.wildbosses.skill.SkillContext;
import com.yourshika.wildbosses.skill.Target;
import com.yourshika.wildbosses.util.Effects;
import com.yourshika.wildbosses.util.Keys;
import com.yourshika.wildbosses.util.Params;
import com.yourshika.wildbosses.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.WitherSkull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
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
        register("say", MechanicRegistry::say);
        register("taunt", MechanicRegistry::say);
        register("arrow_rain", MechanicRegistry::arrowRain);
        register("throw_potion", MechanicRegistry::throwPotion);
        register("petrify", MechanicRegistry::petrify);
        register("lifesteal", MechanicRegistry::lifesteal);
        register("fly", MechanicRegistry::fly);
        register("radial", MechanicRegistry::radial);
        register("axe_throw", MechanicRegistry::radial);
        register("danger_zone", MechanicRegistry::dangerZone);
        register("shockwave", MechanicRegistry::shockwave);
        register("meteor", MechanicRegistry::meteor);
        register("meteor_rain", MechanicRegistry::meteor);
        register("healer_adds", MechanicRegistry::healerAdds);
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
        // Clamp so a typo/abuse in YAML (count: 50000000) can't stall the main thread.
        int count = Math.max(0, Math.min(5000, p.getInt("count", 20)));
        double spread = p.getDouble("spread", 0.5);
        double speed = p.getDouble("speed", 0.0);
        double offsetY = p.getDouble("offset-y", 1.0);
        for (Location loc : locations(ctx, targets)) {
            emit(loc.getWorld(), particle, loc.clone().add(0, offsetY, 0), count, spread, speed);
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
        // Always outline the blast radius so players can see how far it reaches.
        Particle particle = enumOr(Particle.class, particleName == null ? "EXPLOSION" : particleName, Particle.EXPLOSION);
        emit(center.getWorld(), particle, center.clone().add(0, 1, 0), 50, radius / 2, 0.03);
        ring(center, radius, particle);
        center.getWorld().playSound(center, "entity.generic.explode", 1.0f, 1.0f);
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
        int amount = Math.max(1, (int) Math.round(p.getInt("amount", 3) * ctx.boss().addMultiplier()));
        double radius = p.getDouble("radius", 3);
        double health = p.getDouble("health", 0);
        boolean baby = p.getBoolean("baby", false);
        List<PotionEffect> effects = parseEffects(p.getStringList("effects"));
        Location base = targets.isEmpty() ? ctx.location() : targets.get(0).location();
        World world = base.getWorld();
        for (int i = 0; i < amount; i++) {
            // Land minions on top of the ground, never inside a wall/floor where they'd suffocate.
            Location loc = safeGround(base.clone().add(rand(radius), 0, rand(radius)));
            if (loc == null) {
                loc = base;
            }
            Entity e = world.spawnEntity(loc, type);
            e.getPersistentDataContainer().set(Keys.ENCOUNTER_ID, PersistentDataType.STRING, ctx.boss().encounterId());
            e.addScoreboardTag("wildbosses");
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
            Location safe = safeGround(dest);
            if (safe != null) {
                dest = safe;
            }
        }
        ctx.self().teleport(dest);
    }

    private static void teleportTarget(SkillContext ctx, List<Target> targets, Params p) {
        double radius = p.getDouble("radius", 2);
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                // Only relocate the player to a verified safe spot - never into solid blocks.
                Location dest = safeGround(ctx.location().clone().add(rand(radius), 0, rand(radius)));
                if (dest != null) {
                    t.entity().teleport(dest);
                }
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
        // kill-caster: the boss dies from its own blast (e.g. Creeper King's final detonation).
        if (p.getBoolean("kill-caster", false) && ctx.self().isValid()) {
            ctx.self().setHealth(0);
        }
    }

    private static void say(SkillContext ctx, List<Target> targets, Params p) {
        List<String> lines = p.getStringList("lines");
        if (lines.isEmpty()) {
            String single = p.getString("line", null);
            if (single != null) {
                lines = List.of(single);
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        String prefix = p.getString("prefix", ctx.boss().def().name());
        var message = Text.mm(prefix + "<gray>: <white>" + line);
        double radius = p.getDouble("radius", 40);
        double rSq = radius * radius;
        Location loc = ctx.location();
        for (Player player : ctx.world().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= rSq) {
                player.sendMessage(message);
            }
        }
    }

    private static void arrowRain(SkillContext ctx, List<Target> targets, Params p) {
        int count = Math.max(1, p.getInt("count", 12));
        double spread = p.getDouble("spread", p.getDouble("radius", 4));
        double height = p.getDouble("height", 18);
        boolean fire = p.getBoolean("fire", false);
        for (Location base : locations(ctx, targets)) {
            World world = base.getWorld();
            for (int i = 0; i < count; i++) {
                Location spawn = base.clone().add(rand(spread), height, rand(spread));
                Arrow arrow = world.spawn(spawn, Arrow.class);
                arrow.setVelocity(new Vector(0, -2.2, 0));
                arrow.setShooter(ctx.self());
                arrow.setDamage(p.getDouble("damage", 5));
                if (fire) {
                    arrow.setFireTicks(200);
                }
            }
        }
    }

    private static void throwPotion(SkillContext ctx, List<Target> targets, Params p) {
        PotionEffectType type = Effects.type(p.getString("type", "POISON"));
        int duration = p.getInt("duration", 140);
        int amplifier = p.getInt("amplifier", 0);
        boolean lingering = p.getBoolean("lingering", false);
        ItemStack potion = new ItemStack(lingering ? Material.LINGERING_POTION : Material.SPLASH_POTION);
        if (potion.getItemMeta() instanceof PotionMeta meta && type != null) {
            meta.addCustomEffect(new PotionEffect(type, duration, amplifier, false, true, true), true);
            potion.setItemMeta(meta);
        }
        LivingEntity self = ctx.self();
        Location eye = self.getEyeLocation();
        Vector dir = targets.isEmpty() ? eye.getDirection()
                : aimDir(eye, targets.get(0).location().clone().add(0, -0.5, 0));
        ThrownPotion thrown = self.launchProjectile(ThrownPotion.class, dir.multiply(p.getDouble("velocity", 0.9)));
        thrown.setItem(potion);
        thrown.setShooter(self);
    }

    private static void petrify(SkillContext ctx, List<Target> targets, Params p) {
        int duration = p.getInt("duration", 80);
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                t.entity().addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 5, false, true, true));
                t.entity().addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 2, false, true, true));
                t.entity().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Math.min(duration, 60), 0, false, true, true));
            }
        }
    }

    private static void lifesteal(SkillContext ctx, List<Target> targets, Params p) {
        double amount = p.getDouble("amount", 6);
        double ratio = p.getDouble("heal-ratio", 0.5);
        double healed = 0;
        for (Target t : targets) {
            if (t.entity() != null && t.entity() != ctx.self()) {
                t.entity().damage(amount, ctx.self());
                healed += amount * ratio;
            }
        }
        LivingEntity self = ctx.self();
        if (healed > 0) {
            self.setHealth(Math.min(ctx.boss().maxHealth(), self.getHealth() + healed));
        }
    }

    private static void fly(SkillContext ctx, List<Target> targets, Params p) {
        int duration = p.getInt("duration", 100);
        double lift = p.getDouble("lift", 0.5);
        LivingEntity self = ctx.self();
        self.setGravity(false);
        Vector velocity = new Vector(0, lift, 0);
        if (!targets.isEmpty()) {
            Vector toward = targets.get(0).location().toVector().subtract(self.getLocation().toVector());
            toward.setY(0);
            if (toward.lengthSquared() > 1) {
                velocity.add(toward.normalize().multiply(0.35));
            }
        }
        self.setVelocity(velocity);
        Bukkit.getScheduler().runTaskLater(ctx.plugin(), () -> {
            if (self.isValid()) {
                self.setGravity(true);
            }
        }, duration);
    }

    private static void radial(SkillContext ctx, List<Target> targets, Params p) {
        int count = Math.max(1, p.getInt("count", 8));
        double velocity = p.getDouble("velocity", 1.2);
        String type = p.getString("type", "ARROW").toUpperCase(Locale.ROOT);
        Class<? extends Projectile> clazz = switch (type) {
            case "SNOWBALL" -> Snowball.class;
            case "SMALL_FIREBALL" -> SmallFireball.class;
            default -> Arrow.class;
        };
        LivingEntity self = ctx.self();
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            Vector dir = new Vector(Math.cos(angle), 0.08, Math.sin(angle)).multiply(velocity);
            Projectile proj = self.launchProjectile(clazz, dir);
            proj.setShooter(self);
        }
        String particle = p.getString("particle", null);
        if (particle != null) {
            Particle part = enumOr(Particle.class, particle, Particle.CRIT);
            emit(self.getWorld(), part, self.getLocation().add(0, 1, 0), 30, 1, 0.05);
        }
    }

    private static void arrowVolley(SkillContext ctx, List<Target> targets, Params p) {
        int count = Math.max(1, p.getInt("count", 8));
        double spread = p.getDouble("spread", 0.25);
        double velocity = p.getDouble("velocity", 1.6);
        boolean fire = p.getBoolean("fire", false);
        LivingEntity self = ctx.self();
        Location eye = self.getEyeLocation();
        Vector base = targets.isEmpty() ? eye.getDirection() : aimDir(eye, targets.get(0).location());
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
                emit(point.getWorld(), particle, point, 1, 0, 0);
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
        Vector dir = targets.isEmpty() ? eye.getDirection() : aimDir(eye, targets.get(0).location());
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

    private static void dangerZone(SkillContext ctx, List<Target> targets, Params p) {
        double radius = p.getDouble("radius", 4);
        double damage = p.getDouble("damage", 8);
        double knockback = p.getDouble("knockback", 0);
        int delay = Math.max(5, p.getInt("delay", 30));
        double offsetY = p.getDouble("offset-y", 0);
        Particle warn = enumOr(Particle.class, p.getString("warn-particle", "FLAME"), Particle.FLAME);
        Particle hit = enumOr(Particle.class, p.getString("particle", "EXPLOSION_EMITTER"), Particle.EXPLOSION_EMITTER);
        java.util.List<Location> spots = new java.util.ArrayList<>();
        for (Location l : locations(ctx, targets)) {
            // offset-y lifts the telegraph off the ground (e.g. an aerial AoE that forms in the air).
            spots.add(l.clone().add(0, offsetY, 0));
        }
        LivingEntity self = ctx.self();
        var plugin = ctx.plugin();
        // Pulse a filled warning disc so players clearly see (and can leave) the danger area.
        for (int t = 0; t < delay; t += 3) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> spots.forEach(loc -> disc(loc, radius, warn)), t);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location loc : spots) {
                emit(loc.getWorld(), hit, loc.clone().add(0, 0.5, 0), 40, radius / 2, 0.05);
                loc.getWorld().playSound(loc, "entity.generic.explode", 1.0f, 1.2f);
                double rSq = radius * radius;
                for (Player pl : loc.getWorld().getPlayers()) {
                    if (pl.getLocation().distanceSquared(loc) <= rSq) {
                        pl.damage(damage, self);
                        if (knockback > 0) {
                            pushAway(pl, loc, knockback, 0.4);
                        }
                    }
                }
            }
        }, delay);
    }

    private static void shockwave(SkillContext ctx, List<Target> targets, Params p) {
        double radius = p.getDouble("radius", 6);
        double damage = p.getDouble("damage", 8);
        double knockup = p.getDouble("knockup", 0.8);
        Particle particle = enumOr(Particle.class, p.getString("particle", "EXPLOSION"), Particle.EXPLOSION);
        Location center = ctx.location().clone();
        LivingEntity self = ctx.self();
        var plugin = ctx.plugin();
        for (int step = 1; step <= 5; step++) {
            double r = radius * step / 5.0;
            Bukkit.getScheduler().runTaskLater(plugin, () -> ring(center, r, particle), step * 2L);
        }
        double rSq = radius * radius;
        for (Player pl : center.getWorld().getPlayers()) {
            if (pl.getLocation().distanceSquared(center) <= rSq) {
                pl.damage(damage, self);
                Vector v = pl.getLocation().toVector().subtract(center.toVector());
                v.setY(0);
                if (v.lengthSquared() < 1.0E-4) {
                    v = new Vector(0, 1, 0);
                }
                pl.setVelocity(v.normalize().multiply(0.6).setY(knockup));
            }
        }
    }

    private static void meteor(SkillContext ctx, List<Target> targets, Params p) {
        int count = Math.max(1, p.getInt("count", 6));
        double radius = p.getDouble("radius", 8);
        double height = p.getDouble("height", 22);
        double damage = p.getDouble("damage", 8);
        int delay = Math.max(10, p.getInt("delay", 25));
        boolean fire = p.getBoolean("fire", false);
        Location base = (targets.isEmpty() ? ctx.location() : targets.get(0).location()).clone();
        World w = base.getWorld();
        LivingEntity self = ctx.self();
        var plugin = ctx.plugin();
        for (int i = 0; i < count; i++) {
            Location spot = base.clone().add(rand(radius), 0, rand(radius));
            for (int t = 0; t < delay; t += 3) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> disc(spot, 2.5, Particle.FLAME), t);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                SmallFireball fb = w.spawn(spot.clone().add(0, height, 0), SmallFireball.class);
                fb.setVelocity(new Vector(0, -2, 0));
                fb.setYield(0f);
                fb.setIsIncendiary(fire);
                fb.setShooter(self);
            }, delay - 8L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                emit(w, Particle.EXPLOSION_EMITTER, spot.clone().add(0, 0.5, 0), 20, 1.5, 0.02);
                w.playSound(spot, "entity.generic.explode", 1.2f, 1.0f);
                for (Player pl : w.getPlayers()) {
                    if (pl.getLocation().distanceSquared(spot) <= 9) {
                        pl.damage(damage, self);
                        if (fire) {
                            pl.setFireTicks(60);
                        }
                    }
                }
            }, delay);
        }
    }

    private static void healerAdds(SkillContext ctx, List<Target> targets, Params p) {
        EntityType type = enumOr(EntityType.class, p.getString("type", "VEX"), EntityType.VEX);
        int amount = Math.max(1, p.getInt("amount", 2));
        double radius = p.getDouble("radius", 5);
        String name = p.getString("name", "<green>Attendant");
        double hps = p.getDouble("heal-per-second", ctx.boss().maxHealth() * 0.02);
        ctx.boss().setHealerHealPerTick(hps / 20.0);
        Location base = ctx.location();
        World w = base.getWorld();
        for (int i = 0; i < amount; i++) {
            Location loc = safeGround(base.clone().add(rand(radius), 0, rand(radius)));
            if (loc == null) {
                loc = base;
            }
            Entity e = w.spawnEntity(loc, type);
            e.getPersistentDataContainer().set(Keys.ENCOUNTER_ID, PersistentDataType.STRING, ctx.boss().encounterId());
            e.addScoreboardTag("wildbosses");
            if (e instanceof LivingEntity le) {
                le.customName(Text.mm(name));
                le.setCustomNameVisible(true);
            }
            ctx.boss().addHealer(e.getUniqueId());
        }
    }

    private static void ring(Location center, double radius, Particle particle) {
        World w = center.getWorld();
        // Cap the point count so a huge radius from YAML can't emit tens of thousands of particles.
        int points = (int) Math.max(16, Math.min(360, radius * 14));
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            emit(w, particle, center.clone().add(Math.cos(a) * radius, 0.15, Math.sin(a) * radius), 1, 0, 0);
        }
    }

    /** A filled disc of particles (concentric rings) - a clearly visible danger area on the ground. */
    private static void disc(Location center, double radius, Particle particle) {
        // Bound the number of concentric rings (~40 max) regardless of radius.
        double step = Math.max(0.9, radius / 40.0);
        for (double r = step; r < radius; r += step) {
            ring(center, r, particle);
        }
        ring(center, radius, particle);
    }

    // ---- helpers --------------------------------------------------------------------------

    /**
     * Find a safe standing spot at {@code desired}'s column: a solid, non-liquid floor with two
     * passable blocks above. Searches a few blocks up and down from the desired Y so summoned mobs
     * and teleports land ON the ground, never embedded in blocks. Returns {@code null} if no safe
     * spot is nearby (the caller decides whether to skip or fall back).
     */
    private static Location safeGround(Location desired) {
        World w = desired.getWorld();
        if (w == null) {
            return null;
        }
        int x = desired.getBlockX();
        int z = desired.getBlockZ();
        int top = Math.min(w.getMaxHeight() - 3, desired.getBlockY() + 6);
        int bottom = Math.max(w.getMinHeight() + 1, desired.getBlockY() - 10);
        for (int y = top; y >= bottom; y--) {
            org.bukkit.block.Block floor = w.getBlockAt(x, y, z);
            org.bukkit.block.Block feet = w.getBlockAt(x, y + 1, z);
            org.bukkit.block.Block head = w.getBlockAt(x, y + 2, z);
            if (floor.getType().isSolid() && !floor.isLiquid() && feet.isPassable() && head.isPassable()) {
                return new Location(w, x + 0.5, y + 1, z + 0.5, desired.getYaw(), desired.getPitch());
            }
        }
        return null;
    }

    /**
     * A normalized direction from {@code eye} to {@code target}. Falls back to the eye's facing when
     * the two points coincide, so {@code normalize()} never divides by zero (which would produce a
     * NaN vector and make {@code launchProjectile} throw "non-finite velocity").
     */
    private static Vector aimDir(Location eye, Location target) {
        Vector d = target.toVector().subtract(eye.toVector());
        return d.lengthSquared() < 1.0e-4 ? eye.getDirection() : d.normalize();
    }

    /** Spawn a particle, supplying sensible default data for particles that require it (Dust, etc.). */
    private static void emit(World world, Particle particle, Location loc, int count, double spread, double speed) {
        Class<?> data = particle.getDataType();
        try {
            if (data == org.bukkit.Particle.DustOptions.class) {
                world.spawnParticle(particle, loc, count, spread, spread, spread, speed,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 105, 180), 1.6f));
            } else if (data == org.bukkit.Particle.DustTransition.class) {
                world.spawnParticle(particle, loc, count, spread, spread, spread, speed,
                        new org.bukkit.Particle.DustTransition(org.bukkit.Color.fromRGB(255, 105, 180),
                                org.bukkit.Color.fromRGB(120, 180, 255), 1.6f));
            } else if (data == org.bukkit.block.data.BlockData.class) {
                world.spawnParticle(particle, loc, count, spread, spread, spread, speed, Material.STONE.createBlockData());
            } else if (data == ItemStack.class) {
                world.spawnParticle(particle, loc, count, spread, spread, spread, speed, new ItemStack(Material.DIAMOND));
            } else if (data == Float.class) {
                world.spawnParticle(particle, loc, count, spread, spread, spread, speed, 0f);
            } else {
                world.spawnParticle(particle, loc, count, spread, spread, spread, speed);
            }
        } catch (Throwable ignored) {
            // exotic particle data requirement - skip rather than crash the mechanic
        }
    }

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
