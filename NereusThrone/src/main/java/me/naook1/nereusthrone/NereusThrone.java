package me.naook1.nereusthrone;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import me.naook1.nereusthrone.afk.AfkListener;
import me.naook1.nereusthrone.afk.AfkManager;
import me.naook1.nereusthrone.command.AfkCommand;
import me.naook1.nereusthrone.command.NereusCommand;
import me.naook1.nereusthrone.daily.DailyManager;
import me.naook1.nereusthrone.data.DataStore;
import me.naook1.nereusthrone.gui.MenuListener;
import me.naook1.nereusthrone.gui.MenuManager;
import me.naook1.nereusthrone.joinleave.JoinLeaveListener;
import me.naook1.nereusthrone.joinleave.StyleManager;
import me.naook1.nereusthrone.util.Messages;
import me.naook1.nereusthrone.util.Text;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NereusThrone - custom join/leave, AFK rewards, daily and menus.
 * Developer: naook1/adityap
 */
public final class NereusThrone extends JavaPlugin {

    private Messages messages;
    private DataStore data;
    private StyleManager styles;
    private AfkManager afk;
    private DailyManager daily;
    private MenuManager menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Text.setPapi(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI"));

        messages = new Messages(this);
        data = new DataStore(this);
        styles = new StyleManager(this);
        afk = new AfkManager(this);
        daily = new DailyManager(this);
        menus = new MenuManager(this);
        reloadAll();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new JoinLeaveListener(this), this);
        pm.registerEvents(new AfkListener(this), this);
        pm.registerEvents(new MenuListener(this), this);

        NereusCommand nereus = new NereusCommand(this);
        getCommand("nereus").setExecutor(nereus);
        getCommand("nereus").setTabCompleter(nereus);
        getCommand("afk").setExecutor(new AfkCommand(this));

        afk.start();
        getServer().getScheduler().runTaskTimer(this, () -> data.flush(true), 20L * 30, 20L * 30);

        getLogger().info("NereusThrone v" + getDescription().getVersion()
                + " enabled | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (afk != null) {
            afk.stop();
        }
        if (data != null) {
            data.flush(false);
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        styles.reload();
        afk.reload();
        daily.reload();
        menus.reload();
    }

    /** Loads a bundled yml (copied to the plugin folder on first run, missing keys fall back to jar defaults). */
    public FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = getResource(name)) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // defaults are optional
        }
        return cfg;
    }

    public Messages messages() {
        return messages;
    }

    public DataStore data() {
        return data;
    }

    public StyleManager styles() {
        return styles;
    }

    public AfkManager afk() {
        return afk;
    }

    public DailyManager daily() {
        return daily;
    }

    public MenuManager menus() {
        return menus;
    }
}
