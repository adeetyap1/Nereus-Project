package my.nereus.relics.protection;

import my.nereus.relics.NereusRelics;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Melindungi area altar (blok, ledakan, piston, api, cairan) selama altar AKTIF/RITUAL. */
public final class AltarProtection implements Listener {

    private final NereusRelics plugin;

    public AltarProtection(NereusRelics plugin) {
        this.plugin = plugin;
    }

    private boolean protectedBlock(Block b) {
        return plugin.altars().zoneAt(b.getLocation()) != null;
    }

    private boolean bypass(Player p) {
        return plugin.getConfig().getBoolean("protection.admin-bypass", true) && p.hasPermission("nereusrelics.admin");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (protectedBlock(e.getBlock()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            plugin.tellRaw(e.getPlayer(), "&cArea altar dilindungi.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (protectedBlock(e.getBlock()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            plugin.tellRaw(e.getPlayer(), "&cArea altar dilindungi.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (protectedBlock(e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        for (Block b : e.getBlocks()) {
            if (protectedBlock(b) || protectedBlock(b.getRelative(e.getDirection()))) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (protectedBlock(e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        for (Block b : e.getBlocks()) {
            if (protectedBlock(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBurn(BlockBurnEvent e) {
        if (protectedBlock(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onIgnite(BlockIgniteEvent e) {
        if (protectedBlock(e.getBlock())) {
            Player p = e.getPlayer();
            if (p == null || !bypass(p)) e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onFluid(BlockFromToEvent e) {
        if (protectedBlock(e.getToBlock())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (protectedBlock(e.getBlock())) e.setCancelled(true);
    }
}
