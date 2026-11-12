package com.nereus.pets;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Duel pet melawan pet pemain lain: tantangan, hitung mundur, hasil, XP. */
public final class Duels {
    private record Challenge(UUID from, long expire) {
    }

    private final NereusPets plugin;
    private final PetManager mgr;
    private final Map<UUID, Challenge> pending = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private BukkitTask task;

    public Duels(NereusPets plugin, PetManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public boolean hasPending(UUID target) {
        Challenge c = pending.get(target);
        return c != null && c.expire() > System.currentTimeMillis();
    }

    public UUID challenger(UUID target) {
        Challenge c = pending.get(target);
        return c == null ? null : c.from();
    }

    // ------------------------------------------------------------------ tantangan

    public void challenge(Player from, Player to) {
        if (!plugin.getConfig().getBoolean("duel.enabled", true)) {
            plugin.send(from, "duel-disabled");
            return;
        }
        if (from.equals(to)) {
            plugin.send(from, "duel-self");
            return;
        }
        ActivePet a = mgr.activeOf(from);
        ActivePet b = mgr.activeOf(to);
        if (a == null) {
            plugin.send(from, "duel-need-pet");
            return;
        }
        if (b == null) {
            plugin.send(from, "duel-target-no-pet", "{player}", to.getName());
            return;
        }
        if (a.duel != null || b.duel != null) {
            plugin.send(from, "duel-busy");
            return;
        }
        if (!from.getWorld().equals(to.getWorld())
                || from.getLocation().distance(to.getLocation()) > plugin.getConfig().getDouble("duel.max-distance", 30)) {
            plugin.send(from, "duel-too-far", "{dist}", String.valueOf((int) plugin.getConfig().getDouble("duel.max-distance", 30)));
            return;
        }
        long now = System.currentTimeMillis();
        Long cd = cooldown.get(from.getUniqueId());
        if (cd != null && cd > now) {
            plugin.send(from, "duel-cooldown", "{time}", String.valueOf((cd - now) / 1000 + 1));
            return;
        }
        long ttl = plugin.getConfig().getLong("duel.request-seconds", 30) * 1000L;
        pending.put(to.getUniqueId(), new Challenge(from.getUniqueId(), now + ttl));
        plugin.send(from, "duel-sent", "{player}", to.getName());
        plugin.send(to, "duel-received", "{player}", from.getName(), "{pet}", mgr.plain(mgr.displayName(a.data)));
        to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
    }

    public void accept(Player p) {
        Challenge c = pending.remove(p.getUniqueId());
        if (c == null || c.expire() < System.currentTimeMillis()) {
            plugin.send(p, "duel-none");
            return;
        }
        Player from = Bukkit.getPlayer(c.from());
        if (from == null || !from.isOnline()) {
            plugin.send(p, "duel-none");
            return;
        }
        ActivePet a = mgr.activeOf(from);
        ActivePet b = mgr.activeOf(p);
        if (a == null || b == null) {
            plugin.send(p, "duel-need-pet");
            return;
        }
        if (a.duel != null || b.duel != null) {
            plugin.send(p, "duel-busy");
            return;
        }
        if (!from.getWorld().equals(p.getWorld())
                || from.getLocation().distance(p.getLocation()) > plugin.getConfig().getDouble("duel.max-distance", 30) + 10) {
            plugin.send(p, "duel-too-far", "{dist}", String.valueOf((int) plugin.getConfig().getDouble("duel.max-distance", 30)));
            return;
        }
        begin(from, a, p, b);
    }

    public void deny(Player p) {
        Challenge c = pending.remove(p.getUniqueId());
        if (c == null) {
            plugin.send(p, "duel-none");
            return;
        }
        Player from = Bukkit.getPlayer(c.from());
        plugin.send(p, "duel-denied-self");
        if (from != null) {
            plugin.send(from, "duel-denied", "{player}", p.getName());
        }
    }

    // ------------------------------------------------------------------ jalannya duel

    private void begin(Player pa, ActivePet a, Player pb, ActivePet b) {
        long now = System.currentTimeMillis();
        long count = plugin.getConfig().getLong("duel.countdown-seconds", 3) * 1000L;
        long max = plugin.getConfig().getLong("duel.max-seconds", 120) * 1000L;
        a.duel = b;
        b.duel = a;
        a.duelStart = now + count;
        b.duelStart = now + count;
        a.duelEnd = now + count + max;
        b.duelEnd = now + count + max;
        a.duelGo = false;
        b.duelGo = false;
        a.target = b.entity;
        b.target = a.entity;
        mgr.applyStats(a, true);
        mgr.applyStats(b, true);
        plugin.send(pa, "duel-start", "{vs}", mgr.plain(mgr.displayName(b.data)), "{player}", pb.getName());
        plugin.send(pb, "duel-start", "{vs}", mgr.plain(mgr.displayName(a.data)), "{player}", pa.getName());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        pending.values().removeIf(c -> c.expire() < now);
        Set<ActivePet> seen = new HashSet<>();
        for (UUID id : new ArrayList<>(mgr.activeOwners())) {
            ActivePet ap = mgr.activeById(id);
            if (ap == null || ap.duel == null || seen.contains(ap) || seen.contains(ap.duel)) {
                continue;
            }
            seen.add(ap);
            ActivePet other = ap.duel;
            Player p1 = Bukkit.getPlayer(ap.owner);
            Player p2 = Bukkit.getPlayer(other.owner);
            if (now < ap.duelStart) {
                long left = (ap.duelStart - now + 999) / 1000;
                bar(p1, "&eDuel dimulai dalam &6" + left);
                bar(p2, "&eDuel dimulai dalam &6" + left);
            } else if (!ap.duelGo) {
                ap.duelGo = true;
                other.duelGo = true;
                bar(p1, "&c&lADU!");
                bar(p2, "&c&lADU!");
                if (p1 != null) {
                    p1.playSound(p1.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.8f);
                }
                if (p2 != null) {
                    p2.playSound(p2.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.8f);
                }
            }
            if (now >= ap.duelEnd) {
                draw(ap, other);
            }
        }
    }

    private void bar(Player p, String msg) {
        if (p != null) {
            p.sendActionBar(NereusPets.LEGACY.deserialize(msg));
        }
    }

    private void clear(ActivePet a, ActivePet b) {
        a.duel = null;
        b.duel = null;
        a.target = null;
        b.target = null;
        a.duelGo = false;
        b.duelGo = false;
        a.goal = null;
        b.goal = null;
    }

    /** Pemenang ditentukan. loser tidak pingsan, kedua pet dipulihkan. */
    public void finish(ActivePet winner, ActivePet loser, boolean healLoser) {
        clear(winner, loser);
        winner.data.wins++;
        loser.data.losses++;
        double xp = plugin.getConfig().getDouble("duel.xp-base", 40)
                + plugin.getConfig().getDouble("duel.xp-per-level", 6) * loser.data.level;
        String wn = mgr.plain(mgr.displayName(winner.data));
        String ln = mgr.plain(mgr.displayName(loser.data));
        mgr.gainXp(winner, xp);
        if (winner.entity.isValid()) {
            mgr.applyStats(winner, true);
        }
        if (healLoser && loser.entity.isValid()) {
            mgr.applyStats(loser, true);
        }
        Player pw = Bukkit.getPlayer(winner.owner);
        Player pl = Bukkit.getPlayer(loser.owner);
        if (pw != null) {
            plugin.send(pw, "duel-win", "{pet}", wn, "{xp}", String.valueOf(Math.round(xp)));
            pw.playSound(pw.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
        if (pl != null) {
            plugin.send(pl, "duel-lose", "{pet}", ln, "{winner}", wn);
        }
        long cd = plugin.getConfig().getLong("duel.cooldown-seconds", 10) * 1000L;
        cooldown.put(winner.owner, System.currentTimeMillis() + cd);
        cooldown.put(loser.owner, System.currentTimeMillis() + cd);
    }

    public void draw(ActivePet a, ActivePet b) {
        clear(a, b);
        if (a.entity.isValid()) {
            mgr.applyStats(a, true);
        }
        if (b.entity.isValid()) {
            mgr.applyStats(b, true);
        }
        for (UUID id : new UUID[] {a.owner, b.owner}) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.send(p, "duel-draw");
            }
        }
    }

    /** Salah satu pet ditarik (recall, pemain keluar): lawan menang. */
    public void abort(ActivePet quitter) {
        ActivePet opp = quitter.duel;
        if (opp == null) {
            return;
        }
        finish(opp, quitter, false);
    }
}
