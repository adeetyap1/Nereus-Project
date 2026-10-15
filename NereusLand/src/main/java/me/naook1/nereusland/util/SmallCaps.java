package me.naook1.nereusland.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts plain text to small caps. Color codes, {placeholders}, %papi% placeholders
 * and MiniMessage tags are left untouched.
 * Developer: naook1/adityap
 */
public final class SmallCaps {

    private static final String MAP = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private static final Pattern TOKEN = Pattern.compile(
            "&#[0-9a-fA-F]{6}"
                    + "|[&§][0-9a-fA-FklmnorKLMNORxX]"
                    + "|\\{[^}\\s]*\\}"
                    + "|%[^%\\s]*%"
                    + "|</?[a-zA-Z#!][^>\\s]*>"
                    + "|\\[mm\\]");

    private SmallCaps() {
    }

    public static String convert(String in) {
        if (in == null || in.isEmpty()) {
            return in;
        }
        Matcher m = TOKEN.matcher(in);
        StringBuilder sb = new StringBuilder(in.length());
        int last = 0;
        while (m.find()) {
            map(sb, in, last, m.start());
            sb.append(m.group());
            last = m.end();
        }
        map(sb, in, last, in.length());
        return sb.toString();
    }

    private static void map(StringBuilder sb, String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') {
                sb.append(MAP.charAt(c - 'a'));
            } else if (c >= 'A' && c <= 'Z') {
                sb.append(MAP.charAt(c - 'A'));
            } else {
                sb.append(c);
            }
        }
    }
}
