package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.skill.SkillEngine;
import com.yourshika.wildbosses.util.Keys;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns all spawned bosses: spawning, the per-tick update loop (boss bar, phase transitions, skill
 * ticks), and death/cleanup. Skill firing, terrain and rewards are injected so this class stays
 * independent of those subsystems.
 */
public final class BossManager {

    private final WildBossesPlugin plugin;
    private final BossRegistry registry;
    private final Broadcaster broadcaster;

    // ConcurrentHashMap: PlaceholderAPI (via TAB/scoreboard plugins) reads active()/count() off the
    // main thread while the tick loop mutates this map; its weakly-consistent iterator avoids the
    // ConcurrentModificationException a plain map would throw during that concurrent copy.
    private final Map<UUID, ActiveBoss> byEntity = new java.util.concurrent.ConcurrentHashMap<>();

    private SkillEngine skillEngine = SkillEngine.NOOP;
    private EncounterHook encounterHook = EncounterHook.NOOP;
    private BossDeathListener deathListener = BossDeathListener.NOOP;

    private BukkitTask task;
    private long tick;

    public BossManager(WildBossesPlugin plugin, BossRegistry registry, Broadcaster broadcaster) {
        this.plugin = plugin;
        this.registry = registry;
        this.broadcaster = broadcaster;
    }

    public void setSkillEngine(SkillEngine skillEngine) {
        this.skillEngine = skillEngine == null ? SkillEngine.NOOP : skillEngine;
    }

    public void setEncounterHook(EncounterHook encounterHook) {
        this.encounterHook = encounterHook == null ? EncounterHook.NOOP : encounterHook;
    }

    public void setDeathListener(BossDeathListener deathListener) {
        this.deathListener = deathListener == null ? BossDeathListener.NOOP : deathListener;
    }

    public long currentTick() {
        return tick;
    }

    public BossRegistry registry() {
        return registry;
    }

    // ---- lifecycle ------------------------------------------------------------------------

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (ActiveBoss boss : new ArrayList<>(byEntity.values())) {
            encounterHook.onEnd(boss);
            boss.cleanup(true); // remove the entity too, so a reload/disable leaves nothing behind
        }
        byEntity.clear();
    }

    // ---- spawning -------------------------------------------------------------------------

    public ActiveBoss spawn(BossDefinition def, Location loc) {
        return spawn(def, loc, UUID.randomUUID().toString(), true, true);
    }

    public ActiveBoss spawn(BossDefinition def, Location loc, String encounterId,
                            boolean broadcast, boolean applyTerrain) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        Entity spawned;
        try {
            spawned = world.spawnEntity(loc, def.baseEntity());
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to spawn base entity " + def.baseEntity()
                    + " for boss " + def.id() + ": " + ex.getMessage());
            return null;
        }
        if (!(spawned instanceof LivingEntity le)) {
            spawned.remove();
            plugin.getLogger().warning("Boss " + def.id() + " base-entity is not a living entity.");
            return null;
        }

        applyStats(le, def);
        applyEquipment(le, def);
        le.setRemoveWhenFarAway(false);
        // NOT persistent: a boss must never survive a server restart as an untracked strong mob.
        // It stays while the fight's chunks are loaded; if it unloads/restarts, it's gone.
        le.setPersistent(false);
        if (le instanceof Mob mob) {
            mob.setAware(true);
        }
        if (le instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setShouldBurnInDay(false); // bosses shouldn't die to sunlight
        }
        if (le instanceof org.bukkit.entity.AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false); // skeleton bosses shouldn't be whittled down by daylight
        }
        if (le instanceof org.bukkit.entity.Creeper creeper) {
            creeper.setExplosionRadius(0); // no self-detonation; boss explodes only via its skills
        }
        if (le instanceof org.bukkit.entity.Wolf wolf) {
            wolf.setAngry(true); // a boss wolf hunts on sight
        }
        if (le instanceof org.bukkit.entity.Horse horse) {
            horse.setColor(org.bukkit.entity.Horse.Color.WHITE); // the unicorn is always a white steed
            horse.setStyle(org.bukkit.entity.Horse.Style.NONE);
            horse.setAdult();
        }
        if (le instanceof org.bukkit.entity.Bee bee) {
            bee.setAnger(Integer.MAX_VALUE); // stays hostile for the whole fight
        }
        tag(le, def, encounterId);

        double scale = applyScaling(le, loc);
        double maxHp = maxHealthOf(le, def);
        BossBar bar = BossBar.bossBar(displayName(def), 1f, barColor(def), def.bossBar().overlay());
        le.customName(displayName(def));
        le.setCustomNameVisible(true);

        ActiveBoss boss = new ActiveBoss(def, le, bar, maxHp, tick, encounterId);
        boss.setAddMultiplier(scale);
        spawnBurst(le.getLocation());
        if (plugin.config().bossLifetimeEnabled()) {
            long minTicks = plugin.config().bossLifetimeMinMinutes() * 60L * 20L;
            long maxTicks = plugin.config().bossLifetimeMaxMinutes() * 60L * 20L;
            long life = maxTicks > minTicks ? ThreadLocalRandom.current().nextLong(minTicks, maxTicks) : minTicks;
            boss.setFleeAtTick(tick + life);
        }
        byEntity.put(le.getUniqueId(), boss);

        applyPhase(boss, computePhaseIndex(boss), true);
        if (applyTerrain) {
            encounterHook.onStart(boss);
        }
        skillEngine.onSpawn(boss);
        if (broadcast) {
            broadcaster.bossSpawn(def, loc);
        }
        return boss;
    }

    private void applyStats(LivingEntity le, BossDefinition def) {
        BossStats s = def.stats();
        setAttr(le, Attribute.MAX_HEALTH, s.health());
        setAttr(le, Attribute.MOVEMENT_SPEED, s.movementSpeed());
        setAttr(le, Attribute.ATTACK_DAMAGE, s.attackDamage());
        setAttr(le, Attribute.ARMOR, s.armor());
        setAttr(le, Attribute.ARMOR_TOUGHNESS, s.armorToughness());
        setAttr(le, Attribute.KNOCKBACK_RESISTANCE, s.knockbackResistance());
        setAttr(le, Attribute.FOLLOW_RANGE, s.followRange());
        if (s.scale() != 1.0) {
            setAttr(le, Attribute.SCALE, s.scale());
        }
        if (def.immuneTo("KNOCKBACK")) {
            setAttr(le, Attribute.KNOCKBACK_RESISTANCE, 1.0);
        }
        le.setHealth(maxHealthOf(le, def));
    }

    /** Multiply the boss' max health by the nearby-player scaling factor and return that factor. */
    private double applyScaling(LivingEntity le, Location loc) {
        if (!plugin.config().scalingEnabled()) {
            return 1.0;
        }
        double radiusSq = plugin.config().scalingRadius() * plugin.config().scalingRadius();
        int players = 0;
        World world = loc.getWorld();
        if (world != null) {
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= radiusSq) {
                    players++;
                }
            }
        }
        double mult = Math.min(plugin.config().scalingMaxMultiplier(),
                1.0 + Math.max(0, players - 1) * plugin.config().scalingHealthPerPlayer());
        if (mult > 1.0) {
            AttributeInstance max = le.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.setBaseValue(max.getBaseValue() * mult);
                le.setHealth(max.getValue());
            }
        }
        return mult;
    }

    private void applyEquipment(LivingEntity le, BossDefinition def) {
        EntityEquipment eq = le.getEquipment();
        if (eq == null) {
            return;
        }
        if (def.randomEquipment().enabled()) {
            applyRandomEquipment(eq, def.randomEquipment());
        } else {
            EquipmentSet e = def.equipment();
            if (e.mainHand() != null) {
                eq.setItemInMainHand(new ItemStack(e.mainHand()));
            }
            if (e.offHand() != null) {
                eq.setItemInOffHand(new ItemStack(e.offHand()));
            }
            if (e.helmet() != null) {
                eq.setHelmet(new ItemStack(e.helmet()));
            }
            if (e.chestplate() != null) {
                eq.setChestplate(new ItemStack(e.chestplate()));
            }
            if (e.leggings() != null) {
                eq.setLeggings(new ItemStack(e.leggings()));
            }
            if (e.boots() != null) {
                eq.setBoots(new ItemStack(e.boots()));
            }
        }
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
    }

    private void applyRandomEquipment(EntityEquipment eq, RandomEquipment re) {
        String tier = re.armorTiers().get(ThreadLocalRandom.current().nextInt(re.armorTiers().size()))
                .toUpperCase(Locale.ROOT);
        eq.setHelmet(enchantedRandom(armorPiece(tier, "HELMET"), re));
        eq.setChestplate(enchantedRandom(armorPiece(tier, "CHESTPLATE"), re));
        eq.setLeggings(enchantedRandom(armorPiece(tier, "LEGGINGS"), re));
        eq.setBoots(enchantedRandom(armorPiece(tier, "BOOTS"), re));
        Material weapon = re.weapons().get(ThreadLocalRandom.current().nextInt(re.weapons().size()));
        eq.setItemInMainHand(enchantedRandom(new ItemStack(weapon), re));
    }

    private static ItemStack armorPiece(String tier, String slot) {
        Material m = Material.matchMaterial(tier + "_" + slot);
        return m == null ? null : new ItemStack(m);
    }

    /** Apply {@code re.enchantCount} random registry enchants (vanilla + datapack) to an item. */
    private ItemStack enchantedRandom(ItemStack item, RandomEquipment re) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || re.enchantCount() <= 0) {
            return item;
        }
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : Registry.ENCHANTMENT) {
            all.add(e);
        }
        Collections.shuffle(all);
        int applied = 0;
        for (Enchantment e : all) {
            if (applied >= re.enchantCount()) {
                break;
            }
            if (e.canEnchantItem(item)) {
                int max = Math.max(1, e.getMaxLevel() + re.extraLevels());
                meta.addEnchant(e, 1 + ThreadLocalRandom.current().nextInt(max), true);
                applied++;
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    private void tag(LivingEntity le, BossDefinition def, String encounterId) {
        PersistentDataContainer pdc = le.getPersistentDataContainer();
        pdc.set(Keys.BOSS_ID, PersistentDataType.STRING, def.id());
        pdc.set(Keys.ENCOUNTER_ID, PersistentDataType.STRING, encounterId);
    }

    private static void setAttr(LivingEntity le, Attribute attr, double value) {
        AttributeInstance inst = le.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    private static double maxHealthOf(LivingEntity le, BossDefinition def) {
        AttributeInstance inst = le.getAttribute(Attribute.MAX_HEALTH);
        return inst != null ? inst.getValue() : def.stats().health();
    }

    private BossBar.Color barColor(BossDefinition def) {
        return def.bossBar().color() != null ? def.bossBar().color() : def.difficulty().barColor();
    }

    private Component displayName(BossDefinition def) {
        // Difficulty is intentionally not shown on the name tag / boss bar (only in the broadcast).
        return Text.mm(def.name());
    }

    // ---- tick loop ------------------------------------------------------------------------

    private void tick() {
        tick++;
        if (byEntity.isEmpty()) {
            return;
        }
        // Iterate a snapshot: a skill run this tick can synchronously kill a boss (explode/AoE),
        // which fires EntityDeathEvent -> handleDeath -> byEntity.remove(). Mutating the live map
        // while iterating it would throw ConcurrentModificationException.
        for (ActiveBoss boss : new java.util.ArrayList<>(byEntity.values())) {
            // The boss may already have been removed by an earlier iteration's side effect.
            if (byEntity.get(boss.entity().getUniqueId()) != boss) {
                continue;
            }
            if (!boss.isValid()) {
                encounterHook.onEnd(boss);
                boss.cleanup(true);
                byEntity.remove(boss.entity().getUniqueId());
                continue;
            }
            if (boss.fleeAtTick() > 0 && tick >= boss.fleeAtTick()) {
                fleeBoss(boss);
                byEntity.remove(boss.entity().getUniqueId());
                continue;
            }
            boss.updateBossBar();
            int newPhase = computePhaseIndex(boss);
            if (newPhase > boss.phaseIndex()) {
                applyPhase(boss, newPhase, false);
            }
            processEnrage(boss);
            processHealers(boss);
            if (tick % 20 == 0) {
                acquireTarget(boss); // keep every boss proactively hostile toward players
            }
            if (boss.entity() instanceof org.bukkit.entity.Bee bee) {
                bee.setHasStung(false); // a boss bee must not die to its own sting
            }
            skillEngine.onTick(boss, tick);
        }
    }

    /** Keep the boss locked onto the nearest reachable player so it never sits passively idle. */
    private void acquireTarget(ActiveBoss boss) {
        LivingEntity self = boss.entity();
        double range = Math.max(16, boss.def().stats().followRange());
        double rangeSq = range * range;
        LivingEntity current = boss.target();
        boolean keep = current != null && current.isValid() && !current.isDead()
                && current.getWorld() == self.getWorld()
                && current.getLocation().distanceSquared(self.getLocation()) <= rangeSq
                && !(current instanceof Player p
                        && (p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                            || p.getGameMode() == org.bukkit.GameMode.CREATIVE));
        if (keep) {
            if (self instanceof Mob mob && mob.getTarget() != current) {
                mob.setTarget(current);
            }
            return;
        }
        Player nearest = null;
        double best = rangeSq;
        World world = self.getWorld();
        if (world != null) {
            for (Player p : world.getPlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                        || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    continue;
                }
                double d = p.getLocation().distanceSquared(self.getLocation());
                if (d <= best) {
                    best = d;
                    nearest = p;
                }
            }
        }
        if (nearest != null) {
            boss.setTarget(nearest);
            if (self instanceof Mob mob) {
                mob.setTarget(nearest);
            }
        }
    }

    private int computePhaseIndex(ActiveBoss boss) {
        List<PhaseDefinition> phases = boss.def().phases();
        if (phases.isEmpty()) {
            return -1;
        }
        double pct = boss.healthPercent();
        int result = -1;
        for (int i = 0; i < phases.size(); i++) {
            if (pct <= phases.get(i).atHealthPercent()) {
                result = i;
            }
        }
        return result;
    }

    private void applyPhase(ActiveBoss boss, int newIndex, boolean initial) {
        if (newIndex <= boss.phaseIndex()) {
            if (initial && newIndex >= 0) {
                boss.setPhaseIndex(newIndex);
            }
            return;
        }
        for (int i = boss.phaseIndex() + 1; i <= newIndex; i++) {
            enterPhase(boss, i);
        }
        boss.setPhaseIndex(newIndex);
    }

    private void enterPhase(ActiveBoss boss, int index) {
        PhaseDefinition phase = boss.def().phases().get(index);
        if (phase.enrage()) {
            applyEnrage(boss.entity());
        }
        if (phase.message() != null && !phase.message().isBlank()) {
            announceNearby(boss, Text.mm(phase.message()));
        }
        skillEngine.onPhaseChange(boss, index);
    }

    private void applyEnrage(LivingEntity le) {
        AttributeInstance speed = le.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 1.15);
        }
        AttributeInstance dmg = le.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() * 1.25);
        }
    }

    public void announceNearby(ActiveBoss boss, Component message) {
        Location loc = boss.location();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        double rangeSq = 80 * 80;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= rangeSq) {
                p.sendMessage(message);
            }
        }
    }

    private void fleeBoss(ActiveBoss boss) {
        broadcaster.bossFled(boss.def());
        LivingEntity e = boss.entity();
        if (e instanceof Mob mob) {
            mob.setTarget(null);
        }
        Player near = nearestPlayer(e.getLocation());
        if (near != null) {
            Vector away = e.getLocation().toVector().subtract(near.getLocation().toVector());
            if (away.lengthSquared() > 1.0E-4) {
                e.setVelocity(away.normalize().multiply(0.8).setY(0.3));
            }
        }
        encounterHook.onEnd(boss);
        boss.cleanup(false); // remove bar, let the entity dash off
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) {
                e.remove();
            }
        }, 100L);
    }

    private void processEnrage(ActiveBoss boss) {
        EnrageTimer et = boss.def().enrageTimer();
        if (!et.enabled()) {
            return;
        }
        long afterTicks = et.afterSeconds() * 20L;
        long intervalTicks = et.intervalSeconds() * 20L;
        if (tick - boss.spawnTick() < afterTicks) {
            return;
        }
        if (boss.lastEnrageTick() != 0 && tick - boss.lastEnrageTick() < intervalTicks) {
            return;
        }
        boss.setLastEnrageTick(tick);
        LivingEntity e = boss.entity();
        AttributeInstance dmg = e.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(dmg.getBaseValue() * et.damageMult());
        }
        AttributeInstance spd = e.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(spd.getBaseValue() * et.speedMult());
        }
        e.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, e.getLocation().add(0, 1.8, 0), 8, 0.4, 0.4, 0.4, 0);
    }

    private void processHealers(ActiveBoss boss) {
        if (boss.healers().isEmpty()) {
            return;
        }
        LivingEntity self = boss.entity();
        double heal = boss.healerHealPerTick();
        double total = 0;
        Iterator<UUID> it = boss.healers().iterator();
        while (it.hasNext()) {
            Entity e = Bukkit.getEntity(it.next());
            if (e == null || e.isDead() || !e.isValid()) {
                it.remove();
                continue;
            }
            total += heal;
            if (tick % 4 == 0) {
                e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, 1, 0), 1, 0.2, 0.2, 0.2, 0);
            }
        }
        if (total > 0 && self.getHealth() < boss.maxHealth()) {
            self.setHealth(Math.min(boss.maxHealth(), self.getHealth() + total));
        }
    }

    private void spawnBurst(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 40, 0.6, 1.0, 0.6, 0.05);
    }

    /** A rising light beam at a boss' death location for a few seconds (loot marker). */
    private void deathBeam(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Location base = loc.clone();
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 100 || !base.getChunk().isLoaded()) {
                    cancel();
                    return;
                }
                for (double y = 0; y < 12; y += 0.5) {
                    world.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0.05, 0, 0.05, 0);
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private Player nearestPlayer(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    // ---- death ----------------------------------------------------------------------------

    public void handleDeath(ActiveBoss boss, EntityDeathEvent event) {
        byEntity.remove(boss.entity().getUniqueId());
        deathBeam(boss.entity().getLocation());
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = boss.entity().getKiller();
        try {
            deathListener.onBossDeath(boss, killer, event);
        } catch (Exception ex) {
            plugin.getLogger().warning("Reward handling failed for boss " + boss.def().id() + ": " + ex.getMessage());
        }
        broadcaster.bossDeath(boss.def());
        playDeathSound();
        encounterHook.onEnd(boss);
        skillEngine.onDeath(boss);
        boss.cleanup(false);
    }

    /** Play the victory sound to every online player when a boss falls. */
    private void playDeathSound() {
        String sound = plugin.config().deathSound();
        if (sound == null || sound.isBlank()) {
            return;
        }
        String key = sound.toLowerCase(Locale.ROOT);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), key, 1.0f, 1.0f);
        }
    }

    /** Forward: the boss took damage from {@code damager}. */
    public void onBossDamaged(ActiveBoss boss, Entity damager, double amount) {
        if (damager instanceof LivingEntity living) {
            boss.setTarget(living);
        }
        Player contributor = resolvePlayer(damager);
        if (contributor != null) {
            boss.recordDamage(contributor.getUniqueId(), amount);
        }
        skillEngine.onDamaged(boss, damager, amount);
    }

    private static Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /** Forward: the boss dealt damage to {@code victim}. */
    public void onBossDealtDamage(ActiveBoss boss, Entity victim, double amount) {
        skillEngine.onDealDamage(boss, victim, amount);
    }

    // ---- queries --------------------------------------------------------------------------

    public ActiveBoss get(Entity entity) {
        return entity == null ? null : byEntity.get(entity.getUniqueId());
    }

    public boolean isBoss(Entity entity) {
        return get(entity) != null;
    }

    public List<ActiveBoss> active() {
        return new ArrayList<>(byEntity.values());
    }

    public int count() {
        return byEntity.size();
    }

    public int countOfDefinition(String bossId) {
        int n = 0;
        for (ActiveBoss b : byEntity.values()) {
            if (b.def().id().equalsIgnoreCase(bossId)) {
                n++;
            }
        }
        return n;
    }

    public double nearestBossDistance(Location loc) {
        double best = Double.MAX_VALUE;
        for (ActiveBoss b : byEntity.values()) {
            World w = b.location().getWorld();
            if (w != null && w.equals(loc.getWorld())) {
                best = Math.min(best, b.location().distance(loc));
            }
        }
        return best;
    }

    /** Remove a single active boss (bar, model and entity). */
    public void killOne(ActiveBoss boss) {
        if (byEntity.remove(boss.entity().getUniqueId()) != null) {
            encounterHook.onEnd(boss);
            boss.cleanup(true);
        }
    }

    public int killAll() {
        int n = byEntity.size();
        for (ActiveBoss boss : new ArrayList<>(byEntity.values())) {
            encounterHook.onEnd(boss);
            boss.cleanup(true);
        }
        byEntity.clear();
        // Also sweep any summoned adds / army minions still tagged in loaded worlds.
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (Keys.isWildBossesEntity(e)) {
                    e.remove();
                    n++;
                }
            }
        }
        return n;
    }
}
