package com.nereus.rpg;

import java.util.Locale;

/** Satu tugas: bunuh, hancurkan, kumpulkan, capai rank, bicara, atau manual. */
public record Objective(Type type, String target, int amount) {
    public enum Type { KILL, BREAK, GET, RANK, TALK, MANUAL, NONE }

    public static Objective parse(String type, String target, int amount) {
        Type t;
        try {
            t = Type.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            t = Type.NONE;
        }
        return new Objective(t, target == null ? "" : target, Math.max(1, amount));
    }

    public String describe() {
        String what = Text.pretty(target);
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
