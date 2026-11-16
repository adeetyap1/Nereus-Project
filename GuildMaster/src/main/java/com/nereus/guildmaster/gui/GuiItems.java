package com.nereus.guildmaster.gui;

import com.nereus.guildmaster.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Membuat ItemStack dari bagian YAML dengan placeholder. */
final class GuiItems {
    private GuiItems() {
    }

    static Material mat(String name, Material def) {
        if (name == null) {
            return def;
        }
        Material m = Material.matchMaterial(name);
        return m == null ? def : m;
    }

    /** Baris lore: %kunci% biasa diganti; baris yang hanya berisi %kunci% multi diganti banyak baris. */
    static List<String> lore(List<String> lines, Map<String, String> vars, Map<String, List<String>> multi) {
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            String t = l.trim();
            boolean expanded = false;
            for (Map.Entry<String, List<String>> e : multi.entrySet()) {
                if (t.equals("%" + e.getKey() + "%")) {
                    out.addAll(e.getValue());
                    expanded = true;
                    break;
                }
            }
            if (!expanded) {
                out.add(Text.fill(l, vars));
            }
        }
        return out;
    }

    static ItemStack make(Material mat, String name, List<String> lore, boolean glow, int amount) {
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.c(name));
        if (!lore.isEmpty()) {
            meta.lore(Text.c(lore));
        }
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack build(ConfigurationSection s, Map<String, String> vars, Map<String, List<String>> multi, Player viewer) {
        if (s == null) {
            return null;
        }
        Material mat = mat(s.getString("material"), Material.PAPER);
        ItemStack item = make(mat, Text.fill(s.getString("name", " "), vars),
                lore(s.getStringList("lore"), vars, multi), s.getBoolean("glow", false), s.getInt("amount", 1));
        if (mat == Material.PLAYER_HEAD && s.getBoolean("viewer-head", false) && viewer != null
                && item.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(viewer);
            item.setItemMeta(sm);
        }
        return item;
    }

    static ItemStack build(ConfigurationSection s, Map<String, String> vars, Player viewer) {
        return build(s, vars, Map.of(), viewer);
    }

    static ItemStack filler(ConfigurationSection root) {
        ConfigurationSection f = root == null ? null : root.getConfigurationSection("filler");
        if (f == null || !f.getBoolean("enabled", true)) {
            return null;
        }
        return make(mat(f.getString("material"), Material.GRAY_STAINED_GLASS_PANE), f.getString("name", " "), List.of(), false, 1);
    }
}
