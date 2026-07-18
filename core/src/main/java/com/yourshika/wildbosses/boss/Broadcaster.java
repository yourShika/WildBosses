package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.integration.DiscordWebhook;
import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/** Sends the configured server-wide broadcast messages for boss/army events. */
public final class Broadcaster {

    private final WildBossesPlugin plugin;

    public Broadcaster(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public void bossSpawn(BossDefinition def, Location loc) {
        sendLocated(plugin.config().broadcastBossSpawn(), def, loc);
        DiscordWebhook.send(plugin, ":crossed_swords: **" + Text.plain(def.name()) + "** ["
                + def.difficulty().label() + "] appeared in " + worldName(loc)
                + " near " + loc.getBlockX() + ", " + loc.getBlockZ());
    }

    public void armySpawn(BossDefinition def, Location loc) {
        sendLocated(plugin.config().broadcastArmySpawn(), def, loc);
        DiscordWebhook.send(plugin, ":skull: **" + Text.plain(def.name()) + "** ["
                + def.difficulty().label() + "] is invading " + worldName(loc)
                + " near " + loc.getBlockX() + ", " + loc.getBlockZ());
    }

    public void bossDeath(BossDefinition def, String slayers) {
        boolean hasSlayers = slayers != null && !slayers.isBlank();
        if (plugin.config().broadcastEnabled()) {
            String fmt = plugin.config().broadcastBossDeath();
            if (fmt != null && !fmt.isBlank()) {
                net.kyori.adventure.text.Component msg = Text.mm(fmt,
                        Text.parsed("boss", def.name()),
                        Text.parsed("difficulty", def.difficulty().bracketedMini()),
                        Text.unparsed("slayers", hasSlayers ? slayers : ""));
                // If the template doesn't use <slayers>, append the killer(s) so they're always shown.
                if (hasSlayers && !fmt.contains("<slayers>")) {
                    msg = msg.append(Text.mm(" <gray>by <yellow>" + slayers));
                }
                Bukkit.broadcast(msg);
            }
        }
        DiscordWebhook.send(plugin, ":trophy: **" + Text.plain(def.name()) + "** has been slain"
                + (hasSlayers ? " by " + slayers : "") + "!");
    }

    private static String worldName(Location loc) {
        return Text.worldName(loc);
    }

    public void bossFled(BossDefinition def) {
        broadcastSimple(plugin.config().broadcastBossFled(), def);
    }

    /**
     * Announce a notable item drop server-wide. {@code display} is the (hoverable) item name and
     * {@code amount} the stack size; {@code finderName} may be {@code null} (unknown killer).
     */
    public void bossDrop(BossDefinition def, net.kyori.adventure.text.Component display, int amount, String finderName) {
        if (!plugin.config().dropBroadcastEnabled()) {
            return;
        }
        String fmt = plugin.config().broadcastBossDrop();
        if (fmt == null || fmt.isBlank()) {
            // Fall back to a built-in format so drops/pets are announced even on an outdated config
            // that has no broadcast.boss-drop line.
            fmt = "<gradient:#f8b500:#fceabb><bold>[WildBosses]</bold></gradient> <yellow><player></yellow> <white>looted <item> <white>from the <boss><white>!";
        }
        Bukkit.broadcast(Text.mm(fmt,
                Text.parsed("boss", def.name()),
                Text.parsed("difficulty", def.difficulty().bracketedMini()),
                Text.unparsed("player", finderName == null || finderName.isBlank() ? "Someone" : finderName),
                Text.num("amount", amount),
                Text.component("item", display)));
        DiscordWebhook.send(plugin, ":gift: **" + Text.plain(def.name()) + "** dropped "
                + Text.plain(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(display))
                + (amount > 1 ? " x" + amount : "")
                + (finderName == null || finderName.isBlank() ? "" : " for " + finderName));
    }

    private void broadcastSimple(String fmt, BossDefinition def) {
        if (!plugin.config().broadcastEnabled() || fmt == null || fmt.isBlank()) {
            return;
        }
        Bukkit.broadcast(Text.mm(fmt,
                Text.parsed("boss", def.name()),
                Text.parsed("difficulty", def.difficulty().bracketedMini())));
    }

    private void sendLocated(String fmt, BossDefinition def, Location loc) {
        if (!plugin.config().broadcastEnabled() || fmt == null || fmt.isBlank()) {
            return;
        }
        String world = loc.getWorld() == null ? "?" : loc.getWorld().getName();
        TagResolver[] resolvers = {
                Text.parsed("boss", def.name()),
                Text.parsed("difficulty", def.difficulty().bracketedMini()),
                Text.unparsed("world", world),
                Text.num("x", loc.getBlockX()),
                Text.num("y", loc.getBlockY()),
                Text.num("z", loc.getBlockZ())
        };
        Bukkit.broadcast(Text.mm(fmt, resolvers));
    }
}
