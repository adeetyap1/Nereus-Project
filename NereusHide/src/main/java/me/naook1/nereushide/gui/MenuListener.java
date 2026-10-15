package me.naook1.nereushide.gui;

import me.naook1.nereushide.GameManager;
import me.naook1.nereushide.NereusHide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    private final NereusHide plugin;

    public MenuListener(NereusHide plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof AdminMenu menu)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory() || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (!p.hasPermission("hide.admin")) {
            p.closeInventory();
            return;
        }
        var m = plugin.messages();
        var g = plugin.game();
        var data = plugin.data();
        var s = plugin.settings();
        int slot = e.getRawSlot();
        switch (slot) {
            case AdminMenu.OPEN -> {
                String err = g.open();
                m.send(p, err == null ? "admin-opened" : err);
            }
            case AdminMenu.START -> {
                String err = g.forceStart();
                if (err != null) {
                    m.send(p, err);
                }
            }
            case AdminMenu.STOP -> {
                g.stop();
                m.send(p, "admin-stopped");
            }
            case AdminMenu.JOIN -> {
                if (g.isIn(p)) {
                    g.leave(p, false);
                } else {
                    p.closeInventory();
                    String err = g.join(p);
                    if (err != null) {
                        m.send(p, err);
                    }
                    return;
                }
            }
            case AdminMenu.SEEKERS_MINUS, AdminMenu.SEEKERS_PLUS -> {
                int players = Math.max(g.count(), s.minPlayers());
                int now = s.seekerCount(players);
                data.setOverride("seekers", Math.max(1, now + (slot == AdminMenu.SEEKERS_PLUS ? 1 : -1)));
                data.flush(true);
            }
            case AdminMenu.TIME_MINUS, AdminMenu.TIME_PLUS -> {
                data.setOverride("seek-time", Math.max(30, s.seekTime() + (slot == AdminMenu.TIME_PLUS ? 30 : -30)));
                data.flush(true);
            }
            case AdminMenu.RESET -> {
                data.clearOverrides();
                data.flush(true);
                m.send(p, "menu-reset");
            }
            case AdminMenu.CLOSE -> {
                p.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        menu.refresh();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof AdminMenu) {
            e.setCancelled(true);
        }
    }
}
