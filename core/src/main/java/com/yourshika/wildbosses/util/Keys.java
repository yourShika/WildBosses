package com.yourshika.wildbosses.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Central holder for the {@link NamespacedKey}s used in entity/item {@code PersistentDataContainer}s.
 * Initialised once from {@code onEnable}.
 */
public final class Keys {

    /** Marks an entity as a WildBosses boss and stores its definition id (String). */
    public static NamespacedKey BOSS_ID;
    /** Marks an entity as a minion and stores the owning encounter id (String). */
    public static NamespacedKey ENCOUNTER_ID;
    /** Marks an entity as belonging to an army and stores the army/boss id (String). */
    public static NamespacedKey ARMY_ID;
    /** Marks a dropped/awarded item as boss loot (byte flag). */
    public static NamespacedKey LOOT_TAG;

    private Keys() {
    }

    public static void init(Plugin plugin) {
        BOSS_ID = new NamespacedKey(plugin, "boss_id");
        ENCOUNTER_ID = new NamespacedKey(plugin, "encounter_id");
        ARMY_ID = new NamespacedKey(plugin, "army_id");
        LOOT_TAG = new NamespacedKey(plugin, "loot_tag");
    }
}
