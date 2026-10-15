package me.naook1.nereushide;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Locale;
import me.naook1.nereushide.GameManager.Phase;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Keeps participants inside the rules of the game. Players that are not in the game are never touched. */
public final class GameListener implements Listener {

    private final NereusHide plugin;

    public GameListener(NereusHide plugin) {
        this.plugin = plugin;
    }

    private GameManager g() {
        return plugin.game();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        GameManager g = g();
        if (e.getEntity() instanceof Player victim && g.isIn(victim)) {
            e.setCancelled(true);
            if (e instanceof EntityDamageByEntityEvent by) {
                Player damager = null;
                if (by.getDamager() instanceof Player d) {
                    damager = d;
                } else if (by.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player d) {
                    damager = d;
                }
                // only a direct hit finds somebody; projectiles never do
                if (damager != null && by.getDamager() instanceof Player && g.isIn(damager)) {
                    g.tryFind(damager, victim);
                }
            }
            return;
        }
        // participants cannot hurt anybody outside the game either
        if (e instanceof EntityDamageByEntityEvent by && by.getDamager() instanceof Player d && g.isIn(d)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && g().isIn(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (g().isIn(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        if (g().isIn(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!g().isIn(e.getPlayer())) {
            return;
        }
        Block b = e.getClickedBlock();
        if (e.getAction() == Action.PHYSICAL) {
            if (b != null && (b.getType() == Material.FARMLAND || b.getType() == Material.TURTLE_EGG)) {
                e.setCancelled(true);
            }
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && b != null && b.getState() instanceof Container) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (g().isIn(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (g().isIn(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && g().isIn(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!g().isIn(p) || p.hasPermission("hide.bypass")) {
            return;
        }
        String msg = e.getMessage().trim();
        String first = msg.startsWith("/") ? msg.substring(1) : msg;
        int space = first.indexOf(' ');
        if (space >= 0) {
            first = first.substring(0, space);
        }
        first = first.toLowerCase(Locale.ROOT);
        int colon = first.indexOf(':');
        if (colon >= 0) {
            first = first.substring(colon + 1);
        }
        List<String> allowed = plugin.settings().allowedCommands();
        if (!allowed.contains(first)) {
            e.setCancelled(true);
            plugin.messages().send(p, "command-blocked");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent e) {
        if (g().isIn(e.getPlayer()) && !g().teleporting() && !e.getPlayer().hasPermission("hide.bypass")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!g().frozen(e.getPlayer())) {
            return;
        }
        var from = e.getFrom();
        var to = e.getTo();
        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
            var back = from.clone();
            back.setYaw(to.getYaw());
            back.setPitch(to.getPitch());
            e.setTo(back);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (g().isIn(e.getPlayer())) {
            g().leave(e.getPlayer(), true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // a game (or crash) left this player's things in states/<uuid>.yml: give them back
        if (plugin.states().has(p.getUniqueId()) && !g().isIn(p)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && !g().isIn(p) && plugin.states().restore(p, null)) {
                    plugin.messages().send(p, "restored");
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (g().isIn(e.getPlayer())) {
            e.setKeepInventory(true);
            e.setKeepLevel(true);
            e.getDrops().clear();
            e.setDroppedExp(0);
            e.deathMessage(null);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (g().isIn(e.getPlayer())) {
            var loc = g().phase() == Phase.LOBBY || g().phase() == Phase.PICKING ? plugin.arena().lobby() : plugin.arena().hide();
            if (loc != null) {
                e.setRespawnLocation(loc);
            }
        }
    }

    /** Hiders only talk to hiders, the seeker side only to the seeker side, outsiders see nothing of the game. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.settings().isolateChat() || g().phase() == Phase.IDLE) {
            return;
        }
        int mine = g().chatGroup(e.getPlayer().getUniqueId());
        e.viewers().removeIf(a -> a instanceof Player v && g().chatGroup(v.getUniqueId()) != mine);
    }
}
