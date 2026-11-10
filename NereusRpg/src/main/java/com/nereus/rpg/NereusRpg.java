package com.nereus.rpg;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class NereusRpg extends JavaPlugin {
    private Core core;
    private Skills skills;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = new Core(this);
        core.start();
        skills = new Skills(this, core);

        getServer().getPluginManager().registerEvents(new RpgListener(this, core, skills), this);
        getServer().getPluginManager().registerEvents(new MenuListener(core), this);

        Commands commands = new Commands(core);
        for (String n : new String[]{"rpg", "quest", "story", "class", "stats", "skill", "rank"}) {
            PluginCommand pc = getCommand(n);
            if (pc != null) {
                pc.setExecutor(commands);
                pc.setTabCompleter(commands);
            }
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new Expansion(core).register();
            getLogger().info("Placeholder %nereusrpg_*% terdaftar.");
        }
        for (Player p : getServer().getOnlinePlayers()) {
            core.handleJoin(p, false);
        }
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.shutdown();
        }
    }

    public Core core() {
        return core;
    }

    public Skills skills() {
        return skills;
    }
}
