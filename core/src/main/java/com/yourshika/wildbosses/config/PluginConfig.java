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
    private int spawnIntervalMinSeconds = 600;
    private int spawnIntervalMaxSeconds = 1800;
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
    private String broadcastBossFled = "";
    private String broadcastBossDrop = "";

    private boolean dropBroadcastEnabled = true;
    private double dropBroadcastThreshold = 0.5;
    private String deathSound = "ui.toast.challenge_complete";

    private boolean bossLifetimeEnabled = true;
    private int bossLifetimeMinMinutes = 30;
    private int bossLifetimeMaxMinutes = 60;

    private boolean scalingEnabled = true;
    private double scalingRadius = 48;
    private double scalingHealthPerPlayer = 0.25;
    private double scalingMaxMultiplier = 4.0;

    private boolean participationLoot = true;
    private String discordWebhook = "";
    private String updateRepo = "yourShika/WildBosses";

    public void load(FileConfiguration c, Logger logger) {
        randomSpawns = c.getBoolean("settings.random-spawns", true);
        spawnIntervalSeconds = Math.max(5, c.getInt("settings.spawn-interval-seconds", 600));
        // Random interval range (minutes). Falls back to the legacy single value if not configured,
        // so each spawn cycle waits a random time in [min, max] instead of a fixed cadence.
        int minM = c.getInt("settings.spawn-interval.min-minutes", -1);
        int maxM = c.getInt("settings.spawn-interval.max-minutes", -1);
        spawnIntervalMinSeconds = minM > 0 ? Math.max(5, minM * 60) : spawnIntervalSeconds;
        spawnIntervalMaxSeconds = maxM > 0 ? Math.max(spawnIntervalMinSeconds, maxM * 60)
                : Math.max(spawnIntervalMinSeconds, spawnIntervalSeconds);
        spawnAttemptsPerCycle = Math.max(1, c.getInt("settings.spawn-attempts-per-cycle", 1));
        maxActiveBosses = Math.max(1, c.getInt("settings.max-active-bosses", 5));
        minDistanceBetweenBosses = Math.max(0, c.getDouble("settings.min-distance-between-bosses", 200));

        bossLifetimeEnabled = c.getBoolean("settings.boss-lifetime.enabled", true);
        bossLifetimeMinMinutes = Math.max(1, c.getInt("settings.boss-lifetime.min-minutes", 30));
        bossLifetimeMaxMinutes = Math.max(bossLifetimeMinMinutes, c.getInt("settings.boss-lifetime.max-minutes", 60));

        scalingEnabled = c.getBoolean("settings.scaling.enabled", true);
        scalingRadius = Math.max(8, c.getDouble("settings.scaling.radius", 48));
        scalingHealthPerPlayer = Math.max(0, c.getDouble("settings.scaling.health-per-player", 0.25));
        scalingMaxMultiplier = Math.max(1, c.getDouble("settings.scaling.max-multiplier", 4.0));

        participationLoot = c.getBoolean("rewards.participation-loot", true);
        discordWebhook = c.getString("integrations.discord-webhook", "");
        updateRepo = c.getString("integrations.update-repo", "yourShika/WildBosses");

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
        broadcastBossFled = c.getString("broadcast.boss-fled", "");
        broadcastBossDrop = c.getString("broadcast.boss-drop", "");

        dropBroadcastEnabled = c.getBoolean("broadcast.drops.enabled", true);
        dropBroadcastThreshold = Math.max(0.0, Math.min(1.0, c.getDouble("broadcast.drops.announce-threshold", 0.5)));
        deathSound = c.getString("broadcast.death-sound", "ui.toast.challenge_complete");

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

    public int spawnIntervalMinSeconds() {
        return spawnIntervalMinSeconds;
    }

    public int spawnIntervalMaxSeconds() {
        return spawnIntervalMaxSeconds;
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

    public String broadcastBossFled() {
        return broadcastBossFled;
    }

    public String broadcastBossDrop() {
        return broadcastBossDrop;
    }

    public boolean dropBroadcastEnabled() {
        return dropBroadcastEnabled;
    }

    /** Drops whose chance is at or below this value are auto-announced (0..1). */
    public double dropBroadcastThreshold() {
        return dropBroadcastThreshold;
    }

    /** Sound key played to every online player when a boss is slain (blank = none). */
    public String deathSound() {
        return deathSound;
    }

    public boolean bossLifetimeEnabled() {
        return bossLifetimeEnabled;
    }

    public int bossLifetimeMinMinutes() {
        return bossLifetimeMinMinutes;
    }

    public int bossLifetimeMaxMinutes() {
        return bossLifetimeMaxMinutes;
    }

    public boolean scalingEnabled() {
        return scalingEnabled;
    }

    public double scalingRadius() {
        return scalingRadius;
    }

    public double scalingHealthPerPlayer() {
        return scalingHealthPerPlayer;
    }

    public double scalingMaxMultiplier() {
        return scalingMaxMultiplier;
    }

    public boolean participationLoot() {
        return participationLoot;
    }

    public String discordWebhook() {
        return discordWebhook;
    }

    public String updateRepo() {
        return updateRepo;
    }

}
