package me.naook1.nereusarmory;

import java.util.HashMap;
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

/** /armory: take the items. Everyone if menu.free-take, otherwise admins only. */
public final class ArmoryGui implements Listener {

    private static final class Holder implements InventoryHolder {
        final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private static final int[] WEAPON_SLOTS = {11, 13, 15};
    private static final int[] ARMOR_SLOTS = {20, 21, 23, 24};
    private final NereusArmory plugin;

    public ArmoryGui(NereusArmory plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 36, Text.parse("&#2fd0c8&lAbyssal Armory"));
        ItemStack pane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        var pm = pane.getItemMeta();
        pm.displayName(Text.item("&r"));
        pane.setItemMeta(pm);
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, pane);
        }
        int w = 0, a = 0;
        for (var d : plugin.items().all().values()) {
            int slot = d.weapon() ? WEAPON_SLOTS[w++] : ARMOR_SLOTS[a++];
            inv.setItem(slot, plugin.items().create(d.id()));
            h.slots.put(slot, d.id());
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
        String id = h.slots.get(e.getSlot());
        if (id == null) {
            return;
        }
        if (!plugin.getConfig().getBoolean("menu.free-take", true) && !p.hasPermission("nereusarmory.admin")) {
            p.sendMessage(Text.parse("&cOnly admins can take items from this menu."));
            return;
        }
        p.getInventory().addItem(plugin.items().create(id)).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.sendMessage(Text.parse("&bReceived " + plugin.items().def(id).name()));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            e.setCancelled(true);
        }
    }
}
