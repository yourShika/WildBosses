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
        FileConfiguration en = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/en.yml"));
        File chosen = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
        if (chosen.exists()) {
            lang = YamlConfiguration.loadConfiguration(chosen);
            lang.setDefaults(en); // English fills any key the chosen language is missing
        } else {
            lang = en;
        }
        prefix = lang.getString("prefix", "");
        terms.clear();
        var section = lang.getConfigurationSection("terms");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                terms.put(key, section.getString(key));
            }
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
