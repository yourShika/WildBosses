package com.yourshika.wildbosses.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

/** Parses potion-effect tokens like {@code "POISON:200:1"} (type:durationTicks:amplifier). */
public final class Effects {

    private Effects() {
    }

    public static PotionEffectType type(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String norm = name.trim();
        PotionEffectType t = PotionEffectType.getByName(norm.toUpperCase(Locale.ROOT));
        if (t != null) {
            return t;
        }
        try {
            return Registry.EFFECT.get(NamespacedKey.minecraft(norm.toLowerCase(Locale.ROOT)));
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
