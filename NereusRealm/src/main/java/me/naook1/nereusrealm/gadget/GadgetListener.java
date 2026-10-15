package me.naook1.nereusrealm.gadget;

import me.naook1.nereusrealm.NereusRealm;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Makes the gadget undroppable, unmovable, undeletable and always present. */
public final class GadgetListener implements Listener {

    private final NereusRealm plugin;
    private final GadgetManager gm;

    public GadgetListener(NereusRealm plugin) {
        this.plugin = plugin;
        this.gm = plugin.gadgets();
    }

    private void ensureLater(Player p, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                gm.ensure(p, false);
            }
        }, delay);
    }

    // ---------------------------------------------------------------- use

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !gm.isGadget(e.getItem())) {
            return;
        }
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR) {
            e.setCancelled(true);
            gm.open(e.getPlayer());
        } else if (a == Action.RIGHT_CLICK_BLOCK) {
            if (e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) {
                // let doors/chests/etc work, only deny the item itself
                e.setUseItemInHand(Event.Result.DENY);
            } else {
                e.setCancelled(true);
                gm.open(e.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        ItemStack inHand = e.getPlayer().getInventory().getItem(e.getHand());
        if (gm.isGadget(inHand)) {
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) {
                gm.open(e.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (gm.isGadget(e.getItemInHand())) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------- lock

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (gm.isGadget(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (gm.isGadget(e.getMainHandItem()) || gm.isGadget(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (gm.isGadget(e.getCurrentItem()) || gm.isGadget(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick() == ClickType.NUMBER_KEY && e.getWhoClicked() instanceof Player p) {
            int button = e.getHotbarButton();
            if (button >= 0 && gm.isGadget(p.getInventory().getItem(button))) {
                e.setCancelled(true);
                return;
            }
        }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR
                && e.getCursor() != null && e.getCursor().getType() == gm.material()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (gm.isGadget(e.getOldCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreative(InventoryCreativeEvent e) {
        if (gm.isGadget(e.getCurrentItem()) || gm.isGadget(e.getCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack item : e.getInventory().getMatrix()) {
            if (gm.isGadget(item)) {
                e.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
        }
    }

    // ---------------------------------------------------------------- always present

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(gm::isGadget);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        ensureLater(e.getPlayer(), 2L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        ensureLater(e.getPlayer(), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        gm.forget(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        ensureLater(e.getPlayer(), 2L);
    }

    @EventHandler
    public void onGameMode(PlayerGameModeChangeEvent e) {
        ensureLater(e.getPlayer(), 2L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            ensureLater(p, 1L);
        }
    }
}
