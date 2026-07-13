package com.yourshika.wildbosses.boss;

import org.bukkit.Material;

import java.util.Map;

/**
 * Terrain-theming ("world corruption") settings for a boss/army.
 *
 * <p>Player builds are never destroyed: only an allowlist of natural blocks (the {@link #mappings()}
 * keys) is transformed, block-entities and protected regions are skipped, CoreProtect-placed blocks
 * are skipped when available, and every change is snapshotted and restored when the encounter ends.
 * When {@link #onlyUngeneratedChunks()} is set, terrain bosses only spawn on never-generated chunks.</p>
 */
public record TerrainSettings(
        boolean enabled,
        int radius,
        int maxBlocks,
        boolean restoreOnEnd,
        boolean requireCoreProtect,
        boolean onlyUngeneratedChunks,
        Map<Material, Material> mappings
) {
    public static TerrainSettings disabled() {
        return new TerrainSettings(false, 0, 0, true, false, true, Map.of());
    }
}
