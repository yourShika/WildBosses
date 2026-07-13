package com.yourshika.wildbosses.boss;

import net.kyori.adventure.bossbar.BossBar;

/**
 * Boss bar appearance. A {@code null} colour means "use the difficulty tier's colour".
 */
public record BossBarSettings(BossBar.Color color, BossBar.Overlay overlay) {

    public static BossBarSettings defaults() {
        return new BossBarSettings(null, BossBar.Overlay.PROGRESS);
    }
}
