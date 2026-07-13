package com.yourshika.wildbosses.army;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.ArmyDefinition;
import com.yourshika.wildbosses.boss.ArmyMinion;
import com.yourshika.wildbosses.boss.BossDefinition;
import com.yourshika.wildbosses.util.Effects;
import com.yourshika.wildbosses.util.Keys;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A running army encounter: waves of minions spawn and reinforce until a kill threshold is reached,
 * then an outcome resolves (spawn end-boss / flee / cleared). A boss bar shows kill progress.
 */
public final class ArmyEncounter {

    private static final double VIEW_RANGE = 90.0;

    private final WildBossesPlugin plugin;
    private final ArmyManager manager;
    private final BossDefinition def;
    private final ArmyDefinition army;
    private final Location anchor;
    private final String id;
    private final BossBar bar;

    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> viewers = new HashSet<>();

    private BukkitTask task;
    private long elapsedTicks;
    private long nextReinforceTick;
    private int kills;
    private boolean ended;

    public ArmyEncounter(WildBossesPlugin plugin, ArmyManager manager, BossDefinition def, Location anchor, String id) {
        this.plugin = plugin;
        this.manager = manager;
        this.def = def;
        this.army = def.army();
        this.anchor = anchor;
        this.id = id;
        this.bar = BossBar.bossBar(title(), 0f, def.difficulty().barColor(), BossBar.Overlay.NOTCHED_10);
    }

    public String id() {
        return id;
    }

    public BossDefinition def() {
        return def;
    }

    public Location anchor() {
        return anchor;
    }

    public int kills() {
        return kills;
    }

    public void start() {
        if (def.hasTerrain()) {
            plugin.terrainManager().applyAt(id, anchor, def.terrain());
        }
        spawnWave();
        nextReinforceTick = army.reinforceIntervalTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (ended) {
            return;
        }
        elapsedTicks += 20;
        updateViewers();

        if (army.timeoutSeconds() > 0 && elapsedTicks / 20 >= army.timeoutSeconds()) {
            resolve(ArmyDefinition.Outcome.CLEARED);
            return;
        }
        if (elapsedTicks >= nextReinforceTick && kills < army.killThreshold()) {
            spawnWave();
            nextReinforceTick += army.reinforceIntervalTicks();
        }
    }

    private void spawnWave() {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        int room = army.maxAlive() - alive.size();
        int toSpawn = Math.min(army.waveSize(), room);
        for (int i = 0; i < toSpawn; i++) {
            ArmyMinion template = pickMinion();
            if (template == null) {
                return;
            }
            Location loc = anchor.clone().add(rand(army.radius()), 0, rand(army.radius()));
            loc.setY(world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 1);
            Entity entity;
            try {
                entity = world.spawnEntity(loc, template.type());
            } catch (Exception ex) {
                continue;
            }
            entity.getPersistentDataContainer().set(Keys.ARMY_ID, PersistentDataType.STRING, id);
            if (entity instanceof LivingEntity le) {
                le.setRemoveWhenFarAway(false);
                if (template.health() > 0) {
                    AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
                    if (max != null) {
                        max.setBaseValue(template.health());
                        le.setHealth(template.health());
                    }
                }
                if (template.name() != null && !template.name().isBlank()) {
                    le.customName(Text.mm(template.name()));
                    le.setCustomNameVisible(true);
                }
                for (String token : template.effects()) {
                    PotionEffect eff = Effects.parse(token);
                    if (eff != null) {
                        le.addPotionEffect(eff);
                    }
                }
            }
            if (entity instanceof Mob mob) {
                Player target = nearestPlayer();
                if (target != null) {
                    mob.setTarget(target);
                }
            }
            alive.add(entity.getUniqueId());
            manager.registerMinion(entity.getUniqueId(), id);
        }
        updateBar();
    }

    /** Called by the manager when a tracked minion of this encounter dies. */
    public void onMinionDeath(Entity entity) {
        if (ended || !alive.remove(entity.getUniqueId())) {
            return;
        }
        kills++;
        updateBar();
        if (kills >= army.killThreshold()) {
            resolve(army.outcome());
        }
    }

    private void resolve(ArmyDefinition.Outcome outcome) {
        if (ended) {
            return;
        }
        switch (outcome) {
            case SPAWN_BOSS -> {
                BossDefinition endBoss = plugin.registry().get(army.endBossId());
                if (endBoss != null) {
                    // applyTerrain=false: the army already themed the ground; don't double up.
                    plugin.bossManager().spawn(endBoss, anchor.clone(), UUID.randomUUID().toString(), true, false);
                } else {
                    plugin.getLogger().warning("Army " + def.id() + " outcome SPAWN_BOSS but end-boss '"
                            + army.endBossId() + "' is unknown.");
                }
                despawnMinions();
            }
            case FLEE -> flee();
            case CLEARED -> despawnMinions();
        }
        end();
    }

    private void flee() {
        for (UUID uuid : new HashSet<>(alive)) {
            Entity e = Bukkit.getEntity(uuid);
            if (e instanceof Mob mob) {
                mob.setTarget(null);
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, false, false));
                Vector away = mob.getLocation().toVector().subtract(anchor.toVector());
                if (away.lengthSquared() > 1.0E-4) {
                    mob.setVelocity(away.normalize().multiply(0.6).setY(0.2));
                }
            }
        }
        // Despawn the fleeing minions shortly after.
        Bukkit.getScheduler().runTaskLater(plugin, this::despawnMinions, 100L);
    }

    private void despawnMinions() {
        for (UUID uuid : new HashSet<>(alive)) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null && !e.isDead()) {
                e.remove();
            }
            manager.unregisterMinion(uuid);
        }
        alive.clear();
    }

    private void end() {
        if (ended) {
            return;
        }
        ended = true;
        if (task != null) {
            task.cancel();
        }
        if (def.hasTerrain()) {
            plugin.terrainManager().restoreEncounter(id, def.terrain().restoreOnEnd());
        }
        hideBarFromAll();
        manager.end(this);
    }

    /** Force-stop (plugin disable / killall): despawn everything and clean up. */
    public void terminate() {
        despawnMinions();
        end();
    }

    // ---- boss bar -------------------------------------------------------------------------

    private void updateBar() {
        float progress = army.killThreshold() <= 0 ? 1f
                : (float) Math.max(0, Math.min(1, (double) kills / army.killThreshold()));
        bar.progress(progress);
        bar.name(title());
    }

    private net.kyori.adventure.text.Component title() {
        return Text.mm(def.name() + " " + def.difficulty().bracketedMini()
                + " <gray>(<yellow>" + kills + "<gray>/<yellow>" + army.killThreshold() + "<gray> slain)");
    }

    private void updateViewers() {
        World world = anchor.getWorld();
        if (world == null) {
            return;
        }
        double rSq = VIEW_RANGE * VIEW_RANGE;
        Set<UUID> near = new HashSet<>();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(anchor) <= rSq) {
                near.add(p.getUniqueId());
                if (viewers.add(p.getUniqueId())) {
                    p.showBossBar(bar);
                }
            }
        }
        Iterator<UUID> it = viewers.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            if (!near.contains(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.hideBossBar(bar);
                }
                it.remove();
            }
        }
    }

    private void hideBarFromAll() {
        for (UUID uuid : viewers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
        viewers.clear();
    }

    // ---- helpers --------------------------------------------------------------------------

    private ArmyMinion pickMinion() {
        int total = 0;
        for (ArmyMinion m : army.minions()) {
            total += Math.max(1, m.weight());
        }
        if (total <= 0 || army.minions().isEmpty()) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (ArmyMinion m : army.minions()) {
            acc += Math.max(1, m.weight());
            if (roll < acc) {
                return m;
            }
        }
        return army.minions().get(army.minions().size() - 1);
    }

    private Player nearestPlayer() {
        World world = anchor.getWorld();
        if (world == null) {
            return null;
        }
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().distanceSquared(anchor);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static double rand(double magnitude) {
        return magnitude <= 0 ? 0 : ThreadLocalRandom.current().nextDouble(-magnitude, magnitude);
    }
}
