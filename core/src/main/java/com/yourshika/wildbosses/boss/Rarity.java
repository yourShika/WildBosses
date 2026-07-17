package com.yourshika.wildbosses.boss;

import java.util.Locale;

/**
 * Loot rarity tiers. Each drop can declare a {@code rarity}; it colours a line in the item's lore
 * and the drop broadcast, and drives sensible defaults: RARE and above glow, LEGENDARY and above are
 * always announced server-wide.
 */
public enum Rarity {

    COMMON("Common", "<gray>"),
    UNCOMMON("Uncommon", "<green>"),
    RARE("Rare", "<aqua>"),
    LEGENDARY("Legendary", "<gold>"),
    MYTHICAL("Mythical", "<gradient:#ff6bd6:#c86bff>");

    private final String label;
    private final String openTag;

    Rarity(String label, String openTag) {
        this.label = label;
        this.openTag = openTag;
    }

    public String label() {
        return label;
    }

    /** Inline coloured label, e.g. {@code <aqua>Rare} - safe to drop into any MiniMessage string. */
    public String inline() {
        return openTag + "✦ " + label;
    }

    /** A full lore line: {@code <dark_gray>Rarity: <color>✦ Label}. */
    public String loreLine() {
        return "<dark_gray>Rarity: </dark_gray>" + inline();
    }

    /** RARE and above glow on the ground so they stand out. */
    public boolean glow() {
        return ordinal() >= RARE.ordinal();
    }

    /** LEGENDARY and above are always broadcast when they drop, whatever their chance. */
    public boolean alwaysAnnounce() {
        return ordinal() >= LEGENDARY.ordinal();
    }

    public static Rarity fromString(String raw, Rarity fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
