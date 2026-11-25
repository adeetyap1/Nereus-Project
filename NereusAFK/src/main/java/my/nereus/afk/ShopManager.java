package my.nereus.afk;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Villager penukar + GUI toko (chest GUI, jalan di Java dan Bedrock). */
final class ShopManager implements Listener {

    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override
        public Inventory getInventory() { return inv; }
    }

    private final NereusAFK plugin;
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    ShopManager(NereusAFK plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ villager

    Villager spawnShop(Location at) {
        return at.getWorld().spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setProfession(Villager.Profession.CLERIC);
            v.customName(Text.color(plugin.getConfig().getString("shop.villager-name", "&6Penukar Fragmen")));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(plugin.shopKey(), PersistentDataType.BYTE, (byte) 1);
        });
    }

    boolean isShop(Entity e) {
        return e.getPersistentDataContainer().has(plugin.shopKey(), PersistentDataType.BYTE);
    }

    /** Hapus villager toko terdekat (maks 5 blok). */
    boolean removeNearest(Location l) {
        Entity best = null;
        double bd = 25;
        for (Entity e : l.getWorld().getNearbyEntities(l, 5, 5, 5, this::isShop)) {
            double d = e.getLocation().distanceSquared(l);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return false;
        best.remove();
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!isShop(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        open(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (isShop(e.getEntity())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ GUI

    private ConfigurationSection items() {
        return plugin.getConfig().getConfigurationSection("shop.items");
    }

    void open(Player p) {
        ConfigurationSection items = items();
        int n = items == null ? 0 : items.getKeys(false).size();
        int rows = Math.min(6, Math.max(2, (n + 8) / 9 + 1));
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, rows * 9, Text.color(plugin.getConfig().getString("shop.title", "Toko")));
        h.inv = inv;
        render(p, inv);
        p.openInventory(inv);
    }

    private void render(Player p, Inventory inv) {
        inv.clear();
        PlayerData d = plugin.store().get(p.getUniqueId());
        ConfigurationSection items = items();
        int slot = 0;
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection s = items.getConfigurationSection(id);
                if (s == null || slot >= inv.getSize() - 9) continue;
                Material m = Material.matchMaterial(s.getString("material", "STONE"));
                ItemStack it = new ItemStack(m == null ? Material.STONE : m);
                ItemMeta meta = it.getItemMeta();
                meta.displayName(Text.item(s.getString("name", id)));
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                for (String l : s.getStringList("lore")) lore.add(Text.item(l));
                int cost = s.getInt("cost", 1);
                int limit = s.getInt("daily-limit", 0);
                lore.add(Text.item(""));
                lore.add(Text.item("&6Harga: &f" + cost + " fragmen"));
                if (limit > 0) lore.add(Text.item("&7Hari ini: &f" + d.bought.getOrDefault(id, 0) + "&7/" + limit));
                lore.add(Text.item(d.fragments >= cost ? "&aKlik untuk menukar" : "&cFragmen belum cukup"));
                meta.lore(lore);
                it.setItemMeta(meta);
                inv.setItem(slot++, it);
            }
        }
        ItemStack info = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta im = info.getItemMeta();
        im.displayName(Text.item("&6Fragmen kamu: &f" + d.fragments));
        im.lore(List.of(Text.item("&7Dapatkan dengan berdiri di zona AFK.")));
        info.setItemMeta(im);
        inv.setItem(inv.getSize() - 5, info);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getInt("shop.click-cooldown-ticks", 5) * 50L;
        if (now - lastClick.getOrDefault(p.getUniqueId(), 0L) < cd) return;
        lastClick.put(p.getUniqueId(), now);

        ConfigurationSection items = items();
        if (items == null) return;
        int idx = 0;
        for (String id : items.getKeys(false)) {
            if (idx++ != e.getSlot()) continue;
            ConfigurationSection s = items.getConfigurationSection(id);
            if (s != null) buy(p, id, s, e.getView().getTopInventory());
            return;
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void buy(Player p, String id, ConfigurationSection s, Inventory inv) {
        PlayerData d = plugin.store().get(p.getUniqueId());
        int cost = Math.max(1, s.getInt("cost", 1));
        int limit = s.getInt("daily-limit", 0);
        if (d.fragments < cost) {
            plugin.tell(p, "not-enough", "cost", String.valueOf(cost), "fragments", String.valueOf(d.fragments));
            return;
        }
        if (limit > 0 && d.bought.getOrDefault(id, 0) >= limit) {
            plugin.tell(p, "daily-limit");
            return;
        }
        d.fragments -= cost;
        d.bought.merge(id, 1, Integer::sum);
        plugin.store().markDirty();
        for (String cmd : s.getStringList("commands")) {
            String c = cmd.replace("%player%", p.getName());
            if (c.startsWith("/")) c = c.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
        }
        plugin.tell(p, "purchased", "item", s.getString("name", id), "cost", String.valueOf(cost));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
        render(p, inv);
    }
}
