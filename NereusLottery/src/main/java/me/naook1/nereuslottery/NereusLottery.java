package me.naook1.nereuslottery;

import me.naook1.nereuslottery.command.LotteryCommand;
import me.naook1.nereuslottery.gui.LotteryMenu;
import me.naook1.nereuslottery.gui.MenuListener;
import me.naook1.nereuslottery.hook.PapiHook;
import me.naook1.nereuslottery.util.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusLottery - scheduled lottery with a growing jackpot. No tax: the whole pot goes to the winners.
 * Developer: naook1/adityap
 */
public final class NereusLottery extends JavaPlugin implements Listener {

    private Messages messages;
    private EconomyHook economy;
    private LotteryManager lottery;
    private int saveCounter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        economy = new EconomyHook(this);
        lottery = new LotteryManager(this);
        messages.reload();
        lottery.load();
        lottery.reload();

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);

        LotteryCommand cmd = new LotteryCommand(this);
        getCommand("lottery").setExecutor(cmd);
        getCommand("lottery").setTabCompleter(cmd);

        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                PapiHook.registerExpansion(this);
            } catch (Throwable t) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
            }
        }
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found - tickets cannot be bought until Vault + an economy plugin are installed.");
        }
        getLogger().info("NereusLottery v" + getDescription().getVersion()
                + " enabled | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (lottery != null) {
            lottery.flush(false);
        }
    }

    private void tick() {
        lottery.tick();
        for (Player p : getServer().getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof LotteryMenu menu) {
                menu.refresh();
            }
        }
        if (++saveCounter >= 30) {
            saveCounter = 0;
            lottery.flush(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) {
                lottery.claimPending(p);
            }
        }, 60L);
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        lottery.reload();
    }

    public Messages messages() {
        return messages;
    }

    public EconomyHook economy() {
        return economy;
    }

    public LotteryManager lottery() {
        return lottery;
    }
}
