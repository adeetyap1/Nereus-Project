package com.nereus.pets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Ageable;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Pusat logika: data pet, pemanggilan, AI sederhana, XP, dan penyimpanan. */
public final class PetManager {
    private final NereusPets plugin;

    private final Map<UUID, List<PetData>> boxes = new HashMap<>();
    private final Map<UUID, UUID> lastActive = new HashMap<>();
    private final Map<UUID, ActivePet> active = new HashMap<>();
    private final Map<UUID, ActivePet> byEntity = new HashMap<>();
    private final Map<EntityType, Species> species = new HashMap<>();
    private final Set<EntityType> engageBlacklist = new HashSet<>();

    private BukkitTask loop;
    private BukkitTask mover;
    private BukkitTask autosave;

    public PetManager(NereusPets plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ config

    public void reloadSpecies() {
        species.clear();
        engageBlacklist.clear();
        for (String name : plugin.getConfig().getStringList("engage-blacklist")) {
            try {
                engageBlacklist.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("engage-blacklist: jenis tidak dikenal " + name);
            }
        }
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("species");
        if (sec == null) {
            return;
        }
        double defaultScale = plugin.getConfig().getDouble("pet-scale", 0.5);
        for (String key : sec.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("species: jenis tidak dikenal " + key);
                continue;
            }
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            species.put(type, new Species(
                    type,
                    s.getString("name", key),
                    Rarity.parse(s.getString("rarity", "COMMON")),
                    s.getDouble("hp", 20),
                    s.getDouble("attack", 3),
                    s.getDouble("hp-per-level", 1.5),
                    s.getDouble("attack-per-level", 0.3),
                    s.getDouble("capture-rate", 0.4),
                    s.getDouble("range", 0),
                    s.getDouble("scale", defaultScale),
                    Skill.parse(s.getString("skill", "none")),
                    s.getInt("skill-level", 5),
                    AttackStyle.parse(s.getString("style", "melee")),
                    s.getBoolean("controlled", false),
                    s.getBoolean("flying", false),
                    s.getString("habitat", "OVERWORLD").toUpperCase(Locale.ROOT),
                    s.getBoolean("egg", true),
                    s.getString("aura"),
                    s.getDouble("wild-scale", 1.0),
                    s.getStringList("lore")));
        }
    }

    public Species species(EntityType type) {
        return species.get(type);
    }

    public Set<EntityType> speciesTypes() {
        return species.keySet();
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 5L);
        mover = Bukkit.getScheduler().runTaskTimer(plugin, this::controlTick, 20L, 2L);
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, 6000L, 6000L);
    }

    public void shutdown() {
        if (loop != null) {
            loop.cancel();
        }
        if (autosave != null) {
            autosave.cancel();
        }
        if (mover != null) {
            mover.cancel();
        }
        for (UUID owner : new ArrayList<>(active.keySet())) {
            recall(owner);
        }
        saveAll();
    }

    /** Hapus sisa pet yang tertinggal (misalnya setelah /reload). */
    public void sweepStrays() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if ((isPetEntity(e) && !byEntity.containsKey(e.getUniqueId())) || isWild(e)) {
                    e.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ storage

    private File file(UUID id) {
        return new File(plugin.getDataFolder(), "data/" + id + ".yml");
    }

    public void load(Player p) {
        UUID id = p.getUniqueId();
        List<PetData> list = new ArrayList<>();
        File f = file(id);
        if (f.exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            ConfigurationSection sec = y.getConfigurationSection("pets");
            if (sec != null) {
                for (String k : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(k);
                    if (s == null) {
                        continue;
                    }
                    try {
                        PetData d = new PetData(UUID.fromString(k),
                                EntityType.valueOf(s.getString("type", "ZOMBIE")),
                                Rarity.parse(s.getString("rarity", "COMMON")));
                        d.nickname = s.getString("name");
                        d.level = Math.max(1, s.getInt("level", 1));
                        d.xp = s.getDouble("xp", 0);
                        d.ivHp = s.getInt("iv-hp", 0);
                        d.ivAtk = s.getInt("iv-atk", 0);
                        d.hp = s.getDouble("hp", -1);
                        d.stamp = s.getLong("stamp", System.currentTimeMillis());
                        d.faintUntil = s.getLong("faint-until", 0);
                        d.stars = Math.max(0, s.getInt("stars", 0));
                        d.wins = s.getInt("wins", 0);
                        d.losses = s.getInt("losses", 0);
                        list.add(d);
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Data pet rusak dilewati: " + k + " (" + p.getName() + ")");
                    }
                }
            }
            String last = y.getString("last-active");
            if (last != null) {
                try {
                    lastActive.put(id, UUID.fromString(last));
                } catch (IllegalArgumentException ignored) {
                    // abaikan
                }
            }
        }
        boxes.put(id, list);
    }

    public void save(UUID id) {
        List<PetData> list = boxes.get(id);
        if (list == null) {
            return;
        }
        ActivePet ap = active.get(id);
        if (ap != null && ap.entity.isValid()) {
            ap.data.hp = ap.entity.getHealth();
            ap.data.stamp = System.currentTimeMillis();
        }
        YamlConfiguration y = new YamlConfiguration();
        for (PetData d : list) {
            String b = "pets." + d.id;
            y.set(b + ".type", d.type.name());
            y.set(b + ".rarity", d.rarity.name());
            y.set(b + ".name", d.nickname);
            y.set(b + ".level", d.level);
            y.set(b + ".xp", d.xp);
            y.set(b + ".iv-hp", d.ivHp);
            y.set(b + ".iv-atk", d.ivAtk);
            y.set(b + ".hp", d.hp);
            y.set(b + ".stamp", d.stamp);
            y.set(b + ".faint-until", d.faintUntil);
            y.set(b + ".stars", d.stars);
            y.set(b + ".wins", d.wins);
            y.set(b + ".losses", d.losses);
        }
        UUID last = lastActive.get(id);
        y.set("last-active", last == null ? null : last.toString());
        File f = file(id);
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try {
            y.save(f);
        } catch (IOException ex) {
            plugin.getLogger().severe("Gagal menyimpan data pet " + id + ": " + ex.getMessage());
        }
    }

    public void saveAll() {
        for (UUID id : new ArrayList<>(boxes.keySet())) {
            save(id);
        }
    }

    public void unload(UUID id) {
        boxes.remove(id);
        lastActive.remove(id);
    }

    // ------------------------------------------------------------------ box

    public List<PetData> box(UUID owner) {
        return boxes.computeIfAbsent(owner, k -> new ArrayList<>());
    }

    public PetData find(UUID owner, UUID petId) {
        for (PetData d : box(owner)) {
            if (d.id.equals(petId)) {
                return d;
            }
        }
        return null;
    }

    public UUID lastActive(UUID owner) {
        return lastActive.get(owner);
    }

    public int boxLimit() {
        return plugin.getConfig().getInt("box-limit", 90);
    }

    public boolean addPet(UUID owner, PetData d) {
        List<PetData> list = box(owner);
        if (list.size() >= boxLimit()) {
            return false;
        }
        list.add(d);
        return true;
    }

    public PetData createPet(EntityType type, int level) {
        Species sp = species.get(type);
        if (sp == null) {
            return null;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        PetData d = new PetData(UUID.randomUUID(), type, sp.rarity());
        d.level = Math.max(1, Math.min(level, plugin.getConfig().getInt("xp.max-level", 50)));
        int floor = sp.rarity() == Rarity.LEGENDARY ? 6 : sp.rarity() == Rarity.EPIC ? 3 : 0;
        d.ivHp = floor + rnd.nextInt(16 - floor);
        d.ivAtk = floor + rnd.nextInt(16 - floor);
        d.hp = -1;
        return d;
    }

    public void release(Player p, PetData d) {
        ActivePet ap = active.get(p.getUniqueId());
        if (ap != null && ap.data == d) {
            recall(p.getUniqueId());
        }
        box(p.getUniqueId()).remove(d);
        if (d.id.equals(lastActive.get(p.getUniqueId()))) {
            lastActive.remove(p.getUniqueId());
        }
    }

    // ------------------------------------------------------------------ stats

    public double maxHp(PetData d) {
        Species sp = species.get(d.type);
        if (sp == null) {
            return 20;
        }
        return (sp.hp() + sp.hpPerLevel() * (d.level - 1)) * d.rarity.statMult * (1 + d.ivHp / 100.0) * starMult(d);
    }

    public double attackPower(PetData d) {
        Species sp = species.get(d.type);
        if (sp == null) {
            return 2;
        }
        return (sp.attack() + sp.attackPerLevel() * (d.level - 1)) * d.rarity.statMult * (1 + d.ivAtk / 100.0) * starMult(d);
    }

    public double starMult(PetData d) {
        return 1 + d.stars * plugin.getConfig().getDouble("fuse.bonus-per-star", 0.08);
    }

    public double xpNeeded(int level) {
        return plugin.getConfig().getDouble("xp.base", 20) + plugin.getConfig().getDouble("xp.growth", 5) * level * level;
    }

    public double effectiveHp(PetData d) {
        double max = maxHp(d);
        for (ActivePet ap : active.values()) {
            if (ap.data == d) {
                return Math.min(max, ap.entity.isValid() ? ap.entity.getHealth() : Math.max(1, d.hp));
            }
        }
        if (d.hp < 0) {
            return max;
        }
        long secs = Math.max(0, (System.currentTimeMillis() - d.stamp) / 1000);
        double rate = max * plugin.getConfig().getDouble("regen-percent-per-second", 1.0) / 100.0;
        return Math.min(max, d.hp + rate * secs);
    }

    public boolean isActive(PetData d) {
        for (ActivePet ap : active.values()) {
            if (ap.data == d) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ lookup

    public ActivePet activeOf(Player p) {
        return active.get(p.getUniqueId());
    }

    public ActivePet byEntity(Entity e) {
        return e == null ? null : byEntity.get(e.getUniqueId());
    }

    public ActivePet byEntityId(UUID id) {
        return id == null ? null : byEntity.get(id);
    }

    public ActivePet activeById(UUID owner) {
        return active.get(owner);
    }

    public Set<UUID> activeOwners() {
        return active.keySet();
    }

    public boolean isWild(Entity e) {
        return e != null && e.getPersistentDataContainer().has(plugin.wildKey(), PersistentDataType.INTEGER);
    }

    /** Pet atau pet liar: dikelola plugin. */
    public boolean isManaged(Entity e) {
        return isPetEntity(e) || isWild(e);
    }

    public boolean isDuelPair(Entity a, Entity b) {
        ActivePet pa = byEntity(a);
        ActivePet pb = byEntity(b);
        return pa != null && pb != null && pa.duel == pb && pb.duel == pa;
    }

    public void hideBossBar(Mob m) {
        try {
            if (m instanceof org.bukkit.entity.Boss b && b.getBossBar() != null) {
                b.getBossBar().setVisible(false);
            }
        } catch (RuntimeException ignored) {
            // tidak semua boss punya bar
        }
    }

    public boolean isPetEntity(Entity e) {
        return e != null && e.getPersistentDataContainer().has(plugin.petKey(), PersistentDataType.STRING);
    }

    /** Mob yang tidak boleh diserang pet (penduduk, hewan jinak). */
    public boolean isProtected(Entity e) {
        return e instanceof org.bukkit.entity.Villager
                || e instanceof org.bukkit.entity.WanderingTrader
                || (e instanceof org.bukkit.entity.Tameable t && t.isTamed());
    }

    // ------------------------------------------------------------------ summon / recall

    public boolean summon(Player p, PetData d) {
        Species sp = species.get(d.type);
        if (sp == null) {
            plugin.send(p, "species-disabled");
            return false;
        }
        if (plugin.getConfig().getStringList("disabled-worlds").contains(p.getWorld().getName())) {
            plugin.send(p, "world-disabled");
            return false;
        }
        long now = System.currentTimeMillis();
        if (d.faintUntil > now) {
            plugin.send(p, "fainted", "{time}", String.valueOf((d.faintUntil - now) / 1000 + 1));
            return false;
        }
        recall(p.getUniqueId());

        double hpNow = effectiveHp(d);
        Mob mob = spawnMob(p, sp);
        if (mob == null) {
            plugin.send(p, "spawn-failed");
            return false;
        }
        d.hp = hpNow;
        d.stamp = now;
        ActivePet ap = new ActivePet(p.getUniqueId(), d, mob);
        ap.nextRegen = now + 1000;
        active.put(p.getUniqueId(), ap);
        byEntity.put(mob.getUniqueId(), ap);
        lastActive.put(p.getUniqueId(), d.id);
        applyStats(ap, false);

        Location l = mob.getLocation().add(0, 0.4, 0);
        l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l, 10, 0.3, 0.3, 0.3, 0.0);
        l.getWorld().playSound(l, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        plugin.send(p, "summoned", "{pet}", plain(displayName(d)));
        return true;
    }

    private Mob spawnMob(Player owner, Species sp) {
        Location loc = owner.getLocation();
        if (sp.flying()) {
            loc = loc.clone().add(0, 1.6, 0);
        }
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
        mob.getPersistentDataContainer().set(plugin.petKey(), PersistentDataType.STRING, owner.getUniqueId().toString());
        mob.setPersistent(false);
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        mob.setCollidable(false);
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
        if (mob instanceof org.bukkit.entity.Slime sl) {
            sl.setSize(2);
        }
        if (sp.controlled()) {
            mob.setAI(false);
            mob.setGravity(false);
        }
        hideBossBar(mob);
        mob.setTarget(null);
        return mob;
    }

    /** Simpan pet kembali ke box. Tidak menghapus pet terakhir untuk auto-summon. */
    public void recall(UUID ownerId) {
        ActivePet ap = active.get(ownerId);
        if (ap == null) {
            return;
        }
        if (ap.duel != null && plugin.duels() != null) {
            plugin.duels().abort(ap);
        }
        active.remove(ownerId);
        byEntity.remove(ap.entity.getUniqueId());
        if (ap.entity.isValid()) {
            ap.data.hp = Math.max(1, ap.entity.getHealth());
            ap.data.stamp = System.currentTimeMillis();
            Location l = ap.entity.getLocation().add(0, 0.3, 0);
            l.getWorld().spawnParticle(Particle.CLOUD, l, 8, 0.2, 0.2, 0.2, 0.01);
        }
        ap.entity.remove();
    }

    /** Pemain sengaja menyimpan pet: auto-summon tidak memanggilnya lagi. */
    public void manualRecall(Player p) {
        ActivePet ap = active.get(p.getUniqueId());
        if (ap == null) {
            return;
        }
        String name = plain(displayName(ap.data));
        recall(p.getUniqueId());
        lastActive.remove(p.getUniqueId());
        plugin.send(p, "recalled", "{pet}", name);
    }

    public void faint(ActivePet ap) {
        Player owner = Bukkit.getPlayer(ap.owner);
        PetData d = ap.data;
        long seconds = plugin.getConfig().getLong("faint-seconds", 60);
        long until = System.currentTimeMillis() + seconds * 1000L;
        String name = plain(displayName(d));
        recall(ap.owner);
        d.hp = maxHp(d) * 0.25;
        d.stamp = until;
        d.faintUntil = until;
        if (owner != null) {
            plugin.send(owner, "pet-fainted", "{pet}", name, "{time}", String.valueOf(seconds));
        }
    }

    public void refreshName(ActivePet ap) {
        ap.entity.customName(displayName(ap.data));
        ap.entity.setCustomNameVisible(true);
    }

    public void applyStats(ActivePet ap, boolean fullHeal) {
        Mob m = ap.entity;
        PetData d = ap.data;
        Species sp = species.get(d.type);
        double max = maxHp(d);
        setBase(m, Attribute.MAX_HEALTH, max);
        if (sp != null) {
            setBase(m, Attribute.SCALE, sp.scale());
        }
        double hp = fullHeal ? max : Math.min(max, Math.max(1.0, d.hp < 0 ? max : d.hp));
        m.setHealth(hp);
        refreshName(ap);
    }

    private static void setBase(LivingEntity e, Attribute attr, double value) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    private static double attrValue(LivingEntity e, Attribute attr, double def) {
        AttributeInstance inst = e.getAttribute(attr);
        return inst == null ? def : inst.getValue();
    }

    // ------------------------------------------------------------------ XP

    public void gainXp(ActivePet ap, double amount) {
        PetData d = ap.data;
        int maxLevel = plugin.getConfig().getInt("xp.max-level", 50);
        if (d.level >= maxLevel || amount <= 0) {
            return;
        }
        d.xp += amount;
        boolean up = false;
        while (d.level < maxLevel && d.xp >= xpNeeded(d.level)) {
            d.xp -= xpNeeded(d.level);
            d.level++;
            up = true;
        }
        if (d.level >= maxLevel) {
            d.xp = 0;
        }
        Player owner = Bukkit.getPlayer(ap.owner);
        if (up) {
            applyStats(ap, true);
            Location l = ap.entity.getLocation().add(0, 0.5, 0);
            l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l, 15, 0.4, 0.4, 0.4, 0.0);
            l.getWorld().playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            if (owner != null) {
                plugin.send(owner, "level-up", "{pet}", plain(displayName(d)), "{level}", String.valueOf(d.level));
            }
        } else if (owner != null) {
            owner.sendActionBar(NereusPets.LEGACY.deserialize(plugin.raw("xp-gain",
                    "{xp}", String.valueOf(Math.round(amount)), "{pet}", plain(displayName(d)))));
        }
    }

    // ------------------------------------------------------------------ AI loop

    private void tick() {
        long now = System.currentTimeMillis();
        double teleportDist = plugin.getConfig().getDouble("teleport-distance", 20.0);
        double followDist = plugin.getConfig().getDouble("follow-distance", 4.0);
        double followSpeed = plugin.getConfig().getDouble("follow-speed", 1.3);
        double meleeReach = plugin.getConfig().getDouble("melee-reach", 1.8);
        long attackInterval = plugin.getConfig().getLong("attack-interval-ms", 1000);
        boolean autoEngage = plugin.getConfig().getBoolean("auto-engage", true);
        double engageRange = plugin.getConfig().getDouble("engage-range", 8);
        Combat combat = plugin.combat();

        for (ActivePet ap : new ArrayList<>(active.values())) {
            if (active.get(ap.owner) != ap) {
                continue;
            }
            Player owner = Bukkit.getPlayer(ap.owner);
            Mob pet = ap.entity;
            if (owner == null || !owner.isOnline() || owner.isDead()) {
                recall(ap.owner);
                continue;
            }
            if (!pet.isValid()) {
                recall(ap.owner);
                plugin.send(owner, "pet-lost");
                continue;
            }
            Species sp = species.get(ap.data.type);
            Location ol = owner.getLocation();
            boolean dueling = ap.duel != null;

            if (dueling && (!ap.duel.entity.isValid() || !ap.duel.entity.getWorld().equals(pet.getWorld()))) {
                plugin.duels().draw(ap, ap.duel);
                dueling = false;
            }

            if (!pet.getWorld().equals(ol.getWorld())
                    || (!dueling && pet.getLocation().distanceSquared(ol) > teleportDist * teleportDist)) {
                pet.teleport(ol);
                ap.target = null;
            }

            if (dueling && now < ap.duelStart) {
                halt(ap, sp);
                continue;
            }

            if (!dueling && now >= ap.nextRegen) {
                ap.nextRegen = now + 1000;
                double max = maxHp(ap.data);
                if (now - ap.lastHurt > 5000 && pet.getHealth() < max) {
                    pet.setHealth(Math.min(max, pet.getHealth() + max * 0.02));
                }
            }

            if (!dueling && sp != null && sp.skill() == Skill.HEAL && ap.data.level >= sp.skillLevel() && now >= ap.nextSkill) {
                double omax = attrValue(owner, Attribute.MAX_HEALTH, 20);
                if (owner.getHealth() < omax * 0.7) {
                    double amount = 2 + ap.data.level * 0.15;
                    owner.setHealth(Math.min(omax, owner.getHealth() + amount));
                    ap.nextSkill = now + 12000;
                    owner.getWorld().spawnParticle(Particle.HEART, owner.getLocation().add(0, 1.6, 0), 5, 0.3, 0.3, 0.3, 0.0);
                }
            }

            LivingEntity t = ap.target;
            if (dueling) {
                t = ap.duel.entity;
                ap.target = t;
            } else if (t != null) {
                if (!combat.valid(ap, t) || !t.getWorld().equals(pet.getWorld())
                        || t.getLocation().distanceSquared(ol) > 24 * 24) {
                    ap.target = null;
                    t = null;
                }
            }

            if (t == null && autoEngage && now >= ap.nextScan) {
                ap.nextScan = now + 1000;
                LivingEntity best = null;
                double bestDist = Double.MAX_VALUE;
                for (Entity e : owner.getNearbyEntities(engageRange, 4, engageRange)) {
                    if (!(e instanceof Monster m) || isManaged(e) || engageBlacklist.contains(e.getType())
                            || !owner.hasLineOfSight(e)) {
                        continue;
                    }
                    double dd = e.getLocation().distanceSquared(ol);
                    if (dd < bestDist) {
                        bestDist = dd;
                        best = m;
                    }
                }
                ap.target = best;
                t = best;
            }

            if (t != null && sp != null) {
                boolean ranged = sp.range() > 0;
                double reach = ranged ? sp.range() : meleeReach;
                double dist = pet.getLocation().distance(t.getLocation()) - t.getWidth() / 2.0;
                boolean inRange = dist <= reach && (!ranged || pet.hasLineOfSight(t));
                if (inRange) {
                    halt(ap, sp);
                    if (sp.controlled()) {
                        face(pet, t.getLocation(), sp);
                    }
                    if (now >= ap.nextAttack) {
                        ap.nextAttack = now + attackInterval;
                        attack(ap, sp, t);
                    }
                } else {
                    goTo(ap, sp, t.getLocation(), reach * 0.8, followSpeed, true);
                }
            } else if (!dueling) {
                double dist = pet.getLocation().distance(ol);
                if (dist > followDist) {
                    goTo(ap, sp, ol, followDist, followSpeed, false);
                } else if (dist < followDist * 0.5) {
                    halt(ap, sp);
                }
            }
        }
    }

    private void goTo(ActivePet ap, Species sp, Location dest, double stop, double speed, boolean attacking) {
        if (sp != null && sp.controlled()) {
            Location g = dest.clone();
            if (sp.flying()) {
                g.add(0, attacking ? (sp.range() > 0 ? 2.0 : 0.5) : 1.6, 0);
            }
            ap.goal = g;
            ap.stop = Math.max(0.8, stop);
        } else {
            ap.entity.getPathfinder().moveTo(dest, speed);
        }
    }

    private void halt(ActivePet ap, Species sp) {
        if (sp != null && sp.controlled()) {
            ap.goal = null;
        } else {
            ap.entity.getPathfinder().stopPathfinding();
        }
    }

    private static float yawTo(Location from, Location to, Species sp) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return sp != null && sp.type() == EntityType.ENDER_DRAGON ? yaw + 180f : yaw;
    }

    private void face(Mob pet, Location to, Species sp) {
        Location from = pet.getLocation();
        pet.setRotation(yawTo(from, to, sp), 0f);
    }

    /** Menggerakkan pet terkontrol (AI mati) selangkah demi selangkah. */
    private void controlTick() {
        double step = plugin.getConfig().getDouble("controlled-speed", 0.5);
        for (ActivePet ap : new ArrayList<>(active.values())) {
            Species sp = species.get(ap.data.type);
            if (sp == null || !sp.controlled() || ap.goal == null || !ap.entity.isValid()) {
                continue;
            }
            moveStep(ap, sp, step);
        }
    }

    private void moveStep(ActivePet ap, Species sp, double step) {
        Mob m = ap.entity;
        Location cur = m.getLocation();
        Location g = ap.goal;
        if (g.getWorld() == null || !g.getWorld().equals(cur.getWorld())) {
            return;
        }
        double dx = g.getX() - cur.getX();
        double dz = g.getZ() - cur.getZ();
        double dy = sp.flying() ? g.getY() - cur.getY() : 0;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double mv = Math.min(step, dist - ap.stop * 0.8);
        if (mv <= 0.05) {
            return;
        }
        double nx = cur.getX() + dx / dist * mv;
        double nz = cur.getZ() + dz / dist * mv;
        double ny;
        World w = cur.getWorld();
        if (sp.flying()) {
            ny = cur.getY() + dy / dist * mv;
            Location test = new Location(w, nx, ny, nz);
            if (!test.getBlock().isPassable()) {
                nx = cur.getX();
                nz = cur.getZ();
                ny = cur.getY() + 0.6;
            }
        } else {
            Location start = new Location(w, nx, cur.getY() + 1.3, nz);
            if (!start.getBlock().isPassable()) {
                return;
            }
            var hit = w.rayTraceBlocks(start, new Vector(0, -1, 0), 4.0, org.bukkit.FluidCollisionMode.NEVER, true);
            ny = hit != null ? hit.getHitPosition().getY() : cur.getY() - 0.5;
        }
        Location nl = new Location(w, nx, ny, nz, yawTo(cur, g, sp), 0f);
        m.teleport(nl);
    }

    private void attack(ActivePet ap, Species sp, LivingEntity target) {
        PetData d = ap.data;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double dmg = attackPower(d);
        boolean skillOn = sp.skill() != Skill.NONE && d.level >= sp.skillLevel();

        if (skillOn) {
            if (sp.skill() == Skill.CRIT && rnd.nextDouble() < 0.25) {
                dmg *= 2;
                target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 12, 0.3, 0.3, 0.3, 0.2);
            } else if (sp.skill() == Skill.THUNDER && rnd.nextDouble() < 0.15) {
                dmg *= 1.5;
                target.getWorld().strikeLightningEffect(target.getLocation());
            }
        }
        plugin.combat().deliver(ap, sp, target, dmg);
    }

    // ------------------------------------------------------------------ text helpers

    public Component displayName(PetData d) {
        Species sp = species.get(d.type);
        String base = d.nickname != null ? d.nickname : (sp != null ? sp.name() : d.type.name());
        String stars = d.stars > 0 ? " &e" + "★".repeat(d.stars) : "";
        return NereusPets.LEGACY.deserialize(d.rarity.color + "[Lv." + d.level + "] &f" + base + stars);
    }

    /** Nama pet dalam kode warna & untuk dipakai di pesan. */
    public String plain(Component c) {
        return NereusPets.LEGACY.serialize(c);
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    /** Baris keterangan pet (dipakai di menu dan /pets info). */
    public List<String> detailLines(PetData d, int index) {
        Species sp = species.get(d.type);
        int maxLevel = plugin.getConfig().getInt("xp.max-level", 50);
        List<String> lines = new ArrayList<>();
        lines.add("&8#" + (index + 1));
        lines.add("&7Jenis: &f" + (sp != null ? sp.name() : d.type.name()));
        lines.add("&7Kelangkaan: " + d.rarity.color + d.rarity.label);
        if (sp != null) {
            lines.add("&7Serangan: &f" + sp.style().label);
            for (String l : sp.lore()) {
                lines.add("&8" + l);
            }
        }
        if (d.stars > 0) {
            lines.add("&7Bintang: &e" + "★".repeat(d.stars) + " &8(+" + Math.round(d.stars
                    * plugin.getConfig().getDouble("fuse.bonus-per-star", 0.08) * 100) + "% stat)");
        }
        if (d.wins + d.losses > 0) {
            lines.add("&7Duel: &a" + d.wins + " menang &c" + d.losses + " kalah");
        }
        if (d.level >= maxLevel) {
            lines.add("&7Level: &e" + d.level + " &6(MAX)");
        } else {
            double need = xpNeeded(d.level);
            int filled = (int) Math.max(0, Math.min(10, Math.round(d.xp / need * 10)));
            lines.add("&7Level: &e" + d.level + " &8[&a" + "|".repeat(filled) + "&7" + "|".repeat(10 - filled) + "&8]");
        }
        lines.add("&7HP: &c" + fmt(effectiveHp(d)) + "&7/&c" + fmt(maxHp(d)) + "  &7ATK: &6" + fmt(attackPower(d)));
        lines.add("&7Bakat: &bHP +" + d.ivHp + "%  ATK +" + d.ivAtk + "%");
        if (sp != null && sp.skill() != Skill.NONE) {
            if (d.level >= sp.skillLevel()) {
                lines.add("&7Skill: &d" + sp.skill().label);
                lines.add("&8" + sp.skill().description);
            } else {
                lines.add("&7Skill: &d" + sp.skill().label + " &c(terbuka di Lv." + sp.skillLevel() + ")");
            }
        }
        long now = System.currentTimeMillis();
        if (isActive(d)) {
            lines.add("&7Status: &aAktif");
        } else if (d.faintUntil > now) {
            lines.add("&7Status: &cPingsan (" + ((d.faintUntil - now) / 1000 + 1) + " dtk)");
        } else {
            lines.add("&7Status: &fSiap");
        }
        return lines;
    }

    public ItemStack icon(PetData d, int index) {
        Material mat = Material.matchMaterial(d.type.name() + "_SPAWN_EGG");
        if (mat == null) {
            mat = Material.EGG;
        }
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(displayName(d).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : detailLines(d, index)) {
            lore.add(NereusPets.LEGACY.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(NereusPets.LEGACY.deserialize("&eKlik kiri: &fpanggil / simpan").decoration(TextDecoration.ITALIC, false));
        lore.add(NereusPets.LEGACY.deserialize("&cShift+Kanan: &flepas &8(atau /pets release " + (index + 1) + ")")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
