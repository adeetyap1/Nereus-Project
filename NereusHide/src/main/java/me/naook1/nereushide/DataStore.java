package me.naook1.nereushide;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** data.yml: player statistics and the settings admins changed from the admin menu. */
public final class DataStore {

    private final NereusHide plugin;
    private final File file;
    private final Object writeLock = new Object();
    private final Map<UUID, int[]> stats = new ConcurrentHashMap<>();     // games, wins, finds
    private final Map<String, Integer> overrides = new HashMap<>();
    private boolean dirty;

    public DataStore(NereusHide plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        stats.clear();
        overrides.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("stats");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    stats.put(UUID.fromString(k), new int[]{s.getInt(k + ".games"), s.getInt(k + ".wins"), s.getInt(k + ".finds")});
                } catch (IllegalArgumentException ignored) {
                    // skip broken id
                }
            }
        }
        ConfigurationSection o = y.getConfigurationSection("overrides");
        if (o != null) {
            for (String k : o.getKeys(false)) {
                overrides.put(k, o.getInt(k));
            }
        }
    }

    public int[] stats(UUID id) {
        return stats.getOrDefault(id, new int[3]);
    }

    public void addStats(UUID id, int games, int wins, int finds) {
        int[] a = stats.computeIfAbsent(id, k -> new int[3]);
        a[0] += games;
        a[1] += wins;
        a[2] += finds;
        dirty = true;
    }

    public int override(String key, int def) {
        return overrides.getOrDefault(key, def);
    }

    public void setOverride(String key, int value) {
        overrides.put(key, value);
        dirty = true;
    }

    public void clearOverrides() {
        overrides.clear();
        dirty = true;
    }

    public void flush(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, int[]> e : stats.entrySet()) {
            y.set("stats." + e.getKey() + ".games", e.getValue()[0]);
            y.set("stats." + e.getKey() + ".wins", e.getValue()[1]);
            y.set("stats." + e.getKey() + ".finds", e.getValue()[2]);
        }
        for (Map.Entry<String, Integer> e : overrides.entrySet()) {
            y.set("overrides." + e.getKey(), e.getValue());
        }
        final String text = y.saveToString();
        Runnable write = () -> {
            synchronized (writeLock) {
                try {
                    File dir = file.getParentFile();
                    if (dir != null && !dir.exists()) {
                        dir.mkdirs();
                    }
                    Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
                }
            }
        };
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }
}
