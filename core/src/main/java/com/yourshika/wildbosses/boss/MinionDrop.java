package com.yourshika.wildbosses.boss;

import com.yourshika.wildbosses.util.NumberRange;
import org.bukkit.Material;

/**
 * A simple drop rolled when an army minion dies. Rarity is capped at UNCOMMON by the loader, so
 * minions can only ever hand out low-tier loot.
 *
 * @param material item type
 * @param amount   amount range
 * @param chance   roll chance 0.0-1.0
 * @param rarity   COMMON or UNCOMMON (higher tiers are clamped down)
 */
public record MinionDrop(Material material, NumberRange amount, double chance, Rarity rarity) {
}
