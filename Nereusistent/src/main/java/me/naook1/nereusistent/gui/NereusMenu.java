package me.naook1.nereusistent.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as a Nereusistent menu and stores its slot actions. */
public final class NereusMenu implements InventoryHolder {

    private final String id;
    private final Inventory inventory;
    private final Map<Integer, List<String>> actions = new HashMap<>();

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

    void bind(int slot, List<String> list) {
        if (list != null && !list.isEmpty()) {
            actions.put(slot, list);
        }
    }

    public List<String> actionsFor(int slot) {
        return actions.getOrDefault(slot, List.of());
    }
}
