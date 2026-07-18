package com.yourshika.wildbosses.boss;

import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * A minion template used in army waves.
 *
 * @param type    base entity type
 * @param weight  relative spawn weight within the army
 * @param health  max health override (&lt;=0 keeps vanilla)
 * @param name    optional MiniMessage name tag (nullable)
 * @param effects raw potion-effect tokens like {@code "POISON:200:0"} applied on spawn
 * @param drops   loot rolled when this minion dies (rarity capped at UNCOMMON)
 */
public record ArmyMinion(
        EntityType type,
        int weight,
        double health,
        String name,
        List<String> effects,
        List<MinionDrop> drops
) {
}
