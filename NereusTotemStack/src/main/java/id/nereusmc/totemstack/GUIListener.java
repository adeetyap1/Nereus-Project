package id.nereusmc.totemstack;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Locks our menus: every click and drag while a menu is open is cancelled (no extraction, no
 * shift-click moves from the player's inventory, no number-key swaps). Only clicks on the menu's
 * own slots are forwarded, and GUIManager re-checks permissions on each one.
 */
public final class GUIListener implements Listener {

    private final Main plugin;

    public GUIListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof GUIManager.Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!top.equals(event.getClickedInventory())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        plugin.gui().handleClick(player, holder, slot, event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GUIManager.Holder) {
            event.setCancelled(true);
        }
    }
}
