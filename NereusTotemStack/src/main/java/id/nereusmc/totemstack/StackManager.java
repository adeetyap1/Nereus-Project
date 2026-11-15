package id.nereusmc.totemstack;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Real stacking: every Totem of Undying carries the vanilla max_stack_size data component
 * (set through ItemMeta#setMaxStackSize), so the server itself merges, splits and moves the stacks.
 * Totems are normalized whenever they enter play (join, pickup, spawn, loot, container open),
 * never by a repeating scan.
 */
public final class StackManager implements Listener {

    private final Main plugin;

    public StackManager(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies (or, when stacking is disabled, removes) the stack-size component on a totem.
     *
     * @return true if the item was modified
     */
    public boolean apply(ItemStack item) {
        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        ConfigManager cfg = plugin.config();
        if (cfg.isPluginEnabled() && cfg.isStackingEnabled()) {
            int max = cfg.getMaxStackSize();
            if (meta.hasMaxStackSize() && meta.getMaxStackSize() == max) {
                return false;
            }
            meta.setMaxStackSize(max);
        } else {
            if (!meta.hasMaxStackSize()) {
                return false;
            }
            meta.setMaxStackSize(null);
        }
        item.setItemMeta(meta);
        return true;
    }

    public void normalize(Inventory inventory) {
        if (inventory == null || inventory.getHolder() instanceof GUIManager.Holder) {
            return;
        }
        int size = inventory.getSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) {
                continue;
            }
            if (apply(item)) {
                inventory.setItem(slot, item);
            }
        }
    }

    public void normalize(Player player) {
        normalize(player.getInventory());
        normalize(player.getEnderChest());
    }

    public void normalizeOnline() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            normalize(player);
        }
    }

    // ------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        normalize(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Item entity = event.getItem();
        ItemStack stack = entity.getItemStack();
        if (apply(stack)) {
            entity.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item entity = event.getEntity();
        ItemStack stack = entity.getItemStack();
        if (apply(stack)) {
            entity.setItemStack(stack);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        List<ItemStack> loot = event.getLoot();
        for (int i = 0; i < loot.size(); i++) {
            ItemStack item = loot.get(i);
            if (apply(item)) {
                loot.set(i, item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof GUIManager.Holder) {
            return;
        }
        normalize(event.getInventory());
        if (event.getPlayer() instanceof Player player) {
            normalize(player.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreative(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, () -> normalize(player.getInventory()));
        }
    }
}
