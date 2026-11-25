package my.nereus.afk;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ZoneManager {
    private final NereusAFK plugin;
    private final File file;
    private final Map<String, Zone> zones = new ConcurrentHashMap<>();

    ZoneManager(NereusAFK plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
        load();
    }

    private void load() {
        zones.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("zones");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection z = s.getConfigurationSection(id);
            if (z == null) continue;
            zones.put(id, new Zone(id, z.getString("world", "world"), z.getDouble("x"), z.getDouble("y"), z.getDouble("z"),
                    z.getDouble("radius", 8), z.getDouble("height", 6)));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Zone z : zones.values()) {
            String p = "zones." + z.id();
            y.set(p + ".world", z.world());
            y.set(p + ".x", z.x());
            y.set(p + ".y", z.y());
            y.set(p + ".z", z.z());
            y.set(p + ".radius", z.radius());
            y.set(p + ".height", z.height());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal menyimpan zones.yml: " + e);
        }
    }

    Zone zoneAt(Location l) {
        for (Zone z : zones.values()) if (z.contains(l)) return z;
        return null;
    }

    Collection<Zone> all() {
        return zones.values();
    }

    boolean create(String id, Location at, double radius, double height) {
        String key = id.toLowerCase(Locale.ROOT);
        if (zones.containsKey(key)) return false;
        zones.put(key, new Zone(key, at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), radius, height));
        save();
        return true;
    }

    boolean remove(String id) {
        boolean ok = zones.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (ok) save();
        return ok;
    }
}
