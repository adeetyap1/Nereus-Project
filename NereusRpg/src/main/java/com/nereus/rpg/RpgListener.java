package com.nereus.rpg;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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

    private static boolean weaponLike(Material m) {
        String n = m.name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.equals("BOW") || n.equals("CROSSBOW")
                || n.equals("TRIDENT") || n.equals("MACE") || n.endsWith("_SPEAR");
    }

    private final java.util.Map<java.util.UUID, Long> lastCycle = new java.util.HashMap<>();

    /** Klik kanan = pakai skill terpilih (busur: sambil jongkok). Jongkok + klik kiri = ganti skill. */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        var cfg = plugin.getConfig();
        Profile pr = core.profile(p);
        Defs.Clazz c = core.clazz(pr);
        if (c == null) {
            return;
        }
        Material held = p.getInventory().getItemInMainHand().getType();
        if (held.isAir() || !core.weaponOk(p, c) || (!weaponLike(held) && core.weaponList(c.key()).isEmpty())) {
            return;
        }
        Action a = e.getAction();
        if ((a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) && cfg.getBoolean("controls.right-click-cast", true)) {
            boolean ranged = held == Material.BOW || held == Material.CROSSBOW || held == Material.TRIDENT;
            if (ranged && !p.isSneaking()) {
                return;
            }
            if (a == Action.RIGHT_CLICK_BLOCK && !p.isSneaking() && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().isInteractable()) {
                return;
            }
            e.setCancelled(true);
            skills.cast(p, pr.skillSel);
        } else if ((a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) && p.isSneaking()
                && cfg.getBoolean("controls.shift-left-click-cycle", true)) {
            long now = System.currentTimeMillis();
            Long last = lastCycle.get(p.getUniqueId());
            if (last == null || now - last > 300) {
                lastCycle.put(p.getUniqueId(), now);
                skills.cycle(p);
            }
        }
    }

    @EventHandler
    public void onQuitCycle(PlayerQuitEvent e) {
        lastCycle.remove(e.getPlayer().getUniqueId());
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
