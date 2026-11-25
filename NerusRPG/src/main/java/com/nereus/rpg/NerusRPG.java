package com.nereus.rpg;

import com.nereus.rpg.guild.Guild;
import com.nereus.rpg.guild.NpcIntegration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;

/** NerusRPG: RPG (class, skill, quest, rank F-S) dan Nerus Guild (Caylin) dalam satu plugin. */
public final class NerusRPG extends JavaPlugin implements Listener {
    private Core core;
    private Top top;
    private Skills skills;
    private Backups backups;
    private Migration migration;
    private NpcIntegration npcs;
    private BukkitTask backupTask;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        core = new Core(this);
        backups = new Backups(this);
        migration = new Migration(this, core);
        migration.preConfigs();
        saveDefaultConfig();
        core.guild = new Guild(this, core);
        core.start();
        core.guild.load();
        skills = new Skills(this, core);
        migration.runStartupIfNeeded();

        getServer().getPluginManager().registerEvents(new RpgListener(this, core, skills), this);
        getServer().getPluginManager().registerEvents(new MenuListener(core), this);
        core.display = new Display(core);
        getServer().getPluginManager().registerEvents(core.display, this);
        getServer().getPluginManager().registerEvents(core.guild.dialogue, this);
        getServer().getPluginManager().registerEvents(this, this);

        Commands commands = new Commands(core);
        for (String n : new String[]{"nereusrpg", "quest", "story", "class", "stats", "skill", "rank"}) {
            PluginCommand pc = getCommand(n);
            if (pc != null) {
                pc.setExecutor(commands);
                pc.setTabCompleter(commands);
            }
        }

        npcs = new NpcIntegration(this, core);
        npcs.hook();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Expansion ex = new Expansion(core);
            top = new Top(core);
            ex.top(top);
            top.start();
            ex.register();
            getLogger().info("Placeholder %nereusrpg_*% terdaftar.");
        }
        for (Player p : getServer().getOnlinePlayers()) {
            core.handleJoin(p, false);
        }
        scheduleBackup();
        warnOldPlugins();
    }

    private void warnOldPlugins() {
        for (String old : new String[]{"NereusRpg", "GuildMaster"}) {
            if (Bukkit.getPluginManager().getPlugin(old) != null) {
                getLogger().severe("Plugin lama '" + old + "' masih terpasang! Lepas jar-nya: NerusRPG sudah menggantikannya "
                        + "(command dan placeholder akan bentrok).");
            }
        }
    }

    private void scheduleBackup() {
        if (backupTask != null) {
            backupTask.cancel();
            backupTask = null;
        }
        double hours = getConfig().getDouble("backup.interval-hours", 12);
        if (hours <= 0) {
            return;
        }
        long ticks = (long) (hours * 3600 * 20);
        backupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            core.saveAll();
            backups.backupPlayerdata(null);
        }, ticks, ticks);
    }

    /** Dipanggil setelah /gm reload agar jadwal backup mengikuti config. */
    public void afterReload() {
        scheduleBackup();
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent e) {
        checkGm();
    }

    /** Pastikan /gm milik NerusRPG (bukan alias gamemode plugin lain) sesuai config commands.take-over-gm. */
    private void checkGm() {
        PluginCommand ours = getCommand("nereusrpg");
        if (ours == null) {
            return;
        }
        CommandMap map = Bukkit.getCommandMap();
        Command gm = map.getCommand("gm");
        if (gm == ours) {
            return;
        }
        String owner = gm instanceof PluginCommand pc ? pc.getPlugin().getName() : gm == null ? "-" : gm.getClass().getSimpleName();
        if (getConfig().getBoolean("commands.take-over-gm", true)) {
            Map<String, Command> known = map.getKnownCommands();
            known.put("gm", ours);
            getLogger().warning("/gm sebelumnya dipakai '" + owner + "'. Kini /gm = NerusRPG (gamemode tetap lewat /gamemode). "
                    + "Matikan dengan commands.take-over-gm: false.");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.updateCommands();
            }
        } else {
            getLogger().warning("/gm dipakai '" + owner + "', bukan NerusRPG. Gunakan /nereusrpg atau set commands.take-over-gm: true.");
        }
    }

    @Override
    public void onDisable() {
        if (backupTask != null) {
            backupTask.cancel();
        }
        if (top != null) {
            top.stop();
        }
        if (core != null) {
            core.guild.dialogue.clear();
            core.shutdown();
        }
    }

    public Core core() {
        return core;
    }

    public Skills skills() {
        return skills;
    }

    public Backups backups() {
        return backups;
    }

    public Migration migration() {
        return migration;
    }
}
