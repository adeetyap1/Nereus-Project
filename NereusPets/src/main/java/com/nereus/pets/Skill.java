package com.nereus.pets;

import java.util.Locale;

/** Skill pasif pet. Aktif setelah pet mencapai level skill jenisnya. */
public enum Skill {
    NONE("-", "Tidak ada"),
    CRIT("Serangan Kritis", "25% peluang damage x2"),
    HEAL("Penyembuh", "Memulihkan pemain saat HP di bawah 70%"),
    SLOW("Perlambat", "30% peluang memperlambat musuh"),
    POISON("Racun", "30% peluang meracuni musuh"),
    IGNITE("Api", "30% peluang membakar musuh"),
    THUNDER("Petir", "15% peluang sambaran petir, damage x1.5");

    public final String label;
    public final String description;

    Skill(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public static Skill parse(String s) {
        if (s == null) {
            return NONE;
        }
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
