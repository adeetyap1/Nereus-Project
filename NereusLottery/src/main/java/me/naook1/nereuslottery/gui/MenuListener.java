package me.naook1.nereuslottery.gui;

import me.naook1.nereuslottery.NereusLottery;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    private final NereusLottery plugin;

    public MenuListener(NereusLottery plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof LotteryMenu menu)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int slot = e.getRawSlot();
        if (slot == LotteryMenu.CLOSE_SLOT) {
            p.closeInventory();
            return;
        }
        int amount = menu.amountAt(slot);
        if (amount > 0) {
            plugin.lottery().buy(p, amount);
            menu.refresh();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof LotteryMenu) {
            e.setCancelled(true);
        }
    }
}
