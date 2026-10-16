package me.naook1.nereusfishing;

import me.naook1.nereusfishing.command.FishCommand;
import me.naook1.nereusfishing.util.Messages;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusFishing - custom rods, fish rarities, records and fish selling.
 * Developer: naook1/adityap
 */
public final class NereusFishing extends JavaPlugin {

    private Messages messages;
    private EconomyHook economy;
    private FishManager fish;
    private FishStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        economy = new EconomyHook(this);
        fish = new FishManager(this);
        store = new FishStore(this);
        messages.reload();
        fish.reload();
        store.load();

        getServer().getPluginManager().registerEvents(new FishListener(this), this);
        FishCommand cmd = new FishCommand(this);
        getCommand("fish").setExecutor(cmd);
        getCommand("fish").setTabCompleter(cmd);
        getCommand("fishsell").setExecutor(cmd);

        getServer().getScheduler().runTaskTimer(this, () -> store.save(), 6000L, 6000L);

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found - selling fish is disabled.");
        }
        getLogger().info("NereusFishing v" + getDescription().getVersion()
                + " enabled | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.save();
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        fish.reload();
    }

    public Messages messages() {
        return messages;
    }

    public EconomyHook economy() {
        return economy;
    }

    public FishManager fish() {
        return fish;
    }

    public FishStore store() {
        return store;
    }
}
