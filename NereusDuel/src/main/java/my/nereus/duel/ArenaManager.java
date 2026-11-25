package my.nereus.duel;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ArenaManager {
    private final NereusDuel plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    ArenaManager(NereusDuel plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("arenas");
        if (s == null) return;
        for (String id : s.getKeys(false)) {
            ConfigurationSection a = s.getConfigurationSection(id);
            if (a == null) continue;
            Arena ar = new Arena(id);
            ar.s1 = spot(a.getConfigurationSection("s1"));
            ar.s2 = spot(a.getConfigurationSection("s2"));
            arenas.put(id, ar);
        }
    }

    private Spot spot(ConfigurationSection s) {
        if (s == null) return null;
        return new Spot(s.getString("world", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private void put(YamlConfiguration y, String path, Spot sp) {
        if (sp == null) return;
        y.set(path + ".world", sp.world());
        y.set(path + ".x", sp.x());
        y.set(path + ".y", sp.y());
        y.set(path + ".z", sp.z());
        y.set(path + ".yaw", sp.yaw());
        y.set(path + ".pitch", sp.pitch());
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            put(y, "arenas." + a.id + ".s1", a.s1);
            put(y, "arenas." + a.id + ".s2", a.s2);
            if (a.s1 == null && a.s2 == null) y.set("arenas." + a.id + ".created", true);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal menyimpan arenas.yml: " + e);
        }
    }

    Arena get(String id) {
        return arenas.get(id.toLowerCase(Locale.ROOT));
    }

    Collection<Arena> all() {
        return arenas.values();
    }

    boolean create(String id) {
        String k = id.toLowerCase(Locale.ROOT);
        if (arenas.containsKey(k)) return false;
        arenas.put(k, new Arena(k));
        save();
        return true;
    }

    boolean remove(String id) {
        Arena a = arenas.get(id.toLowerCase(Locale.ROOT));
        if (a == null || a.busy != null) return false;
        arenas.remove(a.id);
        save();
        return true;
    }

    boolean hasReady() {
        for (Arena a : arenas.values()) if (a.ready()) return true;
        return false;
    }

    Arena free() {
        for (Arena a : arenas.values()) if (a.ready() && a.busy == null) return a;
        return null;
    }
}
