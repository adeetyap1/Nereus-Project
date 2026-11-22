package my.nereus.relics.listener;

import my.nereus.relics.NereusRelics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public final class AltarListener implements Listener {

    private final NereusRelics plugin;

    public AltarListener(NereusRelics plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        plugin.offerings().onDrop(e.getPlayer(), e.getItemDrop());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.rememberName(p.getUniqueId(), p.getName());
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.deliveries().process(p), 40L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.deliveries().process(p), 20L);
    }

    /** Chunk altar dimuat: hapus duplikat/yatim dan pulihkan entity yang hilang. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!e.getChunk().isLoaded()) return;
            plugin.altars().reconcile(e.getChunk());
            for (var a : plugin.altars().all()) {
                if (a.isRunning() && a.location() != null
                        && a.location().world().equals(e.getWorld().getName())
                        && a.location().chunkX() == e.getChunk().getX()
                        && a.location().chunkZ() == e.getChunk().getZ()) {
                    plugin.altars().ensureEntities(a, false);
                }
            }
        });
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        // Entity chunk biasanya dimuat bersamaan; pemeriksaan kedua sedikit tertunda sebagai pengaman.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!e.getChunk().isLoaded()) return;
            for (var a : plugin.altars().all()) {
                if (a.isRunning() && a.location() != null
                        && a.location().world().equals(e.getWorld().getName())
                        && a.location().chunkX() == e.getChunk().getX()
                        && a.location().chunkZ() == e.getChunk().getZ()) {
                    plugin.altars().ensureEntities(a, false);
                }
            }
        }, 10L);
    }
}
