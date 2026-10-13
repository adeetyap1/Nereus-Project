package me.naook1.nereusrealm.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import me.naook1.nereusrealm.hook.PapiHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern CODES = Pattern.compile("&#[0-9a-fA-F]{6}|&[0-9a-fA-FklmnorKLMNOR]");

    private static boolean papi;

    private Text() {
    }

    public static void setPapi(boolean enabled) {
        papi = enabled;
    }

    public static String papi(Player player, String text) {
        if (!papi || player == null || text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            return PapiHook.set(player, text);
        } catch (Throwable t) {
            papi = false;
            return text;
        }
    }

    /** Legacy (&a, &#rrggbb) by default. Prefix the text with [mm] to use MiniMessage (gradients etc). */
    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        if (raw.startsWith("[mm]")) {
            return MM.deserialize(raw.substring(4).stripLeading());
        }
        return LEGACY.deserialize(raw.replace('§', '&'));
    }

    /** For item names/lore: no default italic. */
    public static Component item(String raw) {
        return parse(raw).decoration(TextDecoration.ITALIC, false);
    }

    public static String toLegacy(Component component) {
        return LEGACY.serialize(component);
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static String stripCodes(String s) {
        return s == null ? "" : CODES.matcher(s).replaceAll("");
    }

    public static String fill(String text, String... kv) {
        if (text == null) {
            return "";
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            text = text.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return text;
    }

    /** Removes anything that could inject formatting or placeholders from player input. */
    public static String sanitize(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String s = input.replaceAll("[&§<>%{}\\[\\]]", "").replaceAll("\\s+", " ").trim();
        if (maxLength > 0 && s.length() > maxLength) {
            s = s.substring(0, maxLength);
        }
        return s;
    }

    /** True if the text contains a blocked word (compared without spaces/punctuation, case-insensitive). */
    public static boolean containsBlocked(List<String> words, String text) {
        String flat = flatten(stripCodes(text));
        for (String w : words) {
            String f = flatten(w);
            if (!f.isEmpty() && flat.contains(f)) {
                return true;
            }
        }
        return false;
    }

    private static String flatten(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Word-wraps plain text to lines of at most {@code width} characters. */
    public static List<String> wrap(String text, int width) {
        int w = Math.max(10, width);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String raw : text.split("\\s+")) {
            String word = raw;
            if (word.isEmpty()) {
                continue;
            }
            while (word.length() > w) {
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, w));
                word = word.substring(w);
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > w) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** "&e★★★&8☆☆" for n out of 5. */
    public static String stars(int n) {
        int c = Math.max(0, Math.min(5, n));
        return "&e" + "★".repeat(c) + "&8" + "☆".repeat(5 - c);
    }

    /** Progress bar made of block characters. */
    public static String bar(int filled, int total, String onColor, String offColor) {
        int f = Math.max(0, Math.min(total, filled));
        return onColor + "█".repeat(f) + offColor + "░".repeat(total - f);
    }
}
