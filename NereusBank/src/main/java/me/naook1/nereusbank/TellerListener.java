package me.naook1.nereusbank;

import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import java.util.List;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** NPC teller (FancyNpcs): klik NPC yang terdaftar di teller.npc-ids untuk membuka bank. */
public final class TellerListener implements Listener {

    private final NereusBank plugin;

    public TellerListener(NereusBank plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpc(NpcInteractEvent e) {
        List<String> ids = plugin.getConfig().getStringList("teller.npc-ids");
        String id = e.getNpc().getData().getId();
        String name = e.getNpc().getData().getName();
        if (ids.stream().noneMatch(x -> x.equalsIgnoreCase(id) || x.equalsIgnoreCase(name))) {
            return;
        }
        var p = e.getPlayer();
        double max = plugin.getConfig().getDouble("teller.max-distance", 8);
        if (max > 0 && e.getNpc().getData().getLocation().getWorld() == p.getWorld()
                && e.getNpc().getData().getLocation().distance(p.getLocation()) > max) {
            return;
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.6f);
        plugin.messages().send(p, "teller-greet");
        plugin.gui().openMain(p);
    }
}
