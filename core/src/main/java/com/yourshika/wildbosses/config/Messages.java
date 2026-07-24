package com.yourshika.wildbosses.config;

import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads and serves player-facing messages from {@code lang/<language>.yml} (selected by
 * {@code settings.language}), always falling back to English for any missing key. Also holds the
 * {@code terms:} translation map used to localise boss names, titles and item names.
 */
public final class Messages {

    private final Plugin plugin;
    private FileConfiguration lang;
    private String prefix = "";
    private final Map<String, String> terms = new HashMap<>();

    public Messages(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        // Ship the bundled language files on first run.
        for (String code : new String[]{"en", "de", "pl"}) {
            File f = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
            if (!f.exists()) {
                try {
                    plugin.saveResource("lang/" + code + ".yml", false);
                } catch (IllegalArgumentException ignored) {
                    // resource not bundled
                }
            }
        }
        String code = plugin.getConfig().getString("settings.language", "en").trim().toLowerCase(Locale.ROOT);

        // Loading the language files must never be able to disable the plugin: any parse error degrades
        // to English (or an empty config) with a warning instead of throwing out of onEnable.
        try {
            // Load the BUNDLED language files straight from the jar so newly-shipped messages and terms
            // are always current, even if the on-disk lang file was written by an older plugin version.
            // The on-disk file then overrides them, so an admin's own edits still win.
            FileConfiguration enBundled = fromJar("lang/en.yml");
            FileConfiguration chosenBundled = code.equals("en") ? enBundled : fromJar("lang/" + code + ".yml");
            FileConfiguration base = chosenBundled != null ? chosenBundled : enBundled;
            if (base != null && base != enBundled && enBundled != null) {
                base.setDefaults(enBundled); // chosen bundled -> English fallback
            }
            FileConfiguration disk = loadYaml(new File(plugin.getDataFolder(), "lang/" + code + ".yml"));
            if (base != null) {
                disk.setDefaults(base); // on-disk value -> bundled (chosen lang) -> English
            }
            lang = disk;
            prefix = lang.getString("prefix", "");

            // Terms: bundled (jar, always up to date) first, then the on-disk file overrides.
            terms.clear();
            if (base != null) {
                putTerms(base.getConfigurationSection("terms"));
            }
            putTerms(disk.getConfigurationSection("terms"));
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not load language '" + code + "'; falling back to English. "
                    + "Cause: " + ex);
            FileConfiguration en = fromJar("lang/en.yml");
            lang = en != null ? en : newYaml();
            prefix = lang.getString("prefix", "");
            terms.clear();
        }
    }

    /**
     * A YamlConfiguration whose path separator is a character that never appears in a key. Our
     * {@code terms:} keys are whole English sentences (dialogue, lore, tooltips) and many end in '.';
     * with the default '.' separator Bukkit splits them into a path and a trailing empty segment,
     * which throws {@code IllegalArgumentException: Cannot set to an empty path} while parsing. Using a
     * NUL separator makes every key literal. No key in these files uses dotted nesting, so nothing else
     * is affected.
     */
    private static YamlConfiguration newYaml() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.options().pathSeparator('\u0000');
        return cfg;
    }

    /** Parse YAML from a reader into a {@link #newYaml()} config, or {@code null} on any failure. */
    private FileConfiguration parse(java.io.Reader reader, String what) {
        YamlConfiguration cfg = newYaml();
        try {
            cfg.load(reader);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not parse " + what + ": " + ex.getMessage());
            return null;
        }
        return cfg;
    }

    /** Load a YAML resource straight from the plugin jar, or {@code null} if it isn't bundled/parseable. */
    private FileConfiguration fromJar(String path) {
        java.io.InputStream in = plugin.getResource(path);
        if (in == null) {
            return null;
        }
        try (java.io.Reader r = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
            return parse(r, path + " (bundled)");
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    /** Load an on-disk YAML file, or an empty (never-null) config if it's absent/unparseable. */
    private FileConfiguration loadYaml(File file) {
        if (file.exists()) {
            try (java.io.Reader r = new java.io.InputStreamReader(
                    new java.io.FileInputStream(file), java.nio.charset.StandardCharsets.UTF_8)) {
                FileConfiguration parsed = parse(r, file.getName());
                if (parsed != null) {
                    return parsed;
                }
            } catch (java.io.IOException ignored) {
                // fall through to an empty config
            }
        }
        return newYaml();
    }

    private void putTerms(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            terms.put(key, section.getString(key));
        }
    }

    /**
     * Translate an authored MiniMessage string (a boss name, title or item name) into the active
     * language via the {@code terms} map, keyed by its plain text. Returns the original if there's
     * no translation, so untranslated content still works.
     */
    public String tr(String mini) {
        if (mini == null) {
            return "";
        }
        if (terms.isEmpty()) {
            return mini;
        }
        return terms.getOrDefault(Text.plain(mini), mini);
    }

    private String raw(String key) {
        return lang.getString(key, "<red>Missing message: " + key);
    }

    /** Raw string for {@code key} in the active language ("" if absent) - used for broadcast templates. */
    public String string(String key) {
        return lang.getString(key, "");
    }

    /** A prefixed message component. */
    public Component get(String key, TagResolver... resolvers) {
        return Text.mm(prefix + raw(key), resolvers);
    }

    /** A message component without the prefix. */
    public Component plainMessage(String key, TagResolver... resolvers) {
        return Text.mm(raw(key), resolvers);
    }

    /** A list message (each list entry rendered separately, no prefix). */
    public List<Component> getList(String key, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>();
        for (String line : lang.getStringList(key)) {
            out.add(Text.mm(line, resolvers));
        }
        return out;
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(get(key, resolvers));
    }

    public void sendList(CommandSender to, String key, TagResolver... resolvers) {
        for (Component line : getList(key, resolvers)) {
            to.sendMessage(line);
        }
    }
}
