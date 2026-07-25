package com.yourshika.wildbosses.stats;

import com.yourshika.wildbosses.WildBossesPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/**
 * Per-player boss kill counts, persisted to {@code player-stats.yml}. Powers the bestiary's
 * "defeated N times" line and the optional discovery lock. Keyed {@code <player-uuid>.<boss-id>}
 * (UUIDs and boss ids never contain a '.', so the YAML path nesting is safe).
 */
public final class PlayerStats {

    private final WildBossesPlugin plugin;
    private final File file;
    private final YamlConfiguration yml;

    public PlayerStats(WildBossesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-stats.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public int kills(UUID player, String bossId) {
        if (player == null || bossId == null) {
            return 0;
        }
        return yml.getInt(player + "." + bossId.toLowerCase(Locale.ROOT), 0);
    }

    /** Increment {@code player}'s defeat count for {@code bossId} (call {@link #save()} after a batch). */
    public void recordKill(UUID player, String bossId) {
        if (player == null || bossId == null) {
            return;
        }
        String path = player + "." + bossId.toLowerCase(Locale.ROOT);
        yml.set(path, yml.getInt(path, 0) + 1);
    }

    /** Persist the stats file (small; a boss death only happens every few minutes). */
    public void save() {
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save player-stats.yml: " + ex.getMessage());
        }
    }
}
