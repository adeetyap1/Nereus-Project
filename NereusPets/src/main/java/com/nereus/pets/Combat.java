package com.nereus.pets;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Trident;
import org.bukkit.entity.WitherSkull;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Cara pet menyerang sesuai mobnya: pukulan, panah, bola api, sinar, ledakan, dan lainnya. */
public final class Combat {
    private final NereusPets plugin;
    private final PetManager mgr;

    public Combat(NereusPets plugin, PetManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    /** Partikel aman (tanpa data tambahan) dari nama di config. */
    public static Particle particle(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.toUpperCase(java.util.Locale.ROOT)) {
            case "FLAME" -> Particle.FLAME;
            case "SOUL_FIRE_FLAME" -> Particle.SOUL_FIRE_FLAME;
            case "END_ROD" -> Particle.END_ROD;
            case "WITCH" -> Particle.WITCH;
            case "PORTAL" -> Particle.PORTAL;
            case "ELECTRIC_SPARK" -> Particle.ELECTRIC_SPARK;
            case "SOUL" -> Particle.SOUL;
            case "CRIT" -> Particle.CRIT;
            case "HEART" -> Particle.HEART;
            case "HAPPY_VILLAGER" -> Particle.HAPPY_VILLAGER;
            case "CLOUD" -> Particle.CLOUD;
            default -> null;
        };
    }

    public boolean valid(ActivePet ap, LivingEntity t) {
        if (t == null || !t.isValid() || t.isDead() || t == ap.entity || t instanceof Player || mgr.isProtected(t)
                || mgr.isWild(t)) {
            return false;
        }
        if (mgr.isPetEntity(t)) {
            return ap.duel != null && ap.duel.entity == t;
        }
        return true;
    }

    /** Serang target sesuai gaya serangan jenisnya. dmg sudah termasuk kritikal. */
    public void deliver(ActivePet ap, Species sp, LivingEntity target, double dmg) {
        Mob pet = ap.entity;
        World w = pet.getWorld();
        pet.swingMainHand();
        Location from = pet.getEyeLocation();
        Vector dir = target.getEyeLocation().toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 0.0001) {
            dir = new Vector(0, 0, 1);
        }
        dir.normalize();
        Location muzzle = from.clone().add(dir.clone().multiply(0.7));

        switch (sp.style()) {
            case ARROW -> {
                Arrow a = w.spawnArrow(muzzle, dir, 2.4f, 0.4f);
                a.setShooter(pet);
                a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                tag(a, ap, dmg);
                w.playSound(pet.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.2f);
            }
            case FIREBALL -> {
                Class<? extends Fireball> cls = pet.getType() == EntityType.GHAST ? Fireball.class : SmallFireball.class;
                Fireball fb = pet.launchProjectile(cls, dir);
                fb.setDirection(dir);
                fb.setIsIncendiary(false);
                fb.setYield(0f);
                tag(fb, ap, dmg);
                w.playSound(pet.getLocation(), pet.getType() == EntityType.GHAST ? Sound.ENTITY_GHAST_SHOOT : Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.2f);
            }
            case WITHER_SKULL -> {
                WitherSkull sk = pet.launchProjectile(WitherSkull.class, dir);
                sk.setDirection(dir);
                sk.setYield(0f);
                sk.setIsIncendiary(false);
                tag(sk, ap, dmg);
                w.playSound(pet.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.3f);
            }
            case TRIDENT -> {
                Trident t = pet.launchProjectile(Trident.class, dir.clone().multiply(2.2));
                t.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                tag(t, ap, dmg);
                w.playSound(pet.getLocation(), Sound.ENTITY_DROWNED_SHOOT, 0.8f, 1.1f);
            }
            case SONIC_BOOM -> {
                line(from, target.getEyeLocation(), Particle.SONIC_BOOM, 2.0);
                w.playSound(pet.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.4f);
                hit(ap, sp, target, dmg);
            }
            case BEAM -> {
                line(from, target.getEyeLocation(), Particle.END_ROD, 0.4);
                w.playSound(pet.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 0.8f, 1.2f);
                hit(ap, sp, target, dmg);
            }
            case POTION -> {
                w.spawnParticle(Particle.WITCH, target.getLocation().add(0, 1, 0), 18, 0.3, 0.4, 0.3, 0.05);
                w.playSound(pet.getLocation(), Sound.ENTITY_WITCH_THROW, 0.8f, 1.1f);
                hit(ap, sp, target, dmg);
                if (!target.isDead()) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                }
            }
            case BREATH -> {
                Location c = target.getLocation().add(0, 0.5, 0);
                w.spawnParticle(Particle.PORTAL, c, 60, 1.2, 0.6, 1.2, 0.3);
                w.spawnParticle(Particle.WITCH, c, 25, 1.0, 0.5, 1.0, 0.05);
                w.playSound(pet.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 0.8f, 1.3f);
                aoe(ap, sp, c, 3.0, dmg, target);
            }
            case EXPLODE -> {
                Location c = pet.getLocation().add(0, 0.5, 0);
                w.spawnParticle(Particle.EXPLOSION, c, 3, 0.8, 0.3, 0.8, 0.0);
                w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
                aoe(ap, sp, c, 3.0, dmg, target);
            }
            default -> hit(ap, sp, target, dmg);
        }
    }

    private void tag(Projectile proj, ActivePet ap, double dmg) {
        var pdc = proj.getPersistentDataContainer();
        pdc.set(plugin.projKey(), PersistentDataType.STRING, ap.entity.getUniqueId().toString());
        pdc.set(plugin.projDmgKey(), PersistentDataType.DOUBLE, dmg);
        pdc.set(plugin.petKey(), PersistentDataType.STRING, ap.owner.toString());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (proj.isValid()) {
                proj.remove();
            }
        }, 80L);
    }

    /** Dipanggil saat proyektil pet mengenai sesuatu. */
    public void onProjectileHit(Projectile proj, Entity hit) {
        var pdc = proj.getPersistentDataContainer();
        String id = pdc.get(plugin.projKey(), PersistentDataType.STRING);
        Double dmg = pdc.get(plugin.projDmgKey(), PersistentDataType.DOUBLE);
        if (id != null && dmg != null && hit instanceof LivingEntity t) {
            ActivePet ap = mgr.byEntityId(UUID.fromString(id));
            if (ap != null && valid(ap, t)) {
                Species sp = mgr.species(ap.data.type);
                if (sp != null) {
                    hit(ap, sp, t, dmg);
                }
            }
        }
        proj.remove();
    }

    private void hit(ActivePet ap, Species sp, LivingEntity target, double dmg) {
        if (!valid(ap, target)) {
            return;
        }
        target.damage(dmg, ap.entity);
        effects(ap, sp, target);
    }

    private void aoe(ActivePet ap, Species sp, Location center, double radius, double dmg, LivingEntity main) {
        hit(ap, sp, main, dmg);
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (e != main && e instanceof LivingEntity le && valid(ap, le)
                    && (le instanceof Enemy || (ap.duel != null && ap.duel.entity == le))) {
                hit(ap, sp, le, dmg * 0.6);
            }
        }
    }

    /** Efek skill status setelah serangan mengenai. */
    private void effects(ActivePet ap, Species sp, LivingEntity target) {
        if (target.isDead() || sp.skill() == Skill.NONE || ap.data.level < sp.skillLevel()) {
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (sp.skill() == Skill.SLOW && rnd.nextDouble() < 0.30) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
        } else if (sp.skill() == Skill.POISON && rnd.nextDouble() < 0.30) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
        } else if (sp.skill() == Skill.IGNITE && rnd.nextDouble() < 0.30) {
            target.setFireTicks(80);
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 0.8, 0), 8, 0.2, 0.3, 0.2, 0.01);
        }
    }

    private void line(Location a, Location b, Particle p, double step) {
        Vector v = b.toVector().subtract(a.toVector());
        double len = v.length();
        if (len < 0.1) {
            return;
        }
        v.normalize();
        for (double i = 0.8; i < len; i += step) {
            a.getWorld().spawnParticle(p, a.clone().add(v.clone().multiply(i)), 1, 0, 0, 0, 0);
        }
    }
}
