package me.naook1.nereusthrone.gui;

import java.util.List;
import me.naook1.nereusthrone.NereusThrone;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    private final NereusThrone plugin;

    public MenuListener(NereusThrone plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof NereusMenu menu)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        List<String> actions = menu.actionsFor(e.getSlot(), e.isRightClick());
        if (actions.isEmpty()) {
            return;
        }
        plugin.menus().playClick(p);
        // run next tick: opening/closing inventories inside a click event is unsafe
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.menus().runActions(p, menu.id(), actions));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof NereusMenu) {
            e.setCancelled(true);
        }
    }
}
