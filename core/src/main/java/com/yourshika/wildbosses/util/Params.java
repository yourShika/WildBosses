package com.yourshika.wildbosses.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A lenient, read-only view over a map of config values (a mechanic's/skill's parameters).
 * Tolerates strings-for-numbers and missing keys, returning supplied defaults.
 */
public final class Params {

    private static final Params EMPTY = new Params(Map.of());

    private final Map<String, Object> map;

    public Params(Map<String, Object> map) {
        this.map = map == null ? Map.of() : map;
    }

    public static Params of(ConfigurationSection section) {
        return section == null ? EMPTY : new Params(section.getValues(false));
    }

    public static Params empty() {
        return EMPTY;
    }

    public boolean has(String key) {
        return map.containsKey(key);
    }

    public String getString(String key, String def) {
        Object o = map.get(key);
        return o == null ? def : o.toString();
    }

    public int getInt(String key, int def) {
        Object o = map.get(key);
        if (o instanceof Number n) {
            return n.intValue();
        }
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double getDouble(String key, double def) {
        Object o = map.get(key);
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o == null) {
            return def;
        }
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBoolean(String key, boolean def) {
        Object o = map.get(key);
        if (o instanceof Boolean b) {
            return b;
        }
        return o == null ? def : Boolean.parseBoolean(o.toString().trim());
    }

    public List<String> getStringList(String key) {
        Object o = map.get(key);
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object e : list) {
                if (e != null) {
                    out.add(e.toString());
                }
            }
        } else if (o != null) {
            out.add(o.toString());
        }
        return out;
    }

    public NumberRange getRange(String key, NumberRange def) {
        return NumberRange.parse(map.get(key), def);
    }

    public Material getMaterial(String key, Material def) {
        String raw = getString(key, null);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        Material m = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return m == null ? def : m;
    }

    /** The raw backing map (used for nested structures). */
    public Map<String, Object> raw() {
        return map;
    }
}
