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

    /**
     * Fallback list of bundled boss files, used only if the jar can't be scanned (e.g. running from an
     * exploded classes dir in dev). Normally {@link #bundledBossIds()} discovers them from the jar so
     * newly-added bosses are always deployed - no need to keep this list in sync.
     */
    private static final List<String> DEFAULTS = List.of(
            "goblin_army", "infected_army", "zombie_king", "skeleton_king",
            "creeper_king", "enderman_queen", "magical_unicorn", "warthoglin",
            "walak", "werewolf", "queen_bee", "leviathan", "medusa", "harvester",
            "golakar", "spider_swarm", "drowned_tide", "undead_legion");

    private final Plugin plugin;
    private final Logger logger;
    private final Map<String, BossDefinition> bosses = new LinkedHashMap<>();
    private Set<String> disabled = Set.of();

    /** Ids the admin switched off in config: never auto-restored and never loaded. */
    public void setDisabled(Set<String> disabled) {
        this.disabled = disabled == null ? Set.of() : disabled;
    }

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
                if (disabled.contains(id)) {
                    continue; // admin disabled this boss - skip loading it
                }
                bosses.put(id, loader.load(id, yml));
            } catch (Exception ex) {
                logger.severe("Failed to load boss file " + file.getName() + ": " + ex.getMessage());
            }
        }
        logger.info("Loaded " + bosses.size() + " boss definition(s).");
        return bosses.size();
    }

    /**
     * The ids of every boss YAML bundled in the jar, discovered at runtime so a plugin update always
     * deploys newly-added bosses. Falls back to {@link #DEFAULTS} if the jar can't be read.
     */
    private List<String> bundledBossIds() {
        try {
            java.net.URL loc = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            File jar = new File(loc.toURI());
            if (jar.isFile()) {
                List<String> out = new java.util.ArrayList<>();
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar)) {
                    var entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        String n = entries.nextElement().getName();
                        // Only direct children of bosses/ (skip sub-folders), ending in .yml.
                        if (n.startsWith("bosses/") && n.endsWith(".yml") && n.indexOf('/', 7) < 0) {
                            out.add(n.substring("bosses/".length(), n.length() - ".yml".length()));
                        }
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
        } catch (Exception ex) {
            logger.warning("Could not scan the jar for bundled bosses (" + ex.getMessage()
                    + "); using the built-in list.");
        }
        return DEFAULTS;
    }

    /**
     * Write out any missing bundled boss file, AND update the ones the admin hasn't locally edited to
     * the current bundled version (so a plugin update delivers new content like head textures). A
     * per-file content hash is stored: if the on-disk file no longer matches what we last wrote, it's
     * treated as edited and left alone. Disable with {@code settings.auto-update-default-bosses: false}.
     */
    public void restoreMissing() {
        File dir = new File(plugin.getDataFolder(), "bosses");
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warning("Could not create bosses/ directory.");
        }
        boolean autoUpdate = plugin.getConfig().getBoolean("settings.auto-update-default-bosses", true);
        File store = new File(plugin.getDataFolder(), ".default-boss-hashes.properties");
        java.util.Properties hashes = new java.util.Properties();
        if (store.exists()) {
            try (var in = new java.io.FileInputStream(store)) {
                hashes.load(in);
            } catch (java.io.IOException ignored) {
                // start fresh
            }
        }
        boolean changed = false;
        int updated = 0;
        for (String name : bundledBossIds()) {
            if (disabled.contains(name)) {
                continue; // don't re-create/update a boss the admin turned off
            }
            byte[] bundled = resourceBytes("bosses/" + name + ".yml");
            if (bundled == null) {
                logger.warning("Bundled boss resource missing: bosses/" + name + ".yml");
                continue;
            }
            String bundledHash = sha256(bundled);
            File f = new File(dir, name + ".yml");
            if (!f.exists()) {
                writeBytes(f, bundled);
                hashes.setProperty(name, bundledHash);
                changed = true;
                continue;
            }
            if (!autoUpdate) {
                continue;
            }
            String onDiskHash;
            try {
                onDiskHash = sha256(java.nio.file.Files.readAllBytes(f.toPath()));
            } catch (java.io.IOException ex) {
                continue;
            }
            if (onDiskHash.equals(bundledHash)) {
                if (!bundledHash.equals(hashes.getProperty(name))) {
                    hashes.setProperty(name, bundledHash);
                    changed = true;
                }
                continue; // already current
            }
            String recorded = hashes.getProperty(name);
            if (recorded == null || recorded.equals(onDiskHash)) {
                // unedited since we last wrote it (or first sync) -> refresh to the bundled version
                writeBytes(f, bundled);
                hashes.setProperty(name, bundledHash);
                changed = true;
                updated++;
            }
            // else: locally edited -> left untouched
        }
        if (updated > 0) {
            logger.info("Updated " + updated + " default boss file(s) to the new bundled version "
                    + "(locally-edited files were kept).");
        }
        if (changed) {
            try (var out = new java.io.FileOutputStream(store)) {
                hashes.store(out, "WildBosses default-boss content hashes - do not edit");
            } catch (java.io.IOException ignored) {
                // non-fatal
            }
        }
    }

    private byte[] resourceBytes(String path) {
        try (var in = plugin.getResource(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    private void writeBytes(File f, byte[] data) {
        try {
            java.nio.file.Files.write(f.toPath(), data);
        } catch (java.io.IOException ex) {
            logger.warning("Could not write " + f.getName() + ": " + ex.getMessage());
        }
    }

    private static String sha256(byte[] data) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    /** (Re)write every bundled default boss file, overwriting any local edits. Returns the count. */
    public int restoreDefaults() {
        File dir = new File(plugin.getDataFolder(), "bosses");
        if (!dir.exists() && !dir.mkdirs()) {
            logger.warning("Could not create bosses/ directory.");
        }
        int n = 0;
        for (String name : bundledBossIds()) {
            if (disabled.contains(name)) {
                continue; // leave disabled bosses off even on a factory reset
            }
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
