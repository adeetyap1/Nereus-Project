package com.nereus.pets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Telur gacha dan permen XP. Bisa dipakai di crate lewat /pets give. */
public final class Items {
    private final NereusPets plugin;
    private final PetManager mgr;

    public Items(NereusPets plugin, PetManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    private ItemStack build(ConfigurationSection s, Material def, String kind, int amount) {
        Material mat = Material.matchMaterial(s.getString("material", def.name()));
        ItemStack item = new ItemStack(mat == null ? def : mat, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(NereusPets.LEGACY.deserialize(s.getString("name", kind)).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String l : s.getStringList("lore")) {
            lore.add(NereusPets.LEGACY.deserialize(l).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        if (s.getBoolean("glow", false)) {
            meta.setEnchantmentGlintOverride(true);
        }
        String model = s.getString("item-model");
        if (model != null && !model.isBlank()) {
            NamespacedKey key = NamespacedKey.fromString(model);
            if (key != null) {
                meta.setItemModel(key);
            }
        }
        meta.getPersistentDataContainer().set(plugin.itemKey(), PersistentDataType.STRING, kind);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack egg(String tier, int amount) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("eggs." + tier);
        return s == null ? null : build(s, Material.TURTLE_EGG, "egg:" + tier, amount);
    }

    public ItemStack candy(String id, int amount) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("candies." + id);
        return s == null ? null : build(s, Material.SUGAR, "candy:" + id, amount);
    }

    /** "egg:tier" atau "candy:id" jika item milik plugin, selain itu null. */
    public String kind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(plugin.itemKey(), PersistentDataType.STRING);
    }

    public Set<String> eggTiers() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("eggs");
        return s == null ? Set.of() : s.getKeys(false);
    }

    public Set<String> candyIds() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("candies");
        return s == null ? Set.of() : s.getKeys(false);
    }

    public Rarity roll(String tier) {
        ConfigurationSection w = plugin.getConfig().getConfigurationSection("eggs." + tier + ".weights");
        if (w == null) {
            return Rarity.COMMON;
        }
        double total = 0;
        for (String k : w.getKeys(false)) {
            total += Math.max(0, w.getDouble(k));
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (String k : w.getKeys(false)) {
            r -= Math.max(0, w.getDouble(k));
            if (r <= 0) {
                return Rarity.parse(k);
            }
        }
        return Rarity.COMMON;
    }

    /** Hasilkan pet dari telur tier tertentu, atau null jika tidak ada jenis yang cocok. */
    public PetData hatch(String tier) {
        Rarity target = roll(tier);
        List<Species> pool = new ArrayList<>();
        for (Rarity r = target; pool.isEmpty(); r = Rarity.values()[Math.max(0, r.ordinal() - 1)]) {
            for (EntityType t : mgr.speciesTypes()) {
                Species sp = mgr.species(t);
                if (sp.rarity() == r && sp.egg()) {
                    pool.add(sp);
                }
            }
            if (r.ordinal() == 0) {
                break;
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        Species pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        int level = plugin.getConfig().getInt("eggs." + tier + ".level", 1);
        return mgr.createPet(pick.type(), level);
    }

    public double candyXp(String id) {
        return plugin.getConfig().getDouble("candies." + id + ".xp", 100);
    }

    public int candyLevels(String id) {
        return plugin.getConfig().getInt("candies." + id + ".levels", 0);
    }
}
