package com.nereus.rpg;

import java.util.Locale;

/** Status keanggotaan Nerus Guild. */
public enum GuildStatus {
    UNREGISTERED,
    REGISTERED,
    REVOKED;

    public static GuildStatus parse(String s) {
        if (s != null) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // jatuh ke bawah
            }
        }
        return UNREGISTERED;
    }
}
