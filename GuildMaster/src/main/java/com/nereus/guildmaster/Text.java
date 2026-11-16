package com.nereus.guildmaster;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Utilitas teks: kode warna &amp;, placeholder %kunci%, dan konversi untuk Bedrock. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    public static Component c(String s) {
        return LEGACY.deserialize(s == null ? "" : s).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> c(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) {
            out.add(c(l));
        }
        return out;
    }

    /** Ganti %kunci% dengan nilai. */
    public static String fill(String s, Map<String, String> vars) {
        if (s == null) {
            return "";
        }
        String out = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    public static List<String> fillAll(List<String> lines, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            out.add(fill(l, vars));
        }
        return out;
    }

    /** Buang kode warna &amp;x dan §x. */
    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    /** Kode & jadi § supaya warna tampil di form Bedrock. */
    public static String section(String s) {
        return s == null ? "" : s.replaceAll("(?i)&([0-9a-fk-or])", "§$1");
    }
}
