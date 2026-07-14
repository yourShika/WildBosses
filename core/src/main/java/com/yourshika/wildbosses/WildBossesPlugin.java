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
import com.yourshika.wildbosses.model.ModelManager;
import com.yourshika.wildbosses.reward.RewardManager;
import com.yourshika.wildbosses.skill.DefaultSkillEngine;
import com.yourshika.wildbosses.spawn.SpawnScheduler;
import com.yourshika.wildbosses.terrain.TerrainManager;
import com.yourshika.wildbosses.util.Keys;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the WildBosses plugin. Loads configuration, builds the managers, selects the model
 * adapter (BetterModel when present, vanilla fallback otherwise), registers listeners and commands,
 * and starts the runtime tick loop. Everything is torn down cleanly on disable.
 */
public final class WildBossesPlugin extends JavaPlugin {

    private final PluginConfig pluginConfig = new PluginConfig();
    private Messages messages;
    private BossRegistry registry;
    private ModelManager modelManager;
    private Broadcaster broadcaster;
    private BossManager bossManager;
    private TerrainManager terrainManager;
    private SpawnScheduler spawnScheduler;
    private ArmyManager armyManager;
    private com.yourshika.wildbosses.integration.OraxenAssets oraxenAssets;

    @Override
    public void onEnable() {
        getLogger().info("WildBosses is starting up...");
        Keys.init(this);
        saveDefaultConfig();

        messages = new Messages(this);
        registry = new BossRegistry(this);
        modelManager = new ModelManager(this);
        broadcaster = new Broadcaster(this);
        bossManager = new BossManager(this, registry, modelManager, broadcaster);
        terrainManager = new TerrainManager(this);

        modelManager.select();
        oraxenAssets = new com.yourshika.wildbosses.integration.OraxenAssets(this);
        bossManager.setSkillEngine(new DefaultSkillEngine(this));
        bossManager.setDeathListener(new RewardManager(this));
        bossManager.setEncounterHook(terrainManager);
        terrainManager.restorePersisted();
        reloadAll();

        if (pluginConfig.oraxenAutoDeploy() && oraxenAssets.oraxenPresent()) {
            oraxenAssets.deploy();
        }

        armyManager = new ArmyManager(this);

        getServer().getPluginManager().registerEvents(new BossListener(bossManager), this);
        getServer().getPluginManager().registerEvents(new ArmyListener(armyManager), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        bossManager.start();

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
        if (spawnScheduler != null) {
            spawnScheduler.stop();
        }
        if (armyManager != null) {
            armyManager.shutdown();
        }
        if (bossManager != null) {
            bossManager.shutdown();
        }
        getLogger().info("WildBosses disabled.");
    }

    /** Reload config.yml, language and all boss definitions. Returns the number of bosses loaded. */
    public int reloadAll() {
        reloadConfig();
        pluginConfig.load(getConfig(), getLogger());
        messages.reload();
        int count = registry.reload();
        if (spawnScheduler != null) {
            spawnScheduler.start(); // re-apply the (possibly changed) interval
        }
        return count;
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

    public ModelManager modelManager() {
        return modelManager;
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

    public com.yourshika.wildbosses.integration.OraxenAssets oraxenAssets() {
        return oraxenAssets;
    }
}
