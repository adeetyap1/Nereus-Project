package id.nereusmc.totemstack;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

/** Inventory helpers for Totems of Undying. Only touches inventories on demand, never on a timer. */
public final class TotemManager {

    public static final int MAX_GIVE = 2304;

    private final Main plugin;

    public TotemManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING;
    }

    public boolean handHasTotem(Player player) {
        PlayerInventory inv = player.getInventory();
        return isTotem(inv.getItemInMainHand()) || isTotem(inv.getItemInOffHand());
    }

    /** Total totems in storage slots (hotbar + main inventory) and the off hand. */
    public int count(Player player) {
        PlayerInventory inv = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = inv.getItem(slot);
            if (isTotem(item)) {
                total += item.getAmount();
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (isTotem(off)) {
            total += off.getAmount();
        }
        return total;
    }

    /**
     * Finds the slot to take a totem from, following the configured order. Hands are handled by
     * vanilla and are not searched here.
     *
     * @return the slot index, or -1 if none is usable
     */
    public int findSlot(Player player) {
        ConfigManager cfg = plugin.config();
        if (!cfg.isInventoryActivation()) {
            return -1;
        }
        PlayerInventory inv = player.getInventory();
        for (String part : cfg.getSearchOrder()) {
            if (part.equals("HOTBAR") && cfg.isHotbarEnabled()) {
                for (int slot = 0; slot <= 8; slot++) {
                    if (isTotem(inv.getItem(slot))) {
                        return slot;
                    }
                }
            } else if (part.equals("INVENTORY") && cfg.isMainInventoryEnabled()) {
                for (int slot = 9; slot <= 35; slot++) {
                    if (isTotem(inv.getItem(slot))) {
                        return slot;
                    }
                }
            }
        }
        return -1;
    }

    /** Removes exactly one totem from the slot; an emptied stack clears the slot. */
    public boolean consumeOne(Player player, int slot) {
        PlayerInventory inv = player.getInventory();
        ItemStack item = inv.getItem(slot);
        if (!isTotem(item)) {
            return false;
        }
        if (item.getAmount() <= 1) {
            inv.setItem(slot, null);
        } else {
            ItemStack reduced = item.clone();
            reduced.setAmount(item.getAmount() - 1);
            inv.setItem(slot, reduced);
        }
        return true;
    }

    /** Gives totems (already stack-normalized); anything that does not fit is dropped at the player's feet. */
    public void give(Player player, int amount) {
        ConfigManager cfg = plugin.config();
        int perStack = cfg.isStackingEnabled() ? cfg.getMaxStackSize() : 1;
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(remaining, perStack);
            ItemStack stack = new ItemStack(Material.TOTEM_OF_UNDYING, size);
            plugin.stacks().apply(stack);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            remaining -= size;
        }
    }
}
