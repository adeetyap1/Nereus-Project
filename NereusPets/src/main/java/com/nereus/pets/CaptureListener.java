package com.nereus.pets;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Melempar Pet Orb ke mob untuk menangkapnya. */
public final class CaptureListener implements Listener {
    private final NereusPets plugin;
    private final PetManager mgr;
    private final Orbs orbs;

    public CaptureListener(NereusPets plugin, PetManager mgr, Orbs orbs) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.orbs = orbs;
    }

    @EventHandler
    public void onLaunch(PlayerLaunchProjectileEvent e) {
        String tier = orbs.tierOf(e.getItemStack());
        if (tier == null) {
            return;
        }
        e.getProjectile().getPersistentDataContainer().set(plugin.orbKey(), PersistentDataType.STRING, tier);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        String tier = proj.getPersistentDataContainer().get(plugin.orbKey(), PersistentDataType.STRING);
        if (tier == null || !(proj.getShooter() instanceof Player p)) {
            return;
        }
        Entity hit = e.getHitEntity();
        if (!(hit instanceof LivingEntity target)) {
            refund(proj.getLocation(), tier);
            return;
        }
        tryCapture(p, target, tier);
    }

    private void refund(Location at, String tier) {
        at.getWorld().dropItem(at, orbs.create(tier, 1));
    }

    private void tryCapture(Player p, LivingEntity target, String tier) {
        Location loc = target.getLocation();
        Species sp = mgr.species(target.getType());

        String denied = null;
        if (target instanceof Player || mgr.isPetEntity(target)) {
            denied = "capture-invalid";
        } else if (sp == null || target.customName() != null || (target instanceof Tameable t && t.isTamed())) {
            denied = "capture-species";
        } else if (plugin.getConfig().getStringList("disabled-worlds").contains(p.getWorld().getName())) {
            denied = "world-disabled";
        } else if (mgr.box(p.getUniqueId()).size() >= mgr.boxLimit()) {
            denied = "box-full";
        }
        if (denied != null) {
            plugin.send(p, denied, "{limit}", String.valueOf(mgr.boxLimit()));
            refund(loc, tier);
            return;
        }

        AttributeInstance maxAttr = target.getAttribute(Attribute.MAX_HEALTH);
        double max = maxAttr == null ? target.getHealth() : maxAttr.getValue();
        double ratio = max <= 0 ? 1.0 : Math.min(1.0, target.getHealth() / max);
        double lowHpBonus = plugin.getConfig().getDouble("capture.low-hp-bonus", 1.0);
        double chance = sp.captureRate() * orbs.multiplier(tier) * (1.0 + (1.0 - ratio) * lowHpBonus);
        chance = Math.max(plugin.getConfig().getDouble("capture.min-chance", 0.03),
                Math.min(plugin.getConfig().getDouble("capture.max-chance", 0.95), chance));

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Location fx = loc.clone().add(0, target.getHeight() / 2, 0);
        if (rnd.nextDouble() < chance) {
            int wildMax = Math.max(1, plugin.getConfig().getInt("wild-level-max", 5));
            PetData d = mgr.createPet(sp.type(), 1 + rnd.nextInt(wildMax));
            if (d == null) {
                refund(loc, tier);
                return;
            }
            target.remove();
            mgr.addPet(p.getUniqueId(), d);
            fx.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, fx, 20, 0.4, 0.4, 0.4, 0.0);
            fx.getWorld().playSound(fx, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.6f);
            plugin.send(p, "capture-success",
                    "{pet}", sp.name(),
                    "{level}", String.valueOf(d.level),
                    "{rarity}", d.rarity.color + d.rarity.label);
        } else {
            fx.getWorld().spawnParticle(Particle.CLOUD, fx, 12, 0.3, 0.3, 0.3, 0.02);
            fx.getWorld().playSound(fx, Sound.ENTITY_VILLAGER_NO, 0.8f, 1.2f);
            if (plugin.getConfig().getBoolean("capture.show-chance", true)) {
                plugin.send(p, "capture-fail", "{pet}", sp.name(),
                        "{chance}", String.format(Locale.US, "%.0f", chance * 100));
            } else {
                plugin.send(p, "capture-fail-short", "{pet}", sp.name());
            }
        }
    }
}
