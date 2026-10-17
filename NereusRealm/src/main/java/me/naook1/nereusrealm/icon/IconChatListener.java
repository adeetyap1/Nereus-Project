package me.naook1.nereusrealm.icon;

import me.naook1.nereusrealm.NereusRealm;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Runs last so chat plugins (EssentialsChat etc.) have already put the prefix into the format. */
@SuppressWarnings("deprecation")
public final class IconChatListener implements Listener {

    private final NereusRealm plugin;

    public IconChatListener(NereusRealm plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        IconManager icons = plugin.icons();
        e.setFormat(icons.apply(e.getFormat(), p));
        if (p.hasPermission("nereus.icon.chat")) {
            e.setMessage(icons.apply(e.getMessage(), p));
        }
    }
}
