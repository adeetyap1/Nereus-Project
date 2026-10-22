package me.naook1.nereusarmory;

import java.util.ArrayList;
import java.util.HashMap;
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

/** /armory: take the items. Everyone if menu.free-take, otherwise admins only. */
public final class ArmoryGui implements Listener {

    private static final class Holder implements InventoryHolder {
        final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private final NereusArmory plugin;

    public ArmoryGui(NereusArmory plugin) {
        this.plugin = plugin;
    }

    private static final int[] COLS3 = {2, 4, 6};
    private static final int[] COLS4 = {2, 3, 5, 6};

    public void open(Player p) {
        boolean admin = p.hasPermission("nereusarmory.admin");
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Text.parse("&#2fd0c8&lNereus Armory"));
        ItemStack pane = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        var pm = pane.getItemMeta();
        pm.displayName(Text.item("&r"));
        pane.setItemMeta(pm);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }
        String[] groups = {"", "pearl", "dragon", "frost"};
        int row = 1;
        for (String g : groups) {
            List<ArmoryItems.Def> list = new ArrayList<>();
            for (var d : plugin.items().all().values()) {
                if (d.set().equals(g) && (!d.adminOnly() || admin)) {
                    list.add(d);
                }
            }
            if (list.isEmpty()) {
                continue;
            }
            int[] cols = list.size() == 3 ? COLS3 : COLS4;
            for (int i = 0; i < list.size() && i < cols.length; i++) {
                int slot = row * 9 + cols[i];
                inv.setItem(slot, plugin.items().create(list.get(i).id()));
                h.slots.put(slot, list.get(i).id());
            }
            row++;
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
        if ((plugin.items().def(id).adminOnly() || !plugin.getConfig().getBoolean("menu.free-take", true)) && !p.hasPermission("nereusarmory.admin")) {
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
