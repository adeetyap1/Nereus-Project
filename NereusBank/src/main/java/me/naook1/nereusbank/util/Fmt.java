package me.naook1.nereusbank.util;

import java.util.Locale;

public final class Fmt {

    public static String symbol = "$";

    private Fmt() {
    }

    public static String money(long v) {
        return symbol + String.format(Locale.US, "%,d", v);
    }

    public static String compact(long v) {
        double a = Math.abs((double) v);
        String sign = v < 0 ? "-" : "";
        if (a >= 1_000_000_000) {
            return sign + trim(a / 1_000_000_000) + "B";
        }
        if (a >= 1_000_000) {
            return sign + trim(a / 1_000_000) + "M";
        }
        if (a >= 1_000) {
            return sign + trim(a / 1_000) + "K";
        }
        return String.valueOf(v);
    }

    private static String trim(double d) {
        String s = String.format(Locale.US, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    public static String pct(double d) {
        String s = String.format(Locale.US, "%.2f", d);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + "%";
    }

    /** Parses 1500, 1,500, 2k, 1.5m, 3b. Returns -1 when invalid. */
    public static long parse(String in) {
        if (in == null) {
            return -1;
        }
        String s = in.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "").replace(symbol, "");
        if (s.isEmpty()) {
            return -1;
        }
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') {
            mult = 1_000;
        } else if (last == 'm') {
            mult = 1_000_000;
        } else if (last == 'b') {
            mult = 1_000_000_000;
        }
        if (mult != 1) {
            s = s.substring(0, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s) * mult;
            if (Double.isNaN(v) || Double.isInfinite(v) || v < 0 || v > 9e15) {
                return -1;
            }
            return (long) Math.floor(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String duration(long millis) {
        if (millis <= 0) {
            return "0m";
        }
        long m = millis / 60000;
        long d = m / 1440;
        long h = (m % 1440) / 60;
        if (d > 0) {
            return d + "d " + h + "h";
        }
        if (h > 0) {
            return h + "h " + (m % 60) + "m";
        }
        return Math.max(1, m) + "m";
    }
}
