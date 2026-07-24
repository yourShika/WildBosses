package com.yourshika.wildbosses.util;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;

/** Small entity helpers shared across the boss runtime. */
public final class Mobs {

    private Mobs() {
    }

    /** Force a mob to its adult form (covers zombies, piglins, hoglins/zoglins and animals). */
    public static void forceAdult(Entity e) {
        setBaby(e, false);
    }

    /** Set a mob's baby/adult state across every baby-capable entity type. */
    public static void setBaby(Entity e, boolean baby) {
        if (e instanceof Zombie z) {          // zombie, husk, drowned, zombie villager, zombified piglin
            z.setBaby(baby);
        } else if (e instanceof Ageable a) {  // animals (horse, wolf, etc.)
            if (baby) {
                a.setBaby();
            } else {
                a.setAdult();
            }
        }
        if (e instanceof PiglinAbstract p) {  // piglin, piglin brute (hoglin is Ageable, handled above)
            p.setBaby(baby);
        } else if (e instanceof Zoglin zo) {
            zo.setBaby(baby);
        }
    }
}
