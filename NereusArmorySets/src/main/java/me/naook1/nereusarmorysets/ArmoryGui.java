package me.naook1.nereusarmorysets;

import me.naook1.nereusarmorysets.Model.Bonus;
import me.naook1.nereusarmorysets.Model.SetDef;
import me.naook1.nereusarmorysets.Model.Tier;
import net.kyori.adventure.text.Component;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Menu /armoryset: pilih tier -> pilih set -> ambil piece (atau satu set penuh). */
public final class ArmoryGui implements Listener {

    private static final class Holder implements InventoryHolder {
        final Map<Integer, Consumer<Player>> actions = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final NereusArmorySets plugin;

    public ArmoryGui(NereusArmorySets plugin) {
        this.plugin = plugin;
    }

    // ---------- util ----------

    private Component title(String suffix) {
        String base = plugin.getConfig().getString("menu.title", "&8Nereus Armory Sets");
        return ItemFactory.text(suffix.isEmpty() ? base : base + " &8» &7" + suffix);
    }

    private static ItemStack button(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ItemFactory.text(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(ItemFactory.text(s));
        meta.lore(l);
        item.setItemMeta(meta);
        return item;
    }

    private static void fill(Inventory inv) {
        ItemStack pane = button(Material.GRAY_STAINED_GLASS_PANE, "&r", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    /** Salin item dan tambahkan baris lore di akhir. */
    private static ItemStack withExtraLore(ItemStack base, List<String> extra) {
        ItemStack copy = base.clone();
        ItemMeta meta = copy.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        for (String s : extra) lore.add(ItemFactory.text(s));
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    private boolean canTake(Player p, SetDef set) {
        if (plugin.getConfig().getBoolean("menu.free-take", true)) return true;
        return p.hasPermission("nereusarmorysets.admin")
                || p.hasPermission("nereusarmorysets.take." + set.id())
                || p.hasPermission("nereusarmorysets.take.tier" + set.tier().id());
    }

    private void give(Player p, SetDef set, List<String> slots) {
        if (!canTake(p, set)) {
            p.sendMessage(ItemFactory.text("&cSet ini belum bisa kamu ambil."));
            return;
        }
        for (String slot : slots) {
            ItemStack item = plugin.items().create(set, slot);
            if (item == null) continue;
            p.getInventory().addItem(item).values()
                    .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
        p.sendMessage(ItemFactory.text("&aKamu menerima " + set.tier().color() + set.display()
                + (slots.size() == 4 ? " &a(set penuh)" : " &7(" + set.pieces().get(slots.get(0)) + ")")));
    }

    // ---------- halaman ----------

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, title(""));
        fill(inv);

        List<Tier> tiers = new ArrayList<>(plugin.registry().tiers().values());
        int n = Math.min(tiers.size(), 5);
        int start = (9 - (2 * n - 1)) / 2;
        for (int i = 0; i < n; i++) {
            Tier t = tiers.get(i);
            List<SetDef> sets = plugin.registry().setsOfTier(t.id());
            int slot = 9 + start + 2 * i;
            List<String> lore = new ArrayList<>();
            lore.add("&7Jumlah set: &f" + sets.size());
            lore.add("&7Pengali armor: &fx" + ItemFactory.trim(t.armorMultiplier()));
            lore.add("&7Pengali set bonus: &fx" + ItemFactory.trim(t.multiplier()));
            lore.add("");
            lore.add("&eKlik untuk membuka");
            inv.setItem(slot, button(t.icon(), t.color() + "&lTier " + t.id() + " - " + t.name(), lore));
            h.actions.put(slot, pl -> openTier(pl, t));
        }
        p.openInventory(inv);
    }

    private void openTier(Player p, Tier tier) {
        List<SetDef> sets = plugin.registry().setsOfTier(tier.id());
        int rows = Math.max(1, (sets.size() + 6) / 7);
        int size = Math.min(54, (rows + 2) * 9);
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, size, title("Tier " + tier.id() + " " + tier.name()));
        fill(inv);

        for (int i = 0; i < sets.size() && i < rows * 7; i++) {
            SetDef set = sets.get(i);
            int slot = (i / 7 + 1) * 9 + 1 + (i % 7);
            ItemStack icon = plugin.items().create(set, "chest");
            if (icon == null) continue;
            List<String> extra = new ArrayList<>();
            extra.add("&7Kelas: &f" + set.armorClass() + "  &7Total armor: &f"
                    + ItemFactory.trim(plugin.items().totalArmor(set)));
            if (canTake(p, set)) {
                extra.add("&eKlik untuk membuka");
            } else {
                extra.add("&cTerkunci");
            }
            inv.setItem(slot, withExtraLore(icon, extra));
            h.actions.put(slot, pl -> openSet(pl, set));
        }
        int back = size - 5;
        inv.setItem(back, button(Material.ARROW, "&cKembali", List.of()));
        h.actions.put(back, this::open);
        p.openInventory(inv);
    }

    private void openSet(Player p, SetDef set) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 36,
                title(set.tier().name() + " &8» " + set.tier().color() + set.display()));
        fill(inv);

        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < 4; i++) {
            String slotName = SetRegistry.SLOTS[i];
            ItemStack item = plugin.items().create(set, slotName);
            if (item == null) continue;
            inv.setItem(slots[i], withExtraLore(item,
                    List.of(canTake(p, set) ? "&eKlik untuk mengambil" : "&cTerkunci")));
            h.actions.put(slots[i], pl -> give(pl, set, List.of(slotName)));
        }

        List<String> bonusLore = new ArrayList<>();
        for (var e : set.bonuses().entrySet()) {
            bonusLore.add("&7Bonus " + e.getKey() + " piece:");
            for (Bonus b : e.getValue()) {
                bonusLore.add("&a  " + ItemFactory.describe(b, set.tier().multiplier()));
            }
        }
        bonusLore.add("");
        bonusLore.add(canTake(p, set) ? "&eKlik untuk mengambil semua" : "&cTerkunci");
        inv.setItem(22, button(Material.CHEST, set.tier().color() + "&lAmbil set penuh", bonusLore));
        h.actions.put(22, pl -> give(pl, set, List.of(SetRegistry.SLOTS)));

        inv.setItem(31, button(Material.ARROW, "&cKembali", List.of()));
        h.actions.put(31, pl -> openTier(pl, set.tier()));
        p.openInventory(inv);
    }

    // ---------- event ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        Consumer<Player> action = h.actions.get(e.getSlot());
        if (action != null) {
            action.accept(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
