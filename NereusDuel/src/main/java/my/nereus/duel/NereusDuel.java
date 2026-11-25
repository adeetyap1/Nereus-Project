package my.nereus.duel;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class NereusDuel extends JavaPlugin {

    private ArenaManager arenas;
    private KitManager kits;
    private Stats stats;
    private DuelManager duels;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getDataFolder().mkdirs();
        new File(getDataFolder(), "snapshots").mkdirs();
        arenas = new ArenaManager(this);
        kits = new KitManager(this);
        stats = new Stats(this);
        duels = new DuelManager(this);

        Bukkit.getPluginManager().registerEvents(new DuelListener(this), this);
        PluginCommand cmd = getCommand("duel");
        if (cmd != null) {
            DuelCommand dc = new DuelCommand(this);
            cmd.setExecutor(dc);
            cmd.setTabCompleter(dc);
        }
        // Pemulihan setelah reload/crash: pemain online yang masih punya snapshot dikembalikan.
        Bukkit.getScheduler().runTask(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) duels.restoreLeftover(p);
        });
    }

    @Override
    public void onDisable() {
        if (duels != null) duels.shutdown();
        if (stats != null) stats.save();
    }

    ArenaManager arenas() { return arenas; }
    KitManager kits() { return kits; }
    Stats stats() { return stats; }
    DuelManager duels() { return duels; }

    File snapshotFile(java.util.UUID id) {
        return new File(new File(getDataFolder(), "snapshots"), id + ".yml");
    }

    String m(String key, String... kv) {
        String s = getConfig().getString("messages." + key, "");
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }

    void tell(CommandSender to, String key, String... kv) {
        to.sendMessage(Text.color(getConfig().getString("messages.prefix", "") + m(key, kv)));
    }

    void raw(CommandSender to, String legacy) {
        to.sendMessage(Text.color(getConfig().getString("messages.prefix", "") + legacy));
    }

    String modeName(String mode) {
        if (mode.equals("own")) return getConfig().getString("menu.own-name", "Armor Sendiri");
        KitManager.Kit k = kits.get(mode);
        return k == null ? mode : k.name;
    }

    boolean validMode(String mode) {
        return mode.equals("own") || kits.get(mode) != null;
    }
}
