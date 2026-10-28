package me.naook1.nereusclasses;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import me.naook1.nereusclasses.Engine.Ctx;
import me.naook1.nereusclasses.Engine.Tgt;
import me.naook1.nereusclasses.Model.Line;
import me.naook1.nereusclasses.Model.MobDef;
import me.naook1.nereusclasses.Model.Opts;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** mechanic implementations. */
final class Mech {

    static final class Proj {
        Location pos;
        Vector dir;
        double speed;
        boolean ended;
        ArmorStand stand;
        final Set<UUID> hit = new HashSet<>();
    }

    private final Engine e;

    Mech(Engine e) {
        this.e = e;
    }

    void run(Line l, Ctx c, List<Tgt> ts) {
        Opts o = l.o;
        switch (l.name) {
            case "effect:particles", "particles" -> particles(o, c, ts);
            case "effect:particlering", "particlering" -> ring(o, c, ts);
            case "effect:particlesphere", "particlesphere" -> sphere(o, c, ts);
            case "effect:sound", "sound" -> {
                String s = o.str("", "s", "sound");
                float v = (float) o.num(c.mods, 1, "volume", "v");
                float p = (float) o.num(c.mods, 1, "pitch", "p");
                for (Tgt t : ts) {
                    Fx.sound(t.loc(), s, v, p);
                }
            }
            case "effect:explosion", "explosion" -> {
                for (Tgt t : ts) {
                    t.loc().getWorld().spawnParticle(Particle.EXPLOSION, t.loc(), 1);
                    Fx.sound(t.loc(), "entity.generic.explode", 1f, 1f);
                }
            }
            case "damage" -> damage(o, c, ts);
            case "heal" -> {
                double a = o.num(c.mods, 1, "a", "amount");
                for (Tgt t : ts) {
                    if (t.e instanceof LivingEntity le && !(le instanceof ArmorStand)) {
                        double max = le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                        le.setHealth(Math.min(max, le.getHealth() + a));
                    }
                }
            }
            case "potion" -> potion(o, c, ts);
            case "potionclear" -> {
                for (Tgt t : ts) {
                    if (t.e instanceof LivingEntity le) {
                        for (PotionEffect pe : new ArrayList<>(le.getActivePotionEffects())) {
                            le.removePotionEffect(pe.getType());
                        }
                    }
                }
            }
            case "ignite" -> {
                int d = o.integer(c.mods, 60, "duration", "d", "ticks");
                for (Tgt t : ts) {
                    if (t.e != null && e.canTarget(c.caster, t.e)) {
                        t.e.setFireTicks(d);
                    }
                }
            }
            case "skill", "sudoskill" -> {
                String s = o.str("", "s", "skill", "meta", "m");
                Ctx c2 = c.copy();
                if (!ts.isEmpty()) {
                    c2.inherited = new ArrayList<>(ts);
                }
                e.cast(s, c2);
            }
            case "randomskill" -> {
                String[] a = o.str("", "skills", "s").split(",");
                if (a.length > 0) {
                    Ctx c2 = c.copy();
                    if (!ts.isEmpty()) {
                        c2.inherited = new ArrayList<>(ts);
                    }
                    e.cast(a[e.rnd.nextInt(a.length)].trim(), c2);
                }
            }
            case "summon" -> summon(o, c, ts);
            case "projectile", "missile" -> {
                for (Tgt t : ts) {
                    projectile(o, c, t);
                }
            }
            case "orbital" -> orbital(o, c, ts);
            case "totem" -> totem(o, c, ts);
            case "aura" -> aura(o, c, ts);
            case "auraremove" -> {
                String n = o.str("", "auraname", "n", "aura");
                int st = o.integer(c.mods, 0, "stacks");
                for (Tgt t : ts) {
                    if (t.e instanceof LivingEntity le) {
                        e.removeAura(le, n, st);
                    }
                }
            }
            case "ondamaged" -> {
                for (Tgt t : ts) {
                    if (t.e instanceof LivingEntity le) {
                        Engine.Aura a = new Engine.Aura();
                        a.name = o.str("shield", "auraname", "n");
                        a.dmg = true;
                        a.onHit = o.get("onhit", "oh");
                        a.cancel = o.bool(false, "cancelevent", "ce");
                        a.end = e.now + (long) o.num(c.mods, 20, "d", "duration");
                        a.ctx = c.copy();
                        e.applyAura(le, a);
                    }
                }
            }
            case "equip" -> equip(o, c, ts);
            case "remove" -> {
                for (Tgt t : ts) {
                    if (t.e != null && e.stands.containsKey(t.e.getUniqueId())) {
                        t.e.remove();
                    }
                }
            }
            case "signal" -> {
                String s = o.str("", "s", "signal");
                for (Tgt t : ts) {
                    if (t.e != null) {
                        e.signal(t.e, s);
                    }
                }
            }
            case "lunge" -> lunge(o, c, ts);
            case "leap" -> {
                double v = o.num(c.mods, 100, "velocity", "v");
                for (Tgt t : ts) {
                    Vector d = t.loc().toVector().subtract(c.caster.getLocation().toVector());
                    d.setY(0);
                    d = d.lengthSquared() < 0.01 ? e.facing(c.caster, false) : d.normalize();
                    double s = Math.min(1.4, Math.max(0.4, v / 300.0 * 1.0));
                    c.caster.setVelocity(d.multiply(s * 0.8).setY(0.9));
                }
            }
            case "throw" -> throwM(o, c, ts);
            case "pull" -> {
                double v = o.num(c.mods, 5, "velocity", "v") * 0.07;
                Location org = c.origin != null ? c.origin : c.caster.getLocation();
                for (Tgt t : ts) {
                    if (t.e != null && t.e != c.caster && e.canTarget(c.caster, t.e)) {
                        Vector d = org.toVector().subtract(t.e.getLocation().toVector());
                        if (d.lengthSquared() > 0.04) {
                            t.e.setVelocity(d.normalize().multiply(Math.min(1.2, v)).setY(0.12));
                        }
                    }
                }
            }
            case "propel" -> {
                double v = o.num(c.mods, 5, "v", "velocity") * 0.1;
                c.caster.setVelocity(e.facing(c.caster, false).multiply(Math.min(1.5, v)).setY(0.1));
            }
            case "recoil" -> {
                double v = o.num(c.mods, 5, "velocity", "v") * 0.1;
                c.caster.setVelocity(e.facing(c.caster, false).multiply(-Math.min(1.2, v)).setY(0.2));
            }
            case "stun" -> {
                int d = o.integer(c.mods, 20, "d", "duration");
                for (Tgt t : ts) {
                    if (t.e instanceof LivingEntity le && !(le instanceof ArmorStand)) {
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, d, 6, false, false, false));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, d, 128, false, false, false));
                    }
                }
            }
            case "teleport" -> teleport(o, c, ts);
            case "message" -> {
                String m = o.str("", "m", "message").replace("<&co>", ":").replaceAll("<[^>]*>", "");
                for (Tgt t : ts) {
                    if (t.e instanceof Player p) {
                        p.sendMessage(NereusClasses.color(m));
                    }
                }
            }
            case "setskillcooldown" -> e.setCooldown(c.caster, o.str("", "s", "skill"), o.num(c.mods, 0, "cooldown", "t", "d") / 1.0);
            case "modifyprojectile" -> {
                if (c.proj instanceof Proj p) {
                    String trait = o.str("", "trait").toUpperCase(Locale.ROOT);
                    String act = o.str("", "action").toUpperCase(Locale.ROOT);
                    double v = o.num(c.mods, 1, "value");
                    if (trait.equals("VELOCITY")) {
                        p.speed = act.equals("MULTIPLY") ? p.speed * v : act.equals("ADD") ? p.speed + v : v;
                    }
                }
            }
            default -> {
                // delay (handled by the runner), state, hide, look, model, mounttarget ... are no-ops
            }
        }
    }

    // ------------------------------------------------------------------ effects
    private Location offset(Location base, Opts o, Ctx c, Tgt t) {
        Location l = base.clone();
        double fo = o.num(c.mods, 0, "forwardoffset", "fo");
        double so = o.num(c.mods, 0, "sideoffset", "so");
        double y = o.num(c.mods, 0, "y", "yoffset");
        float yaw = t.e != null ? t.e.getLocation().getYaw() : c.caster.getLocation().getYaw();
        if (fo != 0) {
            l.add(Fx.fwd(yaw).multiply(fo));
        }
        if (so != 0) {
            l.add(Fx.side(yaw).multiply(so));
        }
        return l.add(0, y, 0);
    }

    private void particles(Opts o, Ctx c, List<Tgt> ts) {
        Particle p = Fx.particle(o.str("redstone", "particle", "p"));
        Color col = Fx.color(o.get("color", "c"), Color.RED);
        int amt = o.integer(c.mods, 1, "amount", "a");
        double hs = o.num(c.mods, 0, "hs", "hspread", "horizontalspread");
        double vs = o.num(c.mods, 0, "vs", "yspread", "verticalspread");
        double sp = o.num(c.mods, 0, "speed", "s");
        float size = (float) o.num(c.mods, 1, "size");
        for (Tgt t : ts) {
            Location l = offset(t.loc(), o, c, t);
            if (l.getWorld() != null) {
                Fx.spawn(l, p, amt, hs, vs, sp, col, size);
            }
        }
    }

    private void ring(Opts o, Ctx c, List<Tgt> ts) {
        Particle p = Fx.particle(o.str("redstone", "particle", "p"));
        Color col = Fx.color(o.get("color", "c"), Color.RED);
        double r = o.num(c.mods, 3, "radius", "r");
        int pts = Math.min(400, o.integer(c.mods, 16, "points"));
        int amt = o.integer(c.mods, 1, "amount", "a");
        double hs = o.num(c.mods, 0, "hs");
        double vs = o.num(c.mods, 0, "vs");
        double y = o.num(c.mods, 0, "y");
        for (Tgt t : ts) {
            Location b = t.loc();
            if (b.getWorld() == null) {
                continue;
            }
            for (int i = 0; i < pts; i++) {
                double a = 2 * Math.PI * i / pts;
                Fx.spawn(b.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), p, amt, hs, vs, 0, col, 1f);
            }
        }
    }

    private void sphere(Opts o, Ctx c, List<Tgt> ts) {
        Particle p = Fx.particle(o.str("redstone", "particle", "p"));
        Color col = Fx.color(o.get("color", "c"), Color.RED);
        double r = o.num(c.mods, 3, "radius", "r");
        int amt = Math.min(300, o.integer(c.mods, 30, "amount", "a"));
        for (Tgt t : ts) {
            Location b = t.loc();
            if (b.getWorld() == null) {
                continue;
            }
            for (int i = 0; i < amt; i++) {
                Vector v = new Vector(e.rnd.nextGaussian(), e.rnd.nextGaussian(), e.rnd.nextGaussian());
                if (v.lengthSquared() < 1e-6) {
                    continue;
                }
                Fx.spawn(b.clone().add(v.normalize().multiply(r)), p, 1, 0, 0, 0, col, 1f);
            }
        }
    }

    private void damage(Opts o, Ctx c, List<Tgt> ts) {
        double a = o.num(c.mods, 1, "a", "amount") * e.plugin.getConfig().getDouble("engine.damage-multiplier", 1.0);
        boolean pkb = o.bool(false, "pkb", "preventknockback");
        LivingEntity actor = e.actor(c.caster);
        for (Tgt t : ts) {
            if (!(t.e instanceof LivingEntity le) || le instanceof ArmorStand) {
                continue;
            }
            if (le == actor || !e.canTarget(c.caster, le)) {
                continue;
            }
            Vector vel = le.getVelocity();
            e.inSkillDamage = true;
            try {
                le.setNoDamageTicks(0);
                le.damage(a, actor);
            } finally {
                e.inSkillDamage = false;
            }
            if (pkb) {
                e.later(1, () -> le.setVelocity(vel));
            }
        }
    }

    private void potion(Opts o, Ctx c, List<Tgt> ts) {
        PotionEffectType type = Fx.potion(o.str("", "type", "t"));
        if (type == null) {
            return;
        }
        int d = Math.max(1, (int) o.num(c.mods, 100, "duration", "d"));
        int lvl = Math.max(1, o.integer(c.mods, 1, "level", "lvl", "l"));
        boolean particles = o.bool(true, "hasparticles", "p");
        for (Tgt t : ts) {
            if (t.e instanceof LivingEntity le && !(le instanceof ArmorStand)) {
                le.addPotionEffect(new PotionEffect(type, d, lvl - 1, false, particles, particles));
            }
        }
    }

    private void aura(Opts o, Ctx c, List<Tgt> ts) {
        String n = o.str("aura", "auraname", "n", "name");
        long d = (long) o.num(c.mods, 100, "duration", "d");
        int ms = o.integer(c.mods, 1, "maxstacks", "ms");
        int iv = o.integer(c.mods, 0, "interval", "i");
        for (Tgt t : ts) {
            if (!(t.e instanceof LivingEntity le)) {
                continue;
            }
            Engine.Aura a = new Engine.Aura();
            a.name = n;
            a.maxStacks = Math.max(1, ms);
            a.end = e.now + Math.max(1, d);
            a.interval = iv;
            a.nextTick = e.now + Math.max(1, iv);
            a.onTick = o.get("ontick", "ot");
            a.onEnd = o.get("onend", "oe");
            a.ctx = c.copy();
            boolean fresh = e.aura(le, n) == null;
            e.applyAura(le, a);
            String os = o.get("onstart", "os");
            if (fresh && os != null) {
                Ctx c2 = new Ctx();
                c2.caster = le;
                c2.origin = le.getLocation();
                c2.inherited.add(new Tgt(le));
                e.cast(os, c2);
            }
        }
    }

    private void equip(Opts o, Ctx c, List<Tgt> ts) {
        String spec = o.str("", "item", "i", "material");
        String key = spec;
        int slot = 0;
        int i = spec.lastIndexOf(':');
        if (i > 0) {
            key = spec.substring(0, i);
            try {
                slot = Integer.parseInt(spec.substring(i + 1).trim());
            } catch (NumberFormatException ignored) {
                slot = 0;
            }
        }
        ItemStack it = e.items.frame(key.trim().toLowerCase(Locale.ROOT));
        for (Tgt t : ts) {
            if (t.e instanceof LivingEntity le && le.getEquipment() != null) {
                le.getEquipment().setItem(e.slotOf(slot), it, true);
            }
        }
    }

    private void lunge(Opts o, Ctx c, List<Tgt> ts) {
        double v = o.num(c.mods, 1, "velocity", "v");
        double vy = o.num(c.mods, 0, "velocityy", "vy");
        for (Tgt t : ts) {
            Vector d = t.loc().toVector().subtract(c.caster.getLocation().toVector());
            if (t.e == c.caster || d.lengthSquared() < 0.01) {
                d = e.facing(c.caster, false);
            } else {
                d.normalize();
            }
            Vector vel = d.multiply(Math.min(1.7, v * 0.45));
            vel.setY(Math.max(-1, Math.min(1, vy * 0.45 + (d.getY() * 0.2))));
            c.caster.setVelocity(vel);
        }
    }

    private void throwM(Opts o, Ctx c, List<Tgt> ts) {
        double v = o.num(c.mods, 1, "velocity", "v") * 0.1;
        double vy = o.num(c.mods, 0, "velocityy", "vy") * 0.1;
        for (Tgt t : ts) {
            if (t.e == null) {
                continue;
            }
            if (t.e != c.caster && !(e.canTarget(c.caster, t.e) || t.e instanceof Player)) {
                continue;
            }
            Vector d;
            if (t.e == c.caster) {
                d = e.facing(c.caster, false);
            } else {
                d = t.e.getLocation().toVector().subtract(c.caster.getLocation().toVector()).setY(0);
                d = d.lengthSquared() < 0.01 ? e.facing(c.caster, false) : d.normalize();
            }
            t.e.setVelocity(d.multiply(Math.min(2.0, v)).setY(Math.min(1.5, vy)));
        }
    }

    private void teleport(Opts o, Ctx c, List<Tgt> ts) {
        LivingEntity ce = c.caster;
        if (!ts.isEmpty() && ts.get(0).e != ce && ts.get(0).loc() != null && ts.get(0).e == null) {
            Location l = ts.get(0).loc().clone();
            l.setYaw(ce.getLocation().getYaw());
            l.setPitch(ce.getLocation().getPitch());
            ce.teleport(l);
            return;
        }
        double sh = o.num(c.mods, 3, "spreadh");
        double sv = o.num(c.mods, 0, "spreadv");
        for (int i = 0; i < 8; i++) {
            Location l = ce.getLocation().clone().add((e.rnd.nextDouble() * 2 - 1) * sh, (e.rnd.nextDouble() * 2 - 1) * sv, (e.rnd.nextDouble() * 2 - 1) * sh);
            if (l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable() && !l.clone().add(0, -1, 0).getBlock().isPassable()) {
                ce.teleport(l);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ summon
    private Location surface(Location l) {
        Location c = l.clone();
        for (int i = 0; i < 8; i++) {
            if (!c.getBlock().isPassable()) {
                return c.getBlock().getLocation().add(c.getX() - c.getBlockX(), 1.0, c.getZ() - c.getBlockZ());
            }
            c.add(0, -1, 0);
        }
        return l;
    }

    private void summon(Opts o, Ctx c, List<Tgt> ts) {
        String type = o.str("", "type", "mob", "t").toLowerCase(Locale.ROOT);
        MobDef def = e.mobs.get(type);
        if (def == null) {
            return;
        }
        int amt = Math.min(12, o.integer(c.mods, 1, "amount", "a"));
        double rad = o.num(c.mods, 0, "radius", "r");
        double yr = o.num(c.mods, 0, "yradius", "yr");
        boolean onSurf = o.bool(false, "onsurface", "os");
        for (Tgt t : ts) {
            for (int i = 0; i < amt; i++) {
                Location l = t.loc().clone();
                if (rad > 0) {
                    double a = e.rnd.nextDouble() * Math.PI * 2;
                    double d = e.rnd.nextDouble() * rad;
                    l.add(Math.cos(a) * d, 0, Math.sin(a) * d);
                }
                if (yr > 0) {
                    l.add(0, (e.rnd.nextDouble() * 2 - 1) * yr, 0);
                }
                if (onSurf) {
                    l = surface(l);
                }
                e.spawnStand(def, l, c.caster, c.caster.getLocation().getYaw());
            }
        }
    }

    // ------------------------------------------------------------------ projectile
    private void projectile(Opts o, Ctx c, Tgt t) {
        LivingEntity caster = c.caster;
        double v = o.num(c.mods, 5, "v", "velocity");
        int iv = Math.max(1, o.integer(c.mods, 1, "i", "interval"));
        double mr = o.num(c.mods, 20, "mr", "maxrange");
        int md = o.integer(c.mods, 0, "md", "maxduration");
        double hr = o.num(c.mods, 1, "hr", "horizontalradius");
        double vr = o.num(c.mods, 1, "vr", "verticalradius");
        boolean hnp = o.bool(false, "hnp", "hitnonplayers");
        boolean hp = o.bool(true, "hp", "hitplayers");
        boolean se = o.bool(false, "se", "stopatentity");
        boolean sb = o.bool(true, "sb", "stopatblock");
        boolean hug = o.bool(false, "hs", "hugsurface");
        double hfs = o.num(c.mods, 0, "hfs", "heightfromsurface");
        double syo = o.num(c.mods, 0, "syo", "startyoffset");
        double tyo = o.num(c.mods, 0, "tyo", "targetyoffset");
        double sfo = o.num(c.mods, 0, "sfo", "startforwardoffset");
        double sso = o.num(c.mods, 0, "sso", "startsideoffset");
        double ho = o.num(c.mods, 0, "ho", "horizontaloffset");
        double vo = o.num(c.mods, 0, "vo", "verticaloffset");
        boolean fromOrigin = o.bool(false, "fromorigin");
        String mob = "mob".equalsIgnoreCase(o.str("", "bullettype", "bt")) ? o.str(null, "mob") : null;
        String onHit = o.get("onhit", "oh");
        String onTick = o.get("ontick", "ot");
        String onEnd = o.get("onend", "oe");
        String onStart = o.get("onstart", "os");

        Location base = fromOrigin && c.origin != null ? c.origin.clone() : caster.getLocation().clone();
        float yaw = caster.getLocation().getYaw();
        Location start = base.clone().add(0, syo, 0).add(Fx.fwd(yaw).multiply(sfo)).add(Fx.side(yaw).multiply(sso));
        Location tl = t.loc().clone().add(0, tyo, 0);
        Vector dir = tl.toVector().subtract(start.toVector());
        if (dir.lengthSquared() < 1e-4) {
            dir = e.facing(caster, true);
        } else {
            dir.normalize();
        }
        if (ho != 0) {
            dir = Fx.rotY(dir, ho);
        }
        if (vo != 0) {
            Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
            if (right.lengthSquared() < 1e-6) {
                right = new Vector(1, 0, 0);
            }
            dir = dir.clone().rotateAroundAxis(right.normalize(), Math.toRadians(vo)).normalize();
        }
        final Proj p = new Proj();
        p.pos = start;
        p.dir = dir;
        p.speed = v;
        if (mob != null) {
            MobDef def = e.mobs.get(mob.toLowerCase(Locale.ROOT));
            if (def != null) {
                p.stand = e.spawnStand(def, start, caster, Fx.yawOf(dir));
            }
        }
        if (onStart != null) {
            Ctx cs = c.copy();
            cs.origin = start.clone();
            cs.inherited = new ArrayList<>();
            e.cast(onStart, cs);
        }
        final double[] dist = {0};
        final int[] life = {0};
        e.every(iv, iv, () -> {
            if (p.ended) {
                return false;
            }
            life[0] += iv;
            double step = p.speed * iv / 20.0;
            Location np = p.pos.clone().add(p.dir.clone().multiply(step));
            if (hug) {
                Location g = np.clone();
                for (int i = 0; i < 6 && g.getBlock().isPassable(); i++) {
                    g.add(0, -1, 0);
                }
                np.setY(g.getBlockY() + 1 + hfs);
            }
            boolean end = false;
            if (sb) {
                Block b = np.getBlock();
                if (!b.isPassable() && !hug) {
                    end = true;
                }
            }
            if (!end) {
                p.pos = np;
                if (p.stand != null && p.stand.isValid()) {
                    Location sl = np.clone();
                    sl.setYaw(Fx.yawOf(p.dir));
                    p.stand.teleport(sl);
                }
                if (onTick != null) {
                    Ctx ct = c.copy();
                    ct.origin = np.clone();
                    ct.inherited = new ArrayList<>();
                    ct.proj = p;
                    e.cast(onTick, ct);
                }
                if (onHit != null && np.getWorld() != null) {
                    LivingEntity act = e.actor(caster);
                    for (Entity en : np.getWorld().getNearbyEntities(np, hr, vr, hr)) {
                        if (!(en instanceof LivingEntity) || en == caster || en == act || en instanceof ArmorStand || p.hit.contains(en.getUniqueId())) {
                            continue;
                        }
                        if (en instanceof Player ? !hp : !hnp) {
                            continue;
                        }
                        p.hit.add(en.getUniqueId());
                        Ctx ch = c.copy();
                        ch.origin = np.clone();
                        ch.inherited = new ArrayList<>();
                        ch.inherited.add(new Tgt(en));
                        e.cast(onHit, ch);
                        if (se) {
                            end = true;
                            break;
                        }
                    }
                }
                dist[0] += step;
                if (dist[0] >= mr || (md > 0 && life[0] >= md)) {
                    end = true;
                }
            }
            if (end) {
                p.ended = true;
                if (p.stand != null) {
                    p.stand.remove();
                }
                if (onEnd != null) {
                    Ctx ce = c.copy();
                    ce.origin = p.pos.clone();
                    ce.inherited = new ArrayList<>();
                    e.cast(onEnd, ce);
                }
                return false;
            }
            return true;
        });
    }

    // ------------------------------------------------------------------ orbital
    private void orbital(Opts o, Ctx c, List<Tgt> ts) {
        double r = o.num(c.mods, 1, "r", "radius");
        int points = Math.max(1, Math.min(40, o.integer(c.mods, 8, "points")));
        int iv = Math.max(1, o.integer(c.mods, 1, "interval", "i"));
        int dur = (int) Math.min(2400, o.num(c.mods, 40, "duration", "d"));
        double oy = o.num(c.mods, 0, "oy", "yoffset");
        double rx = o.num(c.mods, 0, "rx");
        double ry = o.num(c.mods, 0, "ry");
        double rz = o.num(c.mods, 0, "rz");
        double avx = o.num(c.mods, 0, "avx");
        double avy = o.num(c.mods, 0, "avy");
        double avz = o.num(c.mods, 0, "avz");
        String onTick = o.get("ontick", "ot");
        String onHit = o.get("onhit", "oh");
        double hr = o.num(c.mods, 1, "hr");
        double vr = o.num(c.mods, 1, "vr");
        boolean hp = o.bool(true, "hp");
        boolean hnp = o.bool(false, "hnp");
        String mob = "mob".equalsIgnoreCase(o.str("", "bullettype", "bt")) ? o.str(null, "mob") : null;
        MobDef def = mob == null ? null : e.mobs.get(mob.toLowerCase(Locale.ROOT));
        for (Tgt t : ts) {
            final Tgt tg = t;
            final ArmorStand[] st = new ArmorStand[points];
            final int[] life = {0};
            final Set<UUID> hit = new HashSet<>();
            e.every(0, iv, () -> {
                if (tg.e != null && !tg.e.isValid()) {
                    return cleanup(st);
                }
                if (life[0] >= dur || (c.caster != null && !c.caster.isValid())) {
                    return cleanup(st);
                }
                Location center = tg.loc().clone().add(0, oy, 0);
                double tt = life[0];
                for (int k = 0; k < points; k++) {
                    double phi = 2 * Math.PI * k / points;
                    Vector v = new Vector(r * Math.cos(phi), 0, r * Math.sin(phi));
                    v = rot(v, rx + avx * tt, ry + avy * tt, rz + avz * tt);
                    Location pl = center.clone().add(v);
                    if (def != null) {
                        if (st[k] == null || !st[k].isValid()) {
                            st[k] = e.spawnStand(def, pl, c.caster, c.caster.getLocation().getYaw());
                        } else {
                            st[k].teleport(pl);
                        }
                    }
                    if (onTick != null) {
                        Ctx ct = c.copy();
                        ct.origin = pl.clone();
                        ct.inherited = new ArrayList<>();
                        e.cast(onTick, ct);
                    }
                    if (onHit != null && pl.getWorld() != null) {
                        for (Entity en : pl.getWorld().getNearbyEntities(pl, hr, vr, hr)) {
                            if (!(en instanceof LivingEntity) || en == c.caster || en instanceof ArmorStand || hit.contains(en.getUniqueId())) {
                                continue;
                            }
                            if (en instanceof Player ? !hp : !hnp) {
                                continue;
                            }
                            hit.add(en.getUniqueId());
                            Ctx ch = c.copy();
                            ch.origin = pl.clone();
                            ch.inherited = new ArrayList<>();
                            ch.inherited.add(new Tgt(en));
                            e.cast(onHit, ch);
                        }
                    }
                }
                life[0] += iv;
                return true;
            });
        }
    }

    private boolean cleanup(ArmorStand[] st) {
        for (ArmorStand a : st) {
            if (a != null) {
                a.remove();
            }
        }
        return false;
    }

    private static Vector rot(Vector v, double rx, double ry, double rz) {
        double x = v.getX();
        double y = v.getY();
        double z = v.getZ();
        double c = Math.cos(rx);
        double s = Math.sin(rx);
        double y1 = y * c - z * s;
        double z1 = y * s + z * c;
        c = Math.cos(ry);
        s = Math.sin(ry);
        double x2 = x * c + z1 * s;
        double z2 = -x * s + z1 * c;
        c = Math.cos(rz);
        s = Math.sin(rz);
        double x3 = x2 * c - y1 * s;
        double y3 = x2 * s + y1 * c;
        return new Vector(x3, y3, z2);
    }

    // ------------------------------------------------------------------ totem
    private void totem(Opts o, Ctx c, List<Tgt> ts) {
        int md = Math.max(1, o.integer(c.mods, 2, "md", "maxduration"));
        int iv = Math.max(1, o.integer(c.mods, 1, "i", "interval"));
        double hr = o.num(c.mods, 2, "hr");
        double vr = o.num(c.mods, 2, "vr");
        boolean hp = o.bool(true, "hp");
        boolean hnp = o.bool(true, "hnp");
        String onHit = o.get("oh", "onhit");
        String onTick = o.get("ot", "ontick");
        for (Tgt t : ts) {
            final Location at = t.loc().clone();
            final Set<UUID> hit = new HashSet<>();
            final int[] life = {0};
            e.every(0, iv, () -> {
                if (!c.caster.isValid()) {
                    return false;
                }
                if (onTick != null) {
                    Ctx ct = c.copy();
                    ct.origin = at.clone();
                    ct.inherited = new ArrayList<>();
                    e.cast(onTick, ct);
                }
                if (onHit != null && at.getWorld() != null) {
                    LivingEntity act = e.actor(c.caster);
                    for (Entity en : at.getWorld().getNearbyEntities(at, hr, vr, hr)) {
                        if (!(en instanceof LivingEntity) || en == c.caster || en == act || en instanceof ArmorStand || hit.contains(en.getUniqueId())) {
                            continue;
                        }
                        if (en instanceof Player ? !hp : !hnp) {
                            continue;
                        }
                        hit.add(en.getUniqueId());
                        Ctx ch = c.copy();
                        ch.origin = at.clone();
                        ch.inherited = new ArrayList<>();
                        ch.inherited.add(new Tgt(en));
                        e.cast(onHit, ch);
                    }
                }
                life[0] += iv;
                return life[0] < md;
            });
        }
    }
}
