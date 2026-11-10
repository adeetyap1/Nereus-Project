package com.nereus.rpg;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Klik di menu dibatalkan supaya item tidak bisa diambil. */
public final class MenuListener implements Listener {
    private final Core core;

    public MenuListener(Core core) {
        this.core = core;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menus.Base menu)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        menu.click(e.getSlot(), e.isShiftClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menus.Base) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Menus.ClassMenu && e.getPlayer() instanceof Player p
                && core.profile(p).cls == null) {
            core.msg(p, "&eKamu belum memilih class. Ketik &f/class &euntuk memilih.");
        }
    }
}
