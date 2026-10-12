package me.naook1.nereusthrone.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import me.naook1.nereusthrone.NereusThrone;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

/** Small YAML-backed per-player storage (data.yml). */
public final class DataStore {

    private final NereusThrone plugin;
    private final File file;
    private final Object writeLock = new Object();
    private YamlConfiguration cfg;
    private boolean dirty;

    public DataStore(NereusThrone plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private String path(UUID uuid, String key) {
        return "players." + uuid + "." + key;
    }

    public String getString(UUID uuid, String key, String def) {
        return cfg.getString(path(uuid, key), def);
    }

    public boolean getBoolean(UUID uuid, String key, boolean def) {
        return cfg.getBoolean(path(uuid, key), def);
    }

    public long getLong(UUID uuid, String key, long def) {
        return cfg.getLong(path(uuid, key), def);
    }

    public void set(UUID uuid, String key, Object value) {
        cfg.set(path(uuid, key), value);
        dirty = true;
    }

    public void flush(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        final String content = cfg.saveToString();
        Runnable write = () -> {
            synchronized (writeLock) {
                try {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
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
