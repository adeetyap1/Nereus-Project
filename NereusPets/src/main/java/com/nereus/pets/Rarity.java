package com.nereus.pets;

import java.util.Locale;

/** Kelangkaan pet. Mengalikan stat dasar. */
public enum Rarity {
    COMMON("Biasa", "&f", 1.00),
    UNCOMMON("Tidak Biasa", "&a", 1.10),
    RARE("Langka", "&9", 1.25),
    EPIC("Epik", "&5", 1.45),
    LEGENDARY("Legendaris", "&6", 1.70);

    public final String label;
    public final String color;
    public final double statMult;

    Rarity(String label, String color, double statMult) {
        this.label = label;
        this.color = color;
        this.statMult = statMult;
    }

    public static Rarity parse(String s) {
        if (s == null) {
            return COMMON;
        }
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return COMMON;
        }
    }
}
