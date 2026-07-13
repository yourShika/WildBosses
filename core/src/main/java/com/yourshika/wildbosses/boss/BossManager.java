package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.model.ModelHandle;
import com.yourshika.wildbosses.model.ModelManager;
import com.yourshika.wildbosses.skill.SkillEngine;
import com.yourshika.wildbosses.util.Keys;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
import java.util.LinkedHashMap;
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
    private final ModelManager modelManager;
    private final Broadcaster broadcaster;

    private final Map<UUID, ActiveBoss> byEntity = new LinkedHashMap<>();

    private SkillEngine skillEngine = SkillEngine.NOOP;
    private EncounterHook encounterHook = EncounterHook.NOOP;
    private BossDeathListener deathListener = BossDeathListener.NOOP;

    private BukkitTask task;
    private long tick;

    public BossManager(WildBossesPlugin plugin, BossRegistry registry, ModelManager modelManager,
                       Broadcaster broadcaster) {
        this.plugin = plugin;
        this.registry = registry;
        this.modelManager = modelManager;
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
            boss.cleanup(false);
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
        le.setPersistent(true);
        if (le instanceof Mob mob) {
            mob.setAware(true);
        }
        if (le instanceof org.bukkit.entity.Zombie zombie) {
            zombie.setShouldBurnInDay(false); // bosses shouldn't die to sunlight
        }
        tag(le, def, encounterId);

        double maxHp = maxHealthOf(le, def);
        BossBar bar = BossBar.bossBar(displayName(def), 1f, barColor(def), def.bossBar().overlay());
        ModelHandle model = modelManager.attach(le, def);
        le.customName(displayName(def));
        // Hide the vanilla name tag only when a custom model is actually shown.
        le.setCustomNameVisible(model == ModelHandle.NOOP);

        ActiveBoss boss = new ActiveBoss(def, le, bar, model, maxHp, tick, encounterId);
        if (plugin.config().bossLifetimeEnabled()) {
            long minTicks = plugin.config().bossLifetimeMinMinutes() * 60L * 20L;
            long maxTicks = plugin.config().bossLifetimeMaxMinutes() * 60L * 20L;
            long life = maxTicks > minTicks ? ThreadLocalRandom.current().nextLong(minTicks, maxTicks) : minTicks;
            boss.setFleeAtTick(tick + life);
        }
        byEntity.put(le.getUniqueId(), boss);

        applyPhase(boss, computePhaseIndex(boss), true);
        playAnimationState(boss, "idle", true);
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
        le.setHealth(maxHealthOf(le, def));
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
        var it = byEntity.entrySet().iterator();
        while (it.hasNext()) {
            ActiveBoss boss = it.next().getValue();
            if (!boss.isValid()) {
                encounterHook.onEnd(boss);
                boss.cleanup(true);
                it.remove();
                continue;
            }
            if (boss.fleeAtTick() > 0 && tick >= boss.fleeAtTick()) {
                fleeBoss(boss);
                it.remove();
                continue;
            }
            boss.updateBossBar();
            int newPhase = computePhaseIndex(boss);
            if (newPhase > boss.phaseIndex()) {
                applyPhase(boss, newPhase, false);
            }
            updateAnimation(boss);
            skillEngine.onTick(boss, tick);
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
        if (phase.animation() != null && !phase.animation().isBlank()) {
            playAnimation(boss, phase.animation(), false);
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

    private void playAnimationState(ActiveBoss boss, String state, boolean loop) {
        String anim = boss.def().animation(state);
        if (anim != null && !anim.isBlank()) {
            boss.model().playAnimation(anim, loop);
        }
    }

    private void playAnimation(ActiveBoss boss, String nameOrState, boolean loop) {
        String mapped = boss.def().animation(nameOrState);
        boss.model().playAnimation(mapped != null ? mapped : nameOrState, loop);
    }

    /** Drive BetterModel state animations (walk/sprint/idle/target/swim/fly) by the boss' state. */
    private void updateAnimation(ActiveBoss boss) {
        if (tick < boss.attackHoldUntil()) {
            return; // let a transient attack animation play out
        }
        LivingEntity e = boss.entity();
        String state;
        if (e.isInWater()) {
            state = "swim";
        } else if (!e.hasGravity()) {
            state = "fly";
        } else {
            double speedSq = e.getVelocity().clone().setY(0).lengthSquared();
            if (speedSq > 0.09) {
                state = "sprint";
            } else if (speedSq > 0.0025) {
                state = "walk";
            } else if (boss.target() != null && boss.target().isValid()) {
                state = "target";
            } else {
                state = "idle";
            }
        }
        if (!state.equals(boss.currentAnimState())) {
            boss.setCurrentAnimState(state);
            boss.model().playAnimation(mappedState(boss, state), true);
        }
    }

    private static String mappedState(ActiveBoss boss, String state) {
        String mapped = boss.def().animation(state);
        return mapped != null ? mapped : state;
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
        boss.model().playAnimation(mappedState(boss, "sprint"), true);
        encounterHook.onEnd(boss);
        boss.cleanup(false); // remove bar + model, let the entity dash off
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid()) {
                e.remove();
            }
        }, 100L);
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
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = boss.entity().getKiller();
        try {
            deathListener.onBossDeath(boss, killer, event);
        } catch (Exception ex) {
            plugin.getLogger().warning("Reward handling failed for boss " + boss.def().id() + ": " + ex.getMessage());
        }
        broadcaster.bossDeath(boss.def());
        encounterHook.onEnd(boss);
        skillEngine.onDeath(boss);
        // Play a death animation (if the model has one) before tearing the model down.
        boss.model().playAnimation(mappedState(boss, "death"), false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> boss.cleanup(false), 20L);
    }

    /** Forward: the boss took damage from {@code damager}. */
    public void onBossDamaged(ActiveBoss boss, Entity damager, double amount) {
        if (damager instanceof LivingEntity living) {
            boss.setTarget(living);
        }
        skillEngine.onDamaged(boss, damager, amount);
    }

    /** Forward: the boss dealt damage to {@code victim}. */
    public void onBossDealtDamage(ActiveBoss boss, Entity victim, double amount) {
        boss.setAttackHoldUntil(tick + 8);
        boss.setCurrentAnimState("attack");
        boss.model().playAnimation(mappedState(boss, "attack"), false);
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
        return n;
    }
}
