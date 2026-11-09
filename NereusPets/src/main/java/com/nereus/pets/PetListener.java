package com.nereus.pets;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.util.UUID;

/** Aturan pertarungan, perlindungan pet, XP, dan sesi pemain. */
public final class PetListener implements Listener {
    private final NereusPets plugin;
    private final PetManager mgr;

    public PetListener(NereusPets plugin, PetManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    // ------------------------------------------------------------------ sesi

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        mgr.load(p);
        if (!plugin.getConfig().getBoolean("auto-summon", true)) {
            return;
        }
        UUID last = mgr.lastActive(p.getUniqueId());
        if (last == null) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || mgr.activeOf(p) != null) {
                return;
            }
            PetData d = mgr.find(p.getUniqueId(), last);
            if (d != null) {
                mgr.summon(p, d);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        mgr.recall(id);
        mgr.save(id);
        mgr.unload(id);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        mgr.recall(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Entity en : e.getEntities()) {
                if (en.isValid() && mgr.isPetEntity(en) && mgr.byEntity(en) == null) {
                    en.remove();
                }
            }
        });
    }

    // ------------------------------------------------------------------ perlindungan pet

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetDamaged(EntityDamageEvent e) {
        ActivePet ap = mgr.byEntity(e.getEntity());
        if (ap == null) {
            return;
        }
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity damager = resolveDamager(be.getDamager());
            if (damager instanceof Player || mgr.isPetEntity(damager)) {
                e.setCancelled(true);
                return;
            }
            if (damager instanceof LivingEntity attacker && ap.target == null && !mgr.isProtected(attacker)) {
                ap.target = attacker;
            }
        }
        ap.lastHurt = System.currentTimeMillis();
        LivingEntity le = (LivingEntity) e.getEntity();
        if (le.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (mgr.byEntity(ap.entity) == ap) {
                    mgr.faint(ap);
                }
            });
        }
    }

    /** Pet tidak boleh melukai pemain atau pet lain. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetAttack(EntityDamageByEntityEvent e) {
        Entity damager = resolveDamager(e.getDamager());
        if (mgr.isPetEntity(damager) && (e.getEntity() instanceof Player || mgr.isPetEntity(e.getEntity()))) {
            e.setCancelled(true);
        }
    }

    /** Pet menyerang apa yang diserang pemain, dan membalas yang menyerang pemain. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void trackTargets(EntityDamageByEntityEvent e) {
        Entity damager = resolveDamager(e.getDamager());
        Entity victim = e.getEntity();
        if (damager instanceof Player p && victim instanceof LivingEntity lv
                && !(victim instanceof Player) && !mgr.isPetEntity(victim) && !mgr.isProtected(victim)) {
            ActivePet ap = mgr.activeOf(p);
            if (ap != null) {
                ap.target = lv;
            }
        } else if (victim instanceof Player owner && damager instanceof LivingEntity dl
                && !(damager instanceof Player) && !mgr.isPetEntity(damager)) {
            ActivePet ap = mgr.activeOf(owner);
            if (ap != null) {
                ap.target = dl;
            }
        }
    }

    private Entity resolveDamager(Entity damager) {
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    /** Pet mengatur target sendiri, jadi AI bawaan tidak boleh memilih target. */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (e.getTarget() != null && mgr.isPetEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (mgr.isPetEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent e) {
        if (mgr.isPetEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (mgr.isPetEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (mgr.isPetEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropItem(EntityDropItemEvent e) {
        if (mgr.isPetEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    /** Klik kanan pada pet milik sendiri membuka Pet Box. Interaksi lain diblokir. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        Entity clicked = e.getRightClicked();
        if (!mgr.isPetEntity(clicked)) {
            return;
        }
        e.setCancelled(true);
        ActivePet ap = mgr.byEntity(clicked);
        if (ap != null && ap.owner.equals(e.getPlayer().getUniqueId())) {
            new PetMenu(plugin, mgr, e.getPlayer()).open();
        }
    }

    // ------------------------------------------------------------------ XP

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (mgr.isPetEntity(victim)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            return;
        }
        if (victim instanceof Player) {
            return;
        }
        ActivePet ap = null;
        Player killer = victim.getKiller();
        if (killer != null) {
            ap = mgr.activeOf(killer);
        } else if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent be) {
            ap = mgr.byEntity(be.getDamager());
        }
        if (ap == null) {
            return;
        }
        AttributeInstance maxHp = victim.getAttribute(Attribute.MAX_HEALTH);
        double hp = maxHp == null ? 10 : maxHp.getValue();
        double perHealth = plugin.getConfig().getDouble("xp.per-health", 1.0);
        mgr.gainXp(ap, hp * perHealth);
    }
}
