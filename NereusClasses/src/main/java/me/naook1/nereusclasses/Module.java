package me.naook1.nereusclasses;

import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

/** a feature module (cosmetics / weapons) hosted inside the single NereusClasses plugin. */
public abstract class Module {
    private final JavaPlugin host;
    private final String file;
    private FileConfiguration cfg;

    protected Module(JavaPlugin host, String file) {
        this.host = host;
        this.file = file;
    }

    public final JavaPlugin host() {
        return host;
    }

    public final File getDataFolder() {
        return host.getDataFolder();
    }

    public final Logger getLogger() {
        return host.getLogger();
    }

    public final Server getServer() {
        return host.getServer();
    }

    public final PluginDescriptionFile getDescription() {
        return host.getDescription();
    }

    public final PluginCommand getCommand(String name) {
        return host.getCommand(name);
    }

    public final void saveDefaultConfig() {
        File f = new File(host.getDataFolder(), file);
        if (!f.exists()) {
            host.saveResource(file, false);
        }
    }

    public final FileConfiguration getConfig() {
        if (cfg == null) {
            reloadConfig();
        }
        return cfg;
    }

    public final void reloadConfig() {
        cfg = YamlConfiguration.loadConfiguration(new File(host.getDataFolder(), file));
    }

    public abstract void onEnable();

    public void onDisable() {
    }
}
