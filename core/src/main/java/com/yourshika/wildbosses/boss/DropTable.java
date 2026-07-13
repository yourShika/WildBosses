package com.yourshika.wildbosses.boss;

import java.util.List;

/**
 * Rewards granted on boss death.
 *
 * @param items    item drops (each rolled independently)
 * @param xp       experience dropped
 * @param commands console commands run on death ({@code %player%} = top damager)
 */
public record DropTable(List<DropEntry> items, int xp, List<String> commands) {

    public static DropTable empty() {
        return new DropTable(List.of(), 0, List.of());
    }
}
