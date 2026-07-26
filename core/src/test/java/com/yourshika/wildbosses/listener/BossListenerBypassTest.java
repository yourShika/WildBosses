package com.yourshika.wildbosses.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins which damage may bypass a boss' protections. The bug this guards: a generic CUSTOM hit from an
 * external plugin/datapack used to bypass everything and one-shot an unattended boss (credited to "?").
 * Now only the void or a real force-kill (an admin /kill at Float.MAX_VALUE) may bypass.
 */
class BossListenerBypassTest {

    @Test
    void voidAlwaysBypasses() {
        assertTrue(BossListener.isForceKillOrVoid("VOID", 4));
        assertTrue(BossListener.isForceKillOrVoid("VOID", 0));
    }

    @Test
    void adminForceKillBypasses() {
        assertTrue(BossListener.isForceKillOrVoid("KILL", Float.MAX_VALUE));
        assertTrue(BossListener.isForceKillOrVoid("SUICIDE", Float.MAX_VALUE));
        assertTrue(BossListener.isForceKillOrVoid("KILL", 1.0E9));
    }

    @Test
    void genericCustomDamageNeverBypasses() {
        // The reported bug: an external plugin/datapack dealing CUSTOM must NOT bypass, at ANY magnitude.
        assertFalse(BossListener.isForceKillOrVoid("CUSTOM", 20));
        assertFalse(BossListener.isForceKillOrVoid("CUSTOM", 1000));
        assertFalse(BossListener.isForceKillOrVoid("CUSTOM", Float.MAX_VALUE));
    }

    @Test
    void moderateKillDamageDoesNotBypass() {
        // A KILL/SUICIDE cause with ordinary magnitude (e.g. a plugin using it loosely) is not a real
        // force-kill and must go through the normal player-only gate instead.
        assertFalse(BossListener.isForceKillOrVoid("KILL", 50));
        assertFalse(BossListener.isForceKillOrVoid("SUICIDE", 500));
    }

    @Test
    void ordinaryCausesDoNotBypass() {
        assertFalse(BossListener.isForceKillOrVoid("FALL", 9999));
        assertFalse(BossListener.isForceKillOrVoid("FIRE_TICK", 1));
        assertFalse(BossListener.isForceKillOrVoid("ENTITY_ATTACK", 10));
    }
}
