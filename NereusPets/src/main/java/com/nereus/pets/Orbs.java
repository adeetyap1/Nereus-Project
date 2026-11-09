package com.nereus.pets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Pembuat dan pembaca item Pet Orb (bola salju bertanda). */
public final class Orbs {
    private final NereusPets plugin;

    public Orbs(NereusPets plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(String tier, int amount) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("orbs." + tier);
        if (s == null) {
            return null;
        }
        ItemStack item = new ItemStack(Material.SNOWBALL, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(NereusPets.LEGACY.deserialize(s.getString("name", "&bPet Orb"))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : s.getStringList("lore")) {
            lore.add(NereusPets.LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(plugin.orbKey(), PersistentDataType.STRING, tier);
        item.setItemMeta(meta);
        return item;
    }

    /** Nama tier jika item adalah Pet Orb, selain itu null. */
    public String tierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(plugin.orbKey(), PersistentDataType.STRING);
    }

    public double multiplier(String tier) {
        return plugin.getConfig().getDouble("orbs." + tier + ".multiplier", 1.0);
    }

    public String displayName(String tier) {
        return plugin.getConfig().getString("orbs." + tier + ".name", tier);
    }

    public java.util.Set<String> tiers() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("orbs");
        return s == null ? java.util.Set.of() : s.getKeys(false);
    }
}
