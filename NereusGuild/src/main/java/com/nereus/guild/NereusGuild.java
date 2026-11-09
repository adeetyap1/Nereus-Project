package com.nereus.guild;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class NereusGuild extends JavaPlugin {
    public static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private GuildManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new GuildManager(this);
        manager.reloadRanks();
        manager.load();

        getServer().getPluginManager().registerEvents(new GuildListener(this, manager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);

        GuildCommand command = new GuildCommand(this, manager);
        PluginCommand guild = getCommand("guild");
        if (guild != null) {
            guild.setExecutor(command);
            guild.setTabCompleter(command);
        }

        for (Player p : getServer().getOnlinePlayers()) {
            manager.handleJoin(p);
        }
        manager.start();

        if (manager.gpEnabled() && !manager.pointsAvailable()) {
            getLogger().warning("PlayerPoints tidak ditemukan. GP dianggap 0, jadi tidak ada yang bisa mulai ujian. "
                    + "Pasang PlayerPoints atau set points.enabled: false.");
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();
        manager.reloadRanks();
        for (Player p : getServer().getOnlinePlayers()) {
            manager.applyPermissions(p);
        }
    }

    /** Teks dari messages.<key> dengan penggantian pasangan kunci-nilai. */
    public String raw(String key, String... kv) {
        String s = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace(kv[i], kv[i + 1]);
        }
        return s;
    }

    public void send(CommandSender to, String key, String... kv) {
        String prefix = getConfig().getString("messages.prefix", "");
        to.sendMessage(LEGACY.deserialize(prefix + raw(key, kv)));
    }
}
