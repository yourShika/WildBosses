package com.yourshika.wildbosses.difficulty;

import com.yourshika.wildbosses.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import java.util.Locale;

/**
 * Boss difficulty tiers. Each tier carries a display label, a MiniMessage gradient (from/to colour)
 * and a boss-bar colour. Defaults are baked in but can be overridden from {@code config.yml}.
 */
public enum Difficulty {

    EASY("Easy", "#7CFC00", "#2E8B57", BossBar.Color.GREEN),
    MEDIUM("Medium", "#FFD700", "#FFB300", BossBar.Color.YELLOW),
    HARD("Hard", "#FF8C00", "#FF2D00", BossBar.Color.RED),
    ULTRA_HARD("Ultra Hard", "#FF1A4B", "#7A0000", BossBar.Color.RED),
    MAGICAL("Magical", "#C86BFF", "#FF6BD6", BossBar.Color.PURPLE);

    private final String defaultLabel;
    private final String defaultFrom;
    private final String defaultTo;
    private final BossBar.Color defaultBarColor;

    private String label;
    private String from;
    private String to;
    private BossBar.Color barColor;

    Difficulty(String label, String from, String to, BossBar.Color barColor) {
        this.defaultLabel = label;
        this.defaultFrom = from;
        this.defaultTo = to;
        this.defaultBarColor = barColor;
        resetToDefaults();
    }

    /** Restore the compiled-in defaults (called before applying config overrides on reload). */
    public void resetToDefaults() {
        this.label = defaultLabel;
        this.from = defaultFrom;
        this.to = defaultTo;
        this.barColor = defaultBarColor;
    }

    /** Apply config overrides; any {@code null} argument keeps the current value. */
    public void applyOverrides(String label, String from, String to, BossBar.Color barColor) {
        if (label != null && !label.isBlank()) {
            this.label = label;
        }
        if (from != null && !from.isBlank()) {
            this.from = from;
        }
        if (to != null && !to.isBlank()) {
            this.to = to;
        }
        if (barColor != null) {
            this.barColor = barColor;
        }
    }

    public String label() {
        return label;
    }

    public BossBar.Color barColor() {
        return barColor;
    }

    /** Wrap arbitrary text in this tier's gradient as a MiniMessage string. */
    public String gradient(String text) {
        return "<gradient:" + from + ":" + to + ">" + text + "</gradient>";
    }

    /** The tier label rendered in its gradient. */
    public Component display() {
        return Text.mm(gradient(label));
    }

    /** The tier label in gradient, wrapped in grey brackets, e.g. {@code [Hard]}. */
    public Component bracketed() {
        return Text.mm(bracketedMini());
    }

    /** The bracketed label as a raw MiniMessage string (for embedding in placeholders). */
    public String bracketedMini() {
        return bracketedMini(label);
    }

    /** As {@link #bracketedMini()} but with a translated label. */
    public String bracketedMini(String labelOverride) {
        String l = labelOverride == null || labelOverride.isBlank() ? label : labelOverride;
        return "<gray>[</gray>" + gradient(l) + "<gray>]</gray>";
    }

    /** Lenient parse: case-insensitive, spaces or dashes accepted (e.g. "ultra hard"). */
    public static Difficulty fromString(String s, Difficulty fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
