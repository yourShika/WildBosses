package com.yourshika.wildbosses.boss;

import org.bukkit.Material;

/** Optional equipment placed on the boss entity. Any slot may be {@code null}. */
public record EquipmentSet(
        Material mainHand,
        Material offHand,
        Material helmet,
        Material chestplate,
        Material leggings,
        Material boots
) {
    public static EquipmentSet empty() {
        return new EquipmentSet(null, null, null, null, null, null);
    }
}
