package me.naook1.nereusrealm.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as a NereusRealm menu and stores its slot actions and page. */
public final class NereusMenu implements InventoryHolder {

    private final String id;
    private final Inventory inventory;
    private final int page;
    private final int pages;
    private final Map<Integer, List<String>> left = new HashMap<>();
    private final Map<Integer, List<String>> right = new HashMap<>();

    public NereusMenu(String id, int size, Component title, int page, int pages) {
        this.id = id;
        this.page = page;
        this.pages = pages;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public String id() {
        return id;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return pages;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void clearBindings() {
        left.clear();
        right.clear();
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
