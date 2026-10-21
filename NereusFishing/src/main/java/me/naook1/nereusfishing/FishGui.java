package me.naook1.nereusfishing;

import java.util.ArrayList;
import java.util.List;
import me.naook1.nereusfishing.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** /fish menu: catalogue of rods and fish. Admins (or everyone if menu.free-take) can take items. */
public final class FishGui implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    private static final int PER_PAGE = SLOTS.length;

    private final NereusFishing plugin;

    public FishGui(NereusFishing plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        boolean rods = true;
        int page;
        final List<String> ids = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public boolean canTake(Player p) {
        return p.hasPermission("fishing.admin") || plugin.getConfig().getBoolean("menu.free-take", false);
    }

    public void open(Player p, boolean rods, int page) {
        Holder h = new Holder();
        h.rods = rods;
        FishManager fm = plugin.fish();
        List<ItemStack> items = new ArrayList<>();
        boolean take = canTake(p);
        if (rods) {
            for (FishManager.Rod r : fm.rods().values()) {
                h.ids.add(r.id());
                items.add(withLore(fm.createRod(r), take ? "&a▶ Klik untuk ambil" : "&8Hanya admin yang bisa mengambil"));
            }
        } else {
            for (FishManager.Fish f : plugin.fish().fishList()) {
                h.ids.add(f.id());
                double mid = (f.minWeight() + f.maxWeight()) / 2.0;
                ItemStack it = fm.createFish(fm.catchOf(f, mid), "Nereus");
                items.add(withLore(it, take ? "&a▶ Klik kiri: berat acak" : "&8Hanya admin yang bisa mengambil",
                        take ? "&a▶ Shift+klik: berat maksimal" : "", take ? "&a▶ Klik kanan: berat minimal" : ""));
            }
        }
        int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, Text.parse(rods ? "&b&lNereus Fishing &8» &7Joran" : "&b&lNereus Fishing &8» &7Ikan"));
        h.inv = inv;
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }
        for (int s : SLOTS) {
            inv.setItem(s, null);
        }
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= items.size()) {
                break;
            }
            inv.setItem(SLOTS[i], items.get(idx));
        }
        inv.setItem(45, named(Material.FISHING_ROD, (rods ? "&b&l" : "&7") + "Joran"));
        inv.setItem(46, named(Material.COD, (!rods ? "&b&l" : "&7") + "Ikan"));
        if (page > 0) {
            inv.setItem(48, named(Material.ARROW, "&e« Sebelumnya"));
        }
        inv.setItem(49, named(Material.BARRIER, "&cTutup"));
        if (page < pages - 1) {
            inv.setItem(50, named(Material.ARROW, "&eSelanjutnya »"));
        }
        inv.setItem(53, named(Material.PAPER, "&7Halaman &f" + (page + 1) + "&7/&f" + pages));
        p.openInventory(inv);
    }

    private ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack withLore(ItemStack it, String... extra) {
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        for (String e : extra) {
            if (!e.isEmpty()) {
                lore.add(Text.item(e));
            }
        }
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        int slot = e.getRawSlot();
        switch (slot) {
            case 45 -> open(p, true, 0);
            case 46 -> open(p, false, 0);
            case 48 -> open(p, h.rods, h.page - 1);
            case 50 -> open(p, h.rods, h.page + 1);
            case 49 -> p.closeInventory();
            default -> {
                int i = -1;
                for (int k = 0; k < SLOTS.length; k++) {
                    if (SLOTS[k] == slot) {
                        i = k;
                    }
                }
                int idx = h.page * PER_PAGE + i;
                if (i < 0 || idx >= h.ids.size()) {
                    return;
                }
                if (!canTake(p)) {
                    p.sendMessage(Text.parse("&cHanya admin yang bisa mengambil dari menu ini."));
                    return;
                }
                FishManager fm = plugin.fish();
                ItemStack give;
                if (h.rods) {
                    FishManager.Rod r = fm.rod(h.ids.get(idx));
                    if (r == null) {
                        return;
                    }
                    give = fm.createRod(r);
                } else {
                    FishManager.Fish f = fm.fishById(h.ids.get(idx));
                    if (f == null) {
                        return;
                    }
                    double w;
                    if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
                        w = f.maxWeight();
                    } else if (e.getClick() == ClickType.RIGHT) {
                        w = f.minWeight();
                    } else {
                        w = f.minWeight() + Math.random() * (f.maxWeight() - f.minWeight());
                    }
                    give = fm.createFish(fm.catchOf(f, Math.round(w * 100.0) / 100.0), p.getName());
                }
                p.getInventory().addItem(give).values()
                        .forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
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
