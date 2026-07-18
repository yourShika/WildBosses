package com.yourshika.wildbosses.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Map;

/** Parses potion-effect tokens like {@code "POISON:200:1"} (type:durationTicks:amplifier). */
public final class Effects {

    // Legacy pre-1.20.5 names -> modern registry keys, so old tokens still resolve without hitting
    // Bukkit's deprecated PotionEffectType.getByName (that legacy path is extremely slow on MC 26.x
    // and was hanging the server thread).
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("slow", "slowness"),
            Map.entry("fast_digging", "haste"),
            Map.entry("slow_digging", "mining_fatigue"),
            Map.entry("increase_damage", "strength"),
            Map.entry("heal", "instant_health"),
            Map.entry("harm", "instant_damage"),
            Map.entry("jump", "jump_boost"),
            Map.entry("confusion", "nausea"),
            Map.entry("damage_resistance", "resistance"),
            Map.entry("unluck", "bad_luck"),
            Map.entry("dolphins_grace", "dolphins_grace"));

    private Effects() {
    }

    public static PotionEffectType type(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        key = ALIASES.getOrDefault(key, key);
        try {
            return Registry.EFFECT.get(NamespacedKey.minecraft(key));
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse {@code "TYPE:duration:amplifier"}; returns {@code null} if the type is unknown. */
    public static PotionEffect parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split(":");
        PotionEffectType type = type(parts[0]);
        if (type == null) {
            return null;
        }
        int duration = parts.length > 1 ? parseInt(parts[1], 200) : 200;
        int amplifier = parts.length > 2 ? parseInt(parts[2], 0) : 0;
        return new PotionEffect(type, duration, amplifier, false, true, true);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
