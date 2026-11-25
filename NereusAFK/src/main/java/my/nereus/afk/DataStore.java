package my.nereus.afk;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Data pemain (YAML). Disalin ke string di main thread lalu ditulis asinkron; saat mati ditulis sinkron. */
final class DataStore {
    private final NereusAFK plugin;
    private final File file;
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    DataStore(NereusAFK plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                ConfigurationSection p = s.getConfigurationSection(k);
                if (p == null) continue;
                PlayerData d = new PlayerData();
                d.fragments = p.getLong("fragments");
                d.total = p.getLong("total");
                d.day = p.getString("day", "");
                d.today = p.getInt("today");
                ConfigurationSection b = p.getConfigurationSection("bought");
                if (b != null) for (String bk : b.getKeys(false)) d.bought.put(bk, b.getInt(bk));
                data.put(id, d);
            } catch (IllegalArgumentException ignored) {
                // UUID rusak, lewati
            }
        }
    }

    PlayerData get(UUID id) {
        PlayerData d = data.computeIfAbsent(id, k -> new PlayerData());
        String today = LocalDate.now(zone()).toString();
        if (!today.equals(d.day)) {
            d.day = today;
            d.today = 0;
            d.bought.clear();
            dirty = true;
        }
        return d;
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("timezone", "Asia/Jakarta"));
        } catch (RuntimeException e) {
            return ZoneId.of("Asia/Jakarta");
        }
    }

    void markDirty() {
        dirty = true;
    }

    private String snapshot() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> en : data.entrySet()) {
            String p = "players." + en.getKey();
            PlayerData d = en.getValue();
            y.set(p + ".fragments", d.fragments);
            y.set(p + ".total", d.total);
            y.set(p + ".day", d.day);
            y.set(p + ".today", d.today);
            for (Map.Entry<String, Integer> b : d.bought.entrySet()) y.set(p + ".bought." + b.getKey(), b.getValue());
        }
        return y.saveToString();
    }

    private void write(String content) {
        try {
            File tmp = new File(file.getParentFile(), "data.yml.tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal menyimpan data.yml: " + e);
        }
    }

    void saveAsyncIfDirty() {
        if (!dirty) return;
        dirty = false;
        String content = snapshot();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> write(content));
    }

    void saveNow() {
        dirty = false;
        write(snapshot());
    }
}
