package com.yourshika.wildbosses;

import com.yourshika.wildbosses.army.ArmyManager;
import com.yourshika.wildbosses.boss.BossManager;
import com.yourshika.wildbosses.boss.BossRegistry;
import com.yourshika.wildbosses.boss.Broadcaster;
import com.yourshika.wildbosses.command.WildBossesCommand;
import com.yourshika.wildbosses.config.Messages;
import com.yourshika.wildbosses.config.PluginConfig;
import com.yourshika.wildbosses.gui.GuiListener;
import com.yourshika.wildbosses.listener.ArmyListener;
import com.yourshika.wildbosses.listener.BossListener;
import com.yourshika.wildbosses.reward.RewardManager;
import com.yourshika.wildbosses.skill.DefaultSkillEngine;
import com.yourshika.wildbosses.spawn.SpawnScheduler;
import com.yourshika.wildbosses.terrain.TerrainManager;
import com.yourshika.wildbosses.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the WildBosses plugin. Loads configuration, builds the managers, selects the model
 * registers listeners and commands,
 * and starts the runtime tick loop. Everything is torn down cleanly on disable.
 */
public final class WildBossesPlugin extends JavaPlugin {

    private final PluginConfig pluginConfig = new PluginConfig();
    private Messages messages;
    private BossRegistry registry;
    private Broadcaster broadcaster;
    private BossManager bossManager;
    private TerrainManager terrainManager;
    private SpawnScheduler spawnScheduler;
    private ArmyManager armyManager;
    private com.yourshika.wildbosses.event.LunarEventManager lunarEvents;

    @Override
    public void onEnable() {
        getLogger().info("WildBosses is starting up...");
        Keys.init(this);
        saveDefaultConfig();

        messages = new Messages(this);
        registry = new BossRegistry(this);
        broadcaster = new Broadcaster(this);
        bossManager = new BossManager(this, registry, broadcaster);
        terrainManager = new TerrainManager(this);

        bossManager.setSkillEngine(new DefaultSkillEngine(this));
        bossManager.setDeathListener(new RewardManager(this));
        bossManager.setEncounterHook(terrainManager);
        terrainManager.restorePersisted();
        reloadAll();

        armyManager = new ArmyManager(this);

        sweepLeftovers();

        getServer().getPluginManager().registerEvents(new BossListener(bossManager), this);
        getServer().getPluginManager().registerEvents(new ArmyListener(armyManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(
                new com.yourshika.wildbosses.listener.CleanupListener(this), this);
        bossManager.start();
        // Restore bosses that were alive before a restart/crash. Slightly delayed so all worlds
        // (incl. Multiverse-loaded ones) are available first.
        getServer().getScheduler().runTaskLater(this, bossManager::restoreState, 40L);

        lunarEvents = new com.yourshika.wildbosses.event.LunarEventManager(this);
        getServer().getPluginManager().registerEvents(lunarEvents, this);
        lunarEvents.start();

        spawnScheduler = new SpawnScheduler(this);
        spawnScheduler.setArmyStarter(armyManager);
        spawnScheduler.start();

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new com.yourshika.wildbosses.integration.WildBossesExpansion(this).register();
                getLogger().info("Registered PlaceholderAPI expansion 'wildbosses'.");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }

        PluginCommand command = getCommand("wildbosses");
        if (command != null) {
            WildBossesCommand handler = new WildBossesCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().severe("Command 'wildbosses' missing from plugin.yml - commands disabled.");
        }

        getLogger().info("WildBosses enabled.");
    }

    @Override
    public void onDisable() {
        if (lunarEvents != null) {
            lunarEvents.stop();
        }
        if (spawnScheduler != null) {
            spawnScheduler.stop();
        }
        if (armyManager != null) {
            armyManager.shutdown();
        }
        if (bossManager != null) {
            bossManager.saveState();   // persist live bosses before we tear them down
            bossManager.shutdown();
        }
        getLogger().info("WildBosses disabled.");
    }

    /** Remove any WildBosses entities left in loaded worlds by a previous session (no active event). */
    private void sweepLeftovers() {
        int removed = 0;
        for (org.bukkit.World world : getServer().getWorlds()) {
            for (org.bukkit.entity.Entity e : world.getEntities()) {
                if (Keys.isWildBossesEntity(e)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            getLogger().info("Removed " + removed + " leftover WildBosses entity(ies) from a previous session.");
        }
    }

    /** Reload config.yml, language and all boss definitions. Returns the number of bosses loaded. */
    public int reloadAll() {
        reloadConfig();
        updateConfig();          // merge in any new options added by a plugin update (keeps your values)
        pluginConfig.load(getConfig(), getLogger());
        messages.reload();
        registry.setDisabled(pluginConfig.disabledBosses());
        int count = registry.reload();
        if (spawnScheduler != null) {
            spawnScheduler.start(); // re-apply the (possibly changed) interval
        }
        return count;
    }

    /**
     * Migrate {@code config.yml} across plugin updates: any option present in the bundled default but
     * missing from the user's file is added (with its default value and comment), and the internal
     * {@code config-version} is bumped. Existing values are never touched. No-op once up to date.
     */
    private void updateConfig() {
        java.io.InputStream in = getResource("config.yml");
        if (in == null) {
            return;
        }
        org.bukkit.configuration.file.FileConfiguration def = org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        org.bukkit.configuration.file.FileConfiguration cur = getConfig();
        int defVersion = def.getInt("config-version", 1);
        int curVersion = cur.getInt("config-version", 0);
        if (curVersion >= defVersion) {
            return;
        }
        int added = 0;
        for (String path : def.getKeys(true)) {
            if (def.isConfigurationSection(path)) {
                continue; // only copy leaf values/lists; sections are created implicitly
            }
            if (!cur.contains(path, true)) { // ignoreDefault=true: only the user's own file counts
                cur.set(path, def.get(path));
                try {
                    cur.setComments(path, def.getComments(path));
                    cur.setInlineComments(path, def.getInlineComments(path));
                } catch (Throwable ignored) {
                    // comment APIs unavailable - the value is still merged
                }
                added++;
            }
        }
        cur.set("config-version", defVersion);
        try {
            cur.options().setHeader(def.options().getHeader());
        } catch (Throwable ignored) {
            // header API differences - non-fatal
        }
        saveConfig();
        getLogger().info("Updated config.yml from v" + curVersion + " to v" + defVersion
                + " (" + added + " new option(s) added; your settings were kept).");
    }

    /** The plugin's own jar file (used by the self-updater to name the replacement jar). */
    public java.io.File pluginFile() {
        return getFile();
    }

    public PluginConfig config() {
        return pluginConfig;
    }

    public Messages messages() {
        return messages;
    }

    public BossRegistry registry() {
        return registry;
    }

    public BossManager bossManager() {
        return bossManager;
    }

    public com.yourshika.wildbosses.event.LunarEventManager lunarEvents() {
        return lunarEvents;
    }

    public Broadcaster broadcaster() {
        return broadcaster;
    }

    public TerrainManager terrainManager() {
        return terrainManager;
    }

    public SpawnScheduler spawnScheduler() {
        return spawnScheduler;
    }

    public ArmyManager armyManager() {
        return armyManager;
    }
}
