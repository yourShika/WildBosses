package com.yourshika.wildbosses.boss;

import org.bukkit.inventory.ItemStack;

/**
 * A drop stored as a fully-serialized {@link ItemStack} (1:1 with all enchants, NBT, custom model
 * data and components intact). Used by the in-game GUI's "add from hand" so exactly the item an admin
 * is holding is dropped, without lossy field-by-field re-authoring.
 *
 * @param stack    the exact item to drop (cloned before dropping)
 * @param chance   roll chance 0.0-1.0
 * @param announce force a broadcast regardless of the rarity threshold
 * @param rarity   loot tier (colours the broadcast; drives glow/announce defaults)
 */
public record RawDrop(ItemStack stack, double chance, boolean announce, Rarity rarity) {
}
