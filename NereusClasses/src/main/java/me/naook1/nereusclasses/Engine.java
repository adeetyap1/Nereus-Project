package me.naook1.nereusclasses;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import me.naook1.nereusclasses.Model.Cond;
import me.naook1.nereusclasses.Model.Line;
import me.naook1.nereusclasses.Model.MobDef;
import me.naook1.nereusclasses.Model.Skill;
import me.naook1.nereusclasses.Model.Targeter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

/** Mythic-style skill engine: loads skill + mob yml, runs lines, targeters, conditions, auras. */
final class Engine {

    /** a target: entity or a plain location */
    static final class Tgt {
        final Entity e;
        final Location l;

        Tgt(Entity e) {
            this.e = e;
            this.l = null;
        }

        Tgt(Location l) {
            this.e = null;
            this.l = l;
        }

        Location loc() {
            return e != null ? e.getLocation() : l;
        }
    }

    static final class Ctx {
        LivingEntity caster;
        Location origin;
        List<Tgt> inherited = new ArrayList<>();
        Map<String, Double> mods = new HashMap<>();
        int depth;
        Object proj;

        Ctx copy() {
            Ctx c = new Ctx();
            c.caster = caster;
            c.origin = origin == null ? null : origin.clone();
            c.inherited = new ArrayList<>(inherited);
            c.mods = mods;
            c.depth = depth + 1;
            c.proj = proj;
            return c;
        }
    }

    static final class Aura {
        String name;
        int stacks = 1;
        int maxStacks = 1;
        long end;
        int interval;
        long nextTick;
        String onTick;
        String onEnd;
        Ctx ctx;
        // damage-shield aura
        boolean dmg;
        String onHit;
        boolean cancel;
    }

    final NereusClasses plugin;
    final Items items;
    final Map<String, Skill> skills = new HashMap<>();
    final Map<String, MobDef> mobs = new HashMap<>();
    final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    final Map<UUID, Map<String, Aura>> auras = new HashMap<>();
    final Map<UUID, Long> stands = new HashMap<>();       // stand uuid -> spawn tick
    final Map<UUID, UUID> owners = new HashMap<>();       // stand uuid -> owner uuid
    final Map<UUID, String> mobType = new HashMap<>();
    final Map<UUID, Map<String, List<Line>>> signals = new HashMap<>();
    final Set<BukkitTask> tasks = new HashSet<>();
    final Random rnd = new Random();
    final Mech mech;
    long now = 0;
    boolean inSkillDamage = false;
    final NamespacedKey vfxKey;
    int maxStands;
    int maxLifeTicks;

    Engine(NereusClasses plugin, Items items) {
        this.plugin = plugin;
        this.items = items;
        this.vfxKey = new NamespacedKey(plugin, "vfx");
        this.mech = new Mech(this);
    }

    // ------------------------------------------------------------------ loading
    void load() {
        skills.clear();
        mobs.clear();
        maxStands = plugin.getConfig().getInt("engine.max-stands", 500);
        Fx.particleMult = plugin.getConfig().getDouble("engine.particle-multiplier", 2.0);
        Fx.soundGapMs = plugin.getConfig().getLong("engine.sound-gap-ms", 300);
        maxLifeTicks = plugin.getConfig().getInt("engine.max-vfx-life-ticks", 1200);
        File sd = new File(plugin.getDataFolder(), "skills");
        File[] fs = sd.listFiles((d, n) -> n.endsWith(".yml"));
        int sn = 0;
        if (fs != null) {
            for (File f : fs) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                for (String k : y.getKeys(false)) {
                    if (y.isConfigurationSection(k)) {
                        Map<String, Object> d = y.getConfigurationSection(k).getValues(false);
                        skills.put(k.toLowerCase(Locale.ROOT), Model.parseSkill(k, d));
                        sn++;
                    }
                }
            }
        }
        File md = new File(plugin.getDataFolder(), "mobs");
        File[] ms = md.listFiles((d, n) -> n.endsWith(".yml"));
        if (ms != null) {
            for (File f : ms) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                for (String k : y.getKeys(false)) {
                    if (y.isConfigurationSection(k)) {
                        Map<String, Object> d = y.getConfigurationSection(k).getValues(false);
                        // nested sections (Options/Pose) come back as sections; flatten to maps
                        Map<String, Object> flat = new HashMap<>();
                        for (Map.Entry<String, Object> e : d.entrySet()) {
                            Object v = e.getValue();
                            if (v instanceof org.bukkit.configuration.ConfigurationSection cs) {
                                v = cs.getValues(false);
                            }
                            flat.put(e.getKey(), v);
                        }
                        mobs.put(k.toLowerCase(Locale.ROOT), Model.parseMob(k, flat));
                    }
                }
            }
        }
        plugin.getLogger().info("Engine: " + sn + " skills, " + mobs.size() + " mobs, " + items.size() + " items");
    }

    void start() {
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L));
    }

    void stop() {
        for (BukkitTask t : new ArrayList<>(tasks)) {
            t.cancel();
        }
        tasks.clear();
        for (UUID id : new ArrayList<>(stands.keySet())) {
            Entity e = plugin.getServer().getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        stands.clear();
        auras.clear();
    }

    /** schedules a task and keeps it tracked. */
    BukkitTask later(long delay, Runnable r) {
        if (delay <= 0) {
            r.run();
            return null;
        }
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            tasks.remove(holder[0]);
            try {
                r.run();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("skill task error: " + ex);
            }
        }, delay);
        tasks.add(holder[0]);
        return holder[0];
    }

    /** repeating task; runnable returns false to stop. */
    void every(long delay, long period, java.util.function.BooleanSupplier r) {
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            boolean go;
            try {
                go = r.getAsBoolean();
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("skill loop error: " + ex);
                go = false;
            }
            if (!go) {
                holder[0].cancel();
                tasks.remove(holder[0]);
            }
        }, Math.max(0, delay), Math.max(1, period));
        tasks.add(holder[0]);
    }

    private void tick() {
        now++;
        // auras
        Iterator<Map.Entry<UUID, Map<String, Aura>>> it = auras.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<String, Aura>> en = it.next();
            Entity ent = plugin.getServer().getEntity(en.getKey());
            Map<String, Aura> m = en.getValue();
            Iterator<Map.Entry<String, Aura>> ai = m.entrySet().iterator();
            while (ai.hasNext()) {
                Aura a = ai.next().getValue();
                boolean gone = ent == null || !ent.isValid() || (ent instanceof Player p && !p.isOnline());
                if (gone || now >= a.end) {
                    ai.remove();
                    if (!gone && a.onEnd != null && ent instanceof LivingEntity le) {
                        Ctx c = new Ctx();
                        c.caster = le;
                        c.origin = le.getLocation();
                        c.inherited.add(new Tgt(le));
                        cast(a.onEnd, c);
                    }
                    continue;
                }
                if (a.onTick != null && a.interval > 0 && now >= a.nextTick && ent instanceof LivingEntity le) {
                    a.nextTick = now + a.interval;
                    Ctx c = new Ctx();
                    c.caster = le;
                    c.origin = le.getLocation();
                    c.inherited.add(new Tgt(le));
                    cast(a.onTick, c);
                }
            }
            if (m.isEmpty()) {
                it.remove();
            }
        }
        // stray VFX stands
        if (now % 40 == 0) {
            Iterator<Map.Entry<UUID, Long>> si = stands.entrySet().iterator();
            while (si.hasNext()) {
                Map.Entry<UUID, Long> e = si.next();
                Entity ent = plugin.getServer().getEntity(e.getKey());
                if (ent == null || !ent.isValid()) {
                    owners.remove(e.getKey());
                    mobType.remove(e.getKey());
                    signals.remove(e.getKey());
                    si.remove();
                } else if (now - e.getValue() > maxLifeTicks) {
                    ent.remove();
                }
            }
        }
    }

    // ------------------------------------------------------------------ auras
    Aura aura(LivingEntity e, String name) {
        Map<String, Aura> m = auras.get(e.getUniqueId());
        return m == null ? null : m.get(name.toLowerCase(Locale.ROOT));
    }

    void applyAura(LivingEntity e, Aura a) {
        Map<String, Aura> m = auras.computeIfAbsent(e.getUniqueId(), k -> new HashMap<>());
        String key = a.name.toLowerCase(Locale.ROOT);
        Aura ex = m.get(key);
        if (ex != null && !a.dmg) {
            ex.stacks = Math.min(Math.max(ex.maxStacks, a.maxStacks), ex.stacks + 1);
            ex.end = a.end;
            return;
        }
        m.put(key, a);
    }

    void removeAura(LivingEntity e, String name, int stacks) {
        Map<String, Aura> m = auras.get(e.getUniqueId());
        if (m == null) {
            return;
        }
        Aura a = m.get(name.toLowerCase(Locale.ROOT));
        if (a == null) {
            return;
        }
        if (stacks > 0 && a.stacks > stacks) {
            a.stacks -= stacks;
        } else {
            m.remove(name.toLowerCase(Locale.ROOT));
        }
    }

    void clearAuras(UUID id) {
        auras.remove(id);
    }

    // ------------------------------------------------------------------ casting
    /** acting player (owner of VFX stands) used for damage attribution. */
    LivingEntity actor(LivingEntity caster) {
        UUID o = owners.get(caster.getUniqueId());
        if (o != null) {
            Entity e = plugin.getServer().getEntity(o);
            if (e instanceof LivingEntity le) {
                return le;
            }
        }
        return caster;
    }

    boolean castByName(String id, LivingEntity caster, Location origin, Entity target, Map<String, Double> mods) {
        Ctx c = new Ctx();
        c.caster = caster;
        c.origin = origin != null ? origin : caster.getLocation();
        if (target != null) {
            c.inherited.add(new Tgt(target));
        }
        if (mods != null) {
            c.mods = mods;
        }
        return cast(id, c);
    }

    boolean cast(String id, Ctx c) {
        if (id == null) {
            return false;
        }
        Skill s = skills.get(id.toLowerCase(Locale.ROOT));
        if (s == null || c.depth > 12) {
            return false;
        }
        return exec(s, c);
    }

    boolean onCooldown(LivingEntity caster, Skill s) {
        if (s.cooldown <= 0) {
            return false;
        }
        Map<String, Long> m = cooldowns.get(caster.getUniqueId());
        Long until = m == null ? null : m.get(s.id.toLowerCase(Locale.ROOT));
        return until != null && System.currentTimeMillis() < until;
    }

    long cooldownLeftMs(LivingEntity caster, String id) {
        Map<String, Long> m = cooldowns.get(caster.getUniqueId());
        Long until = m == null ? null : m.get(id.toLowerCase(Locale.ROOT));
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    void setCooldown(LivingEntity caster, String id, double seconds) {
        cooldowns.computeIfAbsent(caster.getUniqueId(), k -> new HashMap<>()).put(id.toLowerCase(Locale.ROOT), System.currentTimeMillis() + (long) (seconds * 1000));
    }

    boolean exec(Skill s, Ctx c) {
        LivingEntity caster = c.caster;
        if (caster == null || !caster.isValid()) {
            return false;
        }
        if (onCooldown(caster, s)) {
            return false;
        }
        // caster conditions
        for (Cond cd : s.conds) {
            boolean met = evalCond(cd, caster, c);
            if (cd.action != null) {
                if (met) {
                    if (cd.action.equals("castinstead")) {
                        cast(cd.actionArg, c.copy());
                        return false;
                    }
                    cast(cd.actionArg, c.copy());
                }
            } else if (met != cd.expect) {
                return false;
            }
        }
        // target conditions
        List<Tgt> inh = c.inherited;
        if (!s.tconds.isEmpty() && !inh.isEmpty()) {
            List<Tgt> ok = new ArrayList<>();
            for (Tgt t : inh) {
                boolean pass = true;
                if (t.e instanceof LivingEntity || t.e != null) {
                    for (Cond cd : s.tconds) {
                        if (evalCond(cd, t.e, c) != cd.expect) {
                            pass = false;
                            break;
                        }
                    }
                }
                if (pass) {
                    ok.add(t);
                }
            }
            if (ok.isEmpty()) {
                return false;
            }
            c = c.copy();
            c.depth--;
            c.inherited = ok;
        }
        if (s.cooldown > 0) {
            setCooldown(caster, s.id, s.cooldown);
        }
        runLines(s.lines, c);
        return true;
    }

    /** runs lines in order honouring delay lines and per-line delay/repeat options. */
    void runLines(List<Line> lines, Ctx c) {
        long delay = 0;
        for (Line l : lines) {
            if (l.trigger != null && !l.trigger.equalsIgnoreCase("onSpawn")) {
                continue;
            }
            if (l.name.equals("delay")) {
                delay += (long) Expr.eval(l.o.str("0", "_arg"), c.mods, 0);
                continue;
            }
            long d = delay + (long) l.o.num(c.mods, 0, "delay");
            int rep = (int) l.o.num(c.mods, 0, "repeat");
            long ri = Math.max(1, (long) l.o.num(c.mods, 1, "repeatinterval", "repeati"));
            if (rep > 400) {
                rep = 400;
            }
            for (int k = 0; k <= rep; k++) {
                final Ctx cc = c;
                later(d + k * ri, () -> execLine(l, cc));
                if (d + k * ri <= 0 && false) {
                    break;
                }
            }
        }
    }

    void execLine(Line l, Ctx c) {
        LivingEntity caster = c.caster;
        if (caster == null || !caster.isValid()) {
            return;
        }
        if (l.chance < 1.0 && rnd.nextDouble() > l.chance) {
            return;
        }
        List<Tgt> ts = resolve(l.tg, c);
        mech.run(l, c, ts);
    }

    // ------------------------------------------------------------------ targeters
    Vector facing(LivingEntity caster, boolean pitch) {
        Vector v = caster instanceof Player ? caster.getEyeLocation().getDirection() : Fx.fwd(caster.getLocation().getYaw());
        if (!pitch) {
            v.setY(0);
            if (v.lengthSquared() < 1e-6) {
                v = Fx.fwd(caster.getLocation().getYaw());
            }
        }
        return v.normalize();
    }

    List<Tgt> resolve(Targeter t, Ctx c) {
        List<Tgt> out = new ArrayList<>();
        LivingEntity caster = c.caster;
        Location origin = c.origin != null ? c.origin : caster.getLocation();
        if (t == null) {
            if (!c.inherited.isEmpty()) {
                return c.inherited;
            }
            out.add(new Tgt(caster));
            return out;
        }
        String n = t.name;
        switch (n) {
            case "self", "caster" -> out.add(new Tgt(caster));
            case "owner" -> out.add(new Tgt(actor(caster)));
            case "trigger", "target" -> {
                if (!c.inherited.isEmpty()) {
                    out.add(c.inherited.get(0));
                } else {
                    out.add(new Tgt(caster));
                }
            }
            case "origin" -> out.add(new Tgt(origin.clone()));
            case "forward" -> {
                double f = t.o.num(c.mods, 5, "f", "forward");
                double y = t.o.num(c.mods, 0, "y");
                boolean fromCaster = origin.distanceSquared(caster.getLocation()) < 9;
                Vector dir = facing(caster, fromCaster);
                Location l = origin.clone().add(dir.multiply(f)).add(0, y, 0);
                out.add(new Tgt(l));
            }
            case "selflocation" -> {
                double fo = t.o.num(c.mods, 0, "forwardoffset", "fo");
                double so = t.o.num(c.mods, 0, "sideoffset", "so");
                double y = t.o.num(c.mods, 0, "y", "yoffset");
                Location l = caster.getLocation().clone();
                l.add(Fx.fwd(l.getYaw()).multiply(fo)).add(Fx.side(l.getYaw()).multiply(so)).add(0, y, 0);
                out.add(new Tgt(l));
            }
            case "targetblock" -> {
                Block b = caster instanceof Player p ? p.getTargetBlockExact(60) : null;
                if (b != null) {
                    out.add(new Tgt(b.getLocation().add(0.5, 1.0, 0.5)));
                } else {
                    out.add(new Tgt(caster.getEyeLocation().add(facing(caster, true).multiply(40))));
                }
            }
            case "entitiesnearorigin", "eir", "entitiesinradius", "eno" -> out.addAll(near(origin, t.o.num(c.mods, 5, "r", "radius"), caster, false, false));
            case "mir", "mobsinradius", "mobsnearorigin" -> out.addAll(near(origin, t.o.num(c.mods, 5, "r", "radius"), caster, false, true));
            case "pir", "playersinradius", "playersnearorigin", "pno" -> out.addAll(near(origin, t.o.num(c.mods, 5, "r", "radius"), caster, true, false));
            case "server" -> {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    out.add(new Tgt(p));
                }
            }
            case "ring" -> {
                double r = t.o.num(c.mods, 3, "r", "radius");
                int pts = t.o.integer(c.mods, 8, "points");
                for (int i = 0; i < pts; i++) {
                    double a = 2 * Math.PI * i / pts;
                    out.add(new Tgt(origin.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
                }
            }
            case "randomlocationsnearorigin", "rlno" -> {
                int amt = t.o.integer(c.mods, 1, "amount", "a");
                double r = t.o.num(c.mods, 5, "radius", "r");
                for (int i = 0; i < amt; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double d = rnd.nextDouble() * r;
                    out.add(new Tgt(origin.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d)));
                }
            }
            default -> {
                // modelpart and unknown targeters: nothing
            }
        }
        return out;
    }

    List<Tgt> near(Location o, double r, LivingEntity caster, boolean playersOnly, boolean mobsOnly) {
        List<Tgt> out = new ArrayList<>();
        if (o.getWorld() == null) {
            return out;
        }
        Collection<Entity> es = o.getWorld().getNearbyEntities(o, r, r, r);
        LivingEntity act = actor(caster);
        for (Entity e : es) {
            if (!(e instanceof LivingEntity)) {
                continue;
            }
            if (playersOnly) {
                if (e instanceof Player) {
                    out.add(new Tgt(e));
                }
                continue;
            }
            if (e == caster || e == act) {
                continue;
            }
            if (mobsOnly && e instanceof Player) {
                continue;
            }
            out.add(new Tgt(e));
        }
        return out;
    }

    // ------------------------------------------------------------------ conditions
    boolean canTarget(LivingEntity attacker, Entity target) {
        if (!(target instanceof LivingEntity) || target instanceof ArmorStand) {
            return false;
        }
        LivingEntity act = actor(attacker);
        if (target == act || target == attacker) {
            return false;
        }
        if (target.hasMetadata("NPC") || stands.containsKey(target.getUniqueId())) {
            return false;
        }
        if (target instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                return false;
            }
            if (!p.getWorld().getPVP() || !plugin.getConfig().getBoolean("engine.pvp", true)) {
                return false;
            }
        }
        List<String> nd = plugin.getConfig().getStringList("engine.no-damage-worlds");
        return !nd.contains(target.getWorld().getName());
    }

    boolean evalCond(Cond cd, Entity subject, Ctx c) {
        Map<String, Double> mods = c.mods;
        LivingEntity living = subject instanceof LivingEntity le ? le : null;
        switch (cd.name) {
            case "crouching", "sneaking" -> {
                return subject instanceof Player p && p.isSneaking();
            }
            case "onground" -> {
                return subject.isOnGround();
            }
            case "moving" -> {
                return subject.getVelocity().lengthSquared() > 0.003;
            }
            case "hasaura" -> {
                return living != null && aura(living, cd.o.str("", "n", "auraname", "aura")) != null;
            }
            case "hasaurastacks" -> {
                if (living == null) {
                    return false;
                }
                Aura a = aura(living, cd.o.str("", "auraname", "n", "aura"));
                if (a == null) {
                    return false;
                }
                String st = cd.o.get("stacks", "s", "amount");
                if (st == null) {
                    return a.stacks > 0;
                }
                String v = st.trim();
                double n = Expr.eval(v, mods, 1);
                if (v.startsWith(">")) {
                    return a.stacks > n;
                }
                if (v.startsWith("<")) {
                    return a.stacks < n;
                }
                return a.stacks == (int) n;
            }
            case "wearing" -> {
                if (!(subject instanceof Player p)) {
                    return false;
                }
                String slot = cd.o.str("HEAD", "slot").toUpperCase(Locale.ROOT);
                String want = cd.o.str("", "m", "material", "item");
                ItemStack it;
                switch (slot) {
                    case "FEET" -> it = p.getInventory().getBoots();
                    case "LEGS" -> it = p.getInventory().getLeggings();
                    case "CHEST" -> it = p.getInventory().getChestplate();
                    default -> it = p.getInventory().getHelmet();
                }
                String k = items.keyOf(it);
                if (k != null && k.equalsIgnoreCase(want)) {
                    return true;
                }
                return slot.equals("HEAD") && plugin.cosmetics != null && want.equalsIgnoreCase(plugin.cosmetics.hatId(p));
            }
            case "entitytype" -> {
                String t = cd.o.str("", "t", "type");
                for (String x : t.split(",")) {
                    if (subject.getType().name().equalsIgnoreCase(x.trim())) {
                        return true;
                    }
                }
                return false;
            }
            case "mmocantarget" -> {
                String inter = cd.o.str("OFFENSE_ACTION", "interaction");
                if (inter.equalsIgnoreCase("SUPPORT_ACTION")) {
                    return subject instanceof LivingEntity && !(subject instanceof ArmorStand);
                }
                return canTarget(c.caster, subject);
            }
            case "mythicmobtype" -> {
                String t = mobType.get(subject.getUniqueId());
                String want = cd.o.str("", "t", "type");
                return t != null && t.equalsIgnoreCase(want);
            }
            case "owner" -> {
                UUID o = owners.get(subject.getUniqueId());
                return o != null && o.equals(actor(c.caster).getUniqueId());
            }
            case "ischild" -> {
                return subject instanceof org.bukkit.entity.Ageable ag && !ag.isAdult();
            }
            case "targetwithin" -> {
                double d = cd.o.num(mods, 10, "d", "distance");
                return c.caster.getWorld().getNearbyEntities(c.caster.getLocation(), d, d, d, e -> e instanceof LivingEntity && e != c.caster && !(e instanceof ArmorStand)).size() > 0;
            }
            case "mobsinradius" -> {
                double d = cd.o.num(mods, 5, "r", "radius");
                return !near(c.caster.getLocation(), d, c.caster, false, true).isEmpty();
            }
            case "health" -> {
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    // ------------------------------------------------------------------ VFX stands
    ArmorStand spawnStand(MobDef def, Location loc, LivingEntity owner, float yaw) {
        if (stands.size() >= maxStands || loc.getWorld() == null) {
            return null;
        }
        Location at = loc.clone();
        at.setYaw(yaw);
        at.setPitch(0);
        ArmorStand as = loc.getWorld().spawn(at, ArmorStand.class, e -> {
            e.setVisible(false);
            e.setGravity(false);
            e.setMarker(true);
            e.setSmall(def.small);
            e.setArms(def.arms || def.pose.containsKey("rightarm") || def.itemHand != null);
            e.setBasePlate(false);
            e.setInvulnerable(true);
            e.setPersistent(false);
            e.setSilent(true);
            e.setCollidable(false);
            e.setCanPickupItems(false);
            e.getPersistentDataContainer().set(vfxKey, PersistentDataType.BYTE, (byte) 1);
            for (Map.Entry<String, double[]> p : def.pose.entrySet()) {
                EulerAngle ea = new EulerAngle(Math.toRadians(p.getValue()[0]), Math.toRadians(p.getValue()[1]), Math.toRadians(p.getValue()[2]));
                switch (p.getKey()) {
                    case "head" -> e.setHeadPose(ea);
                    case "body" -> e.setBodyPose(ea);
                    case "leftarm" -> e.setLeftArmPose(ea);
                    case "rightarm" -> e.setRightArmPose(ea);
                    case "leftleg" -> e.setLeftLegPose(ea);
                    case "rightleg" -> e.setRightLegPose(ea);
                    default -> {
                    }
                }
            }
            if (def.itemHead != null) {
                ItemStack it = items.frame(def.itemHead);
                if (it != null) {
                    e.getEquipment().setHelmet(it);
                }
            }
            if (def.itemHand != null) {
                ItemStack it = items.frame(def.itemHand);
                if (it != null) {
                    e.getEquipment().setItemInMainHand(it);
                }
            }
        });
        stands.put(as.getUniqueId(), now);
        mobType.put(as.getUniqueId(), def.id);
        if (owner != null) {
            owners.put(as.getUniqueId(), actor(owner).getUniqueId());
        }
        // run the mob's own skill lines
        Ctx mc = new Ctx();
        mc.caster = as;
        mc.origin = as.getLocation();
        List<Line> spawn = new ArrayList<>();
        for (Line l : def.lines) {
            String tr = l.trigger == null ? "onspawn" : l.trigger.toLowerCase(Locale.ROOT);
            if (tr.equals("onspawn")) {
                spawn.add(l);
            } else if (tr.startsWith("ontimer:")) {
                long period = Math.max(1, Long.parseLong(tr.substring(8).replaceAll("[^0-9]", "0").isEmpty() ? "1" : tr.substring(8).replaceAll("[^0-9]", "")));
                final Line line = l;
                final ArmorStand st = as;
                every(period, period, () -> {
                    if (!st.isValid()) {
                        return false;
                    }
                    Ctx tc = new Ctx();
                    tc.caster = st;
                    tc.origin = st.getLocation();
                    execLine(line, tc);
                    return true;
                });
            } else if (tr.startsWith("onsignal:")) {
                signals.computeIfAbsent(as.getUniqueId(), k -> new HashMap<>()).computeIfAbsent(tr.substring(9), k -> new ArrayList<>()).add(l);
            }
        }
        // onSpawn lines must not be skipped by the trigger filter
        List<Line> run = new ArrayList<>();
        for (Line l : spawn) {
            Line copy = l;
            run.add(copy);
        }
        runLines(run, mc);
        return as;
    }

    void signal(Entity e, String sig) {
        Map<String, List<Line>> m = signals.get(e.getUniqueId());
        if (m == null || !(e instanceof LivingEntity le)) {
            return;
        }
        List<Line> ls = m.get(sig.toLowerCase(Locale.ROOT));
        if (ls == null) {
            return;
        }
        Ctx c = new Ctx();
        c.caster = le;
        c.origin = le.getLocation();
        for (Line l : ls) {
            execLine(l, c);
        }
    }

    void removeStand(Entity e) {
        if (e != null) {
            e.remove();
        }
    }

    Material solidCheck(Location l) {
        return l.getBlock().getType();
    }

    EquipmentSlot slotOf(int mm) {
        return switch (mm) {
            case 1 -> EquipmentSlot.FEET;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.CHEST;
            case 4 -> EquipmentSlot.HEAD;
            case 5 -> EquipmentSlot.OFF_HAND;
            default -> EquipmentSlot.HAND;
        };
    }
}
