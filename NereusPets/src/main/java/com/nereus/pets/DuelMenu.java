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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Menu Arena Duel: pilih pemain lain yang punya pet aktif, terima atau tolak tantangan. */
public final class DuelMenu implements InventoryHolder {
    private static final int SLOT_BACK = 45;
    private static final int SLOT_STATS = 49;
    private static final int SLOT_ACCEPT = 52;
    private static final int SLOT_DENY = 53;

    private final NereusPets plugin;
    private final PetManager mgr;
    private final Player player;
    private final Map<Integer, UUID> targets = new HashMap<>();
    private Inventory inventory;

    public DuelMenu(NereusPets plugin, PetManager mgr, Player player) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.player = player;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, 54, NereusPets.LEGACY.deserialize("&8Arena Duel Pet"));
        render();
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void render() {
        inventory.clear();
        targets.clear();
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s < 54; s++) {
            inventory.setItem(s, filler);
        }
        inventory.setItem(SLOT_BACK, item(Material.ARROW, "&eKembali ke Pet Box"));
        ActivePet mine = mgr.activeOf(player);
        List<String> st = new ArrayList<>();
        if (mine == null) {
            st.add("&cPanggil pet dulu dari Pet Box.");
        } else {
            st.add("&7Pet kamu: &f" + mgr.plain(mgr.displayName(mine.data)));
            st.add("&7Menang: &a" + mine.data.wins + " &7Kalah: &c" + mine.data.losses);
            st.add("&8Duel tidak membuat pet pingsan.");
        }
        inventory.setItem(SLOT_STATS, item(Material.NETHER_STAR, "&6Arena Duel", st.toArray(new String[0])));

        UUID from = plugin.duels().challenger(player.getUniqueId());
        if (from != null && plugin.duels().hasPending(player.getUniqueId())) {
            Player fp = Bukkit.getPlayer(from);
            String n = fp == null ? "?" : fp.getName();
            inventory.setItem(SLOT_ACCEPT, item(Material.LIME_CONCRETE, "&aTerima tantangan", "&7Dari: &f" + n));
            inventory.setItem(SLOT_DENY, item(Material.RED_CONCRETE, "&cTolak tantangan", "&7Dari: &f" + n));
        }

        int slot = 0;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || slot >= 45) {
                continue;
            }
            ActivePet ap = mgr.activeOf(other);
            if (ap == null) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta instanceof SkullMeta sm) {
                sm.setOwningPlayer(other);
            }
            meta.displayName(NereusPets.LEGACY.deserialize("&f" + other.getName()).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(line("&7Pet: &f" + mgr.plain(mgr.displayName(ap.data))));
            lore.add(line("&7HP: &c" + String.format(java.util.Locale.US, "%.0f", ap.entity.getHealth())
                    + "&7  ATK: &6" + String.format(java.util.Locale.US, "%.1f", mgr.attackPower(ap.data))));
            lore.add(line("&7Rekor: &a" + ap.data.wins + "M &c" + ap.data.losses + "K"));
            lore.add(Component.empty());
            lore.add(line(ap.duel != null ? "&cSedang duel" : "&eKlik untuk menantang"));
            meta.lore(lore);
            head.setItemMeta(meta);
            inventory.setItem(slot, head);
            targets.put(slot, other.getUniqueId());
            slot++;
        }
        if (slot == 0) {
            inventory.setItem(22, item(Material.BARRIER, "&7Belum ada pemain lain",
                    "&8Pemain lain harus memanggil pet mereka.", "&8Atau pakai /pets duel <nama>"));
        }
    }

    private Component line(String s) {
        return NereusPets.LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(line(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(line(s));
            }
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    public void click(int slot, ClickType click) {
        if (slot == SLOT_BACK) {
            new PetMenu(plugin, mgr, player).open();
            return;
        }
        if (slot == SLOT_ACCEPT && plugin.duels().hasPending(player.getUniqueId())) {
            player.closeInventory();
            plugin.duels().accept(player);
            return;
        }
        if (slot == SLOT_DENY && plugin.duels().hasPending(player.getUniqueId())) {
            plugin.duels().deny(player);
            render();
            return;
        }
        UUID id = targets.get(slot);
        if (id == null) {
            return;
        }
        Player other = Bukkit.getPlayer(id);
        if (other == null) {
            render();
            return;
        }
        player.closeInventory();
        plugin.duels().challenge(player, other);
    }
}
