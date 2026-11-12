package com.nereus.pets;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Pakai telur dan permen, proyektil pet, dan perlindungan pet liar. */
public final class ItemListener implements Listener {
    private final NereusPets plugin;
    private final PetManager mgr;
    private final Items items;
    private final Combat combat;

    public ItemListener(NereusPets plugin, PetManager mgr, Items items, Combat combat) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.items = items;
        this.combat = combat;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack held = e.getItem();
        String kind = items.kind(held);
        if (kind == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (kind.startsWith("egg:")) {
            useEgg(p, held, kind.substring(4));
        } else if (kind.startsWith("candy:")) {
            useCandy(p, held, kind.substring(6));
        }
    }

    private void useEgg(Player p, ItemStack held, String tier) {
        if (mgr.box(p.getUniqueId()).size() >= mgr.boxLimit()) {
            plugin.send(p, "box-full", "{limit}", String.valueOf(mgr.boxLimit()));
            return;
        }
        PetData d = items.hatch(tier);
        if (d == null) {
            plugin.send(p, "egg-empty");
            return;
        }
        held.setAmount(held.getAmount() - 1);
        mgr.addPet(p.getUniqueId(), d);
        announceHatch(p, d);
    }

    /** Dipakai juga oleh perintah /pets give hatch (untuk crate). */
    public void announceHatch(Player p, PetData d) {
        Location l = p.getLocation().add(0, 1, 0);
        l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l, 20, 0.5, 0.5, 0.5, 0.0);
        l.getWorld().playSound(l, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
        Species sp = mgr.species(d.type);
        String name = sp == null ? d.type.name() : sp.name();
        plugin.send(p, "egg-hatched", "{pet}", name, "{rarity}", d.rarity.color + d.rarity.label,
                "{level}", String.valueOf(d.level));
        if (d.rarity == Rarity.LEGENDARY || d.rarity == Rarity.EPIC) {
            plugin.broadcast(d.rarity == Rarity.LEGENDARY ? "egg-legendary" : "egg-epic",
                    "{player}", p.getName(), "{pet}", name);
        }
    }

    private void useCandy(Player p, ItemStack held, String id) {
        ActivePet ap = mgr.activeOf(p);
        if (ap == null) {
            plugin.send(p, "no-active");
            return;
        }
        int maxLevel = plugin.getConfig().getInt("xp.max-level", 50);
        if (ap.data.level >= maxLevel) {
            plugin.send(p, "candy-max");
            return;
        }
        held.setAmount(held.getAmount() - 1);
        int levels = items.candyLevels(id);
        if (levels > 0) {
            for (int i = 0; i < levels && ap.data.level < maxLevel; i++) {
                mgr.gainXp(ap, mgr.xpNeeded(ap.data.level) - ap.data.xp);
            }
        } else {
            mgr.gainXp(ap, items.candyXp(id));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ proyektil pet

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectile(ProjectileHitEvent e) {
        Projectile proj = e.getEntity();
        if (!proj.getPersistentDataContainer().has(plugin.projKey(), PersistentDataType.STRING)) {
            return;
        }
        e.setCancelled(true);
        combat.onProjectileHit(proj, e.getHitEntity());
    }

    // ------------------------------------------------------------------ pet liar tidak bisa dibunuh

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWildDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || !mgr.isWild(le)) {
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : be.getDamager();
            if (mgr.isPetEntity(d)) {
                e.setCancelled(true);
                return;
            }
        }
        if (le.getHealth() - e.getFinalDamage() <= 1.0) {
            e.setCancelled(true);
            if (le.getHealth() > 1.0) {
                le.setHealth(1.0);
            }
        }
    }
}
