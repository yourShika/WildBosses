package com.yourshika.wildbosses.util;

import java.util.Locale;

/** Small helpers for lenient enum parsing from config. */
public final class Enums {

    private Enums() {
    }

    /**
     * Normalise a config token to CONSTANT_CASE. Accepts camelCase ({@code onHealthBelow}),
     * spaced ({@code "on health below"}) and dashed ({@code on-health-below}) forms.
     */
    public static String constantCase(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length() + 4);
        char[] chars = input.trim().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == ' ' || c == '-') {
                sb.append('_');
            } else if (Character.isUpperCase(c) && i > 0 && Character.isLetterOrDigit(chars[i - 1])
                    && chars[i - 1] != '_') {
                sb.append('_').append(c);
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }
}
