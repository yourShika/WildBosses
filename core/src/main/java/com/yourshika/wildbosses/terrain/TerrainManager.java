package com.yourshika.wildbosses.terrain;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.boss.EncounterHook;
import com.yourshika.wildbosses.boss.TerrainSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

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
        for (File file : files) {
            try {
                TerrainSnapshot snapshot = TerrainSnapshot.load(file);
                total += snapshot.restore();
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to restore terrain snapshot " + file.getName() + ": " + ex.getMessage());
            }
            if (!file.delete()) {
                plugin.getLogger().warning("Could not delete terrain snapshot " + file.getName());
            }
        }
        plugin.getLogger().info("Restored " + total + " terrain block(s) from " + files.length + " previous snapshot(s).");
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
        if (world == null || ts.mappings().isEmpty()) {
            return;
        }
        TerrainSnapshot snapshot = new TerrainSnapshot(encounterId, world.getUID());
        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int radius = ts.radius();
        int rSq = radius * radius;
        int max = ts.maxBlocks();
        boolean requireCoreProtect = ts.requireCoreProtect();
        int changed = 0;

        for (int dx = -radius; dx <= radius && changed < max; dx++) {
            for (int dz = -radius; dz <= radius && changed < max; dz++) {
                if (dx * dx + dz * dz > rSq) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int y = world.getHighestBlockYAt(x, z);
                Block block = world.getBlockAt(x, y, z);
                Material to = ts.mappings().get(block.getType());
                if (to == null) {
                    continue; // not in the allowlist
                }
                if (!protection.canCorrupt(block, requireCoreProtect)) {
                    continue;
                }
                String from = block.getBlockData().getAsString();
                block.setType(to, false);
                String applied = block.getBlockData().getAsString();
                snapshot.record(x, y, z, from, applied);
                changed++;
            }
        }

        if (snapshot.size() > 0) {
            active.put(encounterId, snapshot);
            snapshot.save(snapshotFile(encounterId), plugin.getLogger());
        }
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
