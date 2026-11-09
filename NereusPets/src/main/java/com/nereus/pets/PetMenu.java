package com.nereus.pets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/** Pet Box: 45 pet per halaman, baris bawah untuk navigasi. */
public final class PetMenu implements InventoryHolder {
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_RECALL = 49;
    private static final int SLOT_NEXT = 53;

    private final NereusPets plugin;
    private final PetManager mgr;
    private final Player player;
    private Inventory inventory;
    private int page;

    private UUID confirmId;
    private long confirmAt;

    public PetMenu(NereusPets plugin, PetManager mgr, Player player) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.player = player;
    }

    public void open() {
        String title = plugin.getConfig().getString("menu-title", "&8Pet Box");
        inventory = Bukkit.createInventory(this, 54, NereusPets.LEGACY.deserialize(title));
        render();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void render() {
        List<PetData> list = mgr.box(player.getUniqueId());
        int pages = Math.max(1, (list.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        inventory.clear();
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = page * PAGE_SIZE + i;
            if (idx >= list.size()) {
                break;
            }
            inventory.setItem(i, mgr.icon(list.get(idx), idx));
        }
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < 54; s++) {
            inventory.setItem(s, filler);
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, named(Material.ARROW, "&eHalaman sebelumnya"));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, named(Material.ARROW, "&eHalaman berikutnya"));
        }
        ActivePet ap = mgr.activeOf(player);
        if (ap != null) {
            inventory.setItem(SLOT_RECALL, named(Material.LEAD,
                    "&cSimpan pet aktif", "&7" + mgr.plain(mgr.displayName(ap.data))));
        } else {
            inventory.setItem(SLOT_RECALL, named(Material.BOOK, "&bPet Box",
                    "&7Pet: &f" + list.size() + "&7/&f" + mgr.boxLimit(),
                    "&7Halaman: &f" + (page + 1) + "&7/&f" + pages,
                    "&8Tangkap mob dengan Pet Orb."));
        }
    }

    private ItemStack named(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(NereusPets.LEGACY.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new java.util.ArrayList<>();
            for (String l : lore) {
                lines.add(NereusPets.LEGACY.deserialize(l).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    public void click(int slot, ClickType click) {
        if (slot == SLOT_PREV) {
            page--;
            render();
            return;
        }
        if (slot == SLOT_NEXT) {
            page++;
            render();
            return;
        }
        if (slot == SLOT_RECALL) {
            if (mgr.activeOf(player) != null) {
                mgr.manualRecall(player);
                render();
            }
            return;
        }
        if (slot < 0 || slot >= PAGE_SIZE) {
            return;
        }
        List<PetData> list = mgr.box(player.getUniqueId());
        int idx = page * PAGE_SIZE + slot;
        if (idx >= list.size()) {
            return;
        }
        PetData d = list.get(idx);

        if (click == ClickType.SHIFT_RIGHT) {
            long now = System.currentTimeMillis();
            if (d.id.equals(confirmId) && now - confirmAt < 5000) {
                String name = mgr.plain(mgr.displayName(d));
                mgr.release(player, d);
                confirmId = null;
                plugin.send(player, "released", "{pet}", name);
            } else {
                confirmId = d.id;
                confirmAt = now;
                plugin.send(player, "release-confirm", "{index}", String.valueOf(idx + 1),
                        "{pet}", mgr.plain(mgr.displayName(d)));
            }
            render();
            return;
        }
        if (click.isLeftClick()) {
            if (mgr.isActive(d)) {
                mgr.manualRecall(player);
            } else {
                mgr.summon(player, d);
            }
            render();
        }
    }
}
