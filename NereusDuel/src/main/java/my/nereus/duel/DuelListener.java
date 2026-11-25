package my.nereus.duel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

final class DuelListener implements Listener {
    private final NereusDuel plugin;

    DuelListener(NereusDuel plugin) {
        this.plugin = plugin;
    }

    private Match of(Entity e) {
        return e instanceof Player p ? plugin.duels().matchOf(p.getUniqueId()) : null;
    }

    private Player shooter(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        return null;
    }

    // ------------------------------------------------------------------ damage: inti "tidak ada kematian sungguhan"

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        Match vm = of(e.getEntity());
        Player attacker = e instanceof EntityDamageByEntityEvent be ? shooter(be.getDamager()) : null;
        Match am = attacker == null ? null : plugin.duels().matchOf(attacker.getUniqueId());
        if (vm == null && am == null) return;
        if (vm == null) {            // duelist memukul orang luar
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent && (attacker == null || am != vm || attacker.equals(e.getEntity()))) {
            e.setCancelled(true);    // hanya lawan yang boleh melukai
            return;
        }
        if (vm.phase != Match.Phase.ACTIVE) {
            e.setCancelled(true);
            return;
        }
        Player victim = (Player) e.getEntity();
        if (victim.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            plugin.duels().roundWin(vm, vm.opponent(victim.getUniqueId()));
        }
    }

    /** Cadangan (mis. /kill): pemain benar-benar mati. Tidak ada drop; match dibatalkan menang untuk lawan. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Match m = of(e.getEntity());
        if (m == null) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        plugin.duels().forfeit(e.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.duels().restoreLeftover(p), 2L);
    }

    // ------------------------------------------------------------------ sesi

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.duels().quit(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.duels().restoreLeftover(p), 5L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Match m = of(e.getPlayer());
        if (m == null || m.phase != Match.Phase.COUNTDOWN) return;
        Location from = e.getFrom(), to = e.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            Location t = from.clone();
            t.setYaw(to.getYaw());
            t.setPitch(to.getPitch());
            e.setTo(t);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (of(e.getPlayer()) == null) return;
        String msg = e.getMessage();
        String first = msg.length() > 1 ? msg.substring(1).split(" ")[0].toLowerCase(Locale.ROOT) : "";
        if (first.contains(":")) first = first.substring(first.indexOf(':') + 1);
        List<String> allowed = plugin.getConfig().getStringList("match.allowed-commands");
        for (String a : allowed) if (a.equalsIgnoreCase(first)) return;
        e.setCancelled(true);
        plugin.tell(e.getPlayer(), "command-blocked");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (of(e.getPlayer()) == null) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if (c != PlayerTeleportEvent.TeleportCause.PLUGIN && c != PlayerTeleportEvent.TeleportCause.UNKNOWN) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent e) {
        if (!plugin.getConfig().getBoolean("match.block-ender-pearl", true)) return;
        if (e.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player p && of(p) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ cegah eksploit (duplikasi/kehilangan item)

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (of(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (of(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (of(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (of(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (of(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (of(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent e) {
        if (of(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (of(e.getPlayer()) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ menu

    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof DuelCommand.MenuHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        String mode = h.modes().get(e.getSlot());
        if (mode == null) return;
        p.closeInventory();
        if (mode.equals("__leave")) plugin.duels().leave(p);
        else plugin.duels().join(p, mode);
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof DuelCommand.MenuHolder) e.setCancelled(true);
    }
}
