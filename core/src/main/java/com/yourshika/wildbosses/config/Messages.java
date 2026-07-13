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
import java.util.List;

/**
 * Loads and serves player-facing messages from {@code lang/en.yml}. Every message is prefixed
 * (unless requested otherwise) and rendered through MiniMessage with the supplied placeholders.
 */
public final class Messages {

    private final Plugin plugin;
    private FileConfiguration lang;
    private String prefix = "";

    public Messages(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "lang/en.yml");
        if (!file.exists()) {
            plugin.saveResource("lang/en.yml", false);
        }
        lang = YamlConfiguration.loadConfiguration(file);
        prefix = lang.getString("prefix", "");
    }

    private String raw(String key) {
        return lang.getString(key, "<red>Missing message: " + key);
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
