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

    public void bossDeath(BossDefinition def) {
        broadcastSimple(plugin.config().broadcastBossDeath(), def);
        DiscordWebhook.send(plugin, ":trophy: **" + Text.plain(def.name()) + "** has been slain!");
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
        String fmt = plugin.config().broadcastBossDrop();
        if (!plugin.config().dropBroadcastEnabled() || fmt == null || fmt.isBlank()) {
            return;
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
