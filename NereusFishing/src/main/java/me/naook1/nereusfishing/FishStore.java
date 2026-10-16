package me.naook1.nereusfishing;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Per player records (heaviest catch, total catches) in data.yml. */
public final class FishStore {

    public record Entry(UUID id, String name, double biggest, String fish, int catches) {
    }

    private final NereusFishing plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<>();
    private boolean dirty;

    public FishStore(NereusFishing plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        entries.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) {
            return;
        }
        for (String key : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ConfigurationSection p = s.getConfigurationSection(key);
                entries.put(id, new Entry(id, p.getString("name", "?"), p.getDouble("biggest"),
                        p.getString("fish", "?"), p.getInt("catches")));
            } catch (IllegalArgumentException ignored) {
                // skip bad key
            }
        }
    }

    /** Records a catch. Returns true if it is a new personal record. */
    public boolean record(UUID id, String name, String fishName, double weight) {
        Entry old = entries.get(id);
        boolean record = old == null || weight > old.biggest();
        int catches = old == null ? 1 : old.catches() + 1;
        entries.put(id, new Entry(id, name,
                record ? weight : old.biggest(), record ? fishName : old.fish(), catches));
        dirty = true;
        return record;
    }

    public List<Entry> top(int n) {
        List<Entry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparingDouble(Entry::biggest).reversed());
        return list.size() > n ? list.subList(0, n) : list;
    }

    public void save() {
        if (!dirty) {
            return;
        }
        YamlConfiguration y = new YamlConfiguration();
        for (Entry e : entries.values()) {
            String p = "players." + e.id();
            y.set(p + ".name", e.name());
            y.set(p + ".biggest", e.biggest());
            y.set(p + ".fish", e.fish());
            y.set(p + ".catches", e.catches());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
