package me.naook1.nereusfarmprice;

import me.naook1.nereusfarmprice.command.FarmCommand;
import me.naook1.nereusfarmprice.hook.EsguiHook;
import me.naook1.nereusfarmprice.util.Messages;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusFarmPrice - rotating farm price buffs and market saturation, synced with EconomyShopGUI.
 * Developer: naook1/adityap
 */
public final class NereusFarmPrice extends JavaPlugin {

    private Messages messages;
    private EsguiPrices prices;
    private FarmManager farm;
    private FarmDisplay display;
    private EsguiHook hook;
    private long lastPriceRead;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        prices = new EsguiPrices(this);
        farm = new FarmManager(this);
        display = new FarmDisplay(this);
        hook = new EsguiHook(this);

        messages.reload();
        prices.reload();
        lastPriceRead = System.currentTimeMillis();
        farm.load();
        farm.reload();

        FarmCommand cmd = new FarmCommand(this);
        getCommand("farmprice").setExecutor(cmd);
        getCommand("farmprice").setTabCompleter(cmd);

        // every 30 seconds: buff expiry, market recovery, rotation, price refresh, save
        getServer().getScheduler().runTaskTimer(this, this::tick, 20L * 30, 20L * 30);

        // EconomyShopGUI may finish enabling after us, so hook now and retry once the server is up
        if (!hook.register()) {
            getServer().getScheduler().runTaskLater(this, () -> {
                if (!hook.register()) {
                    getLogger().warning("EconomyShopGUI events not hooked (" + hook.status()
                            + "). Buff prices will not apply to sales. Check /farmprice debug.");
                }
            }, 20L * 10);
        }
        getLogger().info("NereusFarmPrice v" + getDescription().getVersion() + " enabled | prices read: "
                + prices.itemCount() + " | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (farm != null) {
            farm.flush(false);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long every = Math.max(1, getConfig().getLong("esgui.refresh-minutes", 10)) * 60_000L;
        if (now - lastPriceRead >= every) {
            lastPriceRead = now;
            getServer().getScheduler().runTaskAsynchronously(this, prices::reload);
        }
        farm.tick();
        farm.flush(true);
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        prices.reload();
        farm.reload();
        hook.register();
    }

    public Messages messages() {
        return messages;
    }

    public EsguiPrices prices() {
        return prices;
    }

    public FarmManager farm() {
        return farm;
    }

    public FarmDisplay display() {
        return display;
    }

    public EsguiHook hook() {
        return hook;
    }
}
