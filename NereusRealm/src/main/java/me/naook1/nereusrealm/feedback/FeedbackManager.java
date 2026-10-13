package me.naook1.nereusrealm.feedback;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.util.Text;
import me.naook1.nereusrealm.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Server rating (1-5 stars) and feedback message per player. Stored in reviews.yml. */
public final class FeedbackManager {

    public record Review(UUID uuid, String name, int stars, String message, long time) {
    }

    private final NereusRealm plugin;
    private final File file;
    private final Object writeLock = new Object();
    private final Map<UUID, Review> reviews = new LinkedHashMap<>();
    private final List<String> blocked = new ArrayList<>();
    private boolean dirty;

    private boolean enabled = true;
    private int minPlaytimeMinutes = 30;
    private long cooldownHours = 24;
    private int maxLength = 100;

    // cached stats
    private double average;
    private int[] distribution = new int[6];

    public FeedbackManager(NereusRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reviews.yml");
        load();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("rating.enabled", true);
        minPlaytimeMinutes = Math.max(0, plugin.getConfig().getInt("rating.min-playtime-minutes", 30));
        cooldownHours = Math.max(0, plugin.getConfig().getLong("rating.edit-cooldown-hours", 24L));
        maxLength = Math.max(10, plugin.getConfig().getInt("rating.max-length", 100));
        blocked.clear();
        blocked.addAll(plugin.getConfig().getStringList("rating.blocked-words"));
    }

    private void load() {
        reviews.clear();
        if (file.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection sec = y.getConfigurationSection("reviews");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(key);
                    if (s == null) {
                        continue;
                    }
                    try {
                        UUID id = UUID.fromString(key);
                        reviews.put(id, new Review(id, s.getString("name", "?"),
                                Math.max(1, Math.min(5, s.getInt("stars", 5))),
                                s.getString("message", ""), s.getLong("time", 0L)));
                    } catch (IllegalArgumentException ignored) {
                        // skip broken entry
                    }
                }
            }
        }
        recompute();
    }

    private void recompute() {
        int[] dist = new int[6];
        int sum = 0;
        for (Review r : reviews.values()) {
            dist[r.stars()]++;
            sum += r.stars();
        }
        distribution = dist;
        average = reviews.isEmpty() ? 0 : (double) sum / reviews.size();
    }

    public void flush(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        for (Review r : reviews.values()) {
            String base = "reviews." + r.uuid();
            y.set(base + ".name", r.name());
            y.set(base + ".stars", r.stars());
            y.set(base + ".message", r.message());
            y.set(base + ".time", r.time());
        }
        final String content = y.saveToString();
        Runnable write = () -> {
            synchronized (writeLock) {
                try {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not save reviews.yml: " + e.getMessage());
                }
            }
        };
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    // ------------------------------------------------------------------ queries

    public boolean isEnabled() {
        return enabled;
    }

    public int maxLength() {
        return maxLength;
    }

    public boolean has(UUID id) {
        return reviews.containsKey(id);
    }

    public Review get(UUID id) {
        return reviews.get(id);
    }

    public int stars(UUID id) {
        Review r = reviews.get(id);
        return r == null ? 0 : r.stars();
    }

    public int count() {
        return reviews.size();
    }

    public double average() {
        return average;
    }

    /** Index 1..5 = number of reviews with that many stars. */
    public int[] distribution() {
        return distribution;
    }

    public List<Review> newestFirst() {
        List<Review> list = new ArrayList<>(reviews.values());
        list.sort(Comparator.comparingLong(Review::time).reversed());
        return list;
    }

    /** Seconds until the player may change their rating again (0 = now). */
    public long cooldownLeft(UUID id) {
        Review r = reviews.get(id);
        if (r == null) {
            return 0;
        }
        long left = r.time() + cooldownHours * 3_600_000L - System.currentTimeMillis();
        return left > 0 ? (left + 999) / 1000 : 0;
    }

    /** Sends the reason and returns false if the player may not rate right now. */
    public boolean check(Player p) {
        if (!enabled) {
            plugin.messages().send(p, "feedback.disabled");
            return false;
        }
        if (p.hasPermission("nereus.bypass")) {
            return true;
        }
        int played = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60;
        if (played < minPlaytimeMinutes) {
            plugin.messages().send(p, "feedback.need-playtime",
                    "needed", String.valueOf(minPlaytimeMinutes), "current", String.valueOf(played));
            return false;
        }
        long left = cooldownLeft(p.getUniqueId());
        if (left > 0) {
            plugin.messages().send(p, "feedback.cooldown", "time", TimeUtil.format(left));
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ changes

    /** Returns null on success, otherwise a messages.yml key describing the problem. */
    public String submit(Player p, int stars, String message) {
        String msg = Text.sanitize(message == null ? "" : message, 0);
        if (msg.length() > maxLength) {
            return "feedback.err-too-long";
        }
        if (!msg.isEmpty() && Text.containsBlocked(blocked, msg)) {
            return "feedback.err-blocked";
        }
        int s = Math.max(1, Math.min(5, stars));
        reviews.put(p.getUniqueId(), new Review(p.getUniqueId(), p.getName(), s, msg, System.currentTimeMillis()));
        dirty = true;
        recompute();
        return null;
    }

    public boolean delete(UUID id) {
        boolean removed = reviews.remove(id) != null;
        if (removed) {
            dirty = true;
            recompute();
        }
        return removed;
    }
}
