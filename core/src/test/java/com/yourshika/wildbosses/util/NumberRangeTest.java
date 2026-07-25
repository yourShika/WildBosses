package com.yourshika.wildbosses.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumberRangeTest {

    @Test
    void rollStaysWithinBounds() {
        NumberRange r = new NumberRange(1, 5);
        for (int i = 0; i < 1000; i++) {
            int v = r.roll();
            assertTrue(v >= 1 && v <= 5, "roll out of range: " + v);
        }
    }

    @Test
    void rollSingleValue() {
        assertEquals(7, new NumberRange(7, 7).roll());
    }

    @Test
    void rollDoesNotOverflowAtIntegerMax() {
        NumberRange r = new NumberRange(Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
        for (int i = 0; i < 200; i++) {
            int v = r.roll();
            assertTrue(v == Integer.MAX_VALUE - 1 || v == Integer.MAX_VALUE, "unexpected: " + v);
        }
    }

    @Test
    void constructorOrdersMinMax() {
        NumberRange r = new NumberRange(9, 2);
        assertEquals(2, r.min());
        assertEquals(9, r.max());
    }

    @Test
    void parseRange() {
        NumberRange r = NumberRange.parse("3-6", null);
        assertEquals(3, r.min());
        assertEquals(6, r.max());
    }

    @Test
    void parseSingle() {
        NumberRange r = NumberRange.parse("5", null);
        assertEquals(5, r.min());
        assertEquals(5, r.max());
    }

    @Test
    void parseBadInputReturnsFallback() {
        NumberRange fallback = new NumberRange(2, 2);
        assertSame(fallback, NumberRange.parse("abc", fallback));
        assertSame(fallback, NumberRange.parse(null, fallback));
    }
}
