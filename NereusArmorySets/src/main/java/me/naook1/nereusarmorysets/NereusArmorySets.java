package me.naook1.nereusarmorysets;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class NereusArmorySets extends JavaPlugin implements Listener {
    private final SetRegistry registry = new SetRegistry();
    private ItemFactory items;
    private BonusManager bonuses;
    private ArmorDisplay display;
    private ArmoryGui gui;
    private BukkitTask bonusTask;
    private BukkitTask displayTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new ItemFactory(this);
        bonuses = new BonusManager(this);
        display = new ArmorDisplay(this);
        gui = new ArmoryGui(this);

        reloadAll();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(gui, this);
        ArmoryCommand cmd = new ArmoryCommand(this);
        var pc = getCommand("armoryset");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
    }

    @Override
    public void onDisable() {
        if (bonusTask != null) bonusTask.cancel();
        if (displayTask != null) displayTask.cancel();
        if (display != null) display.clearAll();
        for (Player p : getServer().getOnlinePlayers()) {
            bonuses.removeAll(p);
        }
    }

    /** Muat ulang config dan jadwalkan ulang pengecekan. */
    public void reloadAll() {
        reloadConfig();
        registry.load(getConfig(), getLogger());
        bonuses.clearCache();
        display.clearAll();

        if (bonusTask != null) bonusTask.cancel();
        if (displayTask != null) displayTask.cancel();
        long interval = Math.max(1, getConfig().getLong("update-interval-ticks", 10));
        bonusTask = getServer().getScheduler().runTaskTimer(this, bonuses::refreshAll, 20L, interval);
        long dInterval = Math.max(1, getConfig().getLong("display.interval-ticks", 2));
        displayTask = getServer().getScheduler().runTaskTimer(this, display::tick, 20L, dInterval);
        bonuses.refreshAll();
    }

    public String keyNamespace() {
        return getName().toLowerCase(java.util.Locale.ROOT);
    }

    public SetRegistry registry() {
        return registry;
    }

    public ItemFactory items() {
        return items;
    }

    public ArmoryGui gui() {
        return gui;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        bonuses.forget(e.getPlayer().getUniqueId());
        getServer().getScheduler().runTaskLater(this, () -> bonuses.refresh(e.getPlayer()), 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        bonuses.removeAll(e.getPlayer());
        bonuses.forget(e.getPlayer().getUniqueId());
        display.forget(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        bonuses.forget(e.getPlayer().getUniqueId());
        getServer().getScheduler().runTaskLater(this, () -> bonuses.refresh(e.getPlayer()), 1L);
    }
}
