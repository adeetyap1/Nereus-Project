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
                    s.getInt("skill-level", 5)));
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
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::saveAll, 6000L, 6000L);
    }

    public void shutdown() {
        if (loop != null) {
            loop.cancel();
        }
        if (autosave != null) {
            autosave.cancel();
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
                if (isPetEntity(e) && !byEntity.containsKey(e.getUniqueId())) {
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
        d.ivHp = rnd.nextInt(16);
        d.ivAtk = rnd.nextInt(16);
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
        return (sp.hp() + sp.hpPerLevel() * (d.level - 1)) * d.rarity.statMult * (1 + d.ivHp / 100.0);
    }

    public double attackPower(PetData d) {
        Species sp = species.get(d.type);
        if (sp == null) {
            return 2;
        }
        return (sp.attack() + sp.attackPerLevel() * (d.level - 1)) * d.rarity.statMult * (1 + d.ivAtk / 100.0);
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
        mob.setTarget(null);
        return mob;
    }

    /** Simpan pet kembali ke box. Tidak menghapus pet terakhir untuk auto-summon. */
    public void recall(UUID ownerId) {
        ActivePet ap = active.remove(ownerId);
        if (ap == null) {
            return;
        }
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

        for (ActivePet ap : new ArrayList<>(active.values())) {
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

            if (!pet.getWorld().equals(ol.getWorld())
                    || pet.getLocation().distanceSquared(ol) > teleportDist * teleportDist) {
                pet.teleport(ol);
                ap.target = null;
            }

            // pemulihan HP perlahan di luar pertarungan
            if (now >= ap.nextRegen) {
                ap.nextRegen = now + 1000;
                double max = maxHp(ap.data);
                if (now - ap.lastHurt > 5000 && pet.getHealth() < max) {
                    pet.setHealth(Math.min(max, pet.getHealth() + max * 0.02));
                }
            }

            // skill penyembuh
            if (sp != null && sp.skill() == Skill.HEAL && ap.data.level >= sp.skillLevel() && now >= ap.nextSkill) {
                double omax = attrValue(owner, Attribute.MAX_HEALTH, 20);
                if (owner.getHealth() < omax * 0.7) {
                    double amount = 2 + ap.data.level * 0.15;
                    owner.setHealth(Math.min(omax, owner.getHealth() + amount));
                    ap.nextSkill = now + 12000;
                    owner.getWorld().spawnParticle(Particle.HEART, owner.getLocation().add(0, 1.6, 0), 5, 0.3, 0.3, 0.3, 0.0);
                }
            }

            // validasi target
            LivingEntity t = ap.target;
            if (t != null) {
                if (!t.isValid() || t.isDead() || t instanceof Player || isPetEntity(t)
                        || !t.getWorld().equals(pet.getWorld())
                        || t.getLocation().distanceSquared(ol) > 24 * 24) {
                    ap.target = null;
                    t = null;
                }
            }

            // cari target otomatis
            if (t == null && autoEngage && now >= ap.nextScan) {
                ap.nextScan = now + 1000;
                LivingEntity best = null;
                double bestDist = Double.MAX_VALUE;
                for (Entity e : owner.getNearbyEntities(engageRange, 4, engageRange)) {
                    if (!(e instanceof Monster m) || isPetEntity(e) || engageBlacklist.contains(e.getType())
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
                    pet.getPathfinder().stopPathfinding();
                    if (now >= ap.nextAttack) {
                        ap.nextAttack = now + attackInterval;
                        attack(ap, sp, t);
                    }
                } else {
                    pet.getPathfinder().moveTo(t.getLocation(), followSpeed);
                }
            } else {
                double dist = pet.getLocation().distance(ol);
                if (dist > followDist) {
                    pet.getPathfinder().moveTo(ol, followSpeed);
                } else if (dist < followDist * 0.5) {
                    pet.getPathfinder().stopPathfinding();
                }
            }
        }
    }

    private void attack(ActivePet ap, Species sp, LivingEntity target) {
        PetData d = ap.data;
        Mob pet = ap.entity;
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

        pet.swingMainHand();
        if (sp.range() > 0) {
            particleLine(pet.getEyeLocation(), target.getEyeLocation());
            pet.getWorld().playSound(pet.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.2f);
        }
        target.damage(dmg, pet);

        if (skillOn && !target.isDead()) {
            if (sp.skill() == Skill.SLOW && rnd.nextDouble() < 0.30) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            } else if (sp.skill() == Skill.POISON && rnd.nextDouble() < 0.30) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
            } else if (sp.skill() == Skill.IGNITE && rnd.nextDouble() < 0.30) {
                target.setFireTicks(80);
                target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 0.8, 0), 8, 0.2, 0.3, 0.2, 0.01);
            }
        }
    }

    private void particleLine(Location a, Location b) {
        Vector v = b.toVector().subtract(a.toVector());
        double len = v.length();
        if (len < 0.1) {
            return;
        }
        v.normalize();
        for (double i = 0.5; i < len; i += 0.5) {
            Location p = a.clone().add(v.clone().multiply(i));
            a.getWorld().spawnParticle(Particle.CRIT, p, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------ text helpers

    public Component displayName(PetData d) {
        Species sp = species.get(d.type);
        String base = d.nickname != null ? d.nickname : (sp != null ? sp.name() : d.type.name());
        return NereusPets.LEGACY.deserialize(d.rarity.color + "[Lv." + d.level + "] &f" + base);
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
