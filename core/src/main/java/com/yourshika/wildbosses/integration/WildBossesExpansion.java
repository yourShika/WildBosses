package com.yourshika.wildbosses.integration;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.ActiveBoss;
import com.yourshika.wildbosses.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * PlaceholderAPI expansion exposing WildBosses state. Only loaded/registered when PlaceholderAPI is
 * present. Placeholders: {@code %wildbosses_active%}, {@code %wildbosses_active_armies%},
 * {@code %wildbosses_total%}, {@code %wildbosses_nearest%}, {@code %wildbosses_nearest_distance%}.
 */
public final class WildBossesExpansion extends PlaceholderExpansion {

    private final WildBossesPlugin plugin;

    public WildBossesExpansion(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "wildbosses";
    }

    @Override
    public String getAuthor() {
        return "yourShika";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "active" -> String.valueOf(plugin.bossManager().count());
            case "active_armies" -> String.valueOf(plugin.armyManager().count());
            case "total" -> String.valueOf(plugin.registry().ids().size());
            case "nearest" -> {
                ActiveBoss boss = nearest(player);
                yield boss == null ? "none" : Text.plain(boss.def().name());
            }
            case "nearest_distance" -> {
                ActiveBoss boss = nearest(player);
                yield (boss == null || !(player instanceof Player p)) ? "-1"
                        : String.valueOf((int) p.getLocation().distance(boss.location()));
            }
            default -> null;
        };
    }

    private ActiveBoss nearest(OfflinePlayer player) {
        if (!(player instanceof Player p)) {
            return null;
        }
        ActiveBoss best = null;
        double bestDist = Double.MAX_VALUE;
        for (ActiveBoss boss : plugin.bossManager().active()) {
            Location loc = boss.location();
            if (loc.getWorld() != null && loc.getWorld().equals(p.getWorld())) {
                double d = loc.distanceSquared(p.getLocation());
                if (d < bestDist) {
                    bestDist = d;
                    best = boss;
                }
            }
        }
        return best;
    }
}
