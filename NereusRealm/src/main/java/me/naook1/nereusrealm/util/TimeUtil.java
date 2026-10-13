package me.naook1.nereusrealm.util;

import java.util.Locale;

public final class TimeUtil {

    private TimeUtil() {
    }

    /** 3725 -> "1j 2m 5d" (jam, menit, detik). */
    public static String format(long seconds) {
        long s0 = Math.max(0, seconds);
        long h = s0 / 3600;
        long m = (s0 % 3600) / 60;
        long s = s0 % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) {
            sb.append(h).append("j ");
        }
        if (m > 0) {
            sb.append(m).append("m ");
        }
        if (s > 0 || sb.length() == 0) {
            sb.append(s).append("d");
        }
        return sb.toString().trim();
    }

    /** 125 -> "02:05", 3725 -> "1:02:05". */
    public static String clock(long seconds) {
        long s0 = Math.max(0, seconds);
        long h = s0 / 3600;
        long m = (s0 % 3600) / 60;
        long s = s0 % 60;
        if (h > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    /** Milliseconds since an event as "5 menit lalu". */
    public static String ago(long millis) {
        long s = Math.max(0, millis / 1000);
        if (s < 60) {
            return "baru saja";
        }
        long m = s / 60;
        if (m < 60) {
            return m + " menit lalu";
        }
        long h = m / 60;
        if (h < 24) {
            return h + " jam lalu";
        }
        return (h / 24) + " hari lalu";
    }
}
