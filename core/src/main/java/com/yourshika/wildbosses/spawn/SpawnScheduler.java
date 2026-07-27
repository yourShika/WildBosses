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
    private long nextCycleMillis;
    // Anti-repeat: the last boss the random spawner actually spawned, and how many times in a row.
    private String lastSpawnedId;
    private int consecutiveSpawns;

    public SpawnScheduler(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public void setArmyStarter(EncounterStarter armyStarter) {
        this.armyStarter = armyStarter;
    }

    public void start() {
        stop();
        scheduleNext();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Schedule the next spawn cycle after a random delay in [min, max] (a fixed value if equal). */
    private void scheduleNext() {
        PluginConfig cfg = plugin.config();
        int lo = cfg.spawnIntervalMinSeconds();
        int hi = cfg.spawnIntervalMaxSeconds();
        int seconds = hi > lo ? ThreadLocalRandom.current().nextInt(lo, hi + 1) : lo;
        long delay = Math.max(20L, seconds * 20L);
        nextCycleMillis = System.currentTimeMillis() + delay * 50L; // 50 ms per tick
        task = plugin.getServer().getScheduler().runTaskLater(plugin, this::cycle, delay);
    }

    /** Human-readable spawn diagnostics for {@code /wb debug} (why a boss will/won't spawn). */
    public java.util.List<String> debugInfo() {
        PluginConfig cfg = plugin.config();
        java.util.List<String> out = new ArrayList<>();
        int bosses = plugin.bossManager().count();
        int armies = plugin.armyManager().count();
        out.add("<gray>random-spawns: <yellow>" + cfg.randomSpawns()
                + " <gray>| interval <yellow>" + cfg.spawnIntervalMinSeconds() / 60 + "-"
                + cfg.spawnIntervalMaxSeconds() / 60 + "m <gray>| attempts/cycle <yellow>"
                + cfg.spawnAttemptsPerCycle());
        out.add("<gray>active: <yellow>" + bosses + "<gray> boss + <yellow>" + armies
                + "<gray> army / cap <yellow>" + cfg.maxActiveBosses());
        out.add("<gray>next cycle in ~<yellow>" + Math.max(0, (nextCycleMillis - System.currentTimeMillis()) / 1000) + "s"
                + " <gray>| online <yellow>" + plugin.getServer().getOnlinePlayers().size());
        out.add("<dark_gray>--- per-boss eligibility (weight / cooldown-left / count) ---");
        long now = System.currentTimeMillis();
        for (BossDefinition def : plugin.registry().all()) {
            SpawnRules r = def.spawn();
            long cdLeft = Math.max(0, (r.cooldownSeconds() * 1000L - (now - lastSpawnMillis.getOrDefault(def.id(), 0L))) / 1000);
            int cur = def.isArmy() ? plugin.armyManager().countOfDefinition(def.id())
                    : plugin.bossManager().countOfDefinition(def.id());
            out.add("<dark_gray> - <white>" + def.id() + " <gray>w<yellow>" + r.weight()
                    + " <gray>cd<yellow>" + cdLeft + "s <gray>cnt<yellow>" + cur + "<gray>/<yellow>" + r.maxConcurrent()
                    + (def.hasTerrain() && def.terrain().onlyUngeneratedChunks() ? " <dark_gray>[frontier]" : "")
                    + (def.isArmy() ? " <dark_gray>[army]" : ""));
        }
        return out;
    }

    private void cycle() {
        PluginConfig cfg = plugin.config();
        if (cfg.randomSpawns()) {
            int attempts = cfg.spawnAttemptsPerCycle();
            // A lunar event (Blood/Crystal Moon) makes bosses far more likely to appear.
            if (plugin.lunarEvents() != null) {
                attempts += plugin.lunarEvents().bossExtraAttempts();
            }
            for (int i = 0; i < attempts; i++) {
                attemptSpawn();
            }
        }
        scheduleNext(); // keep the loop going with a fresh random delay
    }

    /**
     * One spawn attempt. For a normal boss this resolves a spot in already-generated terrain and spawns
     * synchronously. For a FRONTIER (terrain) boss the location search generates pristine chunks, so it
     * runs ASYNCHRONOUSLY (off the main thread) and the spawn happens later in a callback - this is what
     * removes the main-thread world-gen stall on boss spawn.
     */
    public boolean attemptSpawn() {
        PluginConfig cfg = plugin.config();
        // Armies count toward the global cap too (they were previously invisible to it, letting
        // unbounded concurrent armies accumulate).
        if (plugin.bossManager().count() + plugin.armyManager().count() >= cfg.maxActiveBosses()) {
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
        if (!timeOk(def, world)) {
            return false;
        }

        boolean frontier = def.hasTerrain() && def.terrain().onlyUngeneratedChunks();
        if (frontier) {
            // Async: generate candidate chunks off-thread, spawn in the callback (no main-thread stall).
            // Re-check the caps AND time-of-day in the callback: several frontier attempts in one cycle
            // (esp. during a lunar event's extra attempts) would otherwise each pass the pre-dispatch
            // check and all spawn, overshooting max-active/max-concurrent.
            findFrontierLocationAsync(anchor, def, loc -> {
                if (loc == null || capReached(def) || !timeOk(def, loc.getWorld()) || !spacedFarEnough(loc)) {
                    return;
                }
                spawnResolved(def, loc);
            });
            return true; // dispatched; the actual spawn resolves on a later tick
        }

        Location loc = findNearbyLocation(anchor, def);
        if (loc == null || !spacedFarEnough(loc)) {
            return false;
        }
        return spawnResolved(def, loc);
    }

    /** Whether the global or this boss' per-definition active cap is already reached (async re-check). */
    private boolean capReached(BossDefinition def) {
        PluginConfig cfg = plugin.config();
        if (plugin.bossManager().count() + plugin.armyManager().count() >= cfg.maxActiveBosses()) {
            return true;
        }
        int active = def.isArmy() ? plugin.armyManager().countOfDefinition(def.id())
                : plugin.bossManager().countOfDefinition(def.id());
        return active >= def.spawn().maxConcurrent();
    }

    /** New encounters must be at least min-distance from every active boss AND army anchor. */
    private boolean spacedFarEnough(Location loc) {
        double nearest = Math.min(plugin.bossManager().nearestBossDistance(loc),
                plugin.armyManager().nearestArmyDistance(loc));
        return nearest >= plugin.config().minDistanceBetweenBosses();
    }

    /** Start the boss/army at a resolved location; records the spawn time on success. */
    private boolean spawnResolved(BossDefinition def, Location loc) {
        boolean started;
        if (def.isArmy() && armyStarter != null) {
            started = armyStarter.start(def, loc);
        } else {
            started = plugin.bossManager().spawn(def, loc) != null;
        }
        if (started) {
            lastSpawnMillis.put(def.id(), System.currentTimeMillis());
            if (def.id().equals(lastSpawnedId)) {
                consecutiveSpawns++;
            } else {
                lastSpawnedId = def.id();
                consecutiveSpawns = 1;
            }
        }
        return started;
    }

    /**
     * Find a scheduler-style random spawn location for a boss - near a random online player in an
     * enabled world, and on a frontier chunk for terrain bosses - or {@code null} if none was found.
     * Backs {@code /wb spawn <id> random} (and {@code /wb army <id> random}).
     */
    public Location randomLocationFor(BossDefinition def) {
        Player anchor = pickAnchorPlayer();
        return anchor == null ? null : resolveLocation(anchor, def);
    }

    private Player pickAnchorPlayer() {
        List<Player> players = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        Collections.shuffle(players);
        for (Player p : players) {
            if (plugin.config().isWorldAllowed(p.getWorld())) {
                return p;
            }
        }
        return null;
    }

    /** Seconds until the next spawn cycle (for the {@code %wildbosses_next_spawn%} placeholder). */
    public long secondsToNextCycle() {
        return Math.max(0, (nextCycleMillis - System.currentTimeMillis()) / 1000);
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
            // Count army encounters via the army manager (they aren't in BossManager.byEntity), so the
            // per-boss max-concurrent cap applies to army bosses too.
            int activeOfDef = def.isArmy()
                    ? plugin.armyManager().countOfDefinition(def.id())
                    : plugin.bossManager().countOfDefinition(def.id());
            if (activeOfDef >= rules.maxConcurrent()) {
                continue;
            }
            long last = lastSpawnMillis.getOrDefault(def.id(), 0L);
            if (now - last < rules.cooldownSeconds() * 1000L) {
                continue;
            }
            eligible.add(def);
            totalWeight += rules.weight();
        }
        // Variety: never let the same boss spawn a 3rd time in a row - drop it from this pick (unless
        // it's the only eligible boss, in which case spawning it again beats spawning nothing).
        if (consecutiveSpawns >= 2 && lastSpawnedId != null && eligible.size() > 1) {
            List<BossDefinition> filtered = new ArrayList<>();
            int filteredWeight = 0;
            for (BossDefinition d : eligible) {
                if (d.id().equals(lastSpawnedId)) {
                    continue;
                }
                filtered.add(d);
                filteredWeight += d.spawn().weight();
            }
            if (!filtered.isEmpty() && filteredWeight > 0) {
                eligible = filtered;
                totalWeight = filteredWeight;
            }
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
        double globalMin = plugin.config().minPlayerDistance();
        double minDist = Math.max(rules.minPlayerDistance(), globalMin);
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double dist = minDist + ThreadLocalRandom.current().nextDouble(300);
            int x = anchor.getLocation().getBlockX() + (int) (Math.cos(angle) * dist);
            int z = anchor.getLocation().getBlockZ() + (int) (Math.sin(angle) * dist);
            // Never spawn on or past the world-border.
            if (!insideBorder(world, x, z, borderMargin(def))) {
                continue;
            }
            // Only probe already-generated (explored) terrain - never force-generate far chunks
            // synchronously (that was the spawn-cycle lag spike).
            if (!world.isChunkGenerated(x >> 4, z >> 4)) {
                continue;
            }
            Integer y = findSafeY(world, x, z, rules.minY(), rules.maxY());
            if (y == null) {
                continue;
            }
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            // Land bosses never spawn in the ocean/rivers or underwater (only water bosses may).
            if (!def.spawn().nearWater() && isWaterySpot(candidate)) {
                continue;
            }
            // The spot must be at least the global minimum from EVERY player - reroll otherwise.
            if (conditionsMet(def, candidate) && farFromAllPlayers(candidate, globalMin)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Whether {@code (x,z)} is safely inside this world's world-border, keeping a {@code margin}-block
     * buffer from the edge so the boss - and any terrain footprint it corrupts - never lands on or past
     * the border. A spot beyond the border is unreachable for players, so bosses must never spawn there.
     * Worlds without a shrunk border keep the ~30M-block vanilla default, so this never rejects them.
     */
    private static boolean insideBorder(World world, int x, int z, int margin) {
        org.bukkit.WorldBorder border = world.getWorldBorder();
        return insideBorder(border.getCenter().getX(), border.getCenter().getZ(),
                border.getSize(), x, z, margin);
    }

    /** Pure border test (see {@link #insideBorder(World, int, int, int)}); split out for unit testing.
     *  {@code size} is the border's full side length (diameter); a spot must be within half of that,
     *  minus {@code margin}, of the centre on both axes. */
    static boolean insideBorder(double centerX, double centerZ, double size, int x, int z, int margin) {
        double half = size / 2.0 - margin;
        if (half <= 0) {
            return false; // border smaller than the safety margin: nowhere safe to spawn
        }
        return Math.abs(x - centerX) <= half && Math.abs(z - centerZ) <= half;
    }

    /** Border buffer for a boss: keep a terrain boss' whole footprint (plus slack) inside; otherwise a
     *  16-block buffer so the boss and its immediate adds don't land on the very edge. */
    private static int borderMargin(BossDefinition def) {
        return def.hasTerrain() ? def.terrain().radius() + 16 : 16;
    }

    /** True if the spot is submerged or in an ocean/river biome (bad for a non-water boss). */
    private boolean isWaterySpot(Location loc) {
        World w = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        if (w.getBlockAt(x, y, z).getType() == Material.WATER
                || w.getBlockAt(x, y + 1, z).getType() == Material.WATER) {
            return true;
        }
        String biome = w.getBlockAt(x, y, z).getBiome().getKey().value().toUpperCase(Locale.ROOT);
        return biome.contains("OCEAN") || biome.contains("RIVER");
    }

    /** True if {@code loc} is at least {@code minDist} blocks from every survival/adventure player. */
    private boolean farFromAllPlayers(Location loc, double minDist) {
        if (minDist <= 0 || loc.getWorld() == null) {
            return true;
        }
        double minSq = minDist * minDist;
        for (Player p : loc.getWorld().getPlayers()) {
            if ((p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)
                    && p.getLocation().distanceSquared(loc) < minSq) {
                return false;
            }
        }
        return true;
    }

    private Location findFrontierLocation(Player anchor, BossDefinition def) {
        World world = anchor.getWorld();
        PluginConfig cfg = plugin.config();
        SpawnRules rules = def.spawn();
        int radius = def.terrain().radius() + 8;
        for (int attempt = 0; attempt < cfg.frontierAttempts(); attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double span = cfg.frontierMaxDistance() - cfg.frontierMinDistance();
            double dist = cfg.frontierMinDistance()
                    + (span > 0 ? ThreadLocalRandom.current().nextDouble(span) : 0);
            int x = anchor.getLocation().getBlockX() + (int) (Math.cos(angle) * dist);
            int z = anchor.getLocation().getBlockZ() + (int) (Math.sin(angle) * dist);
            if (!insideBorder(world, x, z, borderMargin(def))) {
                continue; // never spawn on or past the world-border
            }
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

    /**
     * Frontier search that never force-generates chunks on the main thread: for each attempt it checks
     * the footprint is ungenerated, then generates the candidate chunk with {@code getChunkAtAsync} and
     * probes it on the main thread. Recurses (via the scheduler) to the next attempt on failure and
     * finally calls {@code callback} with a location or {@code null}.
     */
    private void findFrontierLocationAsync(Player anchor, BossDefinition def,
                                           java.util.function.Consumer<Location> callback) {
        World world = anchor.getWorld();
        int radius = def.terrain().radius() + 8;
        frontierAttempt(world, anchor.getLocation(), def, radius, 0, callback);
    }

    private void frontierAttempt(World world, Location anchorLoc, BossDefinition def, int radius,
                                 int attempt, java.util.function.Consumer<Location> callback) {
        PluginConfig cfg = plugin.config();
        if (attempt >= cfg.frontierAttempts() || world.getPlayers().isEmpty()) {
            callback.accept(null);
            return;
        }
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double span = cfg.frontierMaxDistance() - cfg.frontierMinDistance();
        double dist = cfg.frontierMinDistance() + (span > 0 ? ThreadLocalRandom.current().nextDouble(span) : 0);
        int x = anchorLoc.getBlockX() + (int) (Math.cos(angle) * dist);
        int z = anchorLoc.getBlockZ() + (int) (Math.sin(angle) * dist);
        if (!insideBorder(world, x, z, borderMargin(def))
                || !TerrainManager.footprintUngenerated(world, x, z, radius)) {
            frontierAttempt(world, anchorLoc, def, radius, attempt + 1, callback);
            return;
        }
        SpawnRules rules = def.spawn();
        world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, err) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Integer y = err != null ? null : findSafeY(world, x, z, rules.minY(), rules.maxY());
                    if (y != null) {
                        Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                        if (conditionsMet(def, candidate)) {
                            callback.accept(candidate);
                            return;
                        }
                    }
                    frontierAttempt(world, anchorLoc, def, radius, attempt + 1, callback);
                }));
    }

    /**
     * Find a standing Y at (x,z) with a solid floor and two passable blocks above, within bounds.
     * Starts at the SURFACE ({@code getHighestBlockYAt}) rather than the world top, so a normal spawn
     * resolves in a couple of block checks instead of a ~380-block top-down sweep per column.
     */
    private Integer findSafeY(World world, int x, int z, int minY, int maxY) {
        int top = Math.min(maxY, world.getMaxHeight() - 3);
        int bottom = Math.max(minY, world.getMinHeight() + 1);
        int start = Math.min(top, Math.max(bottom, world.getHighestBlockYAt(x, z)));
        for (int y = start; y >= bottom; y--) {
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
