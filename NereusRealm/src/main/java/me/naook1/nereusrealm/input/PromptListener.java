package me.naook1.nereusrealm.input;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Captures the player's next chat line while a prompt is open (the line is never shown in public chat). */
public final class PromptListener implements Listener {

    private final NereusRealm plugin;

    public PromptListener(NereusRealm plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!plugin.prompts().isActive(p.getUniqueId())) {
            return;
        }
        e.setCancelled(true);
        String text = Text.plain(e.message());
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.prompts().handle(p, text));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.prompts().clear(e.getPlayer().getUniqueId());
    }
}
