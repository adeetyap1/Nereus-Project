package me.naook1.nereusthrone.util;

public final class TimeUtil {

    private TimeUtil() {
    }

    /** 3725 -> "1j 2m 5d" (jam, menit, detik). */
    public static String format(long seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
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
}
