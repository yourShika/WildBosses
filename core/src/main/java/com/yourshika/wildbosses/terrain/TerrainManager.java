package com.yourshika.wildbosses.terrain;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.boss.EncounterHook;
import com.yourshika.wildbosses.boss.TerrainFeature;
import com.yourshika.wildbosses.boss.TerrainSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.io.File;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies and restores per-boss terrain theming ("world corruption") while guaranteeing player
 * builds are never destroyed. See {@link ProtectionService} and {@link TerrainSnapshot} for the
 * safety layers; the spawner additionally restricts terrain bosses to ungenerated chunks
 * ({@link #footprintUngenerated}).
 */
public final class TerrainManager implements EncounterHook {

    private final WildBossesPlugin plugin;
    private final ProtectionService protection;
    private final File directory;
    private final Map<String, TerrainSnapshot> active = new HashMap<>();

    public TerrainManager(WildBossesPlugin plugin) {
        this.plugin = plugin;
        this.protection = new ProtectionService(plugin.getLogger());
        this.directory = new File(plugin.getDataFolder(), "terrain-snapshots");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create terrain-snapshots/ directory.");
        }
    }

    public ProtectionService protection() {
        return protection;
    }

    /** Restore any snapshots left on disk by a previous session (crash/restart mid-encounter). */
    public void restorePersisted() {
        File[] files = directory.listFiles((d, n) -> n.toLowerCase().endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }
        int total = 0;
        int deferred = 0;
        for (File file : files) {
            boolean remove = true;
            try {
                TerrainSnapshot snapshot = TerrainSnapshot.load(file);
                if (snapshot.worldAvailable()) {
                    total += snapshot.restore();
                } else {
                    // The target world isn't loaded yet (e.g. a Multiverse/custom world loads after
                    // us). Keep the snapshot so we can restore it on a later start instead of
                    // deleting the only record and leaving the terrain corrupted forever.
                    remove = false;
                    deferred++;
                }
            } catch (Exception ex) {
                // Unparseable snapshot (e.g. half-written during a crash) - the block data is
                // unrecoverable regardless, so drop the file rather than choke on it every start.
                plugin.getLogger().warning("Discarding unreadable terrain snapshot " + file.getName() + ": " + ex.getMessage());
            }
            if (remove && !file.delete()) {
                plugin.getLogger().warning("Could not delete terrain snapshot " + file.getName());
            }
        }
        plugin.getLogger().info("Restored " + total + " terrain block(s) from previous snapshot(s)"
                + (deferred > 0 ? " (" + deferred + " deferred - world not loaded yet)." : "."));
    }

    @Override
    public void onStart(ActiveBoss boss) {
        if (boss.def().hasTerrain()) {
            applyAt(boss.encounterId(), boss.location(), boss.def().terrain());
        }
    }

    @Override
    public void onEnd(ActiveBoss boss) {
        if (boss.def().hasTerrain()) {
            restoreEncounter(boss.encounterId(), boss.def().terrain().restoreOnEnd());
        }
    }

    /** Restore (optionally) and forget an encounter's terrain changes, deleting its snapshot file. */
    public void restoreEncounter(String encounterId, boolean doRestore) {
        TerrainSnapshot snapshot = active.remove(encounterId);
        if (snapshot != null && doRestore) {
            snapshot.restore();
        }
        File file = snapshotFile(encounterId);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete terrain snapshot " + file.getName());
        }
    }

    /** Apply a terrain effect around a location, snapshotting every change for guaranteed restore. */
    public void applyAt(String encounterId, Location center, TerrainSettings ts) {
        World world = center.getWorld();
        if (world == null || (ts.mappings().isEmpty() && ts.features().isEmpty())) {
            return;
        }
        TerrainSnapshot snapshot = new TerrainSnapshot(encounterId, world.getUID());
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int radius = ts.radius();
        int rSq = radius * radius;
        int max = ts.maxBlocks();
        boolean requireCoreProtect = ts.requireCoreProtect();

        // 1) Surface re-theming: the highest block of each column in the allowlist.
        for (int dx = -radius; dx <= radius && snapshot.size() < max; dx++) {
            for (int dz = -radius; dz <= radius && snapshot.size() < max; dz++) {
                if (dx * dx + dz * dz > rSq) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int y = world.getHighestBlockYAt(x, z);
                Block block = world.getBlockAt(x, y, z);
                Material to = ts.mappings().get(block.getType());
                if (to == null || !protection.canCorrupt(block, requireCoreProtect)) {
                    continue;
                }
                setBlock(snapshot, block, to);
            }
        }

        // 2) Scatter decorative structures (bee trees, farms, obsidian shards) that persist.
        Set<Material> ground = groundAllowlist(ts);
        for (TerrainFeature feature : ts.features()) {
            for (int i = 0; i < feature.count() && snapshot.size() < max; i++) {
                placeFeature(feature.type(), world, cx, cz, radius, ground, snapshot, requireCoreProtect, max);
            }
        }

        if (snapshot.size() > 0) {
            active.put(encounterId, snapshot);
            snapshot.save(snapshotFile(encounterId), plugin.getLogger());
        }
    }

    // ---- decorative features --------------------------------------------------------------

    /** Natural blocks a feature may root on, plus whatever the surface mappings turned them into. */
    private static final Set<Material> NATURAL_GROUND = EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL, Material.ROOTED_DIRT,
            Material.MOSS_BLOCK, Material.MUD, Material.MYCELIUM, Material.STONE, Material.SAND, Material.RED_SAND,
            Material.GRAVEL, Material.SANDSTONE, Material.SNOW_BLOCK, Material.NETHERRACK, Material.END_STONE,
            Material.TERRACOTTA);

    private Set<Material> groundAllowlist(TerrainSettings ts) {
        Set<Material> set = EnumSet.copyOf(NATURAL_GROUND);
        set.addAll(ts.mappings().keySet());
        set.addAll(ts.mappings().values());
        return set;
    }

    /** Pick a natural spot within the radius and build the named structure there (best-effort). */
    private void placeFeature(String type, World world, int cx, int cz, int radius,
                              Set<Material> ground, TerrainSnapshot snap, boolean requireCP, int max) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 8; attempt++) {
            double ang = rnd.nextDouble(Math.PI * 2);
            double r = Math.sqrt(rnd.nextDouble()) * radius;
            int x = cx + (int) Math.round(Math.cos(ang) * r);
            int z = cz + (int) Math.round(Math.sin(ang) * r);
            int gy = world.getHighestBlockYAt(x, z);
            Block groundBlock = world.getBlockAt(x, gy, z);
            if (!ground.contains(groundBlock.getType()) || !protection.canCorrupt(groundBlock, requireCP)) {
                continue;
            }
            switch (type) {
                case "bee_tree" -> beeTree(world, x, gy, z, snap, rnd);
                case "honey_patch" -> honeyPatch(world, x, gy, z, snap, rnd);
                case "pumpkin_patch" -> pumpkinPatch(world, x, gy, z, ground, snap, requireCP, rnd, max);
                case "crying_obsidian" -> cryingObsidian(world, x, gy, z, snap, rnd);
                default -> {
                    plugin.getLogger().warning("Unknown terrain feature type: " + type);
                    return;
                }
            }
            return;
        }
    }

    /** A small oak tree with a bee nest tucked into the canopy. */
    private void beeTree(World world, int x, int gy, int z, TerrainSnapshot snap, ThreadLocalRandom rnd) {
        int trunk = 3 + rnd.nextInt(2); // 3-4 tall
        if (!columnClear(world, x, gy + 1, z, trunk + 2)) {
            return;
        }
        for (int i = 1; i <= trunk; i++) {
            setBlock(snap, world.getBlockAt(x, gy + i, z), Material.OAK_LOG);
        }
        int topY = gy + trunk;
        // Two leaf layers around the top, plus a small cap.
        leafDisc(world, x, topY, z, 2, snap);
        leafDisc(world, x, topY + 1, z, 1, snap);
        setLeaves(snap, world.getBlockAt(x, topY + 2, z));
        // Nudge a bee nest into the canopy edge.
        Block nest = world.getBlockAt(x + (rnd.nextBoolean() ? 1 : -1), topY, z);
        setBlock(snap, nest, Material.BEE_NEST);
    }

    private void leafDisc(World world, int cx, int y, int cz, int r, TerrainSnapshot snap) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                Block b = world.getBlockAt(cx + dx, y, cz + dz);
                if (b.getType().isAir() || b.getType() == Material.OAK_LEAVES) {
                    setLeaves(snap, b);
                }
            }
        }
    }

    /** Sticky honey mounds. */
    private void honeyPatch(World world, int x, int gy, int z, TerrainSnapshot snap, ThreadLocalRandom rnd) {
        setBlock(snap, world.getBlockAt(x, gy + 1, z), Material.HONEY_BLOCK);
        int extra = rnd.nextInt(3);
        for (int i = 0; i < extra; i++) {
            int ox = rnd.nextInt(3) - 1;
            int oz = rnd.nextInt(3) - 1;
            Block b = world.getBlockAt(x + ox, gy + 1, z + oz);
            if (b.getType().isAir()) {
                setBlock(snap, b, rnd.nextBoolean() ? Material.HONEY_BLOCK : Material.HONEYCOMB_BLOCK);
            }
        }
    }

    /** A small, overgrown, abandoned crop plot with the odd pumpkin and a lonely scarecrow. */
    private void pumpkinPatch(World world, int x, int gy, int z, Set<Material> ground,
                              TerrainSnapshot snap, boolean requireCP, ThreadLocalRandom rnd, int max) {
        int w = 3 + rnd.nextInt(2);
        int d = 3 + rnd.nextInt(2);
        for (int ox = 0; ox < w; ox++) {
            for (int oz = 0; oz < d; oz++) {
                if (snap.size() >= max) {
                    return;
                }
                int px = x + ox;
                int pz = z + oz;
                int py = world.getHighestBlockYAt(px, pz);
                Block soil = world.getBlockAt(px, py, pz);
                if (!ground.contains(soil.getType()) || !protection.canCorrupt(soil, requireCP)) {
                    continue;
                }
                Block above = world.getBlockAt(px, py + 1, pz);
                if (!above.getType().isAir() && !above.isPassable()) {
                    continue; // don't punch through anything solid overhead
                }
                setBlock(snap, soil, Material.FARMLAND);
                double roll = rnd.nextDouble();
                if (roll < 0.45) {
                    setAged(snap, above, Material.WHEAT, rnd);
                } else if (roll < 0.55) {
                    setAged(snap, above, rnd.nextBoolean() ? Material.CARROTS : Material.POTATOES, rnd);
                } else if (roll < 0.62) {
                    setBlock(snap, above, rnd.nextBoolean() ? Material.PUMPKIN : Material.MELON);
                } else if (roll < 0.72) {
                    setBlock(snap, above, Material.DEAD_BUSH); // abandoned, gone to seed
                } else if (roll < 0.78) {
                    setBlock(snap, above, Material.SHORT_GRASS);
                }
                // else: left bare/trampled
            }
        }
        // A lonely scarecrow on one corner.
        int sy = world.getHighestBlockYAt(x, z);
        if (columnClear(world, x, sy + 1, z, 2)) {
            setBlock(snap, world.getBlockAt(x, sy + 1, z), Material.OAK_FENCE);
            setBlock(snap, world.getBlockAt(x, sy + 2, z), Material.CARVED_PUMPKIN);
        }
        // A stray cobweb or hay for the abandoned feel.
        if (rnd.nextBoolean()) {
            Block hay = world.getBlockAt(x + w, world.getHighestBlockYAt(x + w, z) + 1, z);
            if (hay.getType().isAir()) {
                setBlock(snap, hay, Material.HAY_BLOCK);
            }
        }
    }

    /** Enderman Queen accent: crying-obsidian shards over the obsidian floor, the odd eerie light. */
    private void cryingObsidian(World world, int x, int gy, int z, TerrainSnapshot snap, ThreadLocalRandom rnd) {
        // Recolour the surface block to crying obsidian.
        setBlock(snap, world.getBlockAt(x, gy, z), Material.CRYING_OBSIDIAN);
        double roll = rnd.nextDouble();
        if (roll < 0.35 && columnClear(world, x, gy + 1, z, 2)) {
            // A short shard.
            setBlock(snap, world.getBlockAt(x, gy + 1, z), Material.OBSIDIAN);
            setBlock(snap, world.getBlockAt(x, gy + 2, z), Material.CRYING_OBSIDIAN);
        } else if (roll < 0.45 && world.getBlockAt(x, gy + 1, z).getType().isAir()) {
            // A lonely light.
            setBlock(snap, world.getBlockAt(x, gy + 1, z), Material.END_ROD);
        }
    }

    // ---- block helpers --------------------------------------------------------------------

    /** True if the vertical column of {@code height} blocks from (x,y,z) upward is air/passable. */
    private boolean columnClear(World world, int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            Block b = world.getBlockAt(x, y + i, z);
            if (!b.getType().isAir() && !b.isPassable() && b.getType() != Material.OAK_LEAVES) {
                return false;
            }
        }
        return true;
    }

    private void setBlock(TerrainSnapshot snap, Block block, Material material) {
        String from = block.getBlockData().getAsString();
        block.setType(material, false);
        snap.record(block.getX(), block.getY(), block.getZ(), from, block.getBlockData().getAsString());
    }

    private void setLeaves(TerrainSnapshot snap, Block block) {
        String from = block.getBlockData().getAsString();
        block.setType(Material.OAK_LEAVES, false);
        if (block.getBlockData() instanceof Leaves leaves) {
            leaves.setPersistent(true); // never decay - the tree is meant to stay
            block.setBlockData(leaves, false);
        }
        snap.record(block.getX(), block.getY(), block.getZ(), from, block.getBlockData().getAsString());
    }

    private void setAged(TerrainSnapshot snap, Block block, Material crop, ThreadLocalRandom rnd) {
        String from = block.getBlockData().getAsString();
        block.setType(crop, false);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(rnd.nextInt(ageable.getMaximumAge() + 1)); // ragged, uneven growth
            block.setBlockData(ageable, false);
        }
        snap.record(block.getX(), block.getY(), block.getZ(), from, block.getBlockData().getAsString());
    }

    private File snapshotFile(String encounterId) {
        return new File(directory, encounterId + ".yml");
    }

    /**
     * Whether every chunk in the block-radius footprint around ({@code blockX}, {@code blockZ}) is
     * ungenerated (never visited) - the primary safeguard so terrain corruption only ever lands on
     * pristine frontier terrain with no possible player builds.
     */
    public static boolean footprintUngenerated(World world, int blockX, int blockZ, int radiusBlocks) {
        int minCx = (blockX - radiusBlocks) >> 4;
        int maxCx = (blockX + radiusBlocks) >> 4;
        int minCz = (blockZ - radiusBlocks) >> 4;
        int maxCz = (blockZ + radiusBlocks) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (world.isChunkGenerated(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }
}
