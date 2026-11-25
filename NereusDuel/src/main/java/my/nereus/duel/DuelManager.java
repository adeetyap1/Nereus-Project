package my.nereus.duel;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Seluruh alur duel: antrean, tantangan, ronde best-of-three, penyimpanan/pemulihan inventory. Semua di main thread. */
final class DuelManager {

    private record Challenge(UUID from, String mode, long expireAt) {}

    private final NereusDuel plugin;
    private final Map<UUID, Match> inMatch = new HashMap<>();
    private final List<Match> matches = new ArrayList<>();
    private final Map<String, Deque<UUID>> queues = new LinkedHashMap<>();
    private final Map<UUID, String> queued = new HashMap<>();
    private final Map<UUID, Challenge> challenges = new HashMap<>();
    private final BukkitTask ticker;

    DuelManager(NereusDuel plugin) {
        this.plugin = plugin;
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    Match matchOf(UUID id) {
        return inMatch.get(id);
    }

    boolean queuedFor(UUID id) {
        return queued.containsKey(id);
    }

    // ------------------------------------------------------------------ antrean

    private boolean canEnter(Player p) {
        if (inMatch.containsKey(p.getUniqueId()) || queued.containsKey(p.getUniqueId())) {
            plugin.tell(p, "already");
            return false;
        }
        if ((p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) || p.isDead()) {
            plugin.tell(p, "bad-gamemode");
            return false;
        }
        if (!plugin.arenas().hasReady()) {
            plugin.tell(p, "no-arena");
            return false;
        }
        return true;
    }

    void join(Player p, String mode) {
        if (!plugin.validMode(mode) || !canEnter(p)) return;
        queues.computeIfAbsent(mode, k -> new ArrayDeque<>()).addLast(p.getUniqueId());
        queued.put(p.getUniqueId(), mode);
        plugin.tell(p, "queued", "mode", plugin.modeName(mode));
        tryMatch(mode);
        if (queued.containsKey(p.getUniqueId()) && plugin.arenas().free() == null) plugin.tell(p, "arena-busy");
    }

    /** Keluar antrean, atau menyerah bila sedang duel. */
    void leave(Player p) {
        Match m = inMatch.get(p.getUniqueId());
        if (m != null) {
            forfeit(p);
            return;
        }
        String mode = queued.remove(p.getUniqueId());
        if (mode == null) {
            plugin.tell(p, "not-queued");
            return;
        }
        Deque<UUID> q = queues.get(mode);
        if (q != null) q.remove(p.getUniqueId());
        plugin.tell(p, "left-queue");
    }

    private void dropQueue(UUID id) {
        String mode = queued.remove(id);
        if (mode != null && queues.get(mode) != null) queues.get(mode).remove(id);
    }

    private void tryMatch(String mode) {
        Deque<UUID> q = queues.get(mode);
        if (q == null) return;
        while (true) {
            q.removeIf(id -> Bukkit.getPlayer(id) == null);
            if (q.size() < 2) return;
            Arena ar = plugin.arenas().free();
            if (ar == null) return;
            Player a = Bukkit.getPlayer(q.pollFirst());
            Player b = Bukkit.getPlayer(q.pollFirst());
            if (a == null || b == null) continue;
            queued.remove(a.getUniqueId());
            queued.remove(b.getUniqueId());
            start(a, b, mode, ar);
        }
    }

    private void tryAllQueues() {
        for (String mode : new ArrayList<>(queues.keySet())) tryMatch(mode);
    }

    // ------------------------------------------------------------------ tantangan

    void challenge(Player from, Player to, String mode) {
        if (from.equals(to)) return;
        if (!plugin.validMode(mode)) {
            plugin.raw(from, "&cMode tidak dikenal.");
            return;
        }
        if (inMatch.containsKey(to.getUniqueId()) || queued.containsKey(to.getUniqueId())) {
            plugin.raw(from, "&c" + to.getName() + " sedang sibuk.");
            return;
        }
        if (!canEnterQuiet(from)) {
            plugin.tell(from, "already");
            return;
        }
        int secs = plugin.getConfig().getInt("challenge.expire-seconds", 30);
        challenges.put(to.getUniqueId(), new Challenge(from.getUniqueId(), mode, System.currentTimeMillis() + secs * 1000L));
        plugin.tell(from, "challenge-sent", "player", to.getName(), "mode", plugin.modeName(mode));
        Component c = Text.color(plugin.getConfig().getString("messages.prefix", "") + plugin.m("challenge-received",
                "player", from.getName(), "mode", plugin.modeName(mode), "seconds", String.valueOf(secs)))
                .clickEvent(ClickEvent.runCommand("/duel accept"));
        to.sendMessage(c);
    }

    private boolean canEnterQuiet(Player p) {
        return !inMatch.containsKey(p.getUniqueId()) && !queued.containsKey(p.getUniqueId());
    }

    void accept(Player to) {
        Challenge c = challenges.remove(to.getUniqueId());
        if (c == null || System.currentTimeMillis() > c.expireAt()) {
            plugin.tell(to, c == null ? "challenge-none" : "challenge-expired");
            return;
        }
        Player from = Bukkit.getPlayer(c.from());
        if (from == null) {
            plugin.tell(to, "player-not-found");
            return;
        }
        if (!canEnter(to) || !canEnter(from)) return;
        Arena ar = plugin.arenas().free();
        if (ar == null) {
            plugin.tell(to, "arena-busy");
            plugin.tell(from, "arena-busy");
            return;
        }
        start(from, to, c.mode(), ar);
    }

    void deny(Player to) {
        Challenge c = challenges.remove(to.getUniqueId());
        if (c == null) {
            plugin.tell(to, "challenge-none");
            return;
        }
        Player from = Bukkit.getPlayer(c.from());
        if (from != null) plugin.tell(from, "challenge-denied", "player", to.getName());
    }

    // ------------------------------------------------------------------ mulai & ronde

    private void start(Player a, Player b, String mode, Arena ar) {
        Match m = new Match(a.getUniqueId(), b.getUniqueId(), mode, ar);
        try {
            for (Player p : new Player[]{a, b}) {
                Snapshot s = Snapshot.capture(p);
                s.save(plugin.snapshotFile(p.getUniqueId()));
                m.snaps.put(p.getUniqueId(), s);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal menyimpan snapshot, duel dibatalkan: " + e);
            plugin.raw(a, "&cGagal memulai duel (galat penyimpanan).");
            plugin.raw(b, "&cGagal memulai duel (galat penyimpanan).");
            plugin.snapshotFile(a.getUniqueId()).delete();
            plugin.snapshotFile(b.getUniqueId()).delete();
            return;
        }
        ar.busy = m;
        matches.add(m);
        inMatch.put(a.getUniqueId(), m);
        inMatch.put(b.getUniqueId(), m);
        dropQueue(a.getUniqueId());
        dropQueue(b.getUniqueId());
        plugin.tell(a, "match-found", "opponent", b.getName(), "mode", plugin.modeName(mode));
        plugin.tell(b, "match-found", "opponent", a.getName(), "mode", plugin.modeName(mode));
        beginRound(m);
    }

    private void prepare(Player p, Match m, Spot spot, Spot facing) {
        Location l = spot.toLocation();
        if (l != null) {
            Location f = facing.toLocation();
            if (f != null) {
                double dx = f.getX() - l.getX(), dz = f.getZ() - l.getZ();
                l.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
                l.setPitch(0f);
            }
            p.teleport(l);
        }
        p.setGameMode(GameMode.SURVIVAL);
        p.setAllowFlight(false);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(max == null ? 20 : max.getValue());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        if (m.mode.equals("own")) {
            p.getInventory().clear();
            p.getInventory().setContents(m.snaps.get(p.getUniqueId()).contentsCopy());
        } else {
            KitManager.Kit k = plugin.kits().get(m.mode);
            if (k != null) k.give(p);
        }
    }

    private void beginRound(Match m) {
        Player a = Bukkit.getPlayer(m.a);
        Player b = Bukkit.getPlayer(m.b);
        if (a == null || b == null) {
            if (a != null) forfeitOf(m, m.b);
            else if (b != null) forfeitOf(m, m.a);
            else endMatch(m, null, true);
            return;
        }
        m.round++;
        m.phase = Match.Phase.COUNTDOWN;
        prepare(a, m, m.arena.s1, m.arena.s2);
        prepare(b, m, m.arena.s2, m.arena.s1);
        int secs = Math.max(1, plugin.getConfig().getInt("match.countdown-seconds", 5));
        if (m.task != null) m.task.cancel();
        m.task = new BukkitRunnable() {
            int t = secs;

            @Override
            public void run() {
                if (m.phase == Match.Phase.ENDED) {
                    cancel();
                    return;
                }
                Player pa = Bukkit.getPlayer(m.a);
                Player pb = Bukkit.getPlayer(m.b);
                if (pa == null || pb == null) {
                    cancel();
                    return;
                }
                if (t > 0) {
                    title(pa, plugin.m("countdown", "seconds", String.valueOf(t)), "&7Ronde " + m.round);
                    title(pb, plugin.m("countdown", "seconds", String.valueOf(t)), "&7Ronde " + m.round);
                    pa.playSound(pa.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    pb.playSound(pb.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                    t--;
                } else {
                    m.phase = Match.Phase.ACTIVE;
                    m.roundStart = System.currentTimeMillis();
                    title(pa, plugin.m("fight"), "");
                    title(pb, plugin.m("fight"), "");
                    pa.playSound(pa.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
                    pb.playSound(pb.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /** Dipanggil saat pemain 'mati' (HP akan 0), keluar arena, atau waktu habis. winner null = seri. */
    void roundWin(Match m, UUID winner) {
        if (m.phase != Match.Phase.ACTIVE) return;
        m.phase = Match.Phase.BREAK;
        Player pa = Bukkit.getPlayer(m.a);
        Player pb = Bukkit.getPlayer(m.b);
        if (winner == null) {
            for (Player p : new Player[]{pa, pb}) if (p != null) title(p, plugin.m("round-draw"), "");
        } else {
            if (winner.equals(m.a)) m.scoreA++;
            else m.scoreB++;
            for (Player p : new Player[]{pa, pb}) {
                if (p == null) continue;
                boolean won = p.getUniqueId().equals(winner);
                title(p, plugin.m(won ? "round-win" : "round-lose"),
                        plugin.m("score", "you", String.valueOf(m.score(p.getUniqueId())), "opp", String.valueOf(m.score(m.opponent(p.getUniqueId())))));
                p.playSound(p.getLocation(), won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
            if (m.score(winner) >= Math.max(1, plugin.getConfig().getInt("match.rounds-to-win", 2))) {
                endMatch(m, winner, false);
                return;
            }
        }
        int wait = Math.max(1, plugin.getConfig().getInt("match.intermission-seconds", 5));
        if (m.task != null) m.task.cancel();
        m.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (m.phase == Match.Phase.BREAK) beginRound(m);
        }, wait * 20L);
    }

    private void title(Player p, String main, String sub) {
        p.showTitle(Title.title(Text.color(main), Text.color(sub),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1300), Duration.ofMillis(300))));
    }

    // ------------------------------------------------------------------ akhir duel

    void forfeit(Player p) {
        Match m = inMatch.get(p.getUniqueId());
        if (m == null) return;
        forfeitOf(m, m.opponent(p.getUniqueId()));
    }

    private void forfeitOf(Match m, UUID winner) {
        UUID loser = m.opponent(winner);
        Player lp = Bukkit.getPlayer(loser);
        Player wp = Bukkit.getPlayer(winner);
        if (wp != null) plugin.tell(wp, "forfeit", "player", lp == null ? "Lawan" : lp.getName());
        endMatch(m, winner, true);
    }

    /** winner null = dibatalkan tanpa pemenang (tanpa statistik). */
    void endMatch(Match m, UUID winner, boolean forfeit) {
        if (m.phase == Match.Phase.ENDED) return;
        m.phase = Match.Phase.ENDED;
        if (m.task != null) m.task.cancel();
        matches.remove(m);
        m.arena.busy = null;

        String winName = winner == null ? "-" : nameOf(winner);
        String loseName = winner == null ? "-" : nameOf(m.opponent(winner));
        for (UUID id : new UUID[]{m.a, m.b}) {
            inMatch.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (winner != null) title(p, plugin.m(id.equals(winner) ? "match-win" : "match-lose"),
                    plugin.m("score", "you", String.valueOf(m.score(id)), "opp", String.valueOf(m.score(m.opponent(id)))));
            if (!p.isDead()) {
                restore(p, m.snaps.get(id));
                plugin.tell(p, "restored");
            }
        }
        if (winner != null) {
            plugin.stats().result(winner, winName, m.opponent(winner), loseName);
            if (plugin.getConfig().getBoolean("match.broadcast-results", true)) {
                String msg = plugin.getConfig().getString("messages.prefix", "") + plugin.m("result", "winner", winName,
                        "loser", loseName, "ws", String.valueOf(m.score(winner)), "ls", String.valueOf(m.score(m.opponent(winner))),
                        "mode", plugin.modeName(m.mode));
                for (Player p : Bukkit.getOnlinePlayers()) p.sendMessage(Text.color(msg));
            }
            runCommands("match.winner-commands", winName);
            runCommands("match.loser-commands", loseName);
        }
        tryAllQueues();
    }

    private void runCommands(String path, String player) {
        for (String c : plugin.getConfig().getStringList(path)) {
            String cmd = c.replace("%player%", player);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    private void restore(Player p, Snapshot s) {
        if (s == null) return;
        s.apply(p);
        plugin.snapshotFile(p.getUniqueId()).delete();
    }

    /** Pemain punya snapshot tersisa (crash/mati saat duel): kembalikan. */
    void restoreLeftover(Player p) {
        if (inMatch.containsKey(p.getUniqueId())) return;
        File f = plugin.snapshotFile(p.getUniqueId());
        if (!f.exists()) return;
        try {
            Snapshot s = Snapshot.load(f);
            s.apply(p);
            f.delete();
            plugin.tell(p, "restored");
        } catch (RuntimeException e) {
            plugin.getLogger().severe("Snapshot " + f.getName() + " rusak, dipindahkan agar tidak diulang: " + e);
            f.renameTo(new File(f.getParentFile(), f.getName() + ".rusak"));
        }
    }

    void quit(Player p) {
        dropQueue(p.getUniqueId());
        challenges.remove(p.getUniqueId());
        forfeit(p);
    }

    void shutdown() {
        ticker.cancel();
        for (Match m : new ArrayList<>(matches)) endMatch(m, null, true);
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        long now = System.currentTimeMillis();
        challenges.values().removeIf(c -> now > c.expireAt());
        double radius = plugin.getConfig().getDouble("match.arena-radius", 25);
        double voidDepth = plugin.getConfig().getDouble("match.void-depth", 12);
        int limit = plugin.getConfig().getInt("match.round-time-limit-seconds", 120);
        for (Match m : new ArrayList<>(matches)) {
            Player pa = Bukkit.getPlayer(m.a);
            Player pb = Bukkit.getPlayer(m.b);
            if (pa == null || pb == null) {
                if (pa != null) forfeitOf(m, m.a);
                else if (pb != null) forfeitOf(m, m.b);
                else endMatch(m, null, true);
                continue;
            }
            int left = 0;
            if (m.phase == Match.Phase.ACTIVE) {
                long elapsed = (now - m.roundStart) / 1000;
                left = (int) Math.max(0, limit - elapsed);
                boolean aOut = !m.arena.inside(pa.getLocation(), radius, voidDepth);
                boolean bOut = !m.arena.inside(pb.getLocation(), radius, voidDepth);
                if (aOut || bOut) {
                    roundWin(m, aOut && bOut ? null : (aOut ? m.b : m.a));
                    continue;
                }
                if (limit > 0 && elapsed >= limit) {
                    double ha = pa.getHealth(), hb = pb.getHealth();
                    roundWin(m, Math.abs(ha - hb) < 0.01 ? null : (ha > hb ? m.a : m.b));
                    continue;
                }
            }
            if (m.phase != Match.Phase.ENDED) {
                bar(pa, pb, m, left);
                bar(pb, pa, m, left);
            }
        }
    }

    private void bar(Player p, Player opp, Match m, int left) {
        p.sendActionBar(Text.color(plugin.m("actionbar", "round", String.valueOf(m.round),
                "you", String.valueOf(m.score(p.getUniqueId())), "opp", String.valueOf(m.score(opp.getUniqueId())),
                "opponent", opp.getName(), "time", String.valueOf(left))));
    }
}
