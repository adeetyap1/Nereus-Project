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
    private FishGui gui;

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
        gui = new FishGui(this);
        getServer().getPluginManager().registerEvents(gui, this);
        FishCommand cmd = new FishCommand(this);
        getCommand("fish").setExecutor(cmd);
        getCommand("fish").setTabCompleter(cmd);
        getCommand("fishsell").setExecutor(cmd);

        getServer().getScheduler().runTaskTimer(this, () -> store.save(), 6000L, 6000L);
        getServer().getScheduler().runTaskTimer(this, this::heavyCarry, 20L, 20L);

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found - selling fish is disabled.");
        }
        getLogger().info("NereusFishing v" + getDescription().getVersion()
                + " enabled | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    /** Very heavy fish slow the carrier down: "lifted with two hands". */
    private void heavyCarry() {
        var c = getConfig();
        if (!c.getBoolean("heavy-carry.enabled", true)) {
            return;
        }
        double t1 = c.getDouble("heavy-carry.threshold", 100);
        double t2 = c.getDouble("heavy-carry.heavy", 300);
        double t3 = c.getDouble("heavy-carry.extreme", 550);
        for (var p : getServer().getOnlinePlayers()) {
            double kg = fish.weightOf(p.getInventory().getItemInMainHand());
            if (kg < t1) {
                continue;
            }
            int amp = kg >= t3 ? 2 : kg >= t2 ? 1 : 0;
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS,
                    40, amp, true, false, false));
            p.sendActionBar(me.naook1.nereusfishing.util.Text.parse(
                    messages.format("heavy-carry", "weight", FishManager.fmt(kg))));
        }
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

    public FishGui gui() {
        return gui;
    }

    public FishStore store() {
        return store;
    }
}
