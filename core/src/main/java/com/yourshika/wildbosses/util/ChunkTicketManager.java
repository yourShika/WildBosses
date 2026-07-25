package com.yourshika.wildbosses.util;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Reference-counts plugin chunk tickets per (world, chunk). Bukkit keeps only ONE plugin ticket per
 * chunk regardless of how many callers add it, so without ref-counting the first encounter to end
 * would drop the ticket a second, overlapping encounter (e.g. an army end-boss spawned on the army
 * anchor chunk) still needs - unloading a live fight. Acquire on start, release on end; the underlying
 * ticket is only added on the first acquire and removed on the last release.
 */
public final class ChunkTicketManager {

    private final Plugin plugin;
    private final Map<String, Integer> counts = new HashMap<>();

    public ChunkTicketManager(Plugin plugin) {
        this.plugin = plugin;
    }

    private static String key(World world, int cx, int cz) {
        return world.getUID() + ":" + cx + ":" + cz;
    }

    public void acquire(World world, int cx, int cz) {
        if (world == null) {
            return;
        }
        if (counts.merge(key(world, cx, cz), 1, Integer::sum) == 1) {
            world.addPluginChunkTicket(cx, cz, plugin);
        }
    }

    public void release(World world, int cx, int cz) {
        if (world == null) {
            return;
        }
        String k = key(world, cx, cz);
        Integer c = counts.get(k);
        if (c == null) {
            return;
        }
        if (c <= 1) {
            counts.remove(k);
            world.removePluginChunkTicket(cx, cz, plugin);
        } else {
            counts.put(k, c - 1);
        }
    }

    public void clear() {
        counts.clear(); // Bukkit auto-drops the plugin's tickets on disable
    }
}
