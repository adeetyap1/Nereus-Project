package me.naook1.nereusbarter.gui;

import me.naook1.nereusbarter.BarterSession;
import me.naook1.nereusbarter.NereusBarter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MenuListener implements Listener {

    private final NereusBarter plugin;

    public MenuListener(NereusBarter plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof BarterMenu menu)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || menu.session().ended) {
            return;
        }
        var bm = plugin.barter();
        boolean top = e.getClickedInventory() == e.getView().getTopInventory();
        if (!top) {
            if (e.getClickedInventory() != null) {
                bm.offerAdd(p, e.getCurrentItem(), e.getClick() == ClickType.RIGHT);
            }
            return;
        }
        int slot = e.getRawSlot();
        int idx = BarterMenu.offerIndex(slot);
        if (idx >= 0) {
            bm.offerReturn(p, idx);
            return;
        }
        if (slot == BarterMenu.RESET_SLOT) {
            bm.resetMoney(p);
        } else if (slot == BarterMenu.CANCEL_SLOT) {
            bm.cancelFor(p, "reason-closed");
        } else if (slot == BarterMenu.ACTION_SLOT) {
            if (menu.session().confirmStage) {
                bm.confirm(p);
            } else {
                bm.toggleReady(p);
            }
        } else if (slot == BarterMenu.BACK_SLOT) {
            bm.back(p);
        } else {
            for (int i = 0; i < BarterMenu.STEP_SLOTS.length; i++) {
                if (slot == BarterMenu.STEP_SLOTS[i]) {
                    bm.addMoney(p, i);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof BarterMenu) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof BarterMenu menu) {
            BarterSession s = menu.session();
            if (!s.ended && e.getPlayer() instanceof Player p) {
                plugin.barter().cancelFor(p, "reason-closed");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.barter().cancelFor(e.getPlayer(), "reason-quit");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        plugin.barter().cancelFor(e.getPlayer(), "reason-death");
    }
}
