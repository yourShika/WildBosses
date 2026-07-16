package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.util.NumberRange;
import org.bukkit.Material;

import java.util.List;

/**
 * A single loot entry.
 *
 * @param material         item type
 * @param amount           amount range
 * @param chance           drop chance 0.0-1.0
 * @param name             optional MiniMessage display name (nullable)
 * @param lore             optional MiniMessage lore lines
 * @param enchants         raw enchant tokens like {@code "SHARPNESS:5"}
 * @param customModelData  optional custom model data (&lt;0 = none)
 * @param glow             force an enchantment glint
 * @param announce         always broadcast this drop, regardless of the global rarity threshold
 */
public record DropEntry(
        Material material,
        NumberRange amount,
        double chance,
        String name,
        List<String> lore,
        List<String> enchants,
        int customModelData,
        boolean glow,
        boolean announce
) {
}
