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
                      boolean rain, boolean lights, String sound, double soundLength, String subtitle, List<String> lore,
                      Map<String, Double> effects) {
    }

    public static final class Active {
        final Def def;
        final long start;
        long end;
        final BossBar bar;
        final BossBar boost;

        Active(Def def, long start, long end, BossBar bar, BossBar boost) {
            this.def = def;
            this.start = start;
            this.end = end;
            this.bar = bar;
            this.boost = boost;
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
    private int discoStep;
    private boolean skyActive;
    /** players that just joined: the song waits until their resource pack is loaded. */
    private final Map<java.util.UUID, Long> waitUntil = new java.util.HashMap<>();
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
                        s.getDouble("sound-length", 0), s.getString("subtitle", ""), s.getStringList("lore"), eff));
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
        BossBar boost = BossBar.bossBar(Text.parse(effectLine(def)), 1f, def.color(), BossBar.Overlay.PROGRESS);
        Active a = new Active(def, now, end, bar, boost);
        active.put(def.id(), a);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
            p.showBossBar(boost);
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
        applyLuck();
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
        for (Active a : new ArrayList<>(active.values())) {
            active.remove(a.def.id());
            finish(a, false);
        }
        clearRain();
    }

    private void finish(Active a, boolean natural) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(a.bar);
            p.hideBossBar(a.boost);
        }
        applyLuck();
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
            p.showBossBar(a.boost);
        }
    }

    // ---------------------------------------------------------------- sound

    /**
     * Keeps every song of the running events playing: starts it for players that are not hearing it and, with
     * sound-length > 0, starts it again slightly BEFORE it ends (sound.overlap-ms), so the loop has no silent gap.
     * Events with the same song share one track, so the song never overlaps itself and is only stopped when no
     * running event uses it any more. Runs every 2 ticks.
     */
    public void playTracks() {
        if (active.isEmpty()) {
            return;
        }
        Map<String, Double> keys = new LinkedHashMap<>();
        for (Active a : active.values()) {
            String key = a.def.sound();
            if (key != null && !key.isEmpty()) {
                keys.merge(key, a.def.soundLength(), Math::max);
            }
        }
        tracks.keySet().removeIf(k -> !keys.containsKey(k));
        float vol = (float) plugin.getConfig().getDouble("sound.volume", 1.0);
        long overlap = plugin.getConfig().getLong("sound.overlap-ms", 350);
        SoundCategory cat;
        try {
            cat = SoundCategory.valueOf(plugin.getConfig().getString("sound.category", "RECORDS").toUpperCase());
        } catch (IllegalArgumentException e) {
            cat = SoundCategory.RECORDS;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Double> en : keys.entrySet()) {
            Map<java.util.UUID, Long> track = tracks.computeIfAbsent(en.getKey(), k -> new java.util.HashMap<>());
            for (Player p : Bukkit.getOnlinePlayers()) {
                Long wait = waitUntil.get(p.getUniqueId());
                if (wait != null) {
                    if (now < wait) {
                        continue;
                    }
                    waitUntil.remove(p.getUniqueId());
                }
                Long next = track.get(p.getUniqueId());
                if (next != null && now < next) {
                    continue;
                }
                p.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key(en.getKey()), source(cat), vol, 1f),
                        net.kyori.adventure.sound.Sound.Emitter.self());
                track.put(p.getUniqueId(), en.getValue() > 0
                        ? now + Math.max(1000L, (long) (en.getValue() * 1000.0) - overlap) : Long.MAX_VALUE);
            }
        }
    }

    private static net.kyori.adventure.sound.Sound.Source source(SoundCategory c) {
        return switch (c) {
            case MASTER -> net.kyori.adventure.sound.Sound.Source.MASTER;
            case MUSIC -> net.kyori.adventure.sound.Sound.Source.MUSIC;
            case WEATHER -> net.kyori.adventure.sound.Sound.Source.WEATHER;
            case BLOCKS -> net.kyori.adventure.sound.Sound.Source.BLOCK;
            case HOSTILE -> net.kyori.adventure.sound.Sound.Source.HOSTILE;
            case NEUTRAL -> net.kyori.adventure.sound.Sound.Source.NEUTRAL;
            case PLAYERS -> net.kyori.adventure.sound.Sound.Source.PLAYER;
            case AMBIENT -> net.kyori.adventure.sound.Sound.Source.AMBIENT;
            case VOICE -> net.kyori.adventure.sound.Sound.Source.VOICE;
            default -> net.kyori.adventure.sound.Sound.Source.RECORD;
        };
    }

    /** players whose server resource pack is already loaded this session. */
    private final java.util.Set<java.util.UUID> packReady = new java.util.HashSet<>();

    /** Teleport / world change: restart the song for them so it never goes silent. */
    public void moved(Player p) {
        for (String k : tracks.keySet()) {
            p.stopSound(k);
        }
        waitUntil.put(p.getUniqueId(), System.currentTimeMillis() + 500L);
        for (Map<java.util.UUID, Long> t : tracks.values()) {
            t.remove(p.getUniqueId());
        }
    }

    /** A player joined: the song restarts for them once their resource pack is loaded (Bedrock: a few seconds). */
    public void joined(Player p) {
        for (Map<java.util.UUID, Long> t : tracks.values()) {
            t.remove(p.getUniqueId());
        }
        boolean bedrock = p.getUniqueId().getMostSignificantBits() == 0L;
        long wait = bedrock ? 4000L : packReady.contains(p.getUniqueId()) ? 2500L : 20000L;
        waitUntil.put(p.getUniqueId(), System.currentTimeMillis() + wait);
        showBars(p);
    }

    /** Java client finished loading the server resource pack: play the song now (small delay for the audio engine). */
    public void packLoaded(Player p) {
        packReady.add(p.getUniqueId());
        waitUntil.put(p.getUniqueId(), System.currentTimeMillis() + 1500L);
        for (Map<java.util.UUID, Long> t : tracks.values()) {
            t.remove(p.getUniqueId());
        }
    }

    public void left(Player p) {
        packReady.remove(p.getUniqueId());
        clearLuck(p);
        waitUntil.remove(p.getUniqueId());
        for (Map<java.util.UUID, Long> t : tracks.values()) {
            t.remove(p.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- disco lights (sky)

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

    /** Blinking sky: colour-cycling sky time, light beams and fireworks high above, colour-cycling boss bar. */
    public void disco() {
        if (!hasLights()) {
            if (skyActive) {
                clearLights();
            }
            return;
        }
        skyActive = true;
        discoStep++;
        var cfg = plugin.getConfig();
        int beams = cfg.getInt("lights.beams", 4);
        int bursts = cfg.getInt("lights.sky-bursts", 3);
        boolean flash = cfg.getBoolean("lights.sky-flash", true);
        List<Long> times = cfg.getLongList("lights.sky-times");
        if (times.isEmpty()) {
            times = List.of(12600L, 13500L, 18000L, 22800L);
        }
        for (Active a : active.values()) {
            if (a.def.lights()) {
                a.bar.color(BAR_COLORS[discoStep % BAR_COLORS.length]);
                a.boost.color(BAR_COLORS[(discoStep + 3) % BAR_COLORS.length]);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) {
                continue;
            }
            if (flash) {
                p.setPlayerTime(times.get(discoStep % times.size()), false);
            }
            Location base = p.getLocation();
            // rotating light beams standing on the ground and shooting up into the sky
            for (int b = 0; b < beams; b++) {
                double ang = Math.toRadians(discoStep * 17 + b * (360.0 / Math.max(1, beams)));
                double dist = 9 + 5 * Math.sin(discoStep * 0.3 + b);
                double x = base.getX() + Math.cos(ang) * dist;
                double z = base.getZ() + Math.sin(ang) * dist;
                int y0 = p.getWorld().getHighestBlockYAt((int) x, (int) z) + 1;
                int rgb = java.awt.Color.HSBtoRGB(((discoStep * 0.09f) + b / (float) Math.max(1, beams)) % 1f, 1f, 1f) & 0xFFFFFF;
                Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(rgb), 2.4f);
                for (int h = 0; h < 22; h += 2) {
                    p.spawnParticle(Particle.DUST, x, y0 + h, z, 2, 0.15, 0.4, 0.15, 0, dust);
                }
            }
            // colourful bursts high in the sky
            for (int i = 0; i < bursts; i++) {
                double x = base.getX() + (random.nextDouble() * 2 - 1) * 14;
                double z = base.getZ() + (random.nextDouble() * 2 - 1) * 14;
                double y = base.getY() + 16 + random.nextDouble() * 10;
                int rgb = java.awt.Color.HSBtoRGB(random.nextFloat(), 1f, 1f) & 0xFFFFFF;
                p.spawnParticle(Particle.DUST, x, y, z, 40, 2.2, 1.6, 2.2, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(rgb), 3.5f));
                if (discoStep % 2 == 0) {
                    p.spawnParticle(Particle.FIREWORK, x, y, z, 10, 1.2, 1.2, 1.2, 0.08);
                }
            }
        }
    }

    private void clearLights() {
        skyActive = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.resetPlayerTime();
        }
        for (Active a : active.values()) {
            a.bar.color(a.def.color());
            a.boost.color(a.def.color());
        }
    }

    // ---------------------------------------------------------------- boosts (second boss bar, under the event bar)

    private static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.US, "%.1f", v);
    }

    /** Text of the boost bar of one event: its real boosts. */
    public static String effectLine(Def def) {
        List<String> parts = new ArrayList<>();
        Map<String, Double> e = def.effects();
        Double v = e.get("fishing_luck");
        if (v != null && v != 0) {
            parts.add("&bLuck &a+" + num(v));
        }
        v = e.get("fishing_speed");
        if (v != null && v != 0) {
            parts.add("&bGigitan &a+" + num(v) + "%");
        }
        v = e.get("fishing_sell");
        if (v != null && v != 1.0) {
            parts.add("&bJual Ikan &a×" + num(v));
        }
        v = e.get("fishing_weight");
        if (v != null && v != 1.0) {
            parts.add("&bBerat Ikan &a×" + num(v));
        }
        v = e.get("farm_sell");
        if (v != null && v != 1.0) {
            parts.add("&eJual Farm &a×" + num(v));
        }
        v = e.get("lottery_pot");
        if (v != null && v != 1.0) {
            parts.add("&dHadiah Undian &a×" + num(v));
        }
        return parts.isEmpty() ? "&7Tanpa bonus" : String.join(" &8| ", parts);
    }

    /** Combined boosts (used by the GUI / command). */
    public String boostLine() {
        if (active.isEmpty()) {
            return "";
        }
        return String.join(" &8| ", active.values().stream().map(a -> effectLine(a.def)).toList());
    }

    private final org.bukkit.NamespacedKey luckKey = new org.bukkit.NamespacedKey("nereusevents", "event_luck");
    private double appliedLuck = 0;

    /**
     * Gives every online player the real vanilla Luck attribute while an event with fishing_luck is running, so luck
     * works for vanilla fishing and for other fishing plugins too (NereusFishing also reads fishing_luck itself).
     */
    public void applyLuck() {
        double luck = bonus("fishing_luck");
        for (Player p : Bukkit.getOnlinePlayers()) {
            var attr = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_LUCK);
            if (attr == null) {
                continue;
            }
            org.bukkit.attribute.AttributeModifier cur = null;
            for (var mod : attr.getModifiers()) {
                if (luckKey.equals(mod.getKey())) {
                    cur = mod;
                    break;
                }
            }
            if (cur != null && cur.getAmount() == luck) {
                continue;
            }
            if (cur != null) {
                attr.removeModifier(cur);
            }
            if (luck != 0) {
                attr.addModifier(new org.bukkit.attribute.AttributeModifier(luckKey, luck,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        appliedLuck = luck;
    }

    /** Removes the event luck from a player (quit / plugin disable). */
    public void clearLuck(Player p) {
        var attr = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_LUCK);
        if (attr == null) {
            return;
        }
        for (var mod : new ArrayList<>(attr.getModifiers())) {
            if (luckKey.equals(mod.getKey())) {
                attr.removeModifier(mod);
            }
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
            float prog = Math.max(0f, Math.min(1f, (float) left / total));
            a.bar.progress(prog);
            a.boost.progress(prog);
            a.bar.name(Text.parse(a.def.name() + " &8| &f" + fmt(left)));

        }
        applyLuck();
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
