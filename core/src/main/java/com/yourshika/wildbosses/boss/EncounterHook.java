package com.yourshika.wildbosses.boss;

/**
 * Lifecycle callback for an encounter, used by the terrain system (and potential future systems)
 * to react when a boss starts and ends without the boss runtime depending on those systems.
 */
public interface EncounterHook {

    void onStart(ActiveBoss boss);

    void onEnd(ActiveBoss boss);

    EncounterHook NOOP = new EncounterHook() {
        @Override
        public void onStart(ActiveBoss boss) {
        }

        @Override
        public void onEnd(ActiveBoss boss) {
        }
    };
}
