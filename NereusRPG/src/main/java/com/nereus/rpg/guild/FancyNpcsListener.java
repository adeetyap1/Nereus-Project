package com.nereus.rpg.guild;

import com.nereus.rpg.Core;
import com.nereus.rpg.NereusRPG;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mendengarkan NpcInteractEvent dari FancyNpcs. NPC dikenali dari NpcData.getId() atau NpcData.getName()
 * yang tercantum di config npc.ids. Kelas ini hanya dimuat jika FancyNpcs aktif.
 */
public final class FancyNpcsListener implements Listener {
    private final NereusRPG plugin;
    private final Core core;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public FancyNpcsListener(NereusRPG plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
    }

    @EventHandler
    public void onInteract(NpcInteractEvent e) {
        List<String> ids = plugin.getConfig().getStringList("npc.ids");
        String id = e.getNpc().getData().getId();
        String name = e.getNpc().getData().getName();
        boolean match = false;
        for (String s : ids) {
            if (s.equalsIgnoreCase(id) || s.equalsIgnoreCase(name)) {
                match = true;
                break;
            }
        }
        if (!match) {
            return;
        }
        ActionTrigger t = e.getInteractionType();
        String want = plugin.getConfig().getString("npc.click", "ANY_CLICK").toUpperCase();
        boolean ok = switch (want) {
            case "RIGHT_CLICK" -> t == ActionTrigger.RIGHT_CLICK;
            case "LEFT_CLICK" -> t == ActionTrigger.LEFT_CLICK;
            default -> t == ActionTrigger.RIGHT_CLICK || t == ActionTrigger.LEFT_CLICK || t == ActionTrigger.ANY_CLICK;
        };
        if (!ok) {
            return;
        }
        Player p = e.getPlayer();
        Location npcLoc = e.getNpc().getData().getLocation();
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < plugin.getConfig().getLong("npc.click-cooldown-ms", 1200)) {
            return;
        }
        cooldown.put(p.getUniqueId(), now);
        double maxDist = plugin.getConfig().getDouble("npc.max-distance", 8.0);
        // Event bisa datang dari thread lain: semua pekerjaan dipindah ke main thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            if (npcLoc != null && npcLoc.getWorld() != null && npcLoc.getWorld().equals(p.getWorld())
                    && npcLoc.distanceSquared(p.getLocation()) > maxDist * maxDist) {
                return;
            }
            core.tracker.talk(p, "guild");
            core.guild.dialogue.open(p);
        });
    }

    public void forget(UUID id) {
        cooldown.remove(id);
    }
}
