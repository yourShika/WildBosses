package com.yourshika.wildbosses.army;

import com.yourshika.wildbosses.WildBossesPlugin;
import com.yourshika.wildbosses.boss.BossDefinition;
import com.yourshika.wildbosses.spawn.EncounterStarter;
import com.yourshika.wildbosses.util.Keys;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Manages running {@link ArmyEncounter}s and routes minion deaths to the owning encounter. Also
 * serves as the {@link EncounterStarter} the spawn scheduler uses for army bosses.
 */
public final class ArmyManager implements EncounterStarter {

    private final WildBossesPlugin plugin;
    // ConcurrentHashMap: PlaceholderAPI (via TAB/scoreboard plugins) reads count()/active() off the
    // main thread while the main thread mutates these; weakly-consistent iteration avoids a CME (parity
    // with BossManager.byEntity).
    private final Map<String, ArmyEncounter> encounters = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, String> minionOwner = new java.util.concurrent.ConcurrentHashMap<>();

    public ArmyManager(WildBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean start(BossDefinition def, Location location) {
        if (!def.isArmy() || location.getWorld() == null) {
            return false;
        }
        String id = UUID.randomUUID().toString();
        ArmyEncounter encounter = new ArmyEncounter(plugin, this, def, location.clone(), id);
        encounters.put(id, encounter);
        encounter.start();
        plugin.broadcaster().armySpawn(def, location);
        return true;
    }

    void registerMinion(UUID uuid, String encounterId) {
        minionOwner.put(uuid, encounterId);
    }

    void unregisterMinion(UUID uuid) {
        minionOwner.remove(uuid);
    }

    void end(ArmyEncounter encounter) {
        encounters.remove(encounter.id());
    }

    /** Route an entity death to its owning army encounter, if any. */
    public void onEntityDeath(Entity entity) {
        String encounterId = minionOwner.remove(entity.getUniqueId());
        if (encounterId == null) {
            encounterId = entity.getPersistentDataContainer().get(Keys.ARMY_ID, PersistentDataType.STRING);
        }
        if (encounterId == null) {
            return;
        }
        ArmyEncounter encounter = encounters.get(encounterId);
        if (encounter != null) {
            encounter.onMinionDeath(entity);
        }
    }

    /** Force-stop a single army encounter (despawn minions, remove bar). */
    public void terminate(ArmyEncounter encounter) {
        encounter.terminate();
    }

    public boolean isTrackedMinion(UUID uuid) {
        return minionOwner.containsKey(uuid);
    }

    public int count() {
        return encounters.size();
    }

    /** Number of active army encounters of a given boss definition (for the per-boss spawn cap). */
    public int countOfDefinition(String bossId) {
        int n = 0;
        for (ArmyEncounter e : encounters.values()) {
            if (e.def().id().equalsIgnoreCase(bossId)) {
                n++;
            }
        }
        return n;
    }

    /** Distance to the nearest active army anchor in {@code loc}'s world, or {@link Double#MAX_VALUE}. */
    public double nearestArmyDistance(Location loc) {
        double best = Double.MAX_VALUE;
        for (ArmyEncounter e : encounters.values()) {
            org.bukkit.World w = e.anchor().getWorld();
            if (w != null && w.equals(loc.getWorld())) {
                best = Math.min(best, e.anchor().distance(loc));
            }
        }
        return best;
    }

    public Collection<ArmyEncounter> active() {
        return encounters.values();
    }

    public void shutdown() {
        for (ArmyEncounter encounter : new ArrayList<>(encounters.values())) {
            encounter.terminate();
        }
        encounters.clear();
        minionOwner.clear();
    }
}
