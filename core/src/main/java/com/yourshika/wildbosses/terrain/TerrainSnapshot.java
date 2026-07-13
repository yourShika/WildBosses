package com.yourshika.wildbosses.terrain;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Records every block a terrain effect changed, with both the original and the applied block data,
 * so it can be restored exactly. Persisted to disk so a mid-encounter server restart still restores.
 *
 * <p>Restoration only ever touches a block if it still matches the data we applied - if a player
 * changed it during the encounter, that block is left alone.</p>
 */
public final class TerrainSnapshot {

    /** One changed block: position + original data + the data we applied. */
    private record Change(int x, int y, int z, String from, String to) {
    }

    private final String encounterId;
    private final UUID worldUid;
    private final List<Change> changes = new ArrayList<>();

    public TerrainSnapshot(String encounterId, UUID worldUid) {
        this.encounterId = encounterId;
        this.worldUid = worldUid;
    }

    public String encounterId() {
        return encounterId;
    }

    public int size() {
        return changes.size();
    }

    public void record(int x, int y, int z, String fromData, String toData) {
        changes.add(new Change(x, y, z, fromData, toData));
    }

    /**
     * Restore every recorded block whose current data still matches what we applied.
     *
     * @return the number of blocks restored
     */
    public int restore() {
        World world = Bukkit.getWorld(worldUid);
        if (world == null) {
            return 0;
        }
        int restored = 0;
        for (Change c : changes) {
            Block block = world.getBlockAt(c.x(), c.y(), c.z());
            if (!block.getBlockData().getAsString().equals(c.to())) {
                continue; // changed by a player since; leave it
            }
            try {
                BlockData original = Bukkit.createBlockData(c.from());
                block.setBlockData(original, false);
                restored++;
            } catch (IllegalArgumentException ignored) {
                // unparseable original data (very unlikely) - skip
            }
        }
        return restored;
    }

    // ---- persistence ----------------------------------------------------------------------

    public void save(File file, Logger logger) {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("encounter", encounterId);
        yml.set("world", worldUid.toString());
        List<String> lines = new ArrayList<>(changes.size());
        for (Change c : changes) {
            lines.add(c.x() + ";" + c.y() + ";" + c.z() + ";" + c.from() + ";" + c.to());
        }
        yml.set("changes", lines);
        try {
            yml.save(file);
        } catch (IOException e) {
            logger.warning("Failed to save terrain snapshot " + file.getName() + ": " + e.getMessage());
        }
    }

    public static TerrainSnapshot load(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String encounter = yml.getString("encounter", file.getName());
        UUID world = UUID.fromString(yml.getString("world"));
        TerrainSnapshot snapshot = new TerrainSnapshot(encounter, world);
        for (String line : yml.getStringList("changes")) {
            String[] parts = line.split(";", 5);
            if (parts.length < 5) {
                continue;
            }
            snapshot.changes.add(new Change(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    parts[3],
                    parts[4]));
        }
        return snapshot;
    }
}
