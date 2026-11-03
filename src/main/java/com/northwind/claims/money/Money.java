package com.northwind.claims.money;

/** All money is integer cents. */
public final class Money {
    private Money() {}

    /** Splits a total into n parts that differ by at most one cent and add back up to the total. */
    public static long[] splitEvenly(long totalCents, int parts) {
        if (parts <= 0) {
            throw new IllegalArgumentException("parts must be positive");
        }
        long base = totalCents / parts;
        long remainder = totalCents % parts;
        long[] out = new long[parts];
        for (int i = 0; i < parts; i++) {
            out[i] = base + (i < remainder ? 1 : 0);
        }
        return out;
    }

    public static String format(long cents) {
        return String.format("%d.%02d", cents / 100, Math.abs(cents % 100));
    }
}
