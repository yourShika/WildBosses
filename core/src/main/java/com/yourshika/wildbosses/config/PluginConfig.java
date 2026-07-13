package com.yourshika.wildbosses.config;

import com.yourshika.wildbosses.difficulty.Difficulty;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Typed view over {@code config.yml}. Reloaded via {@link #load(FileConfiguration, Logger)}.
 */
public final class PluginConfig {

    private boolean randomSpawns = true;
    private int spawnIntervalSeconds = 600;
    private int spawnAttemptsPerCycle = 1;
    private int maxActiveBosses = 5;
    private double minDistanceBetweenBosses = 200;

    private int frontierMinDistance = 200;
    private int frontierMaxDistance = 3000;
    private int frontierAttempts = 24;

    private final Map<World.Environment, Boolean> worldEnabled = new EnumMap<>(World.Environment.class);

    private boolean broadcastEnabled = true;
    private String broadcastBossSpawn = "";
    private String broadcastArmySpawn = "";
    private String broadcastBossDeath = "";

    public void load(FileConfiguration c, Logger logger) {
        randomSpawns = c.getBoolean("settings.random-spawns", true);
        spawnIntervalSeconds = Math.max(5, c.getInt("settings.spawn-interval-seconds", 600));
        spawnAttemptsPerCycle = Math.max(1, c.getInt("settings.spawn-attempts-per-cycle", 1));
        maxActiveBosses = Math.max(1, c.getInt("settings.max-active-bosses", 5));
        minDistanceBetweenBosses = Math.max(0, c.getDouble("settings.min-distance-between-bosses", 200));

        frontierMinDistance = Math.max(0, c.getInt("settings.frontier-search.min-distance", 200));
        frontierMaxDistance = Math.max(frontierMinDistance + 16, c.getInt("settings.frontier-search.max-distance", 3000));
        frontierAttempts = Math.max(1, c.getInt("settings.frontier-search.attempts", 24));

        worldEnabled.clear();
        worldEnabled.put(World.Environment.NORMAL, c.getBoolean("worlds.OVERWORLD", true));
        worldEnabled.put(World.Environment.NETHER, c.getBoolean("worlds.NETHER", true));
        worldEnabled.put(World.Environment.THE_END, c.getBoolean("worlds.THE_END", true));

        broadcastEnabled = c.getBoolean("broadcast.enabled", true);
        broadcastBossSpawn = c.getString("broadcast.boss-spawn", "");
        broadcastArmySpawn = c.getString("broadcast.army-spawn", "");
        broadcastBossDeath = c.getString("broadcast.boss-death", "");

        applyDifficultyOverrides(c.getConfigurationSection("difficulties"), logger);
    }

    private void applyDifficultyOverrides(ConfigurationSection section, Logger logger) {
        for (Difficulty d : Difficulty.values()) {
            d.resetToDefaults();
        }
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Difficulty d = Difficulty.fromString(key, null);
            if (d == null) {
                logger.warning("Unknown difficulty in config: " + key);
                continue;
            }
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            BossBar.Color bar = parseBarColor(s.getString("bar"), logger, key);
            d.applyOverrides(s.getString("label"), s.getString("from"), s.getString("to"), bar);
        }
    }

    private static BossBar.Color parseBarColor(String raw, Logger logger, String context) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BossBar.Color.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid boss bar colour '" + raw + "' for difficulty " + context);
            return null;
        }
    }

    public boolean randomSpawns() {
        return randomSpawns;
    }

    public int spawnIntervalSeconds() {
        return spawnIntervalSeconds;
    }

    public int spawnAttemptsPerCycle() {
        return spawnAttemptsPerCycle;
    }

    public int maxActiveBosses() {
        return maxActiveBosses;
    }

    public double minDistanceBetweenBosses() {
        return minDistanceBetweenBosses;
    }

    public int frontierMinDistance() {
        return frontierMinDistance;
    }

    public int frontierMaxDistance() {
        return frontierMaxDistance;
    }

    public int frontierAttempts() {
        return frontierAttempts;
    }

    public boolean isWorldEnabled(World.Environment env) {
        return worldEnabled.getOrDefault(env, false);
    }

    public boolean broadcastEnabled() {
        return broadcastEnabled;
    }

    public String broadcastBossSpawn() {
        return broadcastBossSpawn;
    }

    public String broadcastArmySpawn() {
        return broadcastArmySpawn;
    }

    public String broadcastBossDeath() {
        return broadcastBossDeath;
    }
}
