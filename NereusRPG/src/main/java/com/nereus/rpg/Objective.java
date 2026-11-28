package com.nereus.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Satu tugas: bunuh, hancurkan, kumpulkan, capai rank, bicara, atau manual.
 * target boleh satu nama atau beberapa (dipisah koma); format daftar seperti ['A', 'B'] dari impor lama juga dibaca.
 */
public record Objective(Type type, String target, int amount) {
    public enum Type { KILL, BREAK, GET, RANK, TALK, MANUAL, NONE }

    public static Objective parse(String type, String target, int amount) {
        Type t;
        try {
            t = Type.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            t = Type.NONE;
        }
        return new Objective(t, normalize(target), Math.max(1, amount));
    }

    /** "['A', 'B']" / "A, B" / "A" -> "A,B". */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("[", "").replace("]", "").replace("\"", "").replace("'", "");
        List<String> parts = new ArrayList<>();
        for (String p : s.split(",")) {
            String x = p.trim();
            if (!x.isEmpty()) {
                parts.add(x);
            }
        }
        return String.join(",", parts);
    }

    public List<String> targets() {
        List<String> out = new ArrayList<>();
        for (String p : target.split(",")) {
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    /** Cocok dengan salah satu target (tanpa peduli huruf besar/kecil). */
    public boolean matches(String what) {
        if (what == null) {
            return false;
        }
        for (String p : target.split(",")) {
            if (p.equalsIgnoreCase(what)) {
                return true;
            }
        }
        return false;
    }

    private String label() {
        List<String> t = targets();
        if (t.isEmpty()) {
            return "";
        }
        if (t.size() == 1) {
            return Text.pretty(t.get(0));
        }
        return Text.pretty(t.get(0)) + " (+" + (t.size() - 1) + " jenis lain)";
    }

    public String describe() {
        String what = label();
        return switch (type) {
            case KILL -> "Bunuh " + amount + " " + what;
            case BREAK -> "Hancurkan " + amount + " " + what;
            case GET -> "Kumpulkan dan serahkan " + amount + " " + what;
            case RANK -> "Capai rank " + target.toUpperCase(Locale.ROOT);
            case TALK -> "Kunjungi " + what;
            case MANUAL -> "Diselesaikan oleh staff";
            case NONE -> "Tanpa tugas";
        };
    }
}
