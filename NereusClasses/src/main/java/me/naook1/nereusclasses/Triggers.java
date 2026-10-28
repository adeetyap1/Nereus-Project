package me.naook1.nereusclasses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.naook1.nereusclasses.Engine.Ctx;
import me.naook1.nereusclasses.Engine.Tgt;
import me.naook1.nereusclasses.Model.Line;
import me.naook1.nereusclasses.Model.Skill;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** connects player actions (swing, use, damage, timers) to gear skills. */
final class Triggers implements Listener {
    private final NereusClasses pl;
    private long tick = 0;
    private final Map<UUID, Long> swingCd = new HashMap<>();

    Triggers(NereusClasses pl) {
        this.pl = pl;
    }

    void start() {
        Bukkit.getScheduler().runTaskTimer(pl, this::loop, 20L, 1L);
    }

    private Ctx ctx(Player p, Entity target) {
        Ctx c = new Ctx();
        c.caster = p;
        c.origin = p.getLocation();
        c.mods = new HashMap<>(pl.baseMods);
        if (target != null) {
            c.inherited.add(new Tgt(target));
        }
        return c;
    }

    private NereusClasses.Gear gearOf(ItemStack it) {
        String k = pl.items.keyOf(it);
        return k == null ? null : pl.gear.get(k);
    }

    private NereusClasses.Gear held(Player p) {
        NereusClasses.Gear g = gearOf(p.getInventory().getItemInMainHand());
        return g != null && pl.canUse(p, g.cls) ? g : null;
    }

    private List<NereusClasses.Gear> worn(Player p) {
        List<NereusClasses.Gear> out = new ArrayList<>();
        for (ItemStack it : p.getInventory().getArmorContents()) {
            NereusClasses.Gear g = gearOf(it);
            if (g != null && pl.canUse(p, g.cls)) {
                out.add(g);
            }
        }
        return out;
    }

    private void actionBar(Player p, String s) {
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(NereusClasses.color(s)));
    }

    private int sel(Player p, NereusClasses.Gear g) {
        Map<String, Integer> m = pl.selected.get(p.getUniqueId());
        int i = m == null ? 0 : m.getOrDefault(g.key, 0);
        return g.slots.isEmpty() ? 0 : Math.floorMod(i, g.slots.size());
    }

    String status(Player p, NereusClasses.Gear g) {
        if (g.slots.isEmpty()) {
            return null;
        }
        int i = sel(p, g);
        Line l = g.slots.get(i);
        String id = l.o.str("", "s", "skill", "meta", "m");
        long left = pl.engine.cooldownLeftMs(p, id);
        String cd = left > 0 ? " &c" + String.format("%.1fs", left / 1000.0) : " &a✔";
        return "&6⚔ &f" + NereusClasses.skillName(l) + " &8[" + (i + 1) + "/" + g.slots.size() + "]" + cd;
    }

    void cycle(Player p, int dir) {
        NereusClasses.Gear g = held(p);
        if (g == null || g.slots.isEmpty()) {
            p.sendMessage("§7Pegang senjata class untuk ganti skill.");
            return;
        }
        Map<String, Integer> m = pl.selected.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        m.put(g.key, Math.floorMod(sel(p, g) + dir, g.slots.size()));
        actionBar(p, status(p, g));
    }

    private void loop() {
        tick++;
        boolean bar = pl.getConfig().getBoolean("ui.actionbar", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            NereusClasses.Gear h = held(p);
            List<NereusClasses.Gear> w = worn(p);
            if (h == null && w.isEmpty()) {
                continue;
            }
            if (h != null) {
                runTimers(p, h);
                if (bar && tick % 10 == 0) {
                    String s = status(p, h);
                    if (s != null) {
                        actionBar(p, s);
                    }
                }
            }
            for (NereusClasses.Gear g : w) {
                runTimers(p, g);
            }
        }
    }

    private void runTimers(Player p, NereusClasses.Gear g) {
        for (int i = 0; i < g.timers.size(); i++) {
            int per = g.timerPeriods.get(i);
            if (tick % per == 0) {
                pl.engine.execLine(g.timers.get(i), ctx(p, null));
            }
        }
    }

    // ------------------------------------------------------------------ events
    @EventHandler(ignoreCancelled = false)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player p = e.getPlayer();
        NereusClasses.Gear g = held(p);
        if (g == null) {
            return;
        }
        if (p.isSneaking() && g.slots.size() > 1) {
            long now = System.currentTimeMillis();
            Long last = swingCd.get(p.getUniqueId());
            if (last == null || now - last > 250) {
                swingCd.put(p.getUniqueId(), now);
                cycle(p, 1);
            }
            return;
        }
        if (g.swing.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = swingCd.get(p.getUniqueId());
        long cdMs = pl.getConfig().getLong("engine.swing-cooldown-ticks", 5) * 50;
        if (last != null && now - last < cdMs) {
            return;
        }
        swingCd.put(p.getUniqueId(), now);
        for (Line l : g.swing) {
            pl.engine.execLine(l, ctx(p, null));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (pl.engine.inSkillDamage) {
            return;
        }
        Entity d = e.getDamager();
        Player p = null;
        if (d instanceof Player pp) {
            p = pp;
        } else if (d instanceof Projectile pr && pr.getShooter() instanceof Player pp) {
            p = pp;
        }
        if (p == null || !(e.getEntity() instanceof LivingEntity)) {
            return;
        }
        NereusClasses.Gear g = held(p);
        if (g == null || g.attack.isEmpty()) {
            return;
        }
        for (Line l : g.attack) {
            pl.engine.execLine(l, ctx(p, e.getEntity()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity damager = e instanceof EntityDamageByEntityEvent be ? be.getDamager() : null;
        // shield style auras (onDamaged mechanic)
        Map<String, Engine.Aura> m = pl.engine.auras.get(victim.getUniqueId());
        if (m != null) {
            for (Engine.Aura a : new ArrayList<>(m.values())) {
                if (!a.dmg || pl.engine.now >= a.end) {
                    continue;
                }
                if (a.cancel) {
                    e.setCancelled(true);
                }
                if (a.onHit != null) {
                    Ctx c = new Ctx();
                    c.caster = victim;
                    c.origin = victim.getLocation();
                    c.mods = a.ctx != null ? a.ctx.mods : new HashMap<>(pl.baseMods);
                    if (damager != null) {
                        c.inherited.add(new Tgt(damager));
                    }
                    pl.engine.cast(a.onHit, c);
                }
            }
        }
        if (e.isCancelled() || !(victim instanceof Player p)) {
            return;
        }
        List<NereusClasses.Gear> all = new ArrayList<>(worn(p));
        NereusClasses.Gear h = held(p);
        if (h != null) {
            all.add(h);
        }
        for (NereusClasses.Gear g : all) {
            for (Line l : g.damaged) {
                pl.engine.execLine(l, ctx(p, damager));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        NereusClasses.Gear g = held(p);
        if (g == null || g.slots.isEmpty()) {
            return;
        }
        e.setUseItemInHand(Event.Result.DENY);
        if (p.isSneaking() && g.slots.size() > 1) {
            e.setCancelled(true);
            pl.gui.openSkills(p, g);
            return;
        }
        int i = sel(p, g);
        Line l = g.slots.get(i);
        String id = l.o.str("", "s", "skill", "meta", "m");
        Skill sk = pl.engine.skills.get(id.toLowerCase());
        if (sk != null && pl.engine.onCooldown(p, sk)) {
            actionBar(p, status(p, g));
            return;
        }
        pl.engine.execLine(l, ctx(p, null));
        if (sk != null && sk.doubleFire) {
            pl.engine.later(2, () -> {
                if (p.isOnline()) {
                    pl.engine.execLine(l, ctx(p, null));
                }
            });
        }
        actionBar(p, status(p, g));
    }

    @EventHandler
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent e) {
        NereusClasses.Gear g = held(e.getPlayer());
        if (g != null && g.slots.size() > 1) {
            e.setCancelled(true);
            cycle(e.getPlayer(), 1);
        }
    }

    void select(Player p, NereusClasses.Gear g, int idx) {
        pl.selected.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(g.key, idx);
        actionBar(p, status(p, g));
    }

    int selected(Player p, NereusClasses.Gear g) {
        return sel(p, g);
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTask(pl, () -> {
            Player p = e.getPlayer();
            NereusClasses.Gear g = held(p);
            if (g != null) {
                String s = status(p, g);
                if (s != null) {
                    actionBar(p, s);
                }
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        pl.engine.clearAuras(id);
        swingCd.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        pl.engine.clearAuras(e.getEntity().getUniqueId());
    }
}
