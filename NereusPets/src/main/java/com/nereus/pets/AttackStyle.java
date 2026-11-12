package com.nereus.pets;

import java.util.Locale;

/** Cara pet menyerang, meniru serangan asli mobnya. */
public enum AttackStyle {
    MELEE("Cakar / pukul"),
    ARROW("Panah"),
    FIREBALL("Bola api"),
    WITHER_SKULL("Tengkorak Wither"),
    SONIC_BOOM("Ledakan sonik"),
    BREATH("Napas naga (area)"),
    BEAM("Sinar"),
    POTION("Ramuan sihir"),
    TRIDENT("Lemparan trisula"),
    EXPLODE("Ledakan (area)");

    public final String label;

    AttackStyle(String label) {
        this.label = label;
    }

    public static AttackStyle parse(String s) {
        if (s == null) {
            return MELEE;
        }
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return MELEE;
        }
    }
}
