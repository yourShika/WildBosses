package com.yourshika.wildbosses.listener;

import com.yourshika.wildbosses.army.ArmyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Routes minion deaths to the army system so kill progress is tracked. */
public final class ArmyListener implements Listener {

    private final ArmyManager manager;

    public ArmyListener(ArmyManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        manager.onEntityDeath(event.getEntity());
    }
}
