package com.yourshika.wildbosses.boss;

/**
 * A small decorative structure a terrain boss scatters around its spawn (e.g. bee trees, an
 * abandoned pumpkin patch, crying-obsidian shards). The {@code type} names a builder known to
 * {@code TerrainManager}; {@code count} is how many to attempt within the terrain radius.
 */
public record TerrainFeature(String type, int count) {
}
