package com.yourshika.wildbosses.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * An inclusive integer range parsed from config values such as {@code "3-6"}, {@code "5"} or a
 * plain integer. Used for drop amounts, wave sizes, etc.
 */
public final class NumberRange {

    private final int min;
    private final int max;

    public NumberRange(int min, int max) {
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
    }

    /** Parse {@code "min-max"} or a single number. Falls back to {@code fallback} on bad input. */
    public static NumberRange parse(Object raw, NumberRange fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number n) {
            return new NumberRange(n.intValue(), n.intValue());
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return fallback;
        }
        try {
            int dash = s.indexOf('-', s.startsWith("-") ? 1 : 0);
            if (dash > 0) {
                int lo = Integer.parseInt(s.substring(0, dash).trim());
                int hi = Integer.parseInt(s.substring(dash + 1).trim());
                return new NumberRange(lo, hi);
            }
            int v = Integer.parseInt(s);
            return new NumberRange(v, v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    /** A random value in {@code [min, max]}. */
    public int roll() {
        return min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    @Override
    public String toString() {
        return min == max ? Integer.toString(min) : (min + "-" + max);
    }
}
