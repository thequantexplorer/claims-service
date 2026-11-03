package com.northwind.claims.money;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void splitEvenlyAddsBackUpToTheTotal() {
        long[] parts = Money.splitEvenly(1_000_00, 3);
        assertArrayEquals(new long[] {33_334, 33_333, 33_333}, parts);
        assertEquals(1_000_00, Arrays.stream(parts).sum());
    }

    @Test
    void formatsCents() {
        assertEquals("1234.05", Money.format(123_405));
    }
}
