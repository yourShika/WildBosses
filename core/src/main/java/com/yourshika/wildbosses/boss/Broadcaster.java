package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.integration.DiscordWebhook;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/**
 * Server-wide broadcasts for boss/army events. Templates come from the active language file
 * ({@code lang/<lang>.yml}); the matching {@code broadcast.*} key in config.yml overrides it when set.
 * Boss names and the difficulty label are translated through the language {@code terms} map.
 */
public final class Broadcaster {

    private final WildBossesPlugin plugin;

    public Broadcaster(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public void bossSpawn(BossDefinition def, Location loc) {
        sendLocated(template(plugin.config().broadcastBossSpawn(), "broadcast-boss-spawn"), def, loc);
        discord("discord-boss-spawn", def, loc, null, null);
    }

    public void armySpawn(BossDefinition def, Location loc) {
        sendLocated(template(plugin.config().broadcastArmySpawn(), "broadcast-army-spawn"), def, loc);
        discord("discord-army-spawn", def, loc, null, null);
    }

    public void bossDeath(BossDefinition def, String slayers) {
        boolean hasSlayers = slayers != null && !slayers.isBlank();
        String fmt = template(plugin.config().broadcastBossDeath(), "broadcast-boss-death");
        if (plugin.config().broadcastEnabled() && fmt != null && !fmt.isBlank()) {
            Component msg = Text.mm(fmt,
                    Text.parsed("boss", bossName(def)),
                    Text.parsed("difficulty", difficulty(def)),
                    Text.unparsed("slayers", hasSlayers ? slayers : ""));
            if (hasSlayers && !fmt.contains("<slayers>")) {
                msg = msg.append(Text.mm(" <gray>by <yellow>" + slayers));
            }
            Bukkit.broadcast(msg);
        }
        discord("discord-boss-death", def, null, hasSlayers ? slayers : "?", null);
    }

    public void bossFled(BossDefinition def) {
        broadcastSimple(template(plugin.config().broadcastBossFled(), "broadcast-boss-fled"), def);
    }

    /**
     * Announce a notable item drop. {@code display} is the (already-localised, hoverable) item name;
     * {@code finderName} may be {@code null}.
     */
    public void bossDrop(BossDefinition def, Component display, int amount, String finderName) {
        if (!plugin.config().dropBroadcastEnabled()) {
            return;
        }
        String fmt = template(plugin.config().broadcastBossDrop(), "broadcast-boss-drop");
        if (fmt == null || fmt.isBlank()) {
            fmt = "<gradient:#f8b500:#fceabb><bold>[WildBosses]</bold></gradient> <yellow><player></yellow> <white>looted <item> <white>from the <boss><white>!";
        }
        String player = finderName == null || finderName.isBlank() ? plainOr("word-someone", "Someone") : finderName;
        Bukkit.broadcast(Text.mm(fmt,
                Text.parsed("boss", bossName(def)),
                Text.parsed("difficulty", difficulty(def)),
                Text.unparsed("player", player),
                Text.num("amount", amount),
                Text.component("item", display)));
        DiscordWebhook.send(plugin, ":gift: **" + Text.plain(bossName(def)) + "** -> "
                + PlainTextComponentSerializer.plainText().serialize(display)
                + (amount > 1 ? " x" + amount : "")
                + (finderName == null || finderName.isBlank() ? "" : " (" + finderName + ")"));
    }

    // ---- helpers --------------------------------------------------------------------------

    /** The config value if the admin set one, otherwise the active language's template. */
    private String template(String configValue, String langKey) {
        if (configValue != null && !configValue.isBlank()) {
            return configValue;
        }
        return plugin.messages().string(langKey);
    }

    /** The boss' display name, translated into the active language. */
    private String bossName(BossDefinition def) {
        return plugin.messages().tr(def.name());
    }

    /** The bracketed difficulty tag with its label translated. */
    private String difficulty(BossDefinition def) {
        return def.difficulty().bracketedMini(plugin.messages().tr(def.difficulty().label()));
    }

    private String plainOr(String key, String fallback) {
        String s = plugin.messages().string(key);
        return s == null || s.isBlank() ? fallback : s;
    }

    private void discord(String langKey, BossDefinition def, Location loc, String slayers, String ignored) {
        String t = plugin.messages().string(langKey);
        if (t == null || t.isBlank()) {
            return;
        }
        t = t.replace("%boss%", Text.plain(bossName(def)))
                .replace("%difficulty%", Text.plain(plugin.messages().tr(def.difficulty().label())))
                .replace("%slayers%", slayers == null ? "" : slayers);
        if (loc != null) {
            t = t.replace("%world%", Text.worldName(loc))
                    .replace("%x%", String.valueOf(loc.getBlockX()))
                    .replace("%z%", String.valueOf(loc.getBlockZ()));
        }
        DiscordWebhook.send(plugin, t);
    }

    private void broadcastSimple(String fmt, BossDefinition def) {
        if (!plugin.config().broadcastEnabled() || fmt == null || fmt.isBlank()) {
            return;
        }
        Bukkit.broadcast(Text.mm(fmt,
                Text.parsed("boss", bossName(def)),
                Text.parsed("difficulty", difficulty(def))));
    }

    private void sendLocated(String fmt, BossDefinition def, Location loc) {
        if (!plugin.config().broadcastEnabled() || fmt == null || fmt.isBlank()) {
            return;
        }
        TagResolver[] resolvers = {
                Text.parsed("boss", bossName(def)),
                Text.parsed("difficulty", difficulty(def)),
                Text.unparsed("world", Text.worldName(loc)),
                Text.num("x", loc.getBlockX()),
                Text.num("y", loc.getBlockY()),
                Text.num("z", loc.getBlockZ())
        };
        Bukkit.broadcast(Text.mm(fmt, resolvers));
    }
}
