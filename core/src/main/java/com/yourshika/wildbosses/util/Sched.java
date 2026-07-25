package com.yourshika.wildbosses.util;

/**
 * Scheduling seam + Folia detection. WildBosses' runtime currently assumes the classic single main
 * thread (one tick loop iterating every boss across every region), which Folia's region-threaded model
 * does not provide - a full migration means per-region tick scheduling and is tracked as a follow-up.
 *
 * <p>This class is the seam for that migration and, for now, lets the plugin DETECT Folia so it can
 * warn the operator clearly instead of failing in confusing ways.</p>
 */
public final class Sched {

    private static final boolean FOLIA = detect();

    private Sched() {
    }

    private static boolean detect() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** True when running on a Folia (region-threaded) server. */
    public static boolean isFolia() {
        return FOLIA;
    }
}
