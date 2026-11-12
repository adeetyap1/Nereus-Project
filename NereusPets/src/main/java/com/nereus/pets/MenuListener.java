package com.nereus.pets;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/** Menangani klik di Pet Box dan Arena Duel. Semua klik dibatalkan supaya item tidak bisa diambil. */
public final class MenuListener implements Listener {

    private static boolean ours(InventoryHolder h) {
        return h instanceof PetMenu || h instanceof DuelMenu;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getView().getTopInventory().getHolder();
        if (!ours(holder)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        if (holder instanceof PetMenu menu) {
            menu.click(e.getSlot(), e.getClick());
        } else if (holder instanceof DuelMenu menu) {
            menu.click(e.getSlot(), e.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (ours(e.getView().getTopInventory().getHolder())) {
            e.setCancelled(true);
        }
    }
}
