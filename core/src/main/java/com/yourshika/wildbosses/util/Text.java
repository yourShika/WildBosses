package com.yourshika.wildbosses.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * MiniMessage helpers. All player-facing strings in WildBosses are MiniMessage, which natively
 * understands {@code <gradient:...>}, {@code <color>}, hover/click tags, etc.
 */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** Deserialize a MiniMessage string into a component (empty component for {@code null}). */
    public static Component mm(String input) {
        return input == null ? Component.empty() : MM.deserialize(input);
    }

    /** Deserialize a MiniMessage string with the given tag resolvers (placeholders). */
    public static Component mm(String input, TagResolver... resolvers) {
        return input == null ? Component.empty() : MM.deserialize(input, resolvers);
    }

    /** A placeholder whose value is itself parsed as MiniMessage (use for coloured inserts). */
    public static TagResolver parsed(String key, String value) {
        return Placeholder.parsed(key, value == null ? "" : value);
    }

    /** A placeholder whose value is inserted literally (never parsed as MiniMessage). */
    public static TagResolver unparsed(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    /** Convenience for a numeric placeholder. */
    public static TagResolver num(String key, Number value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** A placeholder replaced by a ready-made component (e.g. an item name with a hover tooltip). */
    public static TagResolver component(String key, Component value) {
        return Placeholder.component(key, value == null ? Component.empty() : value);
    }

    /** Render a MiniMessage string down to plain text (e.g. for logs or item display names). */
    public static String plain(String input) {
        return PlainTextComponentSerializer.plainText().serialize(mm(input));
    }

    /** The world's name for a location, or {@code "?"} if the world is unloaded/null. */
    public static String worldName(org.bukkit.Location loc) {
        return loc == null || loc.getWorld() == null ? "?" : loc.getWorld().getName();
    }

    /** Format a number of seconds as a short {@code "12m 30s"} / {@code "45s"} duration. */
    public static String duration(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }

    /** {@code "GOLDEN_HELMET"} / {@code "golden helmet"} -> {@code "Golden Helmet"}. */
    public static String titleCase(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String lower = s.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder sb = new StringBuilder(lower.length());
        boolean cap = true;
        for (char c : lower.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
                if (c == ' ') {
                    cap = true;
                }
            }
        }
        return sb.toString();
    }
}
