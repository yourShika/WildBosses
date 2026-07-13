package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.WildBossesPlugin;
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
    }

    public void armySpawn(BossDefinition def, Location loc) {
        sendLocated(plugin.config().broadcastArmySpawn(), def, loc);
    }

    public void bossDeath(BossDefinition def) {
        broadcastSimple(plugin.config().broadcastBossDeath(), def);
    }

    public void bossFled(BossDefinition def) {
        broadcastSimple(plugin.config().broadcastBossFled(), def);
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
