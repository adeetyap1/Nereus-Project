package me.naook1.nereusfarmprice;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import me.naook1.nereusfarmprice.util.SmallCaps;
import me.naook1.nereusfarmprice.util.Text;
import me.naook1.nereusfarmprice.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Crops, active buffs, market saturation, automatic rotation and persistence (data.yml). */
public final class FarmManager {

    public record Crop(String id, String name, List<Material> materials, double priceOverride, int capacity) {
    }

    public static final class Buff {
        public double mult;
        public long ends;
        public boolean manual;

        Buff(double mult, long ends, boolean manual) {
            this.mult = mult;
            this.ends = ends;
            this.manual = manual;
        }
    }

    private static final DecimalFormat PRICE = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("id-ID")));

    private final NereusFarmPrice plugin;
    private final File file;
    private final Object writeLock = new Object();
    private final Random random = new Random();
    private final Map<String, Crop> crops = new LinkedHashMap<>();
    private final Map<Material, Crop> byMaterial = new HashMap<>();
    private final Map<String, Buff> buffs = new HashMap<>();
    private final Map<String, Double> pressure = new HashMap<>();
    private final List<String> lastBuffed = new ArrayList<>();
    private long nextRotation;
    private long lastDecay = System.currentTimeMillis();
    private boolean dirty;

    // config
    private boolean rotationEnabled = true;
    private long intervalMs = 6 * 3_600_000L;
    private int count = 2;
    private double minMult = 1.4;
    private double maxMult = 2.0;
    private boolean broadcast = true;
    private boolean marketEnabled = true;
    private int capacity = 3000;
    private double floor = 0.70;
    private double recoveryPerHour = 600;

    public FarmManager(NereusFarmPrice plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ config + storage

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        boolean sc = c.getBoolean("small-caps", true);
        rotationEnabled = c.getBoolean("rotation.enabled", true);
        intervalMs = Math.max(1, c.getLong("rotation.interval-hours", 6)) * 3_600_000L;
        count = Math.max(1, c.getInt("rotation.count", 2));
        minMult = Math.max(1.0, c.getDouble("rotation.min-multiplier", 1.4));
        maxMult = Math.max(minMult, c.getDouble("rotation.max-multiplier", 2.0));
        broadcast = c.getBoolean("rotation.broadcast", true);
        marketEnabled = c.getBoolean("market.enabled", true);
        capacity = Math.max(10, c.getInt("market.capacity", 3000));
        floor = Math.min(1.0, Math.max(0.05, c.getDouble("market.floor", 0.70)));
        recoveryPerHour = Math.max(0, c.getDouble("market.recovery-per-hour", 600));

        crops.clear();
        byMaterial.clear();
        ConfigurationSection sec = c.getConfigurationSection("crops");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                List<Material> mats = new ArrayList<>();
                for (String m : s.getStringList("materials")) {
                    Material mat = Material.matchMaterial(m);
                    if (mat == null) {
                        plugin.getLogger().warning("crops." + id + ": unknown material '" + m + "'");
                    } else {
                        mats.add(mat);
                    }
                }
                if (mats.isEmpty()) {
                    continue;
                }
                String key = id.toLowerCase(Locale.ROOT);
                String name = s.getString("name", id);
                Crop crop = new Crop(key, sc ? SmallCaps.convert(name) : name, mats,
                        s.getDouble("price", 0), s.getInt("capacity", 0));
                crops.put(key, crop);
                for (Material mat : mats) {
                    byMaterial.putIfAbsent(mat, crop);
                }
            }
        }
        buffs.keySet().removeIf(k -> !crops.containsKey(k));
        pressure.keySet().removeIf(k -> !crops.containsKey(k));
        if (rotationEnabled && nextRotation <= 0) {
            rotate();
        }
    }

    public void load() {
        buffs.clear();
        pressure.clear();
        lastBuffed.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        nextRotation = y.getLong("next-rotation", 0);
        lastBuffed.addAll(y.getStringList("last-buffed"));
        ConfigurationSection b = y.getConfigurationSection("buffs");
        if (b != null) {
            for (String k : b.getKeys(false)) {
                buffs.put(k, new Buff(b.getDouble(k + ".mult", 1), b.getLong(k + ".ends", 0), b.getBoolean(k + ".manual", false)));
            }
        }
        ConfigurationSection p = y.getConfigurationSection("pressure");
        if (p != null) {
            for (String k : p.getKeys(false)) {
                pressure.put(k, p.getDouble(k));
            }
        }
    }

    public void flush(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-rotation", nextRotation);
        y.set("last-buffed", lastBuffed);
        for (var e : buffs.entrySet()) {
            y.set("buffs." + e.getKey() + ".mult", e.getValue().mult);
            y.set("buffs." + e.getKey() + ".ends", e.getValue().ends);
            y.set("buffs." + e.getKey() + ".manual", e.getValue().manual);
        }
        for (var e : pressure.entrySet()) {
            y.set("pressure." + e.getKey(), Math.round(e.getValue() * 10) / 10.0);
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

    // ------------------------------------------------------------------ ticking

    /** Called every 30 seconds on the main thread. */
    public void tick() {
        long now = System.currentTimeMillis();
        if (buffs.entrySet().removeIf(e -> e.getValue().ends <= now)) {
            dirty = true;
        }
        long elapsed = now - lastDecay;
        lastDecay = now;
        if (marketEnabled && recoveryPerHour > 0 && !pressure.isEmpty()) {
            double drop = recoveryPerHour * elapsed / 3_600_000.0;
            pressure.replaceAll((k, v) -> Math.max(0, v - drop));
            pressure.values().removeIf(v -> v <= 0);
            dirty = true;
        }
        if (rotationEnabled && now >= nextRotation) {
            rotate();
        }
    }

    public void rotate() {
        long now = System.currentTimeMillis();
        buffs.values().removeIf(b -> !b.manual);
        List<Crop> pool = new ArrayList<>();
        for (Crop c : crops.values()) {
            if (!buffs.containsKey(c.id())) {
                pool.add(c);
            }
        }
        List<Crop> picked = new ArrayList<>();
        int n = Math.min(count, pool.size());
        for (int i = 0; i < n; i++) {
            double total = 0;
            for (Crop c : pool) {
                total += weight(c);
            }
            double r = random.nextDouble() * total;
            Crop chosen = pool.get(pool.size() - 1);
            for (Crop c : pool) {
                r -= weight(c);
                if (r < 0) {
                    chosen = c;
                    break;
                }
            }
            pool.remove(chosen);
            picked.add(chosen);
        }
        lastBuffed.clear();
        for (Crop c : picked) {
            double mult = Math.round((minMult + random.nextDouble() * (maxMult - minMult)) * 20) / 20.0;
            buffs.put(c.id(), new Buff(Math.max(1.05, mult), now + intervalMs, false));
            lastBuffed.add(c.id());
        }
        nextRotation = now + intervalMs;
        dirty = true;
        flush(true);
        if (broadcast && !picked.isEmpty()) {
            announce(picked);
        }
    }

    /** Crops that were just buffed are less likely to be picked again right away. */
    private double weight(Crop c) {
        return lastBuffed.contains(c.id()) ? 0.4 : 1.0;
    }

    private void announce(List<Crop> picked) {
        var m = plugin.messages();
        for (String line : m.list("rotate.header")) {
            Bukkit.broadcast(Text.parse(line));
        }
        long now = System.currentTimeMillis();
        for (Crop c : picked) {
            Buff b = buffs.get(c.id());
            if (b == null) {
                continue;
            }
            Bukkit.broadcast(Text.parse(m.format("rotate.line", priceVars(c, now))));
        }
        for (String line : m.list("rotate.footer")) {
            Bukkit.broadcast(Text.parse(line));
        }
    }

    // ------------------------------------------------------------------ admin actions

    public void buff(Crop c, double mult, long durationMs) {
        buffs.put(c.id(), new Buff(Math.max(1.0, mult), System.currentTimeMillis() + durationMs, true));
        dirty = true;
        flush(true);
    }

    public boolean unbuff(Crop c) {
        boolean removed = buffs.remove(c.id()) != null;
        dirty = true;
        flush(true);
        return removed;
    }

    public void resetMarket() {
        pressure.clear();
        dirty = true;
        flush(true);
    }

    // ------------------------------------------------------------------ prices

    public Crop cropOf(Material m) {
        return byMaterial.get(m);
    }

    public Crop crop(String id) {
        return id == null ? null : crops.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Crop> crops() {
        return new ArrayList<>(crops.values());
    }

    public Buff buffOf(Crop c) {
        Buff b = buffs.get(c.id());
        return b != null && b.ends > System.currentTimeMillis() ? b : null;
    }

    public double buffMult(Crop c) {
        Buff b = buffOf(c);
        return b == null ? 1.0 : b.mult;
    }

    /** 1.0 = no pressure, down to the configured floor when the market is saturated. */
    public double saturation(Crop c) {
        if (!marketEnabled) {
            return 1.0;
        }
        int cap = c.capacity() > 0 ? c.capacity() : capacity;
        double ratio = Math.min(1.0, pressure.getOrDefault(c.id(), 0.0) / cap);
        return 1.0 - ratio * (1.0 - floor);
    }

    /** Total price factor: buff x saturation. */
    public double factor(Crop c) {
        return buffMult(c) * saturation(c);
    }

    /** Called after a successful sale. */
    public void sold(Crop c, int amount) {
        if (!marketEnabled || amount <= 0) {
            return;
        }
        int cap = c.capacity() > 0 ? c.capacity() : capacity;
        pressure.merge(c.id(), (double) amount, Double::sum);
        pressure.computeIfPresent(c.id(), (k, v) -> Math.min(v, cap * 1.2));
        dirty = true;
    }

    /** Normal sell price of the crop's first material that the shop knows, or -1. */
    public double basePrice(Crop c) {
        if (c.priceOverride() > 0) {
            return c.priceOverride();
        }
        for (Material m : c.materials()) {
            double p = plugin.prices().sellPrice(m);
            if (p > 0) {
                return p;
            }
        }
        return -1;
    }

    /** Base price of one specific material (config override scales the crop's first material). */
    public double basePrice(Crop c, Material m) {
        double shop = plugin.prices().sellPrice(m);
        if (c.priceOverride() > 0 && m == c.materials().get(0)) {
            return c.priceOverride();
        }
        return shop;
    }

    public long nextRotation() {
        return rotationEnabled ? nextRotation : 0;
    }

    public boolean rotationEnabled() {
        return rotationEnabled;
    }

    // ------------------------------------------------------------------ formatting

    public String fmt(double price) {
        return "$" + PRICE.format(price);
    }

    public String priceText(double price) {
        return price < 0 ? plugin.messages().raw("ui.unknown-price") : fmt(price);
    }

    /** Percent change vs normal, like "+67" or "-12". */
    public String pct(double factor) {
        long p = Math.round((factor - 1.0) * 100);
        return (p > 0 ? "+" : "") + p;
    }

    /** Placeholders shared by the rotate announcement and the menu rows. */
    public String[] priceVars(Crop c, long now) {
        double base = basePrice(c);
        double f = factor(c);
        Buff b = buffOf(c);
        return new String[]{
                "crop", c.name(),
                "base", priceText(base),
                "price", base < 0 ? "x" + String.format(Locale.ROOT, "%.2f", f) : fmt(base * f),
                "pct", pct(f),
                "mult", String.format(Locale.ROOT, "%.2f", f),
                "time", b == null ? "-" : TimeUtil.left(plugin, b.ends - now)
        };
    }
}
