package me.naook1.nereusistent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import me.naook1.nereusistent.command.NereusistentCommand;
import me.naook1.nereusistent.gadget.GadgetListener;
import me.naook1.nereusistent.gadget.GadgetManager;
import me.naook1.nereusistent.gui.MenuListener;
import me.naook1.nereusistent.gui.MenuManager;
import me.naook1.nereusistent.util.Messages;
import me.naook1.nereusistent.util.Text;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Nereusistent - a locked hotbar gadget that opens a menu (RTP, survival, gacha, info, rules).
 * Developer: naook1/adityap
 */
public final class Nereusistent extends JavaPlugin {

    private Messages messages;
    private GadgetManager gadgets;
    private MenuManager menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Text.setPapi(getServer().getPluginManager().isPluginEnabled("PlaceholderAPI"));

        messages = new Messages(this);
        gadgets = new GadgetManager(this);
        menus = new MenuManager(this);
        reloadAll();

        getServer().getPluginManager().registerEvents(new GadgetListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        NereusistentCommand cmd = new NereusistentCommand(this);
        getCommand("nereusistent").setExecutor(cmd);
        getCommand("nereusistent").setTabCompleter(cmd);

        gadgets.start();
        getLogger().info("Nereusistent v" + getDescription().getVersion()
                + " enabled | developer: " + getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public void onDisable() {
        if (gadgets != null) {
            gadgets.stop();
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages.reload();
        menus.reload();
        gadgets.reload();
        for (Player p : getServer().getOnlinePlayers()) {
            gadgets.ensure(p, true);
        }
    }

    public FileConfiguration loadYaml(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            saveResource(name, false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration defaults = null;
        try (InputStream in = getResource(name)) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // defaults are optional
        }
        if (defaults != null) {
            // An older file-version in the plugin folder is renamed to "name.old-vN" and replaced,
            // so new menu buttons always show up after an update.
            int have = cfg.getInt("file-version", 0);
            int want = defaults.getInt("file-version", 0);
            if (have < want) {
                File backup = new File(getDataFolder(), name + ".old-v" + have);
                try {
                    Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    saveResource(name, true);
                    cfg = YamlConfiguration.loadConfiguration(file);
                    getLogger().warning(name + " is outdated, replaced with the new version. Old copy: " + backup.getName());
                } catch (IOException e) {
                    getLogger().warning("Could not update " + name + ": " + e.getMessage());
                }
            }
            cfg.setDefaults(defaults);
        }
        return cfg;
    }

    public Messages messages() {
        return messages;
    }

    public GadgetManager gadgets() {
        return gadgets;
    }

    public MenuManager menus() {
        return menus;
    }
}
