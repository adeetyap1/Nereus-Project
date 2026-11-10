package com.nereus.rpg;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Pembantu teks dan item. Warna memakai kode &. */
public final class Text {
    public static final LegacyComponentSerializer L = LegacyComponentSerializer.legacyAmpersand();
    public static final String PREFIX = "&8[&6Nereus&8] &7";

    private Text() {
    }

    public static Component c(String s) {
        return L.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static String strip(String s) {
        return s.replaceAll("(?i)&[0-9a-fk-or]", "");
    }

    public static String pretty(String s) {
        return s.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    public static String time(long ms) {
        long sec = Math.max(0, ms / 1000);
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) {
            return h + "j " + m + "m";
        }
        return m > 0 ? m + "m " + s + "d" : s + "d";
    }

    public static String bar(double cur, double max, int len, String on, String off) {
        int filled = max <= 0 ? 0 : (int) Math.round(Math.min(1.0, cur / max) * len);
        StringBuilder sb = new StringBuilder(on);
        for (int i = 0; i < len; i++) {
            if (i == filled) {
                sb.append(off);
            }
            sb.append('|');
        }
        return sb.toString();
    }

    public static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) {
                lines.add(c(l));
            }
            meta.lore(lines);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack item(Material m, String name, String... lore) {
        return item(m, name, Arrays.asList(lore));
    }

    public static ItemStack glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }
}
