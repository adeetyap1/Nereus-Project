package me.naook1.nereusfarmprice.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.naook1.nereusfarmprice.NereusFarmPrice;

public final class TimeUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)([wdhms])");

    private TimeUtil() {
    }

    /** "2 jam 15 menit", "4 menit 10 detik", "1 hari 3 jam". */
    public static String left(NereusFarmPrice plugin, long millis) {
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
            return min + " " + m.raw("time.minute") + (sec > 0 && min < 5 ? " " + sec + " " + m.raw("time.second") : "");
        }
        return sec + " " + m.raw("time.second");
    }

    /** "1d12h" / "30m" / "6h" to milliseconds, -1 if invalid. */
    public static long parse(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        String t = input.trim().toLowerCase(Locale.ROOT);
        Matcher m = PART.matcher(t);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) {
                return -1;
            }
            end = m.end();
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "w" -> 604_800L;
                case "d" -> 86_400L;
                case "h" -> 3_600L;
                case "m" -> 60L;
                default -> 1L;
            };
        }
        return end == t.length() && total > 0 ? total * 1000L : -1;
    }
}
