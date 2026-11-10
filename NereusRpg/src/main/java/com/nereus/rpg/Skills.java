package com.nereus.rpg;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Eksekusi skill class. Skill hanya mengenai musuh (Enemy), bukan pemain atau hewan. */
public final class Skills {
    public static final String SKILL_TAG = "nrpg_skill";
    /** Benar saat skill sedang memberi damage, supaya buff pasif tidak ikut menggandakan. */
    public static boolean damaging;

    private final NereusRpg plugin;
    private final Core core;

    public Skills(NereusRpg plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
    }

    public void cast(Player p, int index) {
        Profile pr = core.profile(p);
        Defs.Clazz c = core.clazz(pr);
        if (c == null) {
            core.msg(p, "&cPilih class dulu dengan &f/class&c.");
            return;
        }
        if (index < 0 || index >= c.skills().size()) {
            core.msg(p, "&cSkill tidak ada.");
            return;
        }
        Defs.Skill s = c.skills().get(index);
        if (pr.level < s.level()) {
            core.msg(p, "&c" + s.name() + " terbuka di level &f" + s.level() + "&c.");
            return;
        }
        if (!core.weaponOk(p, c)) {
            core.msg(p, "&cPegang senjata " + c.name() + " &cuntuk memakai skill.");
            return;
        }
        long now = System.currentTimeMillis();
        Long until = pr.cooldowns.get(s.type());
        if (until != null && until > now) {
            p.sendActionBar(Text.c("&c" + s.name() + " cooldown " + Text.time(until - now)));
            return;
        }
        if (pr.mana < s.mana()) {
            p.sendActionBar(Text.c("&9Mana kurang (" + (int) pr.mana + "/" + s.mana() + ")"));
            return;
        }
        double power = s.power() * (1 + pr.stat("int") * core.plugin.getConfig().getDouble("stats.int-skill-power-percent", 2.0) / 100.0
                + (pr.level - 1) * 0.02);
        if (!perform(p, s, power)) {
            return;
        }
        pr.mana -= s.mana();
        pr.cooldowns.put(s.type(), now + (long) (s.cooldown() * 1000));
        p.sendActionBar(Text.c("&6" + s.name() + "!"));
    }

    public void cycle(Player p) {
        Profile pr = core.profile(p);
        Defs.Clazz c = core.clazz(pr);
        if (c == null || c.skills().isEmpty()) {
            return;
        }
        int n = c.skills().size();
        int next = pr.skillSel;
        for (int i = 0; i < n; i++) {
            next = (next + 1) % n;
            if (pr.level >= c.skills().get(next).level()) {
                break;
            }
        }
        pr.skillSel = next;
        p.sendActionBar(Text.c("&eSkill terpilih: &f" + c.skills().get(next).name()));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
    }

    // ------------------------------------------------------------ pembantu

    private static boolean enemy(Entity e, Player p) {
        return e instanceof Enemy && e instanceof LivingEntity && e != p;
    }

    private void hit(LivingEntity target, Player p, double dmg) {
        damaging = true;
        try {
            target.damage(dmg, p);
        } finally {
            damaging = false;
        }
    }

    private LivingEntity target(Player p, double range) {
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(),
                range, 0.6, e -> enemy(e, p));
        return r != null && r.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    private void fx(Location l, Particle part, int n, double spread) {
        l.getWorld().spawnParticle(part, l, n, spread, spread, spread, 0.02);
    }

    private boolean perform(Player p, Defs.Skill s, double power) {
        World w = p.getWorld();
        Location loc = p.getLocation();
        switch (s.type()) {
            case "cleave" -> {
                for (Entity e : p.getNearbyEntities(3.5, 2, 3.5)) {
                    if (enemy(e, p)) {
                        hit((LivingEntity) e, p, power);
                    }
                }
                fx(loc.clone().add(0, 1, 0), Particle.SWEEP_ATTACK, 8, 1.5);
                w.playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
            }
            case "charge" -> {
                p.setVelocity(loc.getDirection().setY(0.15).normalize().multiply(1.6));
                Set<Entity> hitSet = new HashSet<>();
                new BukkitRunnable() {
                    int t;

                    @Override
                    public void run() {
                        if (!p.isOnline() || ++t > 5) {
                            cancel();
                            return;
                        }
                        for (Entity e : p.getNearbyEntities(2.5, 2, 2.5)) {
                            if (enemy(e, p) && hitSet.add(e)) {
                                hit((LivingEntity) e, p, power);
                                e.setVelocity(p.getLocation().getDirection().setY(0.4).multiply(0.8));
                            }
                        }
                        fx(p.getLocation().add(0, 1, 0), Particle.CLOUD, 4, 0.3);
                    }
                }.runTaskTimer(plugin, 1L, 3L);
                w.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.7f);
            }
            case "warcry" -> {
                int ticks = 20 * 10;
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 0));
                for (Entity e : p.getNearbyEntities(8, 4, 8)) {
                    if (e instanceof Player ally) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, 0));
                    }
                }
                fx(loc.clone().add(0, 1, 0), Particle.CRIT, 30, 1.0);
                w.playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 1f, 1f);
            }
            case "shadowstep" -> {
                LivingEntity t = target(p, 12);
                if (t != null) {
                    Location tl = t.getLocation();
                    Vector back = tl.getDirection().setY(0).normalize().multiply(-1.2);
                    Location dest = tl.clone().add(back);
                    if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) {
                        dest = tl.clone();
                    }
                    dest.setDirection(tl.toVector().subtract(dest.toVector()));
                    p.teleport(dest);
                    hit(t, p, power * 1.5);
                } else {
                    Vector dir = loc.getDirection().setY(0).normalize();
                    RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), dir, 6, FluidCollisionMode.NEVER, true);
                    double dist = r == null ? 6 : Math.max(0, r.getHitPosition().distance(p.getEyeLocation().toVector()) - 1);
                    p.teleport(loc.clone().add(dir.multiply(dist)));
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0));
                fx(loc.clone().add(0, 1, 0), Particle.LARGE_SMOKE, 15, 0.4);
                w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
            }
            case "venom_strike" -> {
                LivingEntity t = target(p, 5);
                if (t == null) {
                    core.msg(p, "&cTidak ada musuh di depanmu.");
                    return false;
                }
                hit(t, p, power);
                t.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 6, 1));
                fx(t.getLocation().add(0, 1, 0), Particle.WITCH, 20, 0.4);
                w.playSound(loc, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 1f);
            }
            case "smoke_bomb" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 80, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1));
                for (Entity e : p.getNearbyEntities(5, 3, 5)) {
                    if (enemy(e, p)) {
                        LivingEntity le = (LivingEntity) e;
                        le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    }
                }
                fx(loc.clone().add(0, 1, 0), Particle.LARGE_SMOKE, 60, 1.5);
                w.playSound(loc, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.6f);
            }
            case "power_shot" -> {
                Arrow a = p.launchProjectile(Arrow.class, loc.getDirection().multiply(3.5));
                a.setDamage(power / 3.5);
                a.setCritical(true);
                a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                a.addScoreboardTag(SKILL_TAG);
                w.playSound(loc, Sound.ENTITY_ARROW_SHOOT, 1f, 0.7f);
            }
            case "roll" -> {
                Vector v = loc.getDirection().setY(0).normalize().multiply(-1.3);
                v.setY(0.35);
                p.setVelocity(v);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1));
                fx(loc.clone().add(0, 0.3, 0), Particle.CLOUD, 10, 0.3);
                w.playSound(loc, Sound.ENTITY_BAT_TAKEOFF, 1f, 1.4f);
            }
            case "arrow_rain" -> {
                RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), loc.getDirection(), 30, FluidCollisionMode.NEVER, true);
                Location center = r != null ? r.getHitPosition().toLocation(w)
                        : p.getEyeLocation().add(loc.getDirection().multiply(20));
                new BukkitRunnable() {
                    int t;

                    @Override
                    public void run() {
                        if (!p.isOnline() || ++t > 20) {
                            cancel();
                            return;
                        }
                        for (int i = 0; i < 2; i++) {
                            Location from = center.clone().add((core.rng.nextDouble() - 0.5) * 8, 10,
                                    (core.rng.nextDouble() - 0.5) * 8);
                            Arrow a = w.spawnArrow(from, new Vector(0, -1, 0), 2.5f, 1f);
                            a.setShooter(p);
                            a.setDamage(power / 2.5);
                            a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                            a.addScoreboardTag(SKILL_TAG);
                        }
                    }
                }.runTaskTimer(plugin, 5L, 2L);
                w.playSound(loc, Sound.ENTITY_ARROW_SHOOT, 1f, 0.5f);
            }
            default -> {
                core.msg(p, "&cTipe skill tidak dikenal: " + s.type());
                return false;
            }
        }
        return true;
    }

    public List<Defs.Skill> skillsOf(Profile pr) {
        Defs.Clazz c = core.clazz(pr);
        return c == null ? List.of() : c.skills();
    }
}
