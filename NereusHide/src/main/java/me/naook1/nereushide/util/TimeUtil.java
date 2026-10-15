package me.naook1.nereushide.util;

import me.naook1.nereushide.NereusHide;

public final class TimeUtil {

    private TimeUtil() {
    }

    /** Remaining time as "1 hari 3 jam", "2 jam 5 menit", "4 menit 10 detik". */
    public static String left(NereusHide plugin, long millis) {
        var m = plugin.messages();
        long s = Math.max(0, millis / 1000);
        long d = s / 86_400;
        long h = (s % 86_400) / 3_600;
        long min = (s % 3_600) / 60;
        long sec = s % 60;
        if (d > 0) {
            return d + " " + m.raw("time.day") + (h > 0 ? " " + h + " " + m.raw("time.hour") : "");
        }
        if (h > 0) {
            return h + " " + m.raw("time.hour") + (min > 0 ? " " + min + " " + m.raw("time.minute") : "");
        }
        if (min > 0) {
            return min + " " + m.raw("time.minute") + (sec > 0 ? " " + sec + " " + m.raw("time.second") : "");
        }
        return sec + " " + m.raw("time.second");
    }

    /** "5 menit lalu" style, in Indonesian. */
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
