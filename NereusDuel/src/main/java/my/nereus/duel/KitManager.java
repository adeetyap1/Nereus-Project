package my.nereus.duel;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

final class KitManager {

    static final class Kit {
        final String id;
        final String name;
        final Material icon;
        ItemStack helmet, chest, legs, boots, offhand;
        final Map<Integer, ItemStack> items = new LinkedHashMap<>();

        Kit(String id, String name, Material icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }

        /** Mengganti seluruh inventory pemain dengan kit ini (inventory asli sudah disimpan sebelumnya). */
        void give(Player p) {
            var inv = p.getInventory();
            inv.clear();
            inv.setHelmet(copy(helmet));
            inv.setChestplate(copy(chest));
            inv.setLeggings(copy(legs));
            inv.setBoots(copy(boots));
            inv.setItemInOffHand(copy(offhand));
            for (Map.Entry<Integer, ItemStack> e : items.entrySet()) {
                if (e.getKey() >= 0 && e.getKey() < 36) inv.setItem(e.getKey(), e.getValue().clone());
            }
            inv.setHeldItemSlot(0);
        }

        private static ItemStack copy(ItemStack i) {
            return i == null ? null : i.clone();
        }
    }

    private final NereusDuel plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    KitManager(NereusDuel plugin) {
        this.plugin = plugin;
        load();
    }

    void load() {
        kits.clear();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("kits");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Material icon = Material.matchMaterial(s.getString("icon", "IRON_SWORD"));
                Kit k = new Kit(id.toLowerCase(Locale.ROOT), s.getString("name", id), icon == null ? Material.IRON_SWORD : icon);
                k.helmet = item(s.getConfigurationSection("helmet"));
                k.chest = item(s.getConfigurationSection("chestplate"));
                k.legs = item(s.getConfigurationSection("leggings"));
                k.boots = item(s.getConfigurationSection("boots"));
                k.offhand = item(s.getConfigurationSection("offhand"));
                ConfigurationSection items = s.getConfigurationSection("items");
                if (items != null) {
                    for (String slot : items.getKeys(false)) {
                        ItemStack it = item(items.getConfigurationSection(slot));
                        if (it != null) k.items.put(Integer.parseInt(slot), it);
                    }
                }
                kits.put(k.id, k);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Kit '" + id + "' tidak valid: " + e);
            }
        }
    }

    private ItemStack item(ConfigurationSection s) {
        if (s == null) return null;
        Material m = Material.matchMaterial(s.getString("material", ""));
        if (m == null || m.isAir()) return null;
        ItemStack it = new ItemStack(m, Math.max(1, s.getInt("amount", 1)));
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        if (s.contains("name")) meta.displayName(Text.item(s.getString("name")));
        meta.setUnbreakable(true);
        ConfigurationSection en = s.getConfigurationSection("enchants");
        if (en != null) {
            for (String k : en.getKeys(false)) {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(k.toLowerCase(Locale.ROOT)));
                if (e != null) meta.addEnchant(e, Math.max(1, en.getInt(k)), true);
            }
        }
        it.setItemMeta(meta);
        return it;
    }

    Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    Iterable<Kit> all() {
        return kits.values();
    }
}
