package me.naook1.nereuscosmetics;

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

public final class CosmeticsGui implements Listener {

    private static final int PAGE = 45;

    private final NereusCosmetics plugin;

    CosmeticsGui(NereusCosmetics plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        Cosmetic.Type filter;
        int page;
        List<Cosmetic> shown = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        it.setItemMeta(meta);
        return it;
    }

    void open(Player p, Cosmetic.Type filter, int page) {
        Holder h = new Holder();
        h.filter = filter;
        for (Cosmetic c : plugin.all().values()) {
            if (filter == null || c.type() == filter) {
                h.shown.add(c);
            }
        }
        int pages = Math.max(1, (h.shown.size() + PAGE - 1) / PAGE);
        h.page = Math.max(0, Math.min(page, pages - 1));
        String title = NereusCosmetics.color(plugin.getConfig().getString("menu.title", "&8Cosmetics"));
        h.inv = Bukkit.createInventory(h, 54, title);
        boolean admin = plugin.isAdmin(p);
        for (int i = 0; i < PAGE; i++) {
            int idx = h.page * PAGE + i;
            if (idx >= h.shown.size()) {
                break;
            }
            Cosmetic c = h.shown.get(idx);
            boolean ok = plugin.canUse(p, c);
            boolean on = c.id().equals(plugin.equippedId(p, c.type()));
            List<String> lore = new ArrayList<>();
            if (!c.desc().isEmpty()) {
                lore.add(c.desc());
            }
            lore.add("§7Tipe: §f" + c.type().name());
            if (on) {
                lore.add("§a▶ Terpasang §7(klik untuk lepas)");
            } else if (ok) {
                lore.add("§e▶ Klik untuk pakai");
            } else {
                lore.add("§c✖ Terkunci untuk rank kamu");
            }
            if (admin) {
                lore.add("§8Permission: " + (c.permission().isEmpty() ? "(semua)" : c.permission()));
            }
            ItemStack it = named(ok ? c.icon() : Material.GRAY_DYE, (ok ? "" : "§7") + c.name(), lore);
            if (on) {
                ItemMeta meta = it.getItemMeta();
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                it.setItemMeta(meta);
            }
            h.inv.setItem(i, it);
        }
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) {
            h.inv.setItem(i, pane);
        }
        h.inv.setItem(45, named(Material.ARROW, "§eSebelumnya", List.of("§7Halaman " + (h.page + 1) + "/" + pages)));
        h.inv.setItem(46, named(Material.GOLDEN_HELMET, "§6Topi / Kepala", List.of("§7Filter: HAT")));
        h.inv.setItem(47, named(Material.ELYTRA, "§bPunggung", List.of("§7Filter: BACK")));
        h.inv.setItem(48, named(Material.NETHER_STAR, "§dAura", List.of("§7Filter: AURA")));
        h.inv.setItem(49, named(Material.FEATHER, "§aJejak Kaki", List.of("§7Filter: TRAIL")));
        h.inv.setItem(50, named(Material.BOOK, "§fSemua", List.of("§7Tampilkan semua")));
        h.inv.setItem(51, named(Material.BARRIER, "§cLepas Semua", List.of("§7Copot semua cosmetic")));
        h.inv.setItem(52, named(admin ? Material.COMMAND_BLOCK : Material.PAPER, admin ? "§6Mode Admin" : "§fInfo",
                admin ? List.of("§7Kamu bisa pakai semua cosmetic.", "§7/cosmetics give <player> <id|all>") : List.of("§7Cosmetic terkunci dibuka lewat rank.")));
        h.inv.setItem(53, named(Material.ARROW, "§eBerikutnya", List.of("§7Halaman " + (h.page + 1) + "/" + pages)));
        p.openInventory(h.inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        int s = e.getRawSlot();
        if (s < PAGE) {
            int idx = h.page * PAGE + s;
            if (idx >= h.shown.size()) {
                return;
            }
            Cosmetic c = h.shown.get(idx);
            if (c.id().equals(plugin.equippedId(p, c.type()))) {
                plugin.unequip(p, c.type());
                p.sendMessage(plugin.msg("removed", "Cosmetic removed."));
            } else if (plugin.canUse(p, c)) {
                plugin.equip(p, c);
                p.sendMessage(plugin.msg("equipped", "Equipped: ") + c.name());
            } else {
                p.sendMessage(plugin.msg("locked", "Your rank cannot use this cosmetic."));
                return;
            }
            open(p, h.filter, h.page);
            return;
        }
        switch (s) {
            case 45 -> open(p, h.filter, h.page - 1);
            case 46 -> open(p, Cosmetic.Type.HAT, 0);
            case 47 -> open(p, Cosmetic.Type.BACK, 0);
            case 48 -> open(p, Cosmetic.Type.AURA, 0);
            case 49 -> open(p, Cosmetic.Type.TRAIL, 0);
            case 50 -> open(p, null, 0);
            case 51 -> {
                plugin.unequipAll(p);
                p.sendMessage(plugin.msg("removed", "Cosmetic removed."));
                open(p, h.filter, h.page);
            }
            case 53 -> open(p, h.filter, h.page + 1);
            default -> {
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
