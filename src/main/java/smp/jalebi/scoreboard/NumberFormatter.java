package smp.jalebi.scoreboard;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumberFormatter {
    private static final String[] SUFFIX = {"", "K", "M", "B", "T", "Q"};

    private NumberFormatter() {}

    /** 1000 -> 1K, 2918 -> 2.91K, 1000000 -> 1M (truncates, never rounds up). */
    public static String shorten(double value) {
        boolean neg = value < 0;
        double v = Math.abs(value);
        int idx = 0;
        while (v >= 1000 && idx < SUFFIX.length - 1) {
            v /= 1000;
            idx++;
        }
        BigDecimal bd = BigDecimal.valueOf(v).setScale(2, RoundingMode.DOWN).stripTrailingZeros();
        String s = bd.toPlainString();
        return (neg ? "-" : "") + s + SUFFIX[idx];
    }

    public static String playtime(long seconds) {
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
