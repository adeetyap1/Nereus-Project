package me.naook1.nereusbank;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.UUID;
import java.util.function.Consumer;
import me.naook1.nereusbank.util.Text;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class BankListener implements Listener {

    private final NereusBank plugin;

    public BankListener(NereusBank plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof BankGui.Holder h)) {
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        var act = h.actions.get(e.getSlot());
        if (act != null && e.getWhoClicked() instanceof org.bukkit.entity.Player p) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.5f);
            act.accept(e.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof BankGui.Holder) {
            e.setCancelled(true);
        }
    }

    /** Chat prompts (type an amount / a name) for the GUI. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (!plugin.hasPrompt(id)) {
            return;
        }
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        Consumer<String> cb = plugin.takePrompt(id);
        if (cb == null) {
            return;
        }
        var p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("batal")) {
                plugin.messages().send(p, "prompt-cancelled");
                plugin.gui().openMain(p);
                return;
            }
            cb.accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.takePrompt(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.bank().deliverNotices(p);
            }
        }, 60L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        var p = e.getPlayer();
        // ATM block
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && plugin.isAtm(e.getClickedBlock())) {
            e.setCancelled(true);
            if (plugin.getConfig().getBoolean("atm.require-card", false) && !plugin.cards().holdsOwnCard(p)) {
                plugin.messages().send(p, "atm-need-card");
                return;
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
            plugin.gui().openMain(p);
            return;
        }
        // Card in hand
        ItemStack hand = p.getInventory().getItemInMainHand();
        UUID owner = plugin.cards().owner(hand);
        if (owner == null) {
            return;
        }
        e.setCancelled(true);
        if (!owner.equals(p.getUniqueId())) {
            plugin.messages().send(p, "card-not-yours");
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
        plugin.gui().openMain(p);
    }
}
