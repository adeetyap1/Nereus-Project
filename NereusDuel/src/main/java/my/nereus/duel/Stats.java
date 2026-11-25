package my.nereus.duel;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Stats {
    static final class Entry {
        String name = "?";
        int wins;
        int losses;
    }

    private final NereusDuel plugin;
    private final File file;
    private final Map<UUID, Entry> map = new HashMap<>();

    Stats(NereusDuel plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection s = y.getConfigurationSection("players");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    try {
                        Entry e = new Entry();
                        e.name = s.getString(k + ".name", "?");
                        e.wins = s.getInt(k + ".wins");
                        e.losses = s.getInt(k + ".losses");
                        map.put(UUID.fromString(k), e);
                    } catch (IllegalArgumentException ignored) {
                        // lewati
                    }
                }
            }
        }
    }

    Entry get(UUID id) {
        return map.computeIfAbsent(id, k -> new Entry());
    }

    void result(UUID win, String winName, UUID lose, String loseName) {
        Entry w = get(win);
        w.name = winName;
        w.wins++;
        Entry l = get(lose);
        l.name = loseName;
        l.losses++;
        save();
    }

    List<Entry> top(int n) {
        List<Entry> l = new ArrayList<>(map.values());
        l.sort((a, b) -> a.wins != b.wins ? Integer.compare(b.wins, a.wins) : Integer.compare(a.losses, b.losses));
        return l.subList(0, Math.min(n, l.size()));
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> e : map.entrySet()) {
            String p = "players." + e.getKey();
            y.set(p + ".name", e.getValue().name);
            y.set(p + ".wins", e.getValue().wins);
            y.set(p + ".losses", e.getValue().losses);
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Gagal menyimpan stats.yml: " + ex);
        }
    }
}
