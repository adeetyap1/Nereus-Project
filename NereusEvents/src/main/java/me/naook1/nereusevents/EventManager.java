package me.naook1.nereusevents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Holds event definitions and the running events; ticks once per second. */
public final class EventManager {

    public record Def(String id, String name, BossBar.Color color, int minutes, Material icon, int weight,
                      boolean rain, boolean lights, String sound, int soundLength, String subtitle, List<String> lore,
                      Map<String, Double> effects) {
    }

    public static final class Active {
        final Def def;
        final long start;
        long end;
        final BossBar bar;

        Active(Def def, long start, long end, BossBar bar) {
            this.def = def;
            this.start = start;
            this.end = end;
            this.bar = bar;
        }

        public Def def() {
            return def;
        }

        public long end() {
            return end;
        }
    }

    private record RainItem(Material material, String name, int weight) {
    }

    private final NereusEvents plugin;
    private final Random random = new Random();
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<String, Active> active = new LinkedHashMap<>();
    private final List<RainItem> rainItems = new ArrayList<>();
    private final List<Item> rainDrops = new ArrayList<>();
    private final NamespacedKey rainKey;
    private long nextAuto;
    private final Map<java.util.UUID, List<Location>> lit = new java.util.HashMap<>();
    private int discoStep;
    /** sound key -> (player -> time the song ends for them). Events sharing a song share one track. */
    private final Map<String, Map<java.util.UUID, Long>> tracks = new java.util.HashMap<>();

    public EventManager(NereusEvents plugin) {
        this.plugin = plugin;
        this.rainKey = new NamespacedKey(plugin, "rain");
    }

    // ---------------------------------------------------------------- config

    public void reload() {
        defs.clear();
        rainItems.clear();
        var cfg = plugin.getConfig();
        ConfigurationSection es = cfg.getConfigurationSection("events");
        if (es != null) {
            for (String id : es.getKeys(false)) {
                ConfigurationSection s = es.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                BossBar.Color color;
                try {
                    color = BossBar.Color.valueOf(s.getString("bar-color", "YELLOW").toUpperCase());
                } catch (IllegalArgumentException ex) {
                    color = BossBar.Color.YELLOW;
                }
                Material icon = Material.matchMaterial(s.getString("icon", "NETHER_STAR"));
                Map<String, Double> eff = new LinkedHashMap<>();
                ConfigurationSection e = s.getConfigurationSection("effects");
                if (e != null) {
                    for (String k : e.getKeys(false)) {
                        eff.put(k.toLowerCase(), e.getDouble(k));
                    }
                }
                defs.put(id.toLowerCase(), new Def(id.toLowerCase(), s.getString("name", id), color,
                        Math.max(1, s.getInt("minutes", 5)), icon == null ? Material.NETHER_STAR : icon,
                        s.getInt("weight", 10), s.getBoolean("rain", false), s.getBoolean("lights", true), s.getString("sound", ""),
                        s.getInt("sound-length", 0), s.getString("subtitle", ""), s.getStringList("lore"), eff));
            }
        }
        for (String line : cfg.getStringList("rain.items")) {
            String[] p = line.split(":");
            Material m = Material.matchMaterial(p[0]);
            if (m != null) {
                rainItems.add(new RainItem(m, p.length > 1 ? p[1] : "", p.length > 2 ? parse(p[2]) : 10));
            }
        }
        if (rainItems.isEmpty()) {
            rainItems.add(new RainItem(Material.COOKED_BEEF, "&eTaco", 1));
        }
        scheduleAuto();
        plugin.getLogger().info("Loaded " + defs.size() + " events.");
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    public Collection<Def> defs() {
        return defs.values();
    }

    public Def def(String id) {
        return id == null ? null : defs.get(id.toLowerCase());
    }

    public Collection<Active> active() {
        return active.values();
    }

    public boolean isActive(String id) {
        return active.containsKey(id.toLowerCase());
    }

    public long nextAuto() {
        return nextAuto;
    }

    // ---------------------------------------------------------------- API values

    /** Product of every active event's value for the key (1.0 when none). */
    public double multiplier(String key) {
        double m = 1.0;
        for (Active a : active.values()) {
            Double v = a.def.effects().get(key);
            if (v != null && v > 0) {
                m *= v;
            }
        }
        return m;
    }

    /** Sum of every active event's value for the key (0.0 when none). */
    public double bonus(String key) {
        double b = 0;
        for (Active a : active.values()) {
            Double v = a.def.effects().get(key);
            if (v != null) {
                b += v;
            }
        }
        return b;
    }

    // ---------------------------------------------------------------- start / stop

    public boolean start(Def def, int minutes) {
        if (active.containsKey(def.id())) {
            return false;
        }
        long now = System.currentTimeMillis();
        long end = now + minutes * 60_000L;
        BossBar bar = BossBar.bossBar(Text.parse(def.name()), 1f, def.color(), BossBar.Overlay.PROGRESS);
        Active a = new Active(def, now, end, bar);
        active.put(def.id(), a);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            p.showTitle(Title.title(Text.parse(def.name()), Text.parse(def.subtitle()),
                    Title.Times.times(java.time.Duration.ofMillis(300), java.time.Duration.ofSeconds(4),
                            java.time.Duration.ofMillis(800))));
        }
        var m = plugin.messages();
        Bukkit.broadcast(Text.parse(m.get("prefix") + Text.fill(m.get("started"), "name", def.name(),
                "minutes", String.valueOf(minutes))));
        for (String l : def.lore()) {
            Bukkit.broadcast(Text.parse("&8  » " + l));
        }
        playTracks();
        return true;
    }

    public boolean stop(String id) {
        Active a = active.remove(id.toLowerCase());
        if (a == null) {
            return false;
        }
        finish(a, false);
        return true;
    }

    public void stopAll() {
        clearLights();
        for (Active a : new ArrayList<>(active.values())) {
            active.remove(a.def.id());
            finish(a, false);
        }
        clearRain();
    }

    private void finish(Active a, boolean natural) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(a.bar);
        }
        String key = a.def.sound();
        if (key != null && !key.isEmpty() && active.values().stream().noneMatch(o -> key.equals(o.def.sound()))) {
            tracks.remove(key);
            stopSong(key);
        }
        var m = plugin.messages();
        Bukkit.broadcast(Text.parse(m.get("prefix") + Text.fill(m.get(natural ? "ended" : "stopped"), "name", a.def.name())));
        if (!hasRain()) {
            clearRain();
        }
    }

    /** Stops the song for everybody (any category), so it can never keep playing after the event. */
    private void stopSong(String key) {
        SoundCategory cat;
        try {
            cat = SoundCategory.valueOf(plugin.getConfig().getString("sound.category", "RECORDS").toUpperCase());
        } catch (IllegalArgumentException e) {
            cat = SoundCategory.RECORDS;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.stopSound(key);
            p.stopSound(key, cat);
            p.stopSound(cat);
        }
    }

    private boolean hasRain() {
        for (Active a : active.values()) {
            if (a.def.rain()) {
                return true;
            }
        }
        return false;
    }

    public void showBars(Player p) {
        for (Active a : active.values()) {
            p.showBossBar(a.bar);
        }
    }

    // ---------------------------------------------------------------- sound

    /**
     * Keeps every song of the running events playing: starts it for players that are not hearing it and, with
     * sound-length > 0, starts it again when it has finished. Events with the same song share one track, so the
     * song never overlaps itself and is only stopped when no running event uses it any more.
     */
    private void playTracks() {
        Map<String, Integer> keys = new LinkedHashMap<>();
        for (Active a : active.values()) {
            String key = a.def.sound();
            if (key != null && !key.isEmpty()) {
                keys.merge(key, a.def.soundLength(), Math::max);
            }
        }
        tracks.keySet().removeIf(k -> !keys.containsKey(k));
        float vol = (float) plugin.getConfig().getDouble("sound.volume", 1.0);
        SoundCategory cat;
        try {
            cat = SoundCategory.valueOf(plugin.getConfig().getString("sound.category", "RECORDS").toUpperCase());
        } catch (IllegalArgumentException e) {
            cat = SoundCategory.RECORDS;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Integer> en : keys.entrySet()) {
            Map<java.util.UUID, Long> track = tracks.computeIfAbsent(en.getKey(), k -> new java.util.HashMap<>());
            for (Player p : Bukkit.getOnlinePlayers()) {
                Long next = track.get(p.getUniqueId());
                if (next != null && now < next) {
                    continue;
                }
                p.playSound(p.getLocation(), en.getKey(), cat, vol, 1f);
                track.put(p.getUniqueId(), en.getValue() > 0 ? now + en.getValue() * 1000L + 1000L : Long.MAX_VALUE);
            }
        }
    }

    /** Joiners get the songs on the next tick. */
    public void playFor(Player p) {
        for (Map<java.util.UUID, Long> t : tracks.values()) {
            t.remove(p.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- disco lights

    private boolean hasLights() {
        if (!plugin.getConfig().getBoolean("lights.enabled", true)) {
            return false;
        }
        for (Active a : active.values()) {
            if (a.def.lights()) {
                return true;
            }
        }
        return false;
    }

    private static final BossBar.Color[] BAR_COLORS = {BossBar.Color.RED, BossBar.Color.YELLOW, BossBar.Color.GREEN,
            BossBar.Color.BLUE, BossBar.Color.PURPLE, BossBar.Color.PINK};

    /** Colourful blinking: dust particles, client-side light blocks and a colour-cycling boss bar. */
    public void disco() {
        if (!hasLights()) {
            if (!lit.isEmpty()) {
                clearLights();
            }
            return;
        }
        discoStep++;
        var cfg = plugin.getConfig();
        int blocks = cfg.getInt("lights.light-blocks", 5);
        int radius = cfg.getInt("lights.radius", 6);
        int particles = cfg.getInt("lights.particles", 12);
        boolean on = discoStep % 2 == 0;
        int rgb = java.awt.Color.HSBtoRGB((discoStep * 0.11f) % 1f, 1f, 1f) & 0xFFFFFF;
        org.bukkit.Color col = org.bukkit.Color.fromRGB(rgb);
        for (Active a : active.values()) {
            if (a.def.lights()) {
                a.bar.color(BAR_COLORS[discoStep % BAR_COLORS.length]);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            List<Location> old = lit.remove(p.getUniqueId());
            if (old != null) {
                for (Location l : old) {
                    p.sendBlockChange(l, l.getBlock().getBlockData());
                }
            }
            Location base = p.getLocation().add(0, 1, 0);
            if (blocks > 0 && on) {
                List<Location> now = new ArrayList<>();
                for (int i = 0; i < blocks; i++) {
                    Location l = base.clone().add(random.nextInt(radius * 2 + 1) - radius,
                            random.nextInt(5) - 1, random.nextInt(radius * 2 + 1) - radius);
                    if (l.getBlock().getType().isAir()) {
                        org.bukkit.block.data.type.Light light = (org.bukkit.block.data.type.Light)
                                Material.LIGHT.createBlockData();
                        light.setLevel(15);
                        p.sendBlockChange(l, light);
                        now.add(l);
                    }
                }
                lit.put(p.getUniqueId(), now);
            }
            if (particles > 0) {
                p.getWorld().spawnParticle(Particle.DUST, base, particles, radius / 2.0, 1.2, radius / 2.0, 0,
                        new Particle.DustOptions(col, 1.6f), false);
                if (discoStep % 3 == 0) {
                    p.getWorld().spawnParticle(Particle.FIREWORK, base.clone().add(0, 1, 0), 4, 1.5, 1, 1.5, 0.05);
                }
            }
        }
    }

    private void clearLights() {
        for (Map.Entry<java.util.UUID, List<Location>> en : lit.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) {
                for (Location l : en.getValue()) {
                    p.sendBlockChange(l, l.getBlock().getBlockData());
                }
            }
        }
        lit.clear();
        for (Active a : active.values()) {
            a.bar.color(a.def.color());
        }
    }

    // ---------------------------------------------------------------- ticking

    public void tick() {
        long now = System.currentTimeMillis();
        for (Active a : new ArrayList<>(active.values())) {
            long total = a.end - a.start;
            long left = a.end - now;
            if (left <= 0) {
                active.remove(a.def.id());
                finish(a, true);
                continue;
            }
            a.bar.progress(Math.max(0f, Math.min(1f, (float) left / total)));
            a.bar.name(Text.parse(a.def.name() + " &8| &f" + fmt(left)));

        }
        playTracks();
        if (hasRain()) {
            rain();
        }
        rainDrops.removeIf(i -> !i.isValid());
        if (nextAuto > 0 && now >= nextAuto) {
            autoStart();
        }
    }

    public static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // ---------------------------------------------------------------- rain

    private void rain() {
        var cfg = plugin.getConfig();
        int per = cfg.getInt("rain.per-second", 3);
        int radius = cfg.getInt("rain.radius", 7);
        int height = cfg.getInt("rain.height", 14);
        int max = cfg.getInt("rain.max-entities", 200);
        int life = cfg.getInt("rain.life-seconds", 25);
        int totalW = 0;
        for (RainItem r : rainItems) {
            totalW += r.weight();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            for (int i = 0; i < per && rainDrops.size() < max; i++) {
                Location base = p.getLocation();
                Location at = base.clone().add((random.nextDouble() * 2 - 1) * radius, height,
                        (random.nextDouble() * 2 - 1) * radius);
                if (!at.getBlock().isPassable()) {
                    continue;
                }
                int pick = random.nextInt(Math.max(1, totalW));
                RainItem chosen = rainItems.get(0);
                for (RainItem r : rainItems) {
                    pick -= r.weight();
                    chosen = r;
                    if (pick < 0) {
                        break;
                    }
                }
                ItemStack stack = new ItemStack(chosen.material());
                ItemMeta meta = stack.getItemMeta();
                if (!chosen.name().isEmpty()) {
                    meta.displayName(Text.item(chosen.name()));
                }
                meta.getPersistentDataContainer().set(rainKey, PersistentDataType.BYTE, (byte) 1);
                stack.setItemMeta(meta);
                Item drop = at.getWorld().dropItem(at, stack);
                drop.setVelocity(new org.bukkit.util.Vector(0, -0.1, 0));
                drop.setPickupDelay(10);
                drop.setGlowing(true);
                drop.setTicksLived(Math.max(1, 6000 - life * 20));
                rainDrops.add(drop);
                at.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 2, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    private void clearRain() {
        for (Item i : rainDrops) {
            if (i.isValid()) {
                i.remove();
            }
        }
        rainDrops.clear();
    }

    // ---------------------------------------------------------------- auto schedule

    public void scheduleAuto() {
        var c = plugin.getConfig();
        if (!c.getBoolean("auto.enabled", true)) {
            nextAuto = 0;
            return;
        }
        int min = Math.max(1, c.getInt("auto.min-minutes", 30));
        int max = Math.max(min, c.getInt("auto.max-minutes", 60));
        nextAuto = System.currentTimeMillis() + (min + random.nextInt(max - min + 1)) * 60_000L;
    }

    private void autoStart() {
        scheduleAuto();
        if (Bukkit.getOnlinePlayers().size() < plugin.getConfig().getInt("auto.min-players", 1)) {
            return;
        }
        randomStart();
    }

    public Def randomStart() {
        List<Def> pool = new ArrayList<>();
        int total = 0;
        for (Def d : defs.values()) {
            if (!active.containsKey(d.id()) && d.weight() > 0) {
                pool.add(d);
                total += d.weight();
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        int pick = random.nextInt(total);
        for (Def d : pool) {
            pick -= d.weight();
            if (pick < 0) {
                start(d, d.minutes());
                return d;
            }
        }
        return null;
    }
}
