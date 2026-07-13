package com.yourshika.wildbosses.spawn;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.BossDefinition;
import com.yourshika.wildbosses.boss.SpawnRules;
import com.yourshika.wildbosses.config.PluginConfig;
import com.yourshika.wildbosses.terrain.TerrainManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically attempts to spawn a random, weighted boss near an online player. Terrain-changing
 * bosses are only ever placed on ungenerated ("frontier") chunks so their corruption never touches
 * player builds; if no such spot is found the attempt is skipped (never falls back to explored land).
 */
public final class SpawnScheduler {

    private final WildBossesPlugin plugin;
    private final Map<String, Long> lastSpawnMillis = new HashMap<>();
    private EncounterStarter armyStarter;
    private BukkitTask task;

    public SpawnScheduler(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public void setArmyStarter(EncounterStarter armyStarter) {
        this.armyStarter = armyStarter;
    }

    public void start() {
        stop();
        long periodTicks = Math.max(20L, plugin.config().spawnIntervalSeconds() * 20L);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::cycle, periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void cycle() {
        PluginConfig cfg = plugin.config();
        if (!cfg.randomSpawns()) {
            return;
        }
        for (int i = 0; i < cfg.spawnAttemptsPerCycle(); i++) {
            attemptSpawn();
        }
    }

    /** One spawn attempt. Returns true if a boss/army was started. */
    public boolean attemptSpawn() {
        PluginConfig cfg = plugin.config();
        if (plugin.bossManager().count() >= cfg.maxActiveBosses()) {
            return false;
        }
        Player anchor = pickAnchorPlayer();
        if (anchor == null) {
            return false;
        }
        World world = anchor.getWorld();
        World.Environment env = world.getEnvironment();

        BossDefinition def = pickWeightedBoss(env);
        if (def == null) {
            return false;
        }

        Location loc = resolveLocation(anchor, def);
        if (loc == null) {
            return false;
        }
        if (plugin.bossManager().nearestBossDistance(loc) < cfg.minDistanceBetweenBosses()) {
            return false;
        }

        boolean started;
        if (def.isArmy() && armyStarter != null) {
            started = armyStarter.start(def, loc);
        } else {
            started = plugin.bossManager().spawn(def, loc) != null;
        }
        if (started) {
            lastSpawnMillis.put(def.id(), System.currentTimeMillis());
        }
        return started;
    }

    private Player pickAnchorPlayer() {
        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        Collections.shuffle(players);
        for (Player p : players) {
            if (plugin.config().isWorldEnabled(p.getWorld().getEnvironment())) {
                return p;
            }
        }
        return null;
    }

    private BossDefinition pickWeightedBoss(World.Environment env) {
        long now = System.currentTimeMillis();
        List<BossDefinition> eligible = new ArrayList<>();
        int totalWeight = 0;
        for (BossDefinition def : plugin.registry().all()) {
            SpawnRules rules = def.spawn();
            if (!rules.allows(env)) {
                continue;
            }
            if (rules.weight() <= 0) {
                continue;
            }
            if (plugin.bossManager().countOfDefinition(def.id()) >= rules.maxConcurrent()) {
                continue;
            }
            long last = lastSpawnMillis.getOrDefault(def.id(), 0L);
            if (now - last < rules.cooldownSeconds() * 1000L) {
                continue;
            }
            eligible.add(def);
            totalWeight += rules.weight();
        }
        if (eligible.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        for (BossDefinition def : eligible) {
            acc += def.spawn().weight();
            if (roll < acc) {
                return def;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    private Location resolveLocation(Player anchor, BossDefinition def) {
        if (!timeOk(def, anchor.getWorld())) {
            return null;
        }
        boolean frontier = def.hasTerrain() && def.terrain().onlyUngeneratedChunks();
        return frontier ? findFrontierLocation(anchor, def) : findNearbyLocation(anchor, def);
    }

    private boolean timeOk(BossDefinition def, World world) {
        String t = def.spawn().timeRequirement();
        if (t == null || t.equals("ANY")) {
            return true;
        }
        long time = world.getTime() % 24000;
        boolean day = time < 12300 || time > 23850;
        return t.equals("DAY") ? day : (!t.equals("NIGHT") || !day);
    }

    private boolean conditionsMet(BossDefinition def, Location loc) {
        return biomeOk(def, loc) && waterOk(def, loc);
    }

    private boolean biomeOk(BossDefinition def, Location loc) {
        var biomes = def.spawn().biomes();
        if (biomes.isEmpty()) {
            return true;
        }
        String name = loc.getBlock().getBiome().getKey().value().toUpperCase(Locale.ROOT);
        for (String want : biomes) {
            if (name.contains(want)) {
                return true;
            }
        }
        return false;
    }

    private boolean waterOk(BossDefinition def, Location loc) {
        if (!def.spawn().nearWater()) {
            return true;
        }
        World w = loc.getWorld();
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = -3; dy <= 1; dy++) {
                    if (w.getBlockAt(bx + dx, by + dy, bz + dz).getType() == Material.WATER) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Location findNearbyLocation(Player anchor, BossDefinition def) {
        World world = anchor.getWorld();
        SpawnRules rules = def.spawn();
        double minDist = Math.max(8, rules.minPlayerDistance());
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double dist = minDist + ThreadLocalRandom.current().nextDouble(48);
            int x = anchor.getLocation().getBlockX() + (int) (Math.cos(angle) * dist);
            int z = anchor.getLocation().getBlockZ() + (int) (Math.sin(angle) * dist);
            Integer y = findSafeY(world, x, z, rules.minY(), rules.maxY());
            if (y != null) {
                Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                if (conditionsMet(def, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private Location findFrontierLocation(Player anchor, BossDefinition def) {
        World world = anchor.getWorld();
        PluginConfig cfg = plugin.config();
        SpawnRules rules = def.spawn();
        int radius = def.terrain().radius() + 8;
        for (int attempt = 0; attempt < cfg.frontierAttempts(); attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double dist = cfg.frontierMinDistance()
                    + ThreadLocalRandom.current().nextDouble(cfg.frontierMaxDistance() - cfg.frontierMinDistance());
            int x = anchor.getLocation().getBlockX() + (int) (Math.cos(angle) * dist);
            int z = anchor.getLocation().getBlockZ() + (int) (Math.sin(angle) * dist);
            if (!TerrainManager.footprintUngenerated(world, x, z, radius)) {
                continue;
            }
            // Generating the column here is intentional - the chunk is pristine (never visited).
            Integer y = findSafeY(world, x, z, rules.minY(), rules.maxY());
            if (y != null) {
                Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                if (conditionsMet(def, candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Find a standing Y at (x,z) with a solid floor and two passable blocks above, within bounds. */
    private Integer findSafeY(World world, int x, int z, int minY, int maxY) {
        int top = Math.min(maxY, world.getMaxHeight() - 3);
        int bottom = Math.max(minY, world.getMinHeight() + 1);
        for (int y = top; y >= bottom; y--) {
            var floor = world.getBlockAt(x, y, z);
            var feet = world.getBlockAt(x, y + 1, z);
            var head = world.getBlockAt(x, y + 2, z);
            if (floor.getType().isSolid()
                    && !floor.isLiquid()
                    && feet.isPassable()
                    && head.isPassable()) {
                return y + 1;
            }
        }
        return null;
    }
}
