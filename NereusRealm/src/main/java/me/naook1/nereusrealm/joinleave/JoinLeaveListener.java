package me.naook1.nereusrealm.joinleave;

import me.naook1.nereusrealm.NereusRealm;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinLeaveListener implements Listener {

    private final NereusRealm plugin;

    public JoinLeaveListener(NereusRealm plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("join-leave.enabled", true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled()) {
            return;
        }
        Player p = e.getPlayer();
        e.joinMessage(null);
        boolean first = !p.hasPlayedBefore();
        long delay = Math.max(1L, plugin.getConfig().getLong("join-leave.join-delay-ticks", 2L));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.styles().announceJoin(p, first);
            }
        }, delay);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        if (!enabled()) {
            return;
        }
        e.quitMessage(null);
        plugin.styles().announceLeave(e.getPlayer());
    }
}
