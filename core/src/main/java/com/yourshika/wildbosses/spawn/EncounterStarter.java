package com.yourshika.wildbosses.spawn;

import com.yourshika.wildbosses.boss.BossDefinition;
import org.bukkit.Location;

/**
 * Starts a special encounter (e.g. an army) for a boss definition. Injected into the spawn scheduler
 * so army bosses are routed to the army system without the scheduler depending on it directly.
 */
@FunctionalInterface
public interface EncounterStarter {

    boolean start(BossDefinition def, Location location);
}
