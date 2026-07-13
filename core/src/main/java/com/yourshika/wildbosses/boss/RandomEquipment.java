package com.yourshika.wildbosses.boss;

import org.bukkit.Material;

import java.util.List;

/**
 * Optional randomised gear for a boss: a random armor tier + weapon, each enchanted with random
 * registry enchantments (vanilla and datapack custom enchants alike).
 *
 * @param enabled       whether to randomise gear on spawn
 * @param armorTiers    tier prefixes, e.g. {@code [IRON, GOLDEN, DIAMOND]} (armor pieces built as {@code <tier>_HELMET} etc.)
 * @param weapons       possible main-hand items
 * @param enchantCount  number of random enchants applied per item
 * @param extraLevels   levels added above each enchant's max (for "over-enchanted" gear)
 */
public record RandomEquipment(
        boolean enabled,
        List<String> armorTiers,
        List<Material> weapons,
        int enchantCount,
        int extraLevels
) {
    public static RandomEquipment disabled() {
        return new RandomEquipment(false, List.of(), List.of(), 0, 0);
    }
}
