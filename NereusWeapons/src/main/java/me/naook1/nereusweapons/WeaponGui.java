package me.naook1.nereusweapons;

import java.util.ArrayList;
import java.util.List;
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

public final class WeaponGui implements Listener {

    private static final int PAGE = 45;
    private final NereusWeapons plugin;

    WeaponGui(NereusWeapons plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        int page;
        final List<NereusWeapons.Weapon> shown = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    void open(Player p, int page) {
        Holder h = new Holder();
        h.shown.addAll(plugin.all().values());
        int pages = Math.max(1, (h.shown.size() + PAGE - 1) / PAGE);
        h.page = Math.max(0, Math.min(page, pages - 1));
        h.inv = Bukkit.createInventory(h, 54, NereusWeapons.color(plugin.getConfig().getString("menu.title", "&8Weapons")));
        boolean admin = p.hasPermission("nereusweapons.admin");
        for (int i = 0; i < PAGE; i++) {
            int idx = h.page * PAGE + i;
            if (idx >= h.shown.size()) {
                break;
            }
            NereusWeapons.Weapon w = h.shown.get(idx);
            boolean ok = plugin.canUse(p, w);
            ItemStack it;
            if (ok) {
                it = plugin.create(w);
                ItemMeta m = it.getItemMeta();
                List<String> lore = new ArrayList<>(m.getLore() == null ? List.of() : m.getLore());
                lore.add(plugin.has(p, w) && !admin ? "§e▶ Kamu sudah punya" : "§a▶ Klik untuk ambil");
                if (admin) {
                    lore.add("§8Permission: " + w.permission());
                }
                m.setLore(lore);
                it.setItemMeta(m);
            } else {
                it = new ItemStack(Material.GRAY_DYE);
                ItemMeta m = it.getItemMeta();
                m.setDisplayName("§7" + w.name().replaceAll("§.", ""));
                m.setLore(List.of("§c✖ Terkunci untuk rank kamu"));
                it.setItemMeta(m);
            }
            h.inv.setItem(i, it);
        }
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.setDisplayName(" ");
        pane.setItemMeta(pm);
        for (int i = 45; i < 54; i++) {
            h.inv.setItem(i, pane);
        }
        h.inv.setItem(45, nav("§eSebelumnya", pages, h.page));
        h.inv.setItem(53, nav("§eBerikutnya", pages, h.page));
        p.openInventory(h.inv);
    }

    private ItemStack nav(String name, int pages, int page) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        m.setLore(List.of("§7Halaman " + (page + 1) + "/" + pages));
        it.setItemMeta(m);
        return it;
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
        if (s == 45) {
            open(p, h.page - 1);
            return;
        }
        if (s == 53) {
            open(p, h.page + 1);
            return;
        }
        if (s >= PAGE) {
            return;
        }
        int idx = h.page * PAGE + s;
        if (idx >= h.shown.size()) {
            return;
        }
        NereusWeapons.Weapon w = h.shown.get(idx);
        if (!plugin.canUse(p, w)) {
            p.sendMessage(plugin.msg("locked", "Rank kamu belum bisa memakai senjata ini."));
            return;
        }
        if (plugin.has(p, w) && !p.hasPermission("nereusweapons.admin")) {
            p.sendMessage(plugin.msg("have", "Kamu sudah punya senjata itu."));
            return;
        }
        p.getInventory().addItem(plugin.create(w)).values().forEach(it -> p.getWorld().dropItem(p.getLocation(), it));
        p.sendMessage(plugin.msg("claimed", "Kamu mendapat {name}.").replace("{name}", w.name()));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
