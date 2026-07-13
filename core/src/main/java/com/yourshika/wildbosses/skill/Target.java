package com.yourshika.wildbosses.skill;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/** A resolved skill target: always a location, optionally a living entity. */
public record Target(Location location, LivingEntity entity) {

    public static Target of(LivingEntity entity) {
        return new Target(entity.getLocation(), entity);
    }

    public static Target of(Location location) {
        return new Target(location.clone(), null);
    }

    public boolean hasEntity() {
        return entity != null;
    }
}
