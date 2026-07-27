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
        // The void bypasses regardless of the allow-command-kill setting.
        assertTrue(BossListener.isForceKillOrVoid("VOID", 4, false));
        assertTrue(BossListener.isForceKillOrVoid("VOID", 0, true));
    }

    @Test
    void commandKillBypassesOnlyWhenEnabled() {
        // The reported bug: a datapack/plugin /kill (cause KILL, Float.MAX_VALUE) deleted bosses.
        // By default (allow-command-kill = false) it must NOT bypass.
        assertFalse(BossListener.isForceKillOrVoid("KILL", Float.MAX_VALUE, false));
        assertFalse(BossListener.isForceKillOrVoid("SUICIDE", Float.MAX_VALUE, false));
        // Only when the admin opts in does a real /kill go through.
        assertTrue(BossListener.isForceKillOrVoid("KILL", Float.MAX_VALUE, true));
        assertTrue(BossListener.isForceKillOrVoid("SUICIDE", Float.MAX_VALUE, true));
    }

    @Test
    void genericCustomDamageNeverBypasses() {
        // An external plugin/datapack dealing CUSTOM must NOT bypass, at ANY magnitude, either way.
        assertFalse(BossListener.isForceKillOrVoid("CUSTOM", 20, false));
        assertFalse(BossListener.isForceKillOrVoid("CUSTOM", Float.MAX_VALUE, false));
        assertFalse(BossListener.isForceKillOrVoid("CUSTOM", Float.MAX_VALUE, true));
    }

    @Test
    void moderateKillDamageDoesNotBypassEvenWhenEnabled() {
        // A KILL/SUICIDE cause with ordinary magnitude is not a real force-kill.
        assertFalse(BossListener.isForceKillOrVoid("KILL", 50, true));
        assertFalse(BossListener.isForceKillOrVoid("SUICIDE", 500, true));
    }

    @Test
    void ordinaryCausesDoNotBypass() {
        assertFalse(BossListener.isForceKillOrVoid("FALL", 9999, true));
        assertFalse(BossListener.isForceKillOrVoid("FIRE_TICK", 1, false));
        assertFalse(BossListener.isForceKillOrVoid("ENTITY_ATTACK", 10, true));
    }
}
