package me.naook1.nereusbarter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** data.yml (toggle + barter counts) and barter-log.yml (recent barters). Written asynchronously. */
public final class BarterStore {

    public record Entry(long time, UUID a, String aName, String aGave, long aMoney,
                        UUID b, String bName, String bGave, long bMoney) {
    }

    private final NereusBarter plugin;
    private final File dataFile;
    private final File logFile;
    private final Object writeLock = new Object();
    private final Set<UUID> off = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> counts = new ConcurrentHashMap<>();
    private final List<Entry> log = new ArrayList<>();
    private boolean dirty;

    public BarterStore(NereusBarter plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        this.logFile = new File(plugin.getDataFolder(), "barter-log.yml");
    }

    public void load() {
        off.clear();
        counts.clear();
        log.clear();
        if (dataFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
            for (String s : y.getStringList("off")) {
                try {
                    off.add(UUID.fromString(s));
                } catch (IllegalArgumentException ignored) {
                    // skip broken id
                }
            }
            ConfigurationSection c = y.getConfigurationSection("counts");
            if (c != null) {
                for (String k : c.getKeys(false)) {
                    try {
                        counts.put(UUID.fromString(k), c.getInt(k));
                    } catch (IllegalArgumentException ignored) {
                        // skip broken id
                    }
                }
            }
        }
        if (logFile.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(logFile);
            for (Map<?, ?> m : y.getMapList("entries")) {
                try {
                    log.add(new Entry(num(m.get("time")), UUID.fromString(str(m.get("a"))), str(m.get("a-name")),
                            str(m.get("a-gave")), num(m.get("a-money")), UUID.fromString(str(m.get("b"))),
                            str(m.get("b-name")), str(m.get("b-gave")), num(m.get("b-money"))));
                } catch (Exception ignored) {
                    // skip broken entry
                }
            }
        }
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    public boolean isOff(UUID id) {
        return off.contains(id);
    }

    /** Returns true when requests are now accepted. */
    public boolean toggle(UUID id) {
        boolean nowOn = off.remove(id);
        if (!nowOn) {
            off.add(id);
        }
        dirty = true;
        return nowOn;
    }

    public int count(UUID id) {
        return counts.getOrDefault(id, 0);
    }

    public void add(Entry e) {
        counts.merge(e.a(), 1, Integer::sum);
        counts.merge(e.b(), 1, Integer::sum);
        log.add(e);
        int keep = Math.max(10, plugin.getConfig().getInt("history.keep", 200));
        while (log.size() > keep) {
            log.remove(0);
        }
        dirty = true;
    }

    /** Newest first. Main thread only. */
    public List<Entry> recent(UUID id, int max) {
        List<Entry> out = new ArrayList<>();
        for (int i = log.size() - 1; i >= 0 && out.size() < max; i--) {
            Entry e = log.get(i);
            if (e.a().equals(id) || e.b().equals(id)) {
                out.add(e);
            }
        }
        return out;
    }

    public void flush(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration data = new YamlConfiguration();
        List<String> offIds = new ArrayList<>();
        for (UUID u : off) {
            offIds.add(u.toString());
        }
        data.set("off", offIds);
        for (Map.Entry<UUID, Integer> en : counts.entrySet()) {
            data.set("counts." + en.getKey(), en.getValue());
        }
        YamlConfiguration lg = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry e : log) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", e.time());
            m.put("a", e.a().toString());
            m.put("a-name", e.aName());
            m.put("a-gave", e.aGave());
            m.put("a-money", e.aMoney());
            m.put("b", e.b().toString());
            m.put("b-name", e.bName());
            m.put("b-gave", e.bGave());
            m.put("b-money", e.bMoney());
            list.add(m);
        }
        lg.set("entries", list);
        final String dataText = data.saveToString();
        final String logText = lg.saveToString();
        Runnable write = () -> {
            synchronized (writeLock) {
                try {
                    File dir = dataFile.getParentFile();
                    if (dir != null && !dir.exists()) {
                        dir.mkdirs();
                    }
                    Files.writeString(dataFile.toPath(), dataText, StandardCharsets.UTF_8);
                    Files.writeString(logFile.toPath(), logText, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not save barter data: " + ex.getMessage());
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
