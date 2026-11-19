package my.nereus.intro.listener;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.session.EndReason;
import my.nereus.intro.session.IntroSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Login: pulihkan sesi terputus lalu mulai intro bila perlu. Logout: hentikan sesi dengan aman. */
public final class SessionListener implements Listener {

    private final NereusIntro plugin;

    public SessionListener(NereusIntro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        final boolean firstJoin = !p.hasPlayedBefore();
        plugin.isolation().onJoin(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            plugin.recovery().recoverIfNeeded(p, () -> {
                if (p.isOnline()) plugin.intro().maybeAutoStart(p, firstJoin);
            });
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        IntroSession s = plugin.sessions().get(e.getPlayer().getUniqueId());
        if (s != null) s.finish(EndReason.QUIT);
        plugin.isolation().release(e.getPlayer());
    }
}
