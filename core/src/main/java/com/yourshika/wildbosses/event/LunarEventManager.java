package com.yourshika.wildbosses.event;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Night-time "lunar events" (Blood Moon, Crystal Moon, ...). While one is active in an Overworld:
 * ambient themed particles drift around each nearby player, every naturally-spawned hostile mob is
 * buffed, and bosses spawn more readily. The event begins on a fresh night (rolled once against a
 * configurable chance) and ends at daybreak - so sleeping through the night also ends it.
 *
 * <p>Note: the vanilla moon's colour can't be changed server-side without a resource pack, so the
 * "red / crystal moon" is conveyed through heavy coloured particles and a title, not the moon texture.</p>
 */
public final class LunarEventManager implements Listener {

    private static final String LUNAR_TAG = "wb_lunar"; // marks an empowered lunar mob
    private static final EntityType[] HORDE_TYPES = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK,
            EntityType.ZOMBIE_VILLAGER, EntityType.STRAY, EntityType.CREEPER};

    private final WildBossesPlugin plugin;
    private final Map<UUID, String> activeByWorld = new HashMap<>();   // world uid -> event type
    private final Map<UUID, Long> rolledNight = new HashMap<>();       // world uid -> day number rolled
    private final Map<UUID, Long> startedAt = new HashMap<>();         // world uid -> event start (millis)
    private final Set<UUID> forced = new HashSet<>();                  // admin-triggered (don't auto-end at dawn)
    private BukkitTask task;

    /** Only Blood/Crystal/Eclipse are night-locked; a golden Harvest may linger into the day. */
    private static boolean nightOnly(String type) {
        return !"harvestmoon".equals(type);
    }

    public LunarEventManager(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // Clean up any lingering empowerment side-effects (esp. infinite invisibility) so nothing is
        // left stuck after a reload/disable - and, on the next enable, after a crash.
        for (World w : plugin.getServer().getWorlds()) {
            clearInvisibility(w);
        }
        activeByWorld.clear();
        forced.clear();
        startedAt.clear();
    }

    /** Remove the infinite invisibility from any empowered mobs in a world (they become visible again). */
    private void clearInvisibility(World world) {
        for (LivingEntity le : world.getLivingEntities()) {
            if (le.getScoreboardTags().contains(LUNAR_TAG)) {
                le.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
    }

    public boolean isActive(World world) {
        return world != null && activeByWorld.containsKey(world.getUID());
    }

    /** The active event type in a world, or {@code null} if none. */
    public String activeType(World world) {
        return world == null ? null : activeByWorld.get(world.getUID());
    }

    /** Admin trigger: start an event now (any time of day) that only ends via {@link #forceStop}. */
    public void forceStart(World world, String type) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return; // lunar events are Overworld-only
        }
        forced.add(world.getUID());
        rolledNight.put(world.getUID(), Math.floorDiv(world.getFullTime(), 24000L));
        startEvent(world, type);
    }

    /** Admin trigger: stop whatever event is running in a world. */
    public boolean forceStop(World world) {
        if (world == null) {
            return false;
        }
        forced.remove(world.getUID());
        if (activeByWorld.containsKey(world.getUID())) {
            endEvent(world, activeByWorld.get(world.getUID()));
            return true;
        }
        return false;
    }

    public boolean activeAnywhere() {
        return !activeByWorld.isEmpty();
    }

    /** Extra boss-spawn attempts to run per cycle while any lunar event is active (Harvest boosts more). */
    public int bossExtraAttempts() {
        if (!activeAnywhere()) {
            return 0;
        }
        int base = Math.max(0, plugin.getConfig().getInt("lunar-events.boss-extra-attempts", 1));
        int extra = base;
        for (String t : activeByWorld.values()) {
            if ("harvestmoon".equals(t)) {
                extra = Math.max(extra, base + 2); // a Harvest Moon draws out far more great beasts
            }
        }
        return extra;
    }

    /** Harvest Moon: mobs killed by players yield extra experience (bosses excluded). */
    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        if (!"harvestmoon".equals(activeByWorld.get(event.getEntity().getWorld().getUID()))) {
            return;
        }
        if (event.getEntity().getScoreboardTags().contains("wildbosses")) {
            return;
        }
        double mult = plugin.getConfig().getDouble("lunar-events.harvest-xp-multiplier", 2.0);
        event.setDroppedExp((int) Math.round(event.getDroppedExp() * Math.max(1.0, mult)));
    }

    /** Sleeping through the night (or a {@code /time} skip) ends any active event in that world. */
    @EventHandler(ignoreCancelled = true)
    public void onTimeSkip(org.bukkit.event.world.TimeSkipEvent event) {
        String active = activeByWorld.get(event.getWorld().getUID());
        if (active != null) {
            endEvent(event.getWorld(), active);
        }
    }

    // ---- loop -----------------------------------------------------------------------------

    private void tick() {
        boolean enabled = plugin.getConfig().getBoolean("lunar-events.enabled", true);
        double chance = plugin.getConfig().getDouble("lunar-events.chance", 0.12);
        List<String> types = plugin.getConfig().getStringList("lunar-events.types");
        if (types.isEmpty()) {
            types = List.of("bloodmoon", "crystalmoon");
        }
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            long timeOfDay = world.getTime() % 24000;
            boolean night = timeOfDay >= 13000 && timeOfDay < 23000;
            UUID id = world.getUID();
            String active = activeByWorld.get(id);
            if (active != null) {
                boolean isForced = forced.contains(id);
                // Hard safety cap so an event can NEVER get stuck running forever (any cause).
                int capMin = isForced
                        ? plugin.getConfig().getInt("lunar-events.forced-max-minutes", 60)
                        : plugin.getConfig().getInt("lunar-events.max-minutes", 20);
                long elapsed = System.currentTimeMillis() - startedAt.getOrDefault(id, 0L);
                boolean expired = capMin > 0 && elapsed >= capMin * 60_000L;
                // Blood/Crystal/Eclipse are NIGHT-ONLY: they fade at dawn. Harvest may linger into day.
                boolean dawnEnded = nightOnly(active) && !night && !isForced;
                if (expired || dawnEnded) {
                    endEvent(world, active);
                } else {
                    ambient(world, active);
                    maybeSpawnHorde(world, active);
                }
                continue;
            }
            if (!enabled || !night || world.getPlayers().isEmpty()) {
                continue;
            }
            long dayNumber = Math.floorDiv(world.getFullTime(), 24000L);
            Long rolled = rolledNight.get(id);
            if (rolled != null && rolled == dayNumber) {
                continue; // already rolled for tonight
            }
            rolledNight.put(id, dayNumber);
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                startEvent(world, types.get(ThreadLocalRandom.current().nextInt(types.size())));
            }
        }
    }

    private void startEvent(World world, String type) {
        activeByWorld.put(world.getUID(), type.toLowerCase(Locale.ROOT));
        startedAt.put(world.getUID(), System.currentTimeMillis());
        Component title = Text.mm(titleFor(type));
        Component sub = Text.mm(subtitleFor(type));
        for (Player p : world.getPlayers()) {
            p.showTitle(net.kyori.adventure.title.Title.title(title, sub));
            p.sendMessage(title);
            p.playSound(p.getLocation(), soundFor(type), 1.0f, 0.6f);
        }
    }

    private void endEvent(World world, String type) {
        activeByWorld.remove(world.getUID());
        forced.remove(world.getUID());
        startedAt.remove(world.getUID());
        clearInvisibility(world); // empowered mobs become visible again - never left permanently invisible
        Component msg = Text.mm("<gray>The " + prettyName(type) + " fades away.");
        for (Player p : world.getPlayers()) {
            p.removePotionEffect(PotionEffectType.DARKNESS); // clear any lingering eclipse gloom at once
            p.sendMessage(msg);
        }
    }

    // ---- effects --------------------------------------------------------------------------

    private void ambient(World world, String type) {
        for (Player p : world.getPlayers()) {
            var base = p.getLocation();
            for (int i = 0; i < 24; i++) {
                var loc = base.clone().add(rand(9), ThreadLocalRandom.current().nextDouble(0, 7), rand(9));
                skyParticle(world, loc, type, i);
            }
            // Eclipse gloom: an OCCASIONAL short Darkness pulse (not constant), so you can still see
            // most of the time. Toggle with lunar-events.eclipse-darkness.
            if (type.equals("eclipse")
                    && plugin.getConfig().getBoolean("lunar-events.eclipse-darkness", true)
                    && ThreadLocalRandom.current().nextInt(9) == 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
            }
            // A shimmer on each empowered mob - but only ones the player can actually SEE, so mobs
            // tucked away in caves behind walls don't light up (no glow, no wall-piercing outline).
            for (Entity e : world.getNearbyEntities(base, 26, 18, 26,
                    en -> en instanceof LivingEntity le && le.getScoreboardTags().contains(LUNAR_TAG))) {
                if (p.hasLineOfSight(e)) {
                    mobShimmer(world, ((LivingEntity) e).getEyeLocation(), type);
                }
            }
        }
    }

    private void skyParticle(World world, org.bukkit.Location loc, String type, int i) {
        switch (type) {
            case "crystalmoon" -> {
                if ((i & 1) == 0) {
                    world.spawnParticle(Particle.END_ROD, loc, 1, 0, 0, 0, 0.001);
                } else {
                    world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(120, 220, 255), 1.4f));
                }
            }
            case "harvestmoon" -> {
                if ((i % 3) == 0) {
                    world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0.1, 0.1, 0.1, 0);
                } else {
                    world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(250, 200, 60), 1.5f));
                }
            }
            case "eclipse" -> {
                if ((i & 1) == 0) {
                    world.spawnParticle(Particle.LARGE_SMOKE, loc, 1, 0.2, 0.2, 0.2, 0.001);
                } else {
                    world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(40, 40, 55), 1.6f));
                }
            }
            default -> world.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(170, 8, 8), 1.6f));
        }
    }

    private void mobShimmer(World world, org.bukkit.Location at, String type) {
        switch (type) {
            case "crystalmoon" -> world.spawnParticle(Particle.END_ROD, at, 3, 0.25, 0.4, 0.25, 0.002);
            case "harvestmoon" -> world.spawnParticle(Particle.WAX_ON, at, 4, 0.3, 0.4, 0.3, 0);
            case "eclipse" -> world.spawnParticle(Particle.SMOKE, at, 5, 0.3, 0.4, 0.3, 0.005);
            default -> world.spawnParticle(Particle.DUST, at, 4, 0.3, 0.4, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 20, 20), 1.2f));
        }
    }

    /**
     * Empower a fraction of the hostile mobs that spawn naturally during the event (WildBosses' own
     * mobs are skipped). Power is randomised per mob - some are only a touch tougher, a few are truly
     * dangerous - and each empowered mob gets a themed variant name. No glow (that pierced cave walls).
     */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster monster) || !isActive(monster.getWorld())) {
            return;
        }
        if (monster.getScoreboardTags().contains("wildbosses")) {
            return; // never double-buff our bosses / minions / adds
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        String type = activeByWorld.get(monster.getWorld().getUID());
        if ("harvestmoon".equals(type)) {
            return; // a Harvest Moon is a peaceful, rewarding night - mobs are NOT empowered
        }
        if (ThreadLocalRandom.current().nextDouble()
                >= plugin.getConfig().getDouble("lunar-events.mob-affect-chance", 0.6)) {
            return; // not every mob is empowered
        }
        empowerMob(monster, type);
    }

    /** Apply randomised lunar power, optional modest gear, and a themed name to a single mob. */
    private void empowerMob(Monster monster, String type) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double healthMax = plugin.getConfig().getDouble("lunar-events.mob-health-multiplier", 1.6);
        int strengthMax = plugin.getConfig().getInt("lunar-events.mob-strength", 1);
        int speedMax = plugin.getConfig().getInt("lunar-events.mob-speed", 0);

        double healthMult = healthMax > 1.0 ? 1.0 + rnd.nextDouble() * (healthMax - 1.0) : 1.0;
        AttributeInstance maxHp = monster.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null && healthMult > 1.0) {
            maxHp.setBaseValue(maxHp.getBaseValue() * healthMult);
            monster.setHealth(maxHp.getBaseValue());
        }
        if (strengthMax >= 1 && rnd.nextInt(3) > 0) { // ~2/3 get some Strength, amplifier randomised
            addInfinite(monster, PotionEffectType.STRENGTH, rnd.nextInt(strengthMax + 1));
        }
        if (speedMax >= 1 && rnd.nextInt(3) == 0) {
            addInfinite(monster, PotionEffectType.SPEED, rnd.nextInt(speedMax + 1));
        }
        // An eclipse hides some of its horrors: a few empowered mobs stalk you invisibly.
        if ("eclipse".equals(type) && rnd.nextInt(4) == 0) {
            addInfinite(monster, PotionEffectType.INVISIBILITY, 0);
        }
        if (rnd.nextDouble() < plugin.getConfig().getDouble("lunar-events.mob-gear-chance", 0.35)) {
            equipRandomGear(monster, rnd);
        }
        monster.getScoreboardTags().add(LUNAR_TAG);
        monster.customName(Text.mm(variantName(monster.getType(), type, rnd)));
        monster.setCustomNameVisible(false); // shows on approach/hover like a normal named mob, not always
    }

    /** Modest, never-OP random gear (leather..iron; no diamond/netherite) the mob won't drop. */
    private void equipRandomGear(Monster monster, ThreadLocalRandom rnd) {
        EntityEquipment eq = monster.getEquipment();
        if (eq == null) {
            return;
        }
        String[] tiers = {"LEATHER", "LEATHER", "CHAINMAIL", "GOLDEN", "IRON"};
        String tier = tiers[rnd.nextInt(tiers.length)];
        String[] slots = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
        for (String slot : slots) {
            if (rnd.nextInt(3) == 0) {
                continue; // leave some slots empty - not always a full set
            }
            Material m = Material.matchMaterial(tier + "_" + slot);
            if (m == null) {
                continue;
            }
            ItemStack piece = new ItemStack(m);
            switch (slot) {
                case "HELMET" -> eq.setHelmet(piece);
                case "CHESTPLATE" -> eq.setChestplate(piece);
                case "LEGGINGS" -> eq.setLeggings(piece);
                default -> eq.setBoots(piece);
            }
        }
        EntityType t = monster.getType();
        if (t == EntityType.ZOMBIE || t == EntityType.HUSK || t == EntityType.ZOMBIE_VILLAGER
                || t == EntityType.ZOMBIFIED_PIGLIN || t == EntityType.PIGLIN) {
            Material[] weapons = {Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.IRON_AXE};
            if (rnd.nextBoolean()) {
                eq.setItemInMainHand(new ItemStack(weapons[rnd.nextInt(weapons.length)]));
            }
        }
        // Never drop the gear - keeps loot sane and avoids a gear flood.
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    /** Occasionally (per active world) spawn a small empowered horde near a random player. */
    private void maybeSpawnHorde(World world, String type) {
        if ("harvestmoon".equals(type) || !plugin.getConfig().getBoolean("lunar-events.hordes", true)) {
            return; // peaceful night, or hordes disabled
        }
        if (world.getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            return; // hostiles would be wiped instantly on Peaceful
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() >= plugin.getConfig().getDouble("lunar-events.horde-chance", 0.03)) {
            return;
        }
        var players = world.getPlayers();
        if (players.isEmpty()) {
            return;
        }
        Player p = players.get(rnd.nextInt(players.size()));
        int min = Math.max(1, plugin.getConfig().getInt("lunar-events.horde-size-min", 3));
        int max = Math.max(min, plugin.getConfig().getInt("lunar-events.horde-size-max", 6));
        int size = min + rnd.nextInt(max - min + 1);
        EntityType mob = HORDE_TYPES[rnd.nextInt(HORDE_TYPES.length)];
        double ang = rnd.nextDouble(Math.PI * 2);
        double dist = 8 + rnd.nextDouble(10);
        int ox = p.getLocation().getBlockX() + (int) (Math.cos(ang) * dist);
        int oz = p.getLocation().getBlockZ() + (int) (Math.sin(ang) * dist);
        int spawned = 0;
        for (int i = 0; i < size * 3 && spawned < size; i++) {
            int x = ox + rnd.nextInt(5) - 2;
            int z = oz + rnd.nextInt(5) - 2;
            Integer y = groundY(world, x, z, p.getLocation().getBlockY());
            if (y == null) {
                continue;
            }
            Entity e = world.spawnEntity(new Location(world, x + 0.5, y, z + 0.5), mob);
            if (e instanceof Monster m) {
                m.setTarget(p);
                empowerMob(m, type); // CUSTOM-spawned, so onSpawn skips them - empower directly
                spawned++;
            } else {
                e.remove();
            }
        }
        if (spawned > 0) {
            world.playSound(p.getLocation(), soundFor(type), 0.7f, 0.9f);
        }
    }

    /** A safe standing Y near {@code nearY} at (x,z): solid floor, two passable blocks above. */
    private Integer groundY(World w, int x, int z, int nearY) {
        int top = Math.min(w.getMaxHeight() - 2, nearY + 6);
        int bottom = Math.max(w.getMinHeight() + 1, nearY - 8);
        for (int y = top; y >= bottom; y--) {
            var floor = w.getBlockAt(x, y, z);
            var feet = w.getBlockAt(x, y + 1, z);
            var head = w.getBlockAt(x, y + 2, z);
            if (floor.getType().isSolid() && !floor.isLiquid() && feet.isPassable() && head.isPassable()) {
                return y + 1;
            }
        }
        return null;
    }

    /** A themed name like "Crystal Zombie", "Crystal Skeleton Warrior", "Bloodmoon Spider Brute". */
    private String variantName(EntityType mob, String event, ThreadLocalRandom rnd) {
        String color;
        String prefix;
        switch (event == null ? "" : event) {
            case "crystalmoon" -> { color = "<aqua>"; prefix = "Crystal"; }
            case "eclipse" -> { color = "<dark_gray>"; prefix = "Shadow"; }
            default -> { color = "<red>"; prefix = "Bloodmoon"; }
        }
        String[] tiers = {"", "", "", " Warrior", " Brute", " Champion", " Stalker"};
        return color + prefix + " " + Text.titleCase(mob.name()) + tiers[rnd.nextInt(tiers.length)];
    }

    private static void addInfinite(LivingEntity e, PotionEffectType type, int amplifier) {
        if (amplifier < 0) {
            return;
        }
        e.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, false));
    }

    // ---- text -----------------------------------------------------------------------------

    private String titleFor(String type) {
        return switch (type) {
            case "crystalmoon" -> "<gradient:#67e8f9:#a5f3fc><bold>Crystal Moon</bold></gradient>";
            case "harvestmoon" -> "<gradient:#fbbf24:#f59e0b><bold>Harvest Moon</bold></gradient>";
            case "eclipse" -> "<gradient:#374151:#0b1220><bold>Blood Eclipse</bold></gradient>";
            default -> "<gradient:#7f1d1d:#ef4444><bold>Blood Moon</bold></gradient>";
        };
    }

    private String subtitleFor(String type) {
        return switch (type) {
            case "crystalmoon" -> "<aqua>The night hums with crystal light - the mobs grow strange and strong.";
            case "harvestmoon" -> "<gold>A golden night - fortune favours the bold, and great beasts stir.";
            case "eclipse" -> "<dark_gray>Darkness swallows the land - things move unseen.";
            default -> "<red>The mobs grow bold and bloodthirsty tonight.";
        };
    }

    private String prettyName(String type) {
        return switch (type) {
            case "crystalmoon" -> "<aqua>Crystal Moon</aqua>";
            case "harvestmoon" -> "<gold>Harvest Moon</gold>";
            case "eclipse" -> "<dark_gray>Eclipse</dark_gray>";
            default -> "<red>Blood Moon</red>";
        };
    }

    private String soundFor(String type) {
        return switch (type) {
            case "crystalmoon" -> "block.amethyst_block.chime";
            case "harvestmoon" -> "block.note_block.chime";
            case "eclipse" -> "entity.warden.emerge";
            default -> "entity.wither.spawn";
        };
    }

    private static double rand(double m) {
        return ThreadLocalRandom.current().nextDouble(-m, m);
    }
}
