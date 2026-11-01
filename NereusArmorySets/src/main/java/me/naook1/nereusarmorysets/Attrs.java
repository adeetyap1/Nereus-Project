package me.naook1.nereusarmorysets;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

/** Pencarian atribut berdasarkan nama pendek (mis. "max_health"). */
public final class Attrs {
    private Attrs() {}

    /** Mencoba prefix generic. / player. / tanpa prefix supaya aman antar versi. */
    public static Attribute resolve(String name) {
        for (String prefix : new String[] {"generic.", "player.", ""}) {
            Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(prefix + name));
            if (a != null) return a;
        }
        return null;
    }
}
