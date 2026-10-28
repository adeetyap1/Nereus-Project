package me.naook1.nereusweapons;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Active skills. Every skill is data driven (see config.yml) and only damages enemies and players. */
public final class Skills {

    private final NereusWeapons plugin;

    public Skills(NereusWeapons plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ helpers
    private boolean valid(Player c, Entity e) {
        if (e == c || !(e instanceof LivingEntity le) || le.isDead() || e instanceof ArmorStand || e.hasMetadata("NPC")) {
            return false;
        }
        if (e instanceof Player p) {
            return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
        }
        return e instanceof Enemy;
    }

    private Set<LivingEntity> near(Player c, Location center, double r) {
        Set<LivingEntity> out = new HashSet<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, r, r, r)) {
            if (valid(c, e) && e.getLocation().add(0, e.getHeight() / 2, 0).distanceSquared(center) <= r * r) {
                out.add((LivingEntity) e);
            }
        }
        return out;
    }

    private void fx(World w, Location at, SkillDef s, int count, double spread) {
        if (s.particle() == Particle.DUST) {
            w.spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0, new Particle.DustOptions(s.color(), 1.5f));
        } else if (s.particle() == Particle.ENTITY_EFFECT) {
            w.spawnParticle(Particle.ENTITY_EFFECT, at, count, spread, spread, spread, 0, s.color());
        } else {
            w.spawnParticle(s.particle(), at, count, spread, spread, spread, 0.01);
        }
    }

    private void ring(Player p, Location c, double r, SkillDef s) {
        for (int deg = 0; deg < 360; deg += 12) {
            double a = Math.toRadians(deg);
            fx(c.getWorld(), c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), s, 1, 0.02);
        }
    }

    private double hit(Player c, LivingEntity t, SkillDef s, Vector from) {
        double dealt = 0;
        if (s.damage() > 0) {
            double before = t.getHealth();
            t.damage(s.damage(), c);
            dealt = Math.max(0, before - t.getHealth());
            if (dealt == 0) {
                dealt = s.damage() * 0.5;
            }
        }
        if (s.knockback() > 0) {
            Vector kb = t.getLocation().toVector().subtract(from);
            kb.setY(0);
            if (kb.lengthSquared() < 1e-4) {
                kb = c.getLocation().getDirection().setY(0);
            }
            t.setVelocity(t.getVelocity().add(kb.normalize().multiply(s.knockback()).setY(0.3)));
        }
        effect(t, s);
        return dealt;
    }

    private void effect(LivingEntity t, SkillDef s) {
        if (s.effect() == null || s.effect().isBlank() || s.effectSeconds() <= 0) {
            return;
        }
        PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(s.effect().toLowerCase()));
        if (type != null) {
            t.addPotionEffect(new PotionEffect(type, s.effectSeconds() * 20, 0, true, true));
        }
    }

    private static Vector flat(Vector v) {
        Vector f = v.clone().setY(0);
        return f.lengthSquared() < 1e-4 ? new Vector(0, 0, 1) : f.normalize();
    }

    // ------------------------------------------------------------ cast
    public boolean cast(Player p, SkillDef s) {
        switch (s.type().toUpperCase()) {
            case "BEAM" -> beam(p, s);
            case "NOVA" -> nova(p, s);
            case "DASH" -> dash(p, s);
            case "BLINK" -> blink(p, s);
            case "PULL" -> pull(p, s);
            case "LEAP" -> leap(p, s);
            case "LINE" -> line(p, s);
            case "RITUAL" -> ritual(p, s);
            case "SWARM" -> swarm(p, s);
            case "GLIDE" -> glide(p, s);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void beam(Player p, SkillDef s) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = p.getWorld().rayTrace(eye, dir, s.range(), FluidCollisionMode.NEVER, true, 0.35, e -> valid(p, e));
        double dist = r == null ? s.range() : r.getHitPosition().distance(eye.toVector());
        for (double d = 1; d < dist; d += 0.6) {
            fx(p.getWorld(), eye.clone().add(dir.clone().multiply(d)).subtract(0, 0.2, 0), s, 1, 0.02);
        }
        if (r != null && r.getHitEntity() instanceof LivingEntity t) {
            hit(p, t, s, p.getLocation().toVector());
            fx(p.getWorld(), t.getLocation().add(0, 1, 0), s, 12, 0.3);
        }
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.2f);
    }

    private void nova(Player p, SkillDef s) {
        Location c = p.getLocation();
        for (int i = 0; i < 3; i++) {
            ring(p, c, s.radius() * (i + 1) / 3.0, s);
        }
        for (LivingEntity t : near(p, c, s.radius())) {
            hit(p, t, s, c.toVector());
        }
        p.getWorld().playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
    }

    private void dash(Player p, SkillDef s) {
        Vector dir = flat(p.getLocation().getDirection());
        p.setVelocity(dir.multiply(s.power()).setY(0.15));
        Set<LivingEntity> done = new HashSet<>();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > 8) {
                    cancel();
                    return;
                }
                fx(p.getWorld(), p.getLocation().add(0, 0.8, 0), s, 4, 0.25);
                for (LivingEntity e : near(p, p.getLocation().add(0, 0.8, 0), s.radius())) {
                    if (done.add(e)) {
                        hit(p, e, s, p.getLocation().toVector());
                    }
                }
            }
        }.runTaskTimer(plugin.host(), 0L, 1L);
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
    }

    private void blink(Player p, SkillDef s) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult br = p.getWorld().rayTraceBlocks(eye, dir, s.range(), FluidCollisionMode.NEVER, true);
        double max = br == null ? s.range() : Math.max(0, br.getHitPosition().distance(eye.toVector()) - 0.7);
        Location dest = null;
        for (double d = max; d >= 1; d -= 0.5) {
            Location feet = eye.clone().add(dir.clone().multiply(d)).subtract(0, 1.62, 0);
            if (feet.getBlock().isPassable() && feet.clone().add(0, 1, 0).getBlock().isPassable()) {
                dest = feet;
                break;
            }
        }
        if (dest == null) {
            return;
        }
        Location from = p.getLocation();
        fx(p.getWorld(), from.clone().add(0, 1, 0), s, 25, 0.4);
        dest.setYaw(from.getYaw());
        dest.setPitch(from.getPitch());
        p.teleport(dest);
        fx(p.getWorld(), dest.clone().add(0, 1, 0), s, 25, 0.4);
        p.getWorld().playSound(dest, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f);
        if (s.damage() > 0) {
            for (LivingEntity t : near(p, dest, s.radius())) {
                hit(p, t, s, dest.toVector());
            }
        }
    }

    private void pull(Player p, SkillDef s) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = p.getWorld().rayTrace(eye, dir, s.range(), FluidCollisionMode.NEVER, true, 0.4, e -> valid(p, e));
        double dist = r == null ? s.range() : r.getHitPosition().distance(eye.toVector());
        for (double d = 1; d < dist; d += 0.5) {
            fx(p.getWorld(), eye.clone().add(dir.clone().multiply(d)).subtract(0, 0.3, 0), s, 1, 0.02);
        }
        if (r != null && r.getHitEntity() instanceof LivingEntity t) {
            Vector to = p.getLocation().toVector().subtract(t.getLocation().toVector());
            t.setVelocity(to.normalize().multiply(1.3).setY(0.35));
            hit(p, t, s, p.getLocation().toVector().add(to));
        }
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_CHAIN_PLACE, 1f, 0.8f);
    }

    private void leap(Player p, SkillDef s) {
        Vector dir = flat(p.getLocation().getDirection());
        p.setVelocity(dir.multiply(0.7).setY(0.95));
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t += 2;
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                if (t >= 6 && (p.isOnGround() || t >= 40)) {
                    cancel();
                    p.setFallDistance(0);
                    Location c = p.getLocation();
                    ring(p, c, s.radius() * 0.5, s);
                    ring(p, c, s.radius(), s);
                    for (LivingEntity e : near(p, c, s.radius())) {
                        hit(p, e, s, c.toVector());
                    }
                    p.getWorld().playSound(c, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.9f);
                } else {
                    p.setFallDistance(0);
                }
            }
        }.runTaskTimer(plugin.host(), 2L, 2L);
    }

    private void line(Player p, SkillDef s) {
        Vector dir = flat(p.getLocation().getDirection());
        Location start = p.getLocation();
        Set<LivingEntity> done = new HashSet<>();
        new BukkitRunnable() {
            int i = 1;

            @Override
            public void run() {
                if (!p.isOnline() || i > s.range()) {
                    cancel();
                    return;
                }
                Location at = start.clone().add(dir.clone().multiply(i));
                for (int y = 0; y < 3; y++) {
                    fx(p.getWorld(), at.clone().add(0, 0.3 + y * 0.5, 0), s, 4, 0.2);
                }
                p.getWorld().spawnParticle(Particle.BLOCK, at, 10, 0.3, 0.1, 0.3, 0, Material.STONE.createBlockData());
                for (LivingEntity e : near(p, at.clone().add(0, 1, 0), 1.6)) {
                    if (done.add(e)) {
                        hit(p, e, s, start.toVector());
                    }
                }
                if (i % 3 == 0) {
                    p.getWorld().playSound(at, org.bukkit.Sound.BLOCK_STONE_BREAK, 0.8f, 0.7f);
                }
                i++;
            }
        }.runTaskTimer(plugin.host(), 0L, 2L);
    }

    private void ritual(Player p, SkillDef s) {
        Location c = p.getLocation().clone();
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                t += 10;
                if (!p.isOnline()) {
                    cancel();
                    return;
                }
                ring(p, c, s.radius() * (1.0 - (t - 10) / 40.0), s);
                if (t >= 30) {
                    cancel();
                    double dealt = 0;
                    for (LivingEntity e : near(p, c, s.radius())) {
                        dealt += hit(p, e, s, c.toVector());
                    }
                    if (s.healPercent() > 0 && dealt > 0) {
                        double max = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        p.setHealth(Math.min(max, p.getHealth() + dealt * s.healPercent() / 100.0));
                        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 5, 0.3, 0.2, 0.3, 0);
                    }
                    p.getWorld().playSound(c, org.bukkit.Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.4f);
                }
            }
        }.runTaskTimer(plugin.host(), 0L, 10L);
        p.getWorld().playSound(c, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.7f);
    }

    private void swarm(Player p, SkillDef s) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        double cos = Math.cos(Math.toRadians(s.angle() <= 0 ? 35 : s.angle()));
        for (int w = 1; w <= 5; w++) {
            final int wave = w;
            new BukkitRunnable() {
                @Override
                public void run() {
                    double d = s.range() * wave / 5.0;
                    double width = Math.tan(Math.toRadians(s.angle() <= 0 ? 35 : s.angle())) * d;
                    Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                    for (int i = -3; i <= 3; i++) {
                        fx(p.getWorld(), eye.clone().add(dir.clone().multiply(d)).add(right.clone().multiply(width * i / 3.0)), s, 2, 0.15);
                    }
                }
            }.runTaskLater(plugin.host(), w);
        }
        for (LivingEntity t : near(p, eye, s.range())) {
            Vector to = t.getEyeLocation().toVector().subtract(eye.toVector());
            if (to.lengthSquared() > 0 && to.clone().normalize().dot(dir) >= cos) {
                hit(p, t, s, eye.toVector());
            }
        }
        p.getWorld().playSound(eye, org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1f, 0.7f);
    }

    private void glide(Player p, SkillDef s) {
        Vector dir = p.getLocation().getDirection();
        p.setVelocity(dir.multiply(s.power()).setY(0.75));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, true, false));
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || t++ > 14) {
                    cancel();
                    return;
                }
                fx(p.getWorld(), p.getLocation().add(0, 0.5, 0), s, 5, 0.3);
            }
        }.runTaskTimer(plugin.host(), 0L, 1L);
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PHANTOM_FLAP, 1f, 1.2f);
    }

}
