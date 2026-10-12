package me.naook1.nereusthrone.afk;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.regex.Pattern;
import me.naook1.nereusthrone.NereusThrone;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class AfkListener implements Listener {

    /** /afk, /eafk and prefixed forms must not count as activity (they toggle AFK themselves). */
    private static final Pattern AFK_COMMAND =
            Pattern.compile("^/(?:[a-z0-9_]+:)?(?:afk|eafk)(?:\\s.*)?$", Pattern.CASE_INSENSITIVE);

    private final NereusThrone plugin;

    public AfkListener(NereusThrone plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        plugin.afk().activity(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent e) {
        plugin.afk().activity(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (AFK_COMMAND.matcher(e.getMessage()).matches()) {
            return;
        }
        plugin.afk().activity(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        plugin.afk().activity(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.afk().activity(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.afk().remove(e.getPlayer().getUniqueId());
    }
}
