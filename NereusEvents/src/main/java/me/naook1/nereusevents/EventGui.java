package me.naook1.nereusevents;

import java.util.ArrayList;
import java.util.List;
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

/** /event gui: list of events. Admins click to start one. */
public final class EventGui implements Listener {

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    private final NereusEvents plugin;

    public EventGui(NereusEvents plugin) {
        this.plugin = plugin;
    }

    private static final class Holder implements InventoryHolder {
        final List<String> ids = new ArrayList<>();
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 45, Text.parse("&c&lNereus Events"));
        h.inv = inv;
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.space());
        pane.setItemMeta(pm);
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, pane);
        }
        boolean admin = p.hasPermission("nereusevents.admin");
        int i = 0;
        for (EventManager.Def d : plugin.events().defs()) {
            if (i >= SLOTS.length) {
                break;
            }
            ItemStack it = new ItemStack(d.icon());
            ItemMeta m = it.getItemMeta();
            m.displayName(Text.item(d.name()));
            List<Component> lore = new ArrayList<>();
            for (String l : d.lore()) {
                lore.add(Text.item(l));
            }
            lore.add(Component.empty());
            lore.add(Text.item("&7Durasi: &f" + d.minutes() + " menit"));
            if (plugin.events().isActive(d.id())) {
                lore.add(Text.item("&a● Sedang berjalan"));
            } else if (admin) {
                lore.add(Text.item("&a▶ Klik untuk mulai"));
            }
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(SLOTS[i], it);
            h.ids.add(d.id());
            i++;
        }
        p.openInventory(inv);
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
        for (int k = 0; k < h.ids.size(); k++) {
            if (SLOTS[k] == e.getRawSlot()) {
                if (!p.hasPermission("nereusevents.admin")) {
                    return;
                }
                var def = plugin.events().def(h.ids.get(k));
                if (def != null && plugin.events().start(def, def.minutes())) {
                    p.closeInventory();
                }
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
