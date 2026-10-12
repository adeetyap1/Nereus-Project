package me.naook1.nereusthrone.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Inventory holder that marks an inventory as a NereusThrone menu and stores slot actions. */
public final class NereusMenu implements InventoryHolder {

    private final String id;
    private final Inventory inventory;
    private final Map<Integer, List<String>> left = new HashMap<>();
    private final Map<Integer, List<String>> right = new HashMap<>();

    public NereusMenu(String id, int size, Component title) {
        this.id = id;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public String id() {
        return id;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void bind(int slot, List<String> leftActions, List<String> rightActions) {
        if (leftActions != null && !leftActions.isEmpty()) {
            left.put(slot, leftActions);
        }
        if (rightActions != null && !rightActions.isEmpty()) {
            right.put(slot, rightActions);
        }
    }

    public List<String> actionsFor(int slot, boolean rightClick) {
        List<String> r = rightClick ? right.get(slot) : null;
        if (r == null || r.isEmpty()) {
            r = left.get(slot);
        }
        return r == null ? List.of() : r;
    }
}
