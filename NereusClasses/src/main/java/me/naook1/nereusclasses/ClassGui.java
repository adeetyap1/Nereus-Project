package me.naook1.nereusclasses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** class / weapon menu. */
final class ClassGui implements Listener {
    private final NereusClasses pl;

    ClassGui(NereusClasses pl) {
        this.pl = pl;
    }

    static final class Holder implements InventoryHolder {
        String cls;
        final List<String> keys = new ArrayList<>();
        final List<String> classIds = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(NereusClasses.color(name));
        if (lore != null) {
            List<String> l = new ArrayList<>();
            for (String s : lore) {
                l.add(NereusClasses.color(s));
            }
            meta.setLore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    void open(Player p, String cls, int page) {
        Holder h = new Holder();
        h.cls = cls;
        h.inv = Bukkit.createInventory(h, 54, NereusClasses.color(pl.getConfig().getString("menu.title", "&8Classes")));
        int slot = 0;
        for (Map.Entry<String, String> e : pl.classNames.entrySet()) {
            if (slot > 8) {
                break;
            }
            boolean ok = pl.canUse(p, e.getKey());
            List<String> lore = new ArrayList<>();
            lore.add(ok ? "&aTerbuka" : "&cTerkunci untuk rank kamu");
            lore.add("&7Klik untuk lihat senjata");
            ItemStack it = named(e.getKey().equals(cls) ? Material.ENCHANTED_BOOK : Material.BOOK, e.getValue(), lore);
            h.inv.setItem(slot, it);
            h.classIds.add(e.getKey());
            slot++;
        }
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 9; i < 18; i++) {
            h.inv.setItem(i, pane);
        }
        int s = 18;
        for (NereusClasses.Gear g : pl.gear.values()) {
            if (cls != null && !g.cls.equals(cls)) {
                continue;
            }
            if (s >= 54) {
                break;
            }
            ItemStack it = pl.giveItem(g.key);
            if (it == null) {
                continue;
            }
            ItemMeta m = it.getItemMeta();
            List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
            lore.add("");
            for (Model.Line l : g.slots) {
                lore.add("§8• §7" + NereusClasses.skillName(l));
            }
            for (Model.Line l : g.swing) {
                lore.add("§8• §7" + NereusClasses.skillName(l) + " §8(serang)");
            }
            lore.add(pl.canUse(p, g.cls) ? "§e▶ Klik untuk ambil" : "§c✖ Terkunci untuk rank kamu");
            m.setLore(lore);
            it.setItemMeta(m);
            h.inv.setItem(s, it);
            h.keys.add(g.key);
            s++;
        }
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        int s = e.getRawSlot();
        if (s >= 0 && s < 9) {
            if (s < h.classIds.size()) {
                open(p, h.classIds.get(s), 0);
            }
            return;
        }
        if (s >= 18 && s < 54) {
            int idx = s - 18;
            if (idx >= h.keys.size()) {
                return;
            }
            String key = h.keys.get(idx);
            NereusClasses.Gear g = pl.gear.get(key);
            if (g == null || !pl.canUse(p, g.cls)) {
                p.sendMessage("§cRank kamu belum membuka class ini.");
                return;
            }
            ItemStack it = pl.giveItem(key);
            if (it != null) {
                p.getInventory().addItem(it).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
                p.sendMessage("§aKamu mendapat " + NereusClasses.color(pl.items.get(key).name()));
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
