package com.yourshika.wildbosses.boss;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads and holds all boss definitions from the {@code bosses/} folder. On first run the bundled
 * default bosses are written out; thereafter every {@code *.yml} in the folder is (re)loaded.
 */
public final class BossRegistry {

    /** Bundled default boss files copied to the data folder on first run. */
    private static final List<String> DEFAULTS = List.of(
            "goblin_army", "infected_army", "zombie_king", "skeleton_king",
            "creeper_king", "enderman_queen", "magical_unicorn", "warthoglin",
            "walak", "werewolf", "queen_bee", "leviathan", "medusa", "harvester");

    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, BossDefinition> bosses = new LinkedHashMap<>();

    public BossRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public int reload() {
        bosses.clear();
        File dir = new File(plugin.getDataFolder(), "bosses");
        restoreMissing();

        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return 0;
        }
        BossLoader loader = new BossLoader(logger);
        for (File file : files) {
            try {
                YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
                String id = yml.getString("id", stripExtension(file.getName())).toLowerCase(Locale.ROOT);
                bosses.put(id, loader.load(id, yml));
            } catch (Exception ex) {
                logger.severe("Failed to load boss file " + file.getName() + ": " + ex.getMessage());
            }
        }
        logger.info("Loaded " + bosses.size() + " boss definition(s).");
        return bosses.size();
    }

    /** Write out any bundled default boss file that is missing from the data folder (never overwrites). */
    public void restoreMissing() {
        File dir = new File(plugin.getDataFolder(), "bosses");
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warning("Could not create bosses/ directory.");
        }
        for (String name : DEFAULTS) {
            if (!new File(dir, name + ".yml").exists()) {
                try {
                    plugin.saveResource("bosses/" + name + ".yml", false);
                } catch (IllegalArgumentException ex) {
                    logger.warning("Bundled boss resource missing: bosses/" + name + ".yml");
                }
            }
        }
    }

    /** (Re)write every bundled default boss file, overwriting any local edits. Returns the count. */
    public int restoreDefaults() {
        File dir = new File(plugin.getDataFolder(), "bosses");
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warning("Could not create bosses/ directory.");
        }
        int n = 0;
        for (String name : DEFAULTS) {
            try {
                plugin.saveResource("bosses/" + name + ".yml", true);
                n++;
            } catch (IllegalArgumentException ex) {
                logger.warning("Bundled boss resource missing: bosses/" + name + ".yml");
            }
        }
        return n;
    }

    public BossDefinition get(String id) {
        return id == null ? null : bosses.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean contains(String id) {
        return get(id) != null;
    }

    public Collection<BossDefinition> all() {
        return bosses.values();
    }

    public Set<String> ids() {
        return bosses.keySet();
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
