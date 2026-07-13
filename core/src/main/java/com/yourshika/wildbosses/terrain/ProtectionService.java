package com.yourshika.wildbosses.terrain;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

/**
 * Decides whether a given natural block may be safely transformed by a terrain effect, guaranteeing
 * player builds are never touched. Layers:
 * <ol>
 *   <li>Block-entities (chests, signs, ...) are always skipped.</li>
 *   <li>If a region plugin (WorldGuard / GriefPrevention) protects the spot, it is skipped.</li>
 *   <li>If CoreProtect is present, any block with logged history is treated as player-placed and
 *       skipped. If {@code require-coreprotect} is set but CoreProtect is absent, everything is
 *       skipped (strict mode).</li>
 * </ol>
 * All optional integrations are reflective and self-disable on any error, so a missing/updated
 * plugin never breaks WildBosses. The primary safeguard remains spawning only on ungenerated chunks.
 */
public final class ProtectionService {

    private final Logger logger;

    // CoreProtect
    private boolean cpInit;
    private boolean cpAvailable;
    private Object cpApi;
    private Method cpBlockLookup;

    // GriefPrevention
    private boolean gpInit;
    private boolean gpAvailable;
    private Object gpDataStore;
    private Method gpGetClaimAt;

    // WorldGuard
    private boolean wgInit;
    private boolean wgAvailable;
    private Object wgQuery;
    private Method wgAdapt;
    private Method wgGetApplicableRegions;
    private Method wgSetSize;

    public ProtectionService(Logger logger) {
        this.logger = logger;
    }

    /** Whether a natural block (already confirmed to be in the allowlist) may be corrupted. */
    public boolean canCorrupt(Block block, boolean requireCoreProtect) {
        if (block.getState() instanceof TileState) {
            return false; // has a block entity / NBT - never a plain natural block we should touch
        }
        if (regionProtected(block.getLocation())) {
            return false;
        }
        if (coreProtectAvailable()) {
            return !playerTouched(block);
        }
        return !requireCoreProtect;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder("allowlist + block-entity guard");
        if (coreProtectAvailable()) {
            sb.append(", CoreProtect");
        }
        if (worldGuardAvailable()) {
            sb.append(", WorldGuard");
        }
        if (griefPreventionAvailable()) {
            sb.append(", GriefPrevention");
        }
        return sb.toString();
    }

    private boolean regionProtected(Location loc) {
        return (worldGuardAvailable() && worldGuardProtected(loc))
                || (griefPreventionAvailable() && griefPreventionClaimed(loc));
    }

    // ---- CoreProtect ----------------------------------------------------------------------

    public boolean coreProtectAvailable() {
        if (!cpInit) {
            cpInit = true;
            Plugin p = Bukkit.getPluginManager().getPlugin("CoreProtect");
            if (p != null && p.isEnabled()) {
                try {
                    Object api = p.getClass().getMethod("getAPI").invoke(p);
                    boolean enabled = (Boolean) api.getClass().getMethod("isEnabled").invoke(api);
                    int version = (Integer) api.getClass().getMethod("APIVersion").invoke(api);
                    if (enabled && version >= 6) {
                        cpBlockLookup = api.getClass().getMethod("blockLookup", Block.class, int.class);
                        cpApi = api;
                        cpAvailable = true;
                    }
                } catch (Throwable t) {
                    logger.warning("CoreProtect integration failed to initialise: " + t.getMessage());
                }
            }
        }
        return cpAvailable;
    }

    private boolean playerTouched(Block block) {
        try {
            @SuppressWarnings("unchecked")
            List<String[]> results = (List<String[]>) cpBlockLookup.invoke(cpApi, block, Integer.MAX_VALUE);
            return results != null && !results.isEmpty();
        } catch (Throwable t) {
            // On any lookup failure, be conservative and treat as player-placed (protect the build).
            return true;
        }
    }

    // ---- GriefPrevention ------------------------------------------------------------------

    public boolean griefPreventionAvailable() {
        if (!gpInit) {
            gpInit = true;
            Plugin p = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            if (p != null && p.isEnabled()) {
                try {
                    Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
                    Object instance = gpClass.getField("instance").get(null);
                    gpDataStore = gpClass.getField("dataStore").get(instance);
                    Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
                    gpGetClaimAt = gpDataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, claimClass);
                    gpAvailable = true;
                } catch (Throwable t) {
                    logger.warning("GriefPrevention integration failed to initialise: " + t.getMessage());
                }
            }
        }
        return gpAvailable;
    }

    private boolean griefPreventionClaimed(Location loc) {
        try {
            return gpGetClaimAt.invoke(gpDataStore, loc, true, null) != null;
        } catch (Throwable t) {
            return true; // be conservative
        }
    }

    // ---- WorldGuard -----------------------------------------------------------------------

    public boolean worldGuardAvailable() {
        if (!wgInit) {
            wgInit = true;
            Plugin p = Bukkit.getPluginManager().getPlugin("WorldGuard");
            if (p != null && p.isEnabled()) {
                try {
                    Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                    Object wg = wgClass.getMethod("getInstance").invoke(null);
                    Object platform = wg.getClass().getMethod("getPlatform").invoke(wg);
                    Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                    wgQuery = container.getClass().getMethod("createQuery").invoke(container);

                    Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                    Class<?> weLocation = Class.forName("com.sk89q.worldedit.util.Location");
                    wgAdapt = adapter.getMethod("adapt", Location.class);
                    wgGetApplicableRegions = wgQuery.getClass().getMethod("getApplicableRegions", weLocation);
                    wgAvailable = true;
                } catch (Throwable t) {
                    logger.warning("WorldGuard integration failed to initialise: " + t.getMessage());
                }
            }
        }
        return wgAvailable;
    }

    private boolean worldGuardProtected(Location loc) {
        try {
            Object weLoc = wgAdapt.invoke(null, loc);
            Object set = wgGetApplicableRegions.invoke(wgQuery, weLoc);
            if (wgSetSize == null) {
                wgSetSize = set.getClass().getMethod("size");
            }
            int size = (Integer) wgSetSize.invoke(set);
            return size > 0; // inside any region -> conservatively protect
        } catch (Throwable t) {
            return true; // be conservative
        }
    }
}
