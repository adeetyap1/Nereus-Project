package me.naook1.nereusfarmprice;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import me.naook1.nereusfarmprice.util.SmallCaps;
import me.naook1.nereusfarmprice.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Daily Farm Boost: every period (24h) picks exactly N different candidates and gives them a percentage bonus on
 * their SELL price. The active list and the period times are stored in data.yml, so restarts never start a new
 * period by accident. Periods are aligned to a fixed time of day in the configured time zone.
 */
public final class FarmManager {

    public record Candidate(Material material, String name, String icon) {
    }

    private static final DecimalFormat PRICE = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("id-ID")));
    private static final DecimalFormat PERCENT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final NereusFarmPrice plugin;
    private final File file;
    private final Object writeLock = new Object();
    private final Map<Material, Candidate> candidates = new LinkedHashMap<>();
    private final List<Material> active = new ArrayList<>();
    private final List<Material> previous = new ArrayList<>();
    private long periodStart;
    private long periodEnd;
    private long lastAttempt;
    private long lastWarn;
    private boolean dirty;

    // config
    private boolean enabled = true;
    private double percent = 50;
    private int count = 3;
    private long durationMs = 24 * 3_600_000L;
    private ZoneId zone = ZoneId.systemDefault();
    private LocalTime rotationTime = LocalTime.MIDNIGHT;
    private boolean avoidRepeat = true;
    private boolean announce = true;

    public FarmManager(NereusFarmPrice plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ config + storage

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("boost.enabled", true);
        percent = Math.max(0, c.getDouble("boost.percent", 50));
        count = Math.max(1, c.getInt("boost.count", 3));
        durationMs = Math.max(1, c.getLong("boost.duration-hours", 24)) * 3_600_000L;
        avoidRepeat = c.getBoolean("boost.avoid-repeat", true);
        announce = c.getBoolean("boost.announce", true);
        try {
            zone = ZoneId.of(c.getString("boost.timezone", "Asia/Makassar"));
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid boost.timezone, using the server time zone.");
            zone = ZoneId.systemDefault();
        }
        try {
            rotationTime = LocalTime.parse(c.getString("boost.rotation-time", "00:00").trim());
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid boost.rotation-time (use HH:mm), using 00:00.");
            rotationTime = LocalTime.MIDNIGHT;
        }

        candidates.clear();
        ConfigurationSection sec = c.getConfigurationSection("candidates");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                Material m = Material.matchMaterial(key);
                if (m == null || !m.isItem()) {
                    plugin.getLogger().warning("candidates: unknown material '" + key + "' (skipped)");
                    continue;
                }
                ConfigurationSection s = sec.getConfigurationSection(key);
                String name = s == null ? "" : s.getString("name", "");
                String icon = s == null ? "" : s.getString("icon", "");
                candidates.put(m, new Candidate(m, name == null || name.isBlank() ? titleCase(m) : name, icon == null ? "" : icon));
            }
        }
        active.removeIf(m -> !candidates.containsKey(m));
        previous.removeIf(m -> !candidates.containsKey(m));
        checkRotation();
    }

    public void load() {
        active.clear();
        previous.clear();
        periodStart = 0;
        periodEnd = 0;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        periodStart = y.getLong("period-start", 0);
        periodEnd = y.getLong("period-end", 0);
        for (String s : y.getStringList("active")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                active.add(m);
            }
        }
        for (String s : y.getStringList("previous")) {
            Material m = Material.matchMaterial(s);
            if (m != null) {
                previous.add(m);
            }
        }
    }

    public void flush(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.set("period-start", periodStart);
        y.set("period-end", periodEnd);
        y.set("active", names(active));
        y.set("previous", names(previous));
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

    private static List<String> names(List<Material> list) {
        List<String> out = new ArrayList<>();
        for (Material m : list) {
            out.add(m.name());
        }
        return out;
    }

    private static String titleCase(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ rotation

    /**
     * Starts a new period when the current one is over (or there is none yet). Does nothing otherwise, so it is
     * safe to call often (every tick, on sales, on menu open) and after restarts. Main thread only.
     */
    public void checkRotation() {
        if (!enabled || !Bukkit.isPrimaryThread()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!active.isEmpty() && now < periodEnd) {
            return;
        }
        if (active.isEmpty() && now < periodEnd && now - lastAttempt < 30_000L) {
            return;     // nothing was selectable a moment ago, do not retry on every call
        }
        lastAttempt = now;
        rotate(announce);
    }

    /** Picks a new set of boosted farms for the current period. Returns false if nothing could be selected. */
    public boolean rotate(boolean announceIt) {
        long now = System.currentTimeMillis();
        List<Material> valid = new ArrayList<>();
        for (Candidate c : candidates.values()) {
            if (plugin.prices().sellPrice(c.material()) > 0) {
                valid.add(c.material());
            }
        }
        // the period that is ending becomes the "previous" one (kept when nothing new can be chosen)
        if (valid.isEmpty()) {
            if (now - lastWarn > 600_000L) {
                lastWarn = now;
                plugin.getLogger().warning("Daily Farm Boost: no candidate has a valid sell price in EconomyShopGUI. "
                        + "Check integration.folders in config.yml and /farmprice debug.");
            }
            active.clear();
            dirty = true;
            return false;
        }

        long[] bounds = boundaries(now);
        List<Material> before = new ArrayList<>(active);
        Random rnd = new Random(bounds[0] * 31 + valid.size());
        Collections.sort(valid);
        List<Material> pool = new ArrayList<>(valid);
        List<Material> picked = new ArrayList<>();
        int n = Math.min(count, pool.size());

        List<Material> fresh = new ArrayList<>(pool);
        if (avoidRepeat) {
            List<Material> skip = before.isEmpty() ? previous : before;
            fresh.removeAll(skip);
        }
        Collections.shuffle(fresh, rnd);
        for (Material m : fresh) {
            if (picked.size() < n) {
                picked.add(m);
            }
        }
        if (picked.size() < n) {            // not enough different candidates: fill without repeating inside this period
            List<Material> rest = new ArrayList<>(pool);
            rest.removeAll(picked);
            Collections.shuffle(rest, rnd);
            for (Material m : rest) {
                if (picked.size() < n) {
                    picked.add(m);
                }
            }
        }

        previous.clear();
        previous.addAll(before.isEmpty() ? previous : before);
        active.clear();
        active.addAll(picked);
        periodStart = bounds[0];
        periodEnd = bounds[1];
        dirty = true;
        flush(true);
        plugin.getLogger().info("Daily Farm Boost rotated: " + names(active) + " until " + periodEnd);
        if (announceIt) {
            announce();
        }
        return true;
    }

    /** {start, end} of the period that contains the given moment, aligned to rotation-time in the time zone. */
    private long[] boundaries(long now) {
        long anchor = LocalDate.of(2024, 1, 1).atTime(rotationTime).atZone(zone).toInstant().toEpochMilli();
        long k = Math.floorDiv(now - anchor, durationMs);
        long start = anchor + k * durationMs;
        return new long[]{start, start + durationMs};
    }

    private void announce() {
        var m = plugin.messages();
        boolean sc = plugin.getConfig().getBoolean("small-caps", true);
        String pct = percentText();
        List<String> lines = new ArrayList<>(m.list("announce.header", "percent", pct));
        for (Material mat : active) {
            Candidate c = candidates.get(mat);
            if (c == null) {
                continue;
            }
            lines.add(fillItem(m.raw("announce.item"), c, sc, "percent", pct));
        }
        lines.addAll(m.list("announce.footer", "percent", pct));
        // only players who are online right now; nothing is queued for later
        for (String line : lines) {
            Bukkit.broadcast(Text.parse(line));
        }
    }

    // ------------------------------------------------------------------ queries

    public boolean enabled() {
        return enabled;
    }

    public boolean isBoosted(Material m) {
        if (!enabled) {
            return false;
        }
        checkRotation();
        return System.currentTimeMillis() < periodEnd && active.contains(m);
    }

    /** Event bonus (NereusEvents farm_sell) applied to every farm item while an event runs. */
    public double eventFactor() {
        return EventsHook.multiplier("farm_sell");
    }

    /** Boosted by the daily rotation OR by a running global event (farm items only). */
    public boolean sellBoosted(Material m) {
        return isBoosted(m) || (enabled && eventFactor() > 1.0 && candidate(m) != null);
    }

    /** Total factor used when selling: rotation boost times event bonus. */
    public double sellFactor(Material m) {
        return factor(m) * eventFactor();
    }

    /** 1 + percent/100 for the boosted farms, otherwise 1. */
    public double factor(Material m) {
        return isBoosted(m) ? 1.0 + percent / 100.0 : 1.0;
    }

    public double percent() {
        return percent;
    }

    public int count() {
        return count;
    }

    public long durationHours() {
        return durationMs / 3_600_000L;
    }

    public List<Material> active() {
        return new ArrayList<>(active);
    }

    public Candidate candidate(Material m) {
        return candidates.get(m);
    }

    public List<Material> candidateMaterials() {
        return new ArrayList<>(candidates.keySet());
    }

    public int candidateCount() {
        return candidates.size();
    }

    public long periodEnd() {
        return periodEnd;
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalTime rotationTime() {
        return rotationTime;
    }

    /** Normal sell price straight from EconomyShopGUI, -1 when unknown. */
    public double normalPrice(Material m) {
        return plugin.prices().sellPrice(m);
    }

    // ------------------------------------------------------------------ formatting

    public String fmt(double price) {
        return "$" + PRICE.format(price);
    }

    public String percentText() {
        return PERCENT.format(percent);
    }

    public String fillItem(String template, Candidate c, boolean smallCaps, String... extra) {
        double normal = normalPrice(c.material());
        String icon = c.icon();
        String line = template.replace("{icon} ", icon.isBlank() ? "" : icon + " ").replace("{icon}", icon);
        String[] base = {
                "name", c.name(),
                "name_sc", smallCaps ? SmallCaps.convert(c.name()) : c.name(),
                "normal", normal < 0 ? "?" : fmt(normal),
                "boosted", normal < 0 ? "?" : fmt(normal * (1.0 + percent / 100.0))
        };
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return Text.fill(line, all);
    }
}
