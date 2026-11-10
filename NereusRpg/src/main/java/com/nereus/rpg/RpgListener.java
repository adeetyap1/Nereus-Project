package com.nereus.rpg;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashSet;
import java.util.Set;

/** Kejadian dunia: XP, progres quest, buff pasif, tombol skill. */
public final class RpgListener implements Listener {
    private final NereusRpg plugin;
    private final Core core;
    private final Skills skills;
    private final Set<Long> placed = new HashSet<>();

    public RpgListener(NereusRpg plugin, Core core, Skills skills) {
        this.plugin = plugin;
        this.core = core;
        this.skills = skills;
    }

    private static long key(Block b) {
        return b.getBlockKey() * 31L + b.getWorld().getUID().hashCode();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        core.handleJoin(e.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        core.unload(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> core.applyBuffs(p));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (placed.size() > 200_000) {
            placed.clear();
        }
        placed.add(key(e.getBlockPlaced()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (placed.remove(key(e.getBlock()))) {
            return;
        }
        core.tracker.progress(e.getPlayer(), Objective.Type.BREAK, e.getBlock().getType().name());
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim instanceof Player) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        var hp = victim.getAttribute(Attribute.MAX_HEALTH);
        double max = hp == null ? 10 : hp.getValue();
        var c = plugin.getConfig();
        long xp = Math.round(Math.min(c.getDouble("level.xp-kill-cap", 60), max * c.getDouble("level.xp-kill-multiplier", 0.5)));
        core.addXp(killer, Math.max(1, xp));
        core.tracker.kill(killer, victim.getType().name());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (plugin.getConfig().getBoolean("exam.fail-on-death", false)) {
            core.failExam(e.getEntity(), "&cKamu mati saat ujian. Kamu gagal.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (Skills.damaging) {
            return;
        }
        double mult = 1.0;
        Entity damager = e.getDamager();
        Player attacker = null;
        boolean bow = false;
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof AbstractArrow a && a.getShooter() instanceof Player p
                && !a.getScoreboardTags().contains(Skills.SKILL_TAG)) {
            attacker = p;
            bow = true;
        }
        if (attacker != null && !(e.getEntity() instanceof Player victimPlayer && victimPlayer.equals(attacker))) {
            Profile pr = core.profile(attacker);
            Defs.Clazz c = core.clazz(pr);
            var cfg = plugin.getConfig();
            double pct = 0;
            double crit = 0;
            double critMul = 1.5;
            if (bow) {
                pct += pr.stat("dex") * cfg.getDouble("stats.dex-bow-damage-percent", 1.0);
            } else {
                pct += pr.stat("str") * cfg.getDouble("stats.str-melee-damage-percent", 1.0);
            }
            crit += pr.stat("dex") * cfg.getDouble("stats.dex-crit-chance-percent", 0.5);
            if (c != null && core.weaponOk(attacker, c)) {
                pct += c.passive(bow ? "bow-damage-percent" : "melee-damage-percent");
                crit += c.passive("crit-chance-percent");
                if (c.passive("crit-multiplier") > 0) {
                    critMul = c.passive("crit-multiplier");
                }
            }
            mult *= 1 + pct / 100.0;
            if (crit > 0 && core.rng.nextDouble() * 100 < crit) {
                mult *= critMul;
                e.getEntity().getWorld().spawnParticle(Particle.CRIT, e.getEntity().getLocation().add(0, 1, 0), 12, 0.3, 0.3, 0.3, 0.1);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
            }
        }
        if (e.getEntity() instanceof Player victim) {
            Defs.Clazz vc = core.clazz(core.profile(victim));
            if (vc != null) {
                mult *= 1 + vc.passive("damage-taken-percent") / 100.0;
            }
        }
        e.setDamage(e.getDamage() * mult);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p) {
            Defs.Clazz c = core.clazz(core.profile(p));
            if (c != null && c.passive("fall-damage-percent") != 0) {
                e.setDamage(e.getDamage() * Math.max(0, 1 + c.passive("fall-damage-percent") / 100.0));
            }
        }
    }

    /** Tombol F memakai skill terpilih. Shift+F mengganti skill. */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        Profile pr = core.profile(p);
        if (pr.cls == null) {
            return;
        }
        e.setCancelled(true);
        if (p.isSneaking()) {
            skills.cycle(p);
        } else {
            skills.cast(p, pr.skillSel);
        }
    }
}
