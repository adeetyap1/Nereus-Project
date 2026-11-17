package me.naook1.nereusbank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusbank.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Brankas Item: tempat menyimpan item berharga di bank guild. 45 slot per halaman, tanpa resource pack.
 * Klik item di brankas = ambil; klik item di tas = simpan.
 */
public final class VaultGui {

    public static final int PAGE = 45;

    public static final class Holder implements InventoryHolder {
        public Inventory inv;
        public int page;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private final NereusBank plugin;

    public VaultGui(NereusBank plugin) {
        this.plugin = plugin;
    }

    public void open(Player p, int page) {
        Account a = plugin.bank().account(p);
        plugin.bank().refreshRank(p);
        plugin.bank().ensureVault(a);
        int slots = plugin.bank().vaultSlots(a);
        int pages = Math.max(1, (int) Math.ceil(Math.max(slots, a.vault.size()) / (double) PAGE));
        page = Math.max(0, Math.min(page, pages - 1));
        Holder h = new Holder();
        h.page = page;
        h.inv = Bukkit.createInventory(h, 54, Text.parse("&5&lBrankas Item &8| &7" + (page + 1) + "/" + pages));
        render(h, a, slots, pages);
        p.openInventory(h.inv);
    }

    private void render(Holder h, Account a, int slots, int pages) {
        h.inv.clear();
        int base = h.page * PAGE;
        for (int i = 0; i < PAGE; i++) {
            int idx = base + i;
            if (idx >= slots && idx >= a.vault.size()) {
                h.inv.setItem(i, named(Material.BLACK_STAINED_GLASS_PANE, "&8Segel belum terbuka",
                        "&7Naikkan tier atau rank guild", "&7untuk menambah slot."));
            } else if (idx < a.vault.size()) {
                h.inv.setItem(i, view(a.vault.get(idx)));
            }
        }
        for (int i = 45; i < 54; i++) {
            h.inv.setItem(i, named(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        h.inv.setItem(45, named(Material.ARROW, "&e&lKembali", "&7Ke aula bank."));
        if (h.page > 0) {
            h.inv.setItem(47, named(Material.ARROW, "&e&lSebelumnya"));
        }
        h.inv.setItem(49, named(Material.ENDER_CHEST, "&5&lBrankas " + a.name,
                "&7Terpakai: &f" + a.vault.size() + "&7/&f" + slots,
                "", "&7Klik item di brankas untuk &aambil&7.",
                "&7Klik item di tasmu untuk &bsimpan&7.",
                "&8Shift+klik = ambil/simpan semua tumpukan."));
        if (h.page + 1 < pages) {
            h.inv.setItem(51, named(Material.ARROW, "&e&lBerikutnya"));
        }
        h.inv.setItem(53, named(Material.BARRIER, "&c&lTutup"));
    }

    private ItemStack view(Account.VaultItem v) {
        if (v.item == null) {
            return named(Material.STRUCTURE_VOID, "&cItem tidak terbaca",
                    "&7Data disimpan aman, tapi tidak bisa", "&7diambil. Hubungi admin.");
        }
        return v.item.clone();
    }

    private static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.item(name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) {
                l.add(Text.item(s));
            }
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    private boolean blocked(ItemStack it) {
        if (plugin.cards().owner(it) != null) {
            return true;
        }
        return plugin.getConfig().getStringList("vault.blacklist-materials").stream()
                .anyMatch(s -> s.equalsIgnoreCase(it.getType().name()));
    }

    public void click(InventoryClickEvent e, Holder h) {
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Account a = plugin.bank().account(p);
        plugin.bank().ensureVault(a);
        int slots = plugin.bank().vaultSlots(a);
        Inventory top = e.getView().getTopInventory();
        boolean shift = e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT;

        if (e.getClickedInventory() == null) {
            return;
        }
        if (e.getClickedInventory() != top) {
            // simpan dari tas
            ItemStack src = e.getCurrentItem();
            if (src == null || src.getType().isAir()) {
                return;
            }
            if (blocked(src)) {
                plugin.messages().send(p, "vault-blocked");
                return;
            }
            if (a.vault.size() >= slots) {
                plugin.messages().send(p, "vault-full");
                return;
            }
            ItemStack stored = src.clone();
            if (!shift && stored.getAmount() > 1 && e.getClick() == ClickType.RIGHT) {
                stored.setAmount(1);
            }
            a.vault.add(new Account.VaultItem(stored, null));
            src.setAmount(src.getAmount() - stored.getAmount());
            if (src.getAmount() <= 0) {
                e.setCurrentItem(null);
            }
            changed(p, a, h, slots);
            plugin.messages().send(p, "vault-stored", "item", name(stored), "amount", String.valueOf(stored.getAmount()));
            return;
        }
        int s = e.getSlot();
        if (s >= PAGE) {
            nav(p, h, s);
            return;
        }
        int idx = h.page * PAGE + s;
        if (idx >= a.vault.size()) {
            return;
        }
        Account.VaultItem v = a.vault.get(idx);
        if (v.item == null) {
            return;
        }
        ItemStack give = v.item.clone();
        var left = p.getInventory().addItem(give);
        if (left.isEmpty()) {
            a.vault.remove(idx);
        } else {
            int remain = left.values().iterator().next().getAmount();
            if (remain >= v.item.getAmount()) {
                plugin.messages().send(p, "vault-bag-full");
                return;
            }
            v.item.setAmount(remain);
            plugin.messages().send(p, "vault-bag-partial");
        }
        changed(p, a, h, slots);
    }

    private void nav(Player p, Holder h, int slot) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
        switch (slot) {
            case 45 -> plugin.getServer().getScheduler().runTask(plugin, () -> plugin.gui().openMain(p));
            case 47 -> plugin.getServer().getScheduler().runTask(plugin, () -> open(p, h.page - 1));
            case 51 -> plugin.getServer().getScheduler().runTask(plugin, () -> open(p, h.page + 1));
            case 53 -> plugin.getServer().getScheduler().runTask(plugin, p::closeInventory);
            default -> {
            }
        }
    }

    private void changed(Player p, Account a, Holder h, int slots) {
        a.vaultDirty = true;
        plugin.bank().markDirty();
        int pages = Math.max(1, (int) Math.ceil(Math.max(slots, a.vault.size()) / (double) PAGE));
        h.page = Math.min(h.page, pages - 1);
        render(h, a, slots, pages);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
    }

    private static String name(ItemStack it) {
        return it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
