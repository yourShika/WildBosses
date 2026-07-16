package com.yourshika.wildbosses.listener;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.util.Keys;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * Removes stale WildBosses entities (bosses / army minions) that are no longer part of a running
 * encounter — e.g. left over after a server restart, or when a chunk with old tagged mobs loads.
 * Combined with {@code setPersistent(false)} on spawn, this ensures a restart never leaves untracked
 * boss mobs roaming the world.
 */
public final class CleanupListener implements Listener {

    private final WildBossesPlugin plugin;

    public CleanupListener(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (Keys.isWildBossesEntity(entity) && !isActive(entity)) {
                entity.remove();
            }
        }
    }

    private boolean isActive(Entity entity) {
        return plugin.bossManager().isBoss(entity)
                || plugin.armyManager().isTrackedMinion(entity.getUniqueId());
    }
}
