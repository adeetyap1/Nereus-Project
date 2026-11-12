package com.nereus.pets;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Pet liar yang tersebar di map, dan event pet Legendaris setiap 10 menit. */
public final class Wild {
    private final NereusPets plugin;
    private final PetManager mgr;

    private final Set<UUID> wilds = new HashSet<>();
    private UUID legendary;
    private long nextLegend;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public Wild(NereusPets plugin, PetManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stopTasks();
        if (!plugin.getConfig().getBoolean("wild.enabled", true)) {
            return;
        }
        long spawnEvery = Math.max(5, plugin.getConfig().getLong("wild.interval-seconds", 60)) * 20L;
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::spawnRound, spawnEvery, spawnEvery));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 100L, 100L));
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, this::aura, 40L, 20L));
        if (plugin.getConfig().getBoolean("legendary.enabled", true)) {
            long every = Math.max(30, plugin.getConfig().getLong("legendary.interval-seconds", 600)) * 20L;
            nextLegend = System.currentTimeMillis() + every * 50L;
            tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                nextLegend = System.currentTimeMillis() + every * 50L;
                legendaryRound();
            }, every, every));
        }
    }

    private void stopTasks() {
        for (BukkitTask t : tasks) {
            t.cancel();
        }
        tasks.clear();
    }

    /** Hentikan semua dan hapus pet liar yang masih ada. */
    public void stop() {
        stopTasks();
        for (UUID id : new ArrayList<>(wilds)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        wilds.clear();
        legendary = null;
    }

    public boolean tracks(UUID id) {
        return wilds.contains(id);
    }

    public void forget(UUID id) {
        wilds.remove(id);
        if (id.equals(legendary)) {
            legendary = null;
        }
    }

    // ------------------------------------------------------------------ habitat & pilihan jenis

    public static String habitatOf(World w) {
        return switch (w.getEnvironment()) {
            case NETHER -> "NETHER";
            case THE_END -> "END";
            default -> "OVERWORLD";
        };
    }

    private boolean fits(Species sp, World w) {
        String h = sp.habitat() == null ? "OVERWORLD" : sp.habitat().toUpperCase(Locale.ROOT);
        return h.equals("ANY") || h.equals(habitatOf(w));
    }

    private double speciesWeight(Species sp) {
        return plugin.getConfig().getDouble("species." + sp.type().name() + ".wild-weight", 1.0);
    }

    private Species pickWeighted(List<Species> list) {
        double total = 0;
        for (Species s : list) {
            total += Math.max(0, speciesWeight(s));
        }
        if (total <= 0) {
            return list.isEmpty() ? null : list.get(ThreadLocalRandom.current().nextInt(list.size()));
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (Species s : list) {
            r -= Math.max(0, speciesWeight(s));
            if (r <= 0) {
                return s;
            }
        }
        return list.get(list.size() - 1);
    }

    private Species pickNormal(World w) {
        Map<Rarity, List<Species>> byRarity = new EnumMap<>(Rarity.class);
        for (java.util.Map.Entry<org.bukkit.entity.EntityType, Species> en : speciesView().entrySet()) {
            Species sp = en.getValue();
            if (fits(sp, w)) {
                byRarity.computeIfAbsent(sp.rarity(), k -> new ArrayList<>()).add(sp);
            }
        }
        double total = 0;
        Map<Rarity, Double> weights = new EnumMap<>(Rarity.class);
        for (Rarity r : Rarity.values()) {
            double wgt = plugin.getConfig().getDouble("wild.rarity-weights." + r.name(), 0);
            if (wgt > 0 && byRarity.containsKey(r)) {
                weights.put(r, wgt);
                total += wgt;
            }
        }
        if (total <= 0) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * total;
        for (Map.Entry<Rarity, Double> en : weights.entrySet()) {
            roll -= en.getValue();
            if (roll <= 0) {
                return pickWeighted(byRarity.get(en.getKey()));
            }
        }
        return null;
    }

    private Species pickLegendary(World w) {
        List<Species> list = new ArrayList<>();
        for (Species sp : speciesView().values()) {
            if (sp.rarity() == Rarity.LEGENDARY && fits(sp, w)) {
                list.add(sp);
            }
        }
        return list.isEmpty() ? null : pickWeighted(list);
    }

    private Map<org.bukkit.entity.EntityType, Species> speciesView() {
        Map<org.bukkit.entity.EntityType, Species> m = new java.util.HashMap<>();
        for (org.bukkit.entity.EntityType t : mgr.speciesTypes()) {
            m.put(t, mgr.species(t));
        }
        return m;
    }

    // ------------------------------------------------------------------ spawn biasa

    private boolean eligible(Player p) {
        return p.isOnline() && !p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                && !plugin.getConfig().getStringList("disabled-worlds").contains(p.getWorld().getName());
    }

    private void spawnRound() {
        int max = plugin.getConfig().getInt("wild.max-alive", 24);
        double chance = plugin.getConfig().getDouble("wild.chance", 0.10);
        int maxNear = plugin.getConfig().getInt("wild.max-near-player", 2);
        double nearR = plugin.getConfig().getDouble("wild.near-radius", 64);
        double minD = plugin.getConfig().getDouble("wild.min-distance", 24);
        double maxD = plugin.getConfig().getDouble("wild.max-distance", 64);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (wilds.size() >= max) {
                return;
            }
            if (!eligible(p) || ThreadLocalRandom.current().nextDouble() >= chance) {
                continue;
            }
            int near = 0;
            for (Entity e : p.getNearbyEntities(nearR, nearR, nearR)) {
                if (mgr.isWild(e)) {
                    near++;
                }
            }
            if (near >= maxNear) {
                continue;
            }
            Species sp = pickNormal(p.getWorld());
            if (sp == null) {
                continue;
            }
            Location loc = findSpot(p, minD, maxD, sp.flying());
            if (loc != null) {
                spawnWild(loc, sp, false);
            }
        }
    }

    // ------------------------------------------------------------------ event Legendaris

    private void legendaryRound() {
        if (legendary != null) {
            Entity old = Bukkit.getEntity(legendary);
            if (old != null && old.isValid()) {
                return;
            }
            legendary = null;
        }
        spawnLegendaryFor(null);
    }

    /** Munculkan Legendaris dekat pemain tertentu (null = pemain acak). */
    public boolean spawnLegendaryFor(Player only) {
        List<Player> players = new ArrayList<>();
        if (only != null) {
            players.add(only);
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (eligible(p)) {
                    players.add(p);
                }
            }
            Collections.shuffle(players);
        }
        double minD = plugin.getConfig().getDouble("legendary.min-distance", 60);
        double maxD = plugin.getConfig().getDouble("legendary.max-distance", 120);
        for (Player p : players) {
            Species sp = pickLegendary(p.getWorld());
            if (sp == null) {
                continue;
            }
            Location loc = findSpot(p, minD, maxD, sp.flying());
            if (loc == null) {
                continue;
            }
            if (legendary != null) {
                Entity old = Bukkit.getEntity(legendary);
                if (old != null) {
                    old.remove();
                }
                forget(legendary);
            }
            Mob m = spawnWild(loc, sp, true);
            if (m == null) {
                continue;
            }
            announce(m, sp);
            return true;
        }
        return false;
    }

    private void announce(Mob m, Species sp) {
        if (!plugin.getConfig().getBoolean("legendary.announce", true)) {
            return;
        }
        int prec = Math.max(1, plugin.getConfig().getInt("legendary.announce-precision", 50));
        Location l = m.getLocation();
        long minutes = Math.max(1, plugin.getConfig().getLong("legendary.expire-seconds", 480) / 60);
        plugin.broadcast("legendary-spawn",
                "{pet}", sp.name(),
                "{x}", String.valueOf(Math.round(l.getX() / prec) * prec),
                "{z}", String.valueOf(Math.round(l.getZ() / prec) * prec),
                "{world}", habitatOf(l.getWorld()),
                "{minutes}", String.valueOf(minutes));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.4f);
        }
    }

    /** Lokasi kira-kira Legendaris yang sedang hidup, atau null. */
    public Location legendaryLocation() {
        if (legendary == null) {
            return null;
        }
        Entity e = Bukkit.getEntity(legendary);
        return e != null && e.isValid() ? e.getLocation() : null;
    }

    public long secondsToNextLegend() {
        return Math.max(0, (nextLegend - System.currentTimeMillis()) / 1000);
    }

    // ------------------------------------------------------------------ spawn inti

    private int rollLevel(Rarity r) {
        String spec = plugin.getConfig().getString("wild.levels." + r.name(), "1-5");
        int lo = 1;
        int hi = 5;
        try {
            String[] parts = spec.split("-");
            lo = Integer.parseInt(parts[0].trim());
            hi = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : lo;
        } catch (RuntimeException ignored) {
            // pakai bawaan
        }
        if (hi < lo) {
            hi = lo;
        }
        return lo + ThreadLocalRandom.current().nextInt(hi - lo + 1);
    }

    public Mob spawnWild(Location loc, Species sp, boolean isLegend) {
        Entity e;
        try {
            e = loc.getWorld().spawnEntity(loc, sp.type());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (!(e instanceof Mob mob)) {
            e.remove();
            return null;
        }
        int level = rollLevel(sp.rarity());
        long expire = System.currentTimeMillis() + 1000L * (isLegend
                ? plugin.getConfig().getLong("legendary.expire-seconds", 480)
                : plugin.getConfig().getLong("wild.expire-seconds", 600));
        var pdc = mob.getPersistentDataContainer();
        pdc.set(plugin.wildKey(), PersistentDataType.INTEGER, level);
        pdc.set(plugin.wildTimeKey(), PersistentDataType.LONG, expire);
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(true);
        mob.setCanPickupItems(false);
        mob.setTarget(null);
        EntityEquipment eq = mob.getEquipment();
        if (eq != null) {
            eq.clear();
        }
        if (mob instanceof Ageable a) {
            a.setAdult();
        }
        if (mob instanceof Zombie z) {
            z.setBaby(false);
        }
        if (mob instanceof org.bukkit.entity.Slime s) {
            s.setSize(3);
        }
        if (sp.controlled()) {
            mob.setAI(false);
            if (sp.flying()) {
                mob.setGravity(false);
            }
        }
        AttributeInstance scale = mob.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(sp.wildScale());
        }
        String star = isLegend ? " &6✦" : "";
        mob.customName(NereusPets.LEGACY.deserialize(sp.rarity().color + "[Lv." + level + "] &f" + sp.name() + star));
        mob.setCustomNameVisible(true);
        if (sp.rarity().ordinal() >= Rarity.EPIC.ordinal()) {
            mob.setGlowing(true);
        }
        mgr.hideBossBar(mob);
        wilds.add(mob.getUniqueId());
        if (isLegend) {
            legendary = mob.getUniqueId();
        }
        Location at = mob.getLocation().add(0, mob.getHeight() / 2, 0);
        at.getWorld().spawnParticle(Particle.PORTAL, at, 40, 0.6, 0.8, 0.6, 0.4);
        return mob;
    }

    /** Cari titik berdiri yang aman pada jarak min sampai max dari pemain. */
    private Location findSpot(Player p, double minD, double maxD, boolean flying) {
        World w = p.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 14; attempt++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = minD + rnd.nextDouble() * Math.max(1, maxD - minD);
            int x = (int) Math.floor(p.getLocation().getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getLocation().getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int top = Math.min(w.getMaxHeight() - 3, p.getLocation().getBlockY() + 16);
            int bottom = Math.max(w.getMinHeight() + 2, p.getLocation().getBlockY() - 24);
            for (int y = top; y >= bottom; y--) {
                Block feet = w.getBlockAt(x, y, z);
                Block head = w.getBlockAt(x, y + 1, z);
                Block below = w.getBlockAt(x, y - 1, z);
                if (feet.isPassable() && !feet.isLiquid() && head.isPassable() && !head.isLiquid()
                        && below.isSolid() && below.getType() != Material.MAGMA_BLOCK) {
                    Location l = new Location(w, x + 0.5, y, z + 0.5);
                    if (flying && w.getBlockAt(x, y + 2, z).isPassable() && w.getBlockAt(x, y + 3, z).isPassable()) {
                        l.add(0, 2, 0);
                    }
                    return l;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ pembersihan & efek

    private void cleanup() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(wilds)) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || !e.isValid()) {
                forget(id);
                continue;
            }
            Long until = e.getPersistentDataContainer().get(plugin.wildTimeKey(), PersistentDataType.LONG);
            if (until != null && now >= until) {
                boolean wasLegend = id.equals(legendary);
                Location l = e.getLocation().add(0, 0.5, 0);
                l.getWorld().spawnParticle(Particle.CLOUD, l, 25, 0.5, 0.6, 0.5, 0.05);
                e.remove();
                forget(id);
                if (wasLegend && plugin.getConfig().getBoolean("legendary.announce", true)) {
                    plugin.broadcast("legendary-gone");
                }
            }
        }
    }

    private void aura() {
        for (UUID id : wilds) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Mob m) || !m.isValid()) {
                continue;
            }
            Species sp = mgr.species(m.getType());
            if (sp == null) {
                continue;
            }
            boolean legend = id.equals(legendary);
            Particle pt = Combat.particle(sp.aura());
            if (pt == null && legend) {
                pt = Particle.END_ROD;
            }
            if (pt != null) {
                m.getWorld().spawnParticle(pt, m.getLocation().add(0, m.getHeight() / 2, 0), 6, 0.5, 0.6, 0.5, 0.02);
            }
            if (legend) {
                Location base = m.getLocation();
                for (int dy = 2; dy <= 26; dy += 2) {
                    base.getWorld().spawnParticle(Particle.END_ROD, base.clone().add(0, dy, 0), 1, 0, 0, 0, 0, null, true);
                }
            }
        }
    }

    /** Pemain admin: komponen teks untuk status. */
    public Component statusLine() {
        Location l = legendaryLocation();
        int prec = Math.max(1, plugin.getConfig().getInt("legendary.announce-precision", 50));
        if (l == null) {
            return NereusPets.LEGACY.deserialize(plugin.raw("legend-none",
                    "{minutes}", String.valueOf(secondsToNextLegend() / 60 + 1)));
        }
        return NereusPets.LEGACY.deserialize(plugin.raw("legend-alive",
                "{x}", String.valueOf(Math.round(l.getX() / prec) * prec),
                "{z}", String.valueOf(Math.round(l.getZ() / prec) * prec),
                "{world}", habitatOf(l.getWorld())));
    }

    @SuppressWarnings("unused")
    private ConfigurationSection unused() {
        return null;
    }
}
