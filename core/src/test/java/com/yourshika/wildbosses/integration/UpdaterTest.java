package com.yourshika.wildbosses.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterTest {

    @Test
    void newerReleaseIsGreater() {
        assertTrue(Updater.compareVersions("0.21.5", "0.21.4") > 0);
    }

    @Test
    void sameVersionIsEqual() {
        assertEquals(0, Updater.compareVersions("0.21.4", "0.21.4"));
    }

    @Test
    void olderReleaseIsNegative() {
        assertTrue(Updater.compareVersions("0.21.3", "0.21.4") < 0);
    }

    @Test
    void comparesNumericallyNotLexically() {
        assertTrue(Updater.compareVersions("0.21.10", "0.21.9") > 0);
        assertTrue(Updater.compareVersions("0.100.0", "0.99.0") > 0);
    }

    @Test
    void majorBumpIsGreater() {
        assertTrue(Updater.compareVersions("1.0.0", "0.99.99") > 0);
    }

    @Test
    void missingTrailingPartsCountAsZero() {
        assertEquals(0, Updater.compareVersions("1.0", "1.0.0"));
    }
}
