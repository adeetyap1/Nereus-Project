package me.naook1.nereusbarter;

import me.naook1.nereusbarter.command.BarterCommand;
import me.naook1.nereusbarter.gui.MenuListener;
import me.naook1.nereusbarter.hook.PapiHook;
import me.naook1.nereusbarter.util.Messages;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusBarter - safe player to player barter (items + money) with a two step confirmation.
 * Developer: naook1/adityap
 */
public final class NereusBarter extends JavaPlugin {

    private Messages messages;
    private EconomyHook economy;
    private BarterStore store;
    private BarterManager barter;
    private int saveCounter;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        economy = new EconomyHook(this);
        store = new BarterStore(this);
        barter = new BarterManager(this);
        messages.reload();
        store.load();
        barter.reload();

        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
        BarterCommand cmd = new BarterCommand(this);
        getCommand("barter").setExecutor(cmd);
        getCommand("barter").setTabCompleter(cmd);

        getServer().getScheduler().runTaskTimer(this, this::tick, 20L, 20L);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                PapiHook.registerExpansion(this);
            } catch (Throwable t) {
                getLogger().warning("Could not register PlaceholderAPI expansion: " + t.getMessage());
            }
        }
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found - money in barters is disabled (items still work).");
        }
        getLogger().info("NereusBarter v" + getDescription().getVersion()
                + " enabled | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (barter != null) {
            barter.cancelAll("reason-shutdown");
        }
        if (store != null) {
            store.flush(false);
        }
    }

    private void tick() {
        barter.tick();
        if (++saveCounter >= 30) {
            saveCounter = 0;
            store.flush(true);
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        barter.reload();
    }

    public Messages messages() {
        return messages;
    }

    public EconomyHook economy() {
        return economy;
    }

    public BarterStore store() {
        return store;
    }

    public BarterManager barter() {
        return barter;
    }
}
