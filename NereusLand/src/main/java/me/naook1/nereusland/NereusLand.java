package me.naook1.nereusland;

import me.naook1.nereusland.command.LandCommand;
import me.naook1.nereusland.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusLand - boss bar with the name of the RedProtect land a player is standing in.
 * Developer: naook1/adityap
 */
public final class NereusLand extends JavaPlugin implements Listener {

    private Messages messages;
    private RedProtectHook hook;
    private LandBars bars;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        hook = new RedProtectHook(this);
        bars = new LandBars(this);
        messages.reload();
        bars.load();
        bars.reload();

        getServer().getPluginManager().registerEvents(this, this);
        LandCommand cmd = new LandCommand(this);
        getCommand("landbar").setExecutor(cmd);
        getCommand("landbar").setTabCompleter(cmd);

        long every = Math.max(2, getConfig().getLong("update-ticks", 10));
        getServer().getScheduler().runTaskTimer(this, bars::tick, 40L, every);

        if (getServer().getPluginManager().getPlugin("RedProtect") == null) {
            getLogger().warning("RedProtect not found - no land names can be shown until it is installed.");
        }
        getLogger().info("NereusLand v" + getDescription().getVersion()
                + " enabled | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (bars != null) {
            bars.hideAll();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        bars.hide(e.getPlayer());
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        bars.reload();
    }

    public Messages messages() {
        return messages;
    }

    public RedProtectHook hook() {
        return hook;
    }

    public LandBars bars() {
        return bars;
    }
}
