package com.nereus.rpg.guild;

import com.nereus.rpg.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tampilan dialog berbasis inventori (pemain Java): buku di tengah atas = ucapan NPC, tombol di baris bawah. */
final class GuiView {
    private GuiView() {
    }

    static final class Holder implements InventoryHolder {
        final long token;
        final Map<Integer, Integer> slots = new HashMap<>();
        Inventory inv;

        Holder(long token) {
            this.token = token;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    static void show(Player p, long token, Screen screen) {
        Holder h = new Holder(token);
        Inventory inv = Bukkit.createInventory(h, 27, Text.c(Text.strip(screen.title()).isEmpty() ? " " : screen.title()));
        h.inv = inv;
        ItemStack pane = Text.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, pane);
        }
        List<String> lore = new ArrayList<>();
        for (String l : screen.lines()) {
            if (l.isEmpty()) {
                lore.add(" ");
            } else {
                lore.addAll(wrap(l, 40));
            }
        }
        inv.setItem(4, Text.item(Material.WRITTEN_BOOK, screen.title(), lore));
        int n = Math.min(7, screen.buttons().size());
        int start = 19 + (7 - n) / 2;
        for (int i = 0; i < n; i++) {
            Screen.Button b = screen.buttons().get(i);
            List<String> tip = new ArrayList<>();
            if (!b.tooltip().isEmpty()) {
                tip.add("&7" + b.tooltip());
            }
            inv.setItem(start + i, Text.item(icon(b.action()), b.label(), tip));
            h.slots.put(start + i, i);
        }
        p.openInventory(inv);
    }

    private static Material icon(String action) {
        if (action.equals("register")) {
            return Material.EMERALD;
        }
        if (action.equals("back")) {
            return Material.ARROW;
        }
        if (action.equals("menu")) {
            return Material.CHEST;
        }
        if (action.equals("close")) {
            return Material.BARRIER;
        }
        return Material.PAPER;
    }

    /** Bungkus teks per kata; tidak menghitung kode warna; warna terakhir dilanjutkan ke baris berikut. */
    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int visible = 0;
        String color = "";
        for (String word : text.split(" ")) {
            int wl = Text.strip(word).length();
            if (visible > 0 && visible + 1 + wl > width) {
                out.add(line.toString());
                line = new StringBuilder(color);
                visible = 0;
            }
            if (visible > 0) {
                line.append(' ');
                visible++;
            }
            line.append(word);
            visible += wl;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]").matcher(word);
            while (m.find()) {
                color = m.group().equalsIgnoreCase("&r") ? "" : m.group();
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
