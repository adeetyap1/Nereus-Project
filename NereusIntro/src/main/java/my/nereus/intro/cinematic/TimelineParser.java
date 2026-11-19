package my.nereus.intro.cinematic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Mengubah daftar map dari config.yml menjadi langkah. Langkah yang tidak valid dilewati dengan peringatan. */
public final class TimelineParser {

    private TimelineParser() {}

    public static List<Step> parse(List<? extends Map<?, ?>> raw, Logger log) {
        List<Step> out = new ArrayList<>();
        int n = 0;
        for (Map<?, ?> m : raw) {
            n++;
            try {
                Step s = parseOne(m);
                if (s != null) out.add(s);
                else log.warning("timeline #" + n + ": type tidak dikenal/tidak lengkap, dilewati");
            } catch (RuntimeException e) {
                log.warning("timeline #" + n + ": " + e.getMessage() + ", dilewati");
            }
        }
        return List.copyOf(out);
    }

    private static Step parseOne(Map<?, ?> m) {
        String type = str(m, "type", "").toLowerCase(Locale.ROOT);
        int wait = integer(m, "wait", -1);
        switch (type) {
            case "narrate": {
                String t = str(m, "text", "");
                return t.isEmpty() ? null : new Steps.Narrate(t, wait);
            }
            case "say": {
                String t = str(m, "text", "");
                return t.isEmpty() ? null : new Steps.Say(t, wait);
            }
            case "player": {
                String t = str(m, "text", "");
                return t.isEmpty() ? null : new Steps.PlayerLine(t, wait);
            }
            case "actionbar": {
                String t = str(m, "text", "");
                return t.isEmpty() ? null : new Steps.ActionBar(t, wait);
            }
            case "title":
                return new Steps.TitleStep(str(m, "title", ""), str(m, "subtitle", ""),
                        integer(m, "fade-in", 10), integer(m, "stay", 60), integer(m, "fade-out", 20), wait);
            case "sound": {
                String s = str(m, "sound", "");
                if (s.isEmpty()) return null;
                return new Steps.Sound(s, str(m, "bedrock-sound", ""),
                        (float) dbl(m, "volume", 1.0), (float) dbl(m, "pitch", 1.0), wait < 0 ? 0 : wait);
            }
            case "fade": {
                boolean out = !"in".equalsIgnoreCase(str(m, "mode", "out"));
                return new Steps.Fade(out, Math.max(1, integer(m, "ticks", 40)), wait);
            }
            case "wait":
                return new Steps.Wait(Math.max(0, integer(m, "ticks", 20)));
            case "teleport": {
                String t = str(m, "target", "").toLowerCase(Locale.ROOT);
                if (t.equals("room")) return new Steps.Teleport(false, wait);
                if (t.equals("spawn")) return new Steps.Teleport(true, wait);
                return null;
            }
            case "npc": {
                String a = str(m, "action", "").toLowerCase(Locale.ROOT);
                return switch (a) {
                    case "spawn" -> new Steps.NpcAction(Steps.NpcAction.Kind.SPAWN, wait);
                    case "look" -> new Steps.NpcAction(Steps.NpcAction.Kind.LOOK, wait);
                    case "remove" -> new Steps.NpcAction(Steps.NpcAction.Kind.REMOVE, wait);
                    default -> null;
                };
            }
            default:
                return null;
        }
    }

    private static String str(Map<?, ?> m, String k, String def) {
        Object v = m.get(k);
        return v == null ? def : String.valueOf(v);
    }

    private static int integer(Map<?, ?> m, String k, int def) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("nilai '" + k + "' bukan angka");
        }
    }

    private static double dbl(Map<?, ?> m, String k, double def) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        if (v == null) return def;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("nilai '" + k + "' bukan angka");
        }
    }
}
