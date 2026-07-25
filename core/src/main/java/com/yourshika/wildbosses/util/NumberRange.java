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
        if (min == max) {
            return min;
        }
        // Use long arithmetic so an upper bound of Integer.MAX_VALUE doesn't overflow max+1 (which
        // would make nextInt(min, negative) throw). Amount ranges are tiny in practice, but a hostile
        // config value like "1-2147483647" must degrade gracefully rather than error on every kill.
        return (int) ThreadLocalRandom.current().nextLong(min, (long) max + 1L);
    }

    @Override
    public String toString() {
        return min == max ? Integer.toString(min) : (min + "-" + max);
    }
}
