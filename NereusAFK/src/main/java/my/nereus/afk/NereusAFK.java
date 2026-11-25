package my.nereus.afk;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NereusAFK extends JavaPlugin {

    private NamespacedKey shopKey;
    private ZoneManager zones;
    private DataStore store;
    private ShopManager shop;
    private AfkTask task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        shopKey = new NamespacedKey(this, "afk_shop");
        zones = new ZoneManager(this);
        store = new DataStore(this);
        shop = new ShopManager(this);
        task = new AfkTask(this);

        Bukkit.getPluginManager().registerEvents(shop, this);
        task.runTaskTimer(this, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, store::saveAsyncIfDirty, 20L * 120, 20L * 120);

        PluginCommand cmd = getCommand("afk");
        if (cmd != null) {
            AfkCommand ac = new AfkCommand(this);
            cmd.setExecutor(ac);
            cmd.setTabCompleter(ac);
        }
    }

    @Override
    public void onDisable() {
        if (store != null) store.saveNow();
    }

    NamespacedKey shopKey() { return shopKey; }
    ZoneManager zones() { return zones; }
    DataStore store() { return store; }
    ShopManager shop() { return shop; }

    String m(String key, String... kv) {
        String s = getConfig().getString("messages." + key, "");
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return getConfig().getString("messages.prefix", "") + s;
    }

    void tell(CommandSender to, String key, String... kv) {
        to.sendMessage(Text.color(m(key, kv)));
    }

    void raw(CommandSender to, String legacy) {
        to.sendMessage(Text.color(getConfig().getString("messages.prefix", "") + legacy));
    }
}
