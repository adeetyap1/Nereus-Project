package my.nereus.intro.listener;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.session.IntroSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.Locale;

/** Mengunci pemain selama intro tanpa mengubah inventory atau data lain. Tidak ada yang dihapus. */
public final class ProtectionListener implements Listener {

    private final NereusIntro plugin;

    public ProtectionListener(NereusIntro plugin) {
        this.plugin = plugin;
    }

    private IntroSession locked(Player p) {
        IntroSession s = plugin.sessions().get(p.getUniqueId());
        return s != null && s.locked() ? s : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (locked(e.getPlayer()) == null) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location fixed = from.clone();
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            e.setTo(fixed);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        IntroSession s = locked(e.getPlayer());
        if (s == null || !s.config().blockCommands) return;
        if (s.isTest() && s.config().adminCommandsInTest) return;
        String msg = e.getMessage();
        String label = msg.startsWith("/") ? msg.substring(1) : msg;
        int sp = label.indexOf(' ');
        if (sp >= 0) label = label.substring(0, sp);
        label = label.toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        if (s.config().allowedCommands.contains(label)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(s.config().message("command-blocked"));
    }

    private void cancelIfLocked(Player p, Cancellable e) {
        if (locked(p) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) { cancelIfLocked(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) { cancelIfLocked(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { cancelIfLocked(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) { cancelIfLocked(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { cancelIfLocked(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { cancelIfLocked(e.getPlayer(), e); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) cancelIfLocked(p, e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) cancelIfLocked(p, e);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) cancelIfLocked(p, e);
    }
}
