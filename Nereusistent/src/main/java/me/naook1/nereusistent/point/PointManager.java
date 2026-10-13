package me.naook1.nereusistent.point;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusistent.Nereusistent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Named teleport points (points.yml) set in-game with /nsi setpoint <name>, used by the [teleport] menu action. */
public final class PointManager {

    private final Nereusistent plugin;
    private final File file;
    private YamlConfiguration cfg = new YamlConfiguration();

    public PointManager(Nereusistent plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "points.yml");
    }

    public void load() {
        cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** The saved location, or null if the point is unknown or its world is not loaded. */
    public Location get(String name) {
        ConfigurationSection s = cfg.getConfigurationSection("points." + key(name));
        if (s == null) {
            return null;
        }
        World w = Bukkit.getWorld(s.getString("world", ""));
        if (w == null) {
            return null;
        }
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public boolean set(String name, Location l) {
        String p = "points." + key(name) + ".";
        cfg.set(p + "world", l.getWorld().getName());
        cfg.set(p + "x", l.getX());
        cfg.set(p + "y", l.getY());
        cfg.set(p + "z", l.getZ());
        cfg.set(p + "yaw", (double) l.getYaw());
        cfg.set(p + "pitch", (double) l.getPitch());
        return save();
    }

    public boolean remove(String name) {
        if (cfg.getConfigurationSection("points." + key(name)) == null) {
            return false;
        }
        cfg.set("points." + key(name), null);
        save();
        return true;
    }

    public List<String> names() {
        ConfigurationSection s = cfg.getConfigurationSection("points");
        return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
    }

    private boolean save() {
        try {
            cfg.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save points.yml: " + e.getMessage());
            return false;
        }
    }
}
