package me.naook1.nereushide;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.naook1.nereushide.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * The whole game: registration, seeker draw, hiding phase, seeking phase, points, results and rewards.
 * Everything runs on the main thread (only the chat filter reads {@link #chatGroup(UUID)} asynchronously,
 * which is why the player map is concurrent).
 */
public final class GameManager {

    public enum Phase { IDLE, LOBBY, PICKING, HIDING, SEEKING, ENDED }

    public enum Role { WAITING, HIDER, SEEKER, FOUND }

    public enum Result { TIMEUP, ALLFOUND, CANCELLED, NOT_ENOUGH }

    public static final class P {
        public final UUID id;
        public final String name;
        public volatile Role role = Role.WAITING;
        public int points;
        public int finds;
        public boolean originalSeeker;
        public Location lastInside;

        P(Player p) {
            this.id = p.getUniqueId();
            this.name = p.getName();
        }
    }

    private final NereusHide plugin;
    private final Map<UUID, P> players = new ConcurrentHashMap<>();
    private final Set<UUID> lastSeekers = new HashSet<>();
    private final Random random = new Random();

    private volatile Phase phase = Phase.IDLE;
    private int timer;
    private int totalTime;
    private int waitLeft;
    private int countdownLeft = -1;
    private int foundCount;
    private BossBar bar;
    private List<UUID> chosen = new ArrayList<>();
    private boolean teleporting;
    private String lastScheduledKey = "";

    public GameManager(NereusHide plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ queries

    public Phase phase() {
        return phase;
    }

    public boolean isIn(UUID id) {
        return players.containsKey(id);
    }

    public boolean isIn(Player p) {
        return players.containsKey(p.getUniqueId());
    }

    public P get(UUID id) {
        return players.get(id);
    }

    public int count() {
        return players.size();
    }

    public boolean teleporting() {
        return teleporting;
    }

    public boolean running() {
        return phase == Phase.HIDING || phase == Phase.SEEKING;
    }

    public int timeLeft() {
        return timer;
    }

    public int hidersLeft() {
        int n = 0;
        for (P p : players.values()) {
            if (p.role == Role.HIDER) {
                n++;
            }
        }
        return n;
    }

    /** 0 = hiders (and everybody before the game starts), 1 = seeker side, -1 = not in the game. */
    public int chatGroup(UUID id) {
        P p = players.get(id);
        if (p == null) {
            return -1;
        }
        if (phase != Phase.HIDING && phase != Phase.SEEKING) {
            return 0;
        }
        return p.role == Role.HIDER ? 0 : 1;
    }

    public boolean frozen(Player p) {
        P me = players.get(p.getUniqueId());
        return phase == Phase.HIDING && me != null && me.role == Role.SEEKER;
    }

    public List<P> ranking() {
        List<P> list = new ArrayList<>(players.values());
        list.sort(Comparator.comparingInt((P p) -> p.points).reversed().thenComparing(p -> p.name));
        return list;
    }

    // ------------------------------------------------------------------ messages

    private String t(String key, String... kv) {
        return plugin.messages().format(key, kv);
    }

    private void tell(Player p, String key, String... kv) {
        plugin.messages().send(p, key, kv);
    }

    private void tellAll(String key, String... kv) {
        String s = t(key, kv);
        if (s.isBlank()) {
            return;
        }
        Component c = Text.parse(s);
        for (Player p : online()) {
            p.sendMessage(c);
        }
    }

    private List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (UUID id : players.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                out.add(p);
            }
        }
        return out;
    }

    private void sound(Player p, String key) {
        String s = plugin.settings().sound(key);
        if (s != null && !s.isBlank()) {
            p.playSound(p.getLocation(), s, 1f, 1f);
        }
    }

    private void soundAll(String key) {
        for (Player p : online()) {
            sound(p, key);
        }
    }

    private void title(Player p, String titleKey, String subKey, int in, int stay, int out, String... kv) {
        Title.Times times = Title.Times.times(Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(out * 50L));
        p.showTitle(Title.title(Text.parse(t(titleKey, kv)), Text.parse(subKey == null ? "" : t(subKey, kv)), times));
    }

    private void titleAll(String titleKey, String subKey, int in, int stay, int out, String... kv) {
        for (Player p : online()) {
            title(p, titleKey, subKey, in, stay, out, kv);
        }
    }

    private void tp(Player p, Location to) {
        if (to == null) {
            return;
        }
        teleporting = true;
        try {
            p.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            teleporting = false;
        }
        p.setFallDistance(0);
    }

    // ------------------------------------------------------------------ registration

    /** Opens the registration. Returns an error message key or null. */
    public String open() {
        if (!plugin.settings().enabled()) {
            return "disabled";
        }
        if (phase != Phase.IDLE) {
            return "already-running";
        }
        if (!plugin.arena().complete()) {
            return "arena-incomplete";
        }
        phase = Phase.LOBBY;
        waitLeft = plugin.settings().lobbyWait();
        countdownLeft = -1;
        foundCount = 0;
        bar = BossBar.bossBar(Text.parse(t("bossbar.lobby", "players", "0", "min", String.valueOf(plugin.settings().minPlayers()))),
                1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);

        Component msg = Text.parse(t("open-broadcast", "min", String.valueOf(plugin.settings().minPlayers()),
                "max", String.valueOf(plugin.settings().maxPlayers()), "seconds", String.valueOf(waitLeft)))
                .appendNewline()
                .append(Text.parse(t("btn-join")).clickEvent(ClickEvent.runCommand("/hide join")));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
            sound(p, "open");
        }
        return null;
    }

    /** Joins the player to the open game. Returns an error key or null. */
    public String join(Player p) {
        if (players.containsKey(p.getUniqueId())) {
            return "already-in";
        }
        if (phase == Phase.IDLE) {
            return "no-open";
        }
        if (phase != Phase.LOBBY) {
            return "in-progress";
        }
        if (players.size() >= plugin.settings().maxPlayers()) {
            return "full";
        }
        if (p.isDead()) {
            return "cannot-join";
        }
        if (!plugin.states().save(p)) {
            return "save-failed";
        }
        P me = new P(p);
        players.put(p.getUniqueId(), me);
        p.leaveVehicle();
        plugin.states().clear(p);
        tp(p, plugin.arena().lobby());
        bar.addViewer(p);
        updateLobbyBar();
        tellAll("joined", "player", p.getName(), "n", String.valueOf(players.size()), "max", String.valueOf(plugin.settings().maxPlayers()));
        sound(p, "join");

        if (players.size() >= plugin.settings().maxPlayers() && countdownLeft > 5) {
            countdownLeft = 5;
        }
        return null;
    }

    /** Player leaves (command, or quit when {@code quit} is true: the saved state is restored on next login). */
    public void leave(Player p, boolean quit) {
        P me = players.remove(p.getUniqueId());
        if (me == null) {
            return;
        }
        if (bar != null) {
            bar.removeViewer(p);
        }
        if (!quit) {
            restore(p);
            tell(p, "left-you");
        }
        if (phase == Phase.IDLE) {
            return;
        }
        tellAll("left", "player", me.name);
        switch (phase) {
            case LOBBY -> {
                updateLobbyBar();
                if (players.size() < plugin.settings().minPlayers()) {
                    countdownLeft = -1;
                }
            }
            case HIDING, SEEKING -> afterRemoval();
            default -> {
            }
        }
    }

    private void afterRemoval() {
        if (players.size() < 2) {
            endGame(Result.NOT_ENOUGH);
            return;
        }
        boolean seekerSide = false;
        for (P q : players.values()) {
            if (q.role == Role.SEEKER || q.role == Role.FOUND) {
                seekerSide = true;
            }
        }
        if (hidersLeft() == 0) {
            endGame(Result.ALLFOUND);
            return;
        }
        if (!seekerSide) {
            List<P> hiders = new ArrayList<>();
            for (P q : players.values()) {
                if (q.role == Role.HIDER) {
                    hiders.add(q);
                }
            }
            P pick = hiders.get(random.nextInt(hiders.size()));
            pick.role = Role.SEEKER;
            pick.originalSeeker = true;
            Player pp = Bukkit.getPlayer(pick.id);
            if (pp != null) {
                tp(pp, plugin.arena().hide());
                title(pp, "title.promoted", "title.promoted-sub", 5, 60, 10);
            }
            tellAll("promoted", "player", pick.name);
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Admin: start right now (needs at least 2 players). Returns error key or null. */
    public String forceStart() {
        if (phase != Phase.LOBBY) {
            return "no-open";
        }
        if (players.size() < 2) {
            return "need-two";
        }
        beginPicking();
        return null;
    }

    public void stop() {
        if (phase == Phase.IDLE) {
            return;
        }
        if (phase == Phase.ENDED) {
            cleanup();
            return;
        }
        endGame(Result.CANCELLED);
    }

    /** Called every second. */
    public void secondTick() {
        checkSchedule();
        switch (phase) {
            case LOBBY -> lobbyTick();
            case HIDING -> {
                timer--;
                updateBar();
                if (timer <= 0) {
                    beginSeeking();
                } else if (timer <= 5) {
                    for (Player p : online()) {
                        sound(p, "tick");
                    }
                }
            }
            case SEEKING -> {
                timer--;
                updateBar();
                if (timer == 60 || timer == 30 || timer == 10) {
                    tellAll("time-warning", "time", String.valueOf(timer));
                }
                if (timer <= 0) {
                    endGame(Result.TIMEUP);
                }
            }
            case ENDED -> {
                timer--;
                if (timer <= 0) {
                    cleanup();
                }
            }
            default -> {
            }
        }
        if (running()) {
            actionBars();
        }
    }

    private void lobbyTick() {
        int min = plugin.settings().minPlayers();
        if (players.size() >= min) {
            if (countdownLeft < 0) {
                countdownLeft = plugin.settings().countdown();
                tellAll("countdown-start", "seconds", String.valueOf(countdownLeft));
            }
            countdownLeft--;
            if (countdownLeft <= 0) {
                beginPicking();
                return;
            }
            if (countdownLeft <= 5 || countdownLeft % 10 == 0) {
                tellAll("countdown", "seconds", String.valueOf(countdownLeft));
                soundAll("tick");
            }
        } else {
            waitLeft--;
            if (waitLeft <= 0) {
                endGame(Result.NOT_ENOUGH);
                return;
            }
        }
        updateLobbyBar();
    }

    private void updateLobbyBar() {
        if (bar == null) {
            return;
        }
        int min = plugin.settings().minPlayers();
        if (countdownLeft >= 0 && players.size() >= min) {
            bar.name(Text.parse(t("bossbar.countdown", "seconds", String.valueOf(Math.max(0, countdownLeft)),
                    "players", String.valueOf(players.size()))));
            bar.progress(Math.max(0f, Math.min(1f, countdownLeft / (float) plugin.settings().countdown())));
            bar.color(BossBar.Color.GREEN);
        } else {
            bar.name(Text.parse(t("bossbar.lobby", "players", String.valueOf(players.size()), "min", String.valueOf(min))));
            bar.progress(Math.max(0f, Math.min(1f, waitLeft / (float) plugin.settings().lobbyWait())));
            bar.color(BossBar.Color.BLUE);
        }
    }

    // ------------------------------------------------------------------ seeker draw

    private void beginPicking() {
        phase = Phase.PICKING;
        chosen = pickSeekers();
        List<String> names = new ArrayList<>();
        for (P p : players.values()) {
            names.add(p.name);
        }
        final List<String> pool = names;
        bar.name(Text.parse(t("bossbar.picking")));
        bar.progress(1f);
        bar.color(BossBar.Color.PURPLE);

        new BukkitRunnable() {
            int step;

            @Override
            public void run() {
                if (phase != Phase.PICKING) {
                    cancel();
                    return;
                }
                step++;
                if (step <= 14) {
                    String shown = pool.isEmpty() ? "?" : pool.get(random.nextInt(pool.size()));
                    for (Player p : online()) {
                        p.showTitle(Title.title(Text.parse(t("title.picking")), Text.parse(t("title.picking-name", "player", shown)),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ZERO)));
                        sound(p, "tick");
                    }
                    return;
                }
                cancel();
                beginHiding();
            }
        }.runTaskTimer(plugin, 10L, 4L);
    }

    private List<UUID> pickSeekers() {
        List<UUID> all = new ArrayList<>(players.keySet());
        int n = plugin.settings().seekerCount(all.size());
        List<UUID> pool = new ArrayList<>(all);
        if (plugin.settings().avoidRepeat()) {
            List<UUID> fresh = new ArrayList<>(all);
            fresh.removeAll(lastSeekers);
            if (fresh.size() >= n) {
                pool = fresh;
            }
        }
        Collections.shuffle(pool, random);
        return new ArrayList<>(pool.subList(0, Math.min(n, pool.size())));
    }

    private void beginHiding() {
        if (players.size() < 2) {
            endGame(Result.NOT_ENOUGH);
            return;
        }
        // people may have left during the draw
        chosen.removeIf(id -> !players.containsKey(id));
        if (chosen.isEmpty()) {
            chosen = pickSeekers();
        }
        List<String> seekerNames = new ArrayList<>();
        for (P p : players.values()) {
            boolean seeker = chosen.contains(p.id);
            p.role = seeker ? Role.SEEKER : Role.HIDER;
            p.originalSeeker = seeker;
            p.points = 0;
            p.finds = 0;
            if (seeker) {
                seekerNames.add(p.name);
            }
        }
        foundCount = 0;
        int hideTime = plugin.settings().hideTime();
        phase = Phase.HIDING;
        timer = hideTime;
        totalTime = hideTime;

        String list = String.join("&7, &f", seekerNames);
        for (Player p : online()) {
            P me = players.get(p.getUniqueId());
            if (me.role == Role.SEEKER) {
                tp(p, plugin.arena().seekerRoom());
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (hideTime + 3) * 20, 0, false, false, false));
                title(p, "title.you-seeker", "title.you-seeker-sub", 5, 70, 10, "seconds", String.valueOf(hideTime));
            } else {
                tp(p, plugin.arena().hide());
                title(p, "title.you-hider", "title.you-hider-sub", 5, 70, 10, "seconds", String.valueOf(hideTime), "seekers", list);
            }
            me.lastInside = p.getLocation();
            sound(p, "start");
        }
        tellAll("seekers-chosen", "seekers", list, "hiders", String.valueOf(hidersLeft()));
        updateBar();
    }

    private void beginSeeking() {
        int seekTime = plugin.settings().seekTime();
        phase = Phase.SEEKING;
        timer = seekTime;
        totalTime = seekTime;
        for (Player p : online()) {
            P me = players.get(p.getUniqueId());
            if (me.role == Role.SEEKER) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                tp(p, plugin.arena().hide());
                title(p, "title.go-seeker", "title.go-seeker-sub", 5, 50, 10);
            } else {
                title(p, "title.go-hider", "title.go-hider-sub", 5, 50, 10, "minutes", String.valueOf(seekTime / 60));
            }
            sound(p, "release");
        }
        tellAll("seeking-started");
        updateBar();
    }

    // ------------------------------------------------------------------ finding

    /** A seeker hit a hider. Returns true when it counted as a find. */
    public boolean tryFind(Player seeker, Player hider) {
        if (phase != Phase.SEEKING) {
            return false;
        }
        P s = players.get(seeker.getUniqueId());
        P h = players.get(hider.getUniqueId());
        if (s == null || h == null || (s.role != Role.SEEKER && s.role != Role.FOUND) || h.role != Role.HIDER) {
            return false;
        }
        int penalty = plugin.settings().penalty(foundCount);
        foundCount++;
        h.points += penalty;
        h.role = Role.FOUND;
        int gain = plugin.settings().pointsPerFind();
        s.points += gain;
        s.finds++;

        tellAll("found", "hider", h.name, "seeker", s.name, "penalty", String.valueOf(penalty),
                "left", String.valueOf(hidersLeft()));
        title(hider, "title.found", "title.found-sub", 5, 60, 10, "penalty", String.valueOf(penalty));
        sound(hider, "found");
        sound(seeker, "find");
        tp(hider, plugin.arena().hide());
        h.lastInside = hider.getLocation();
        updateBar();
        if (hidersLeft() == 0) {
            endGame(Result.ALLFOUND);
        }
        return true;
    }

    // ------------------------------------------------------------------ ending

    private void endGame(Result result) {
        if (phase == Phase.IDLE || phase == Phase.ENDED) {
            return;
        }
        if (result == Result.CANCELLED || result == Result.NOT_ENOUGH) {
            tellAll(result == Result.CANCELLED ? "cancelled" : "not-enough");
            cleanup();
            return;
        }
        phase = Phase.ENDED;
        timer = plugin.settings().resultTime();
        List<P> ranking = ranking();
        List<P> winners = new ArrayList<>();
        for (P p : ranking) {
            boolean win = (result == Result.TIMEUP && p.role == Role.HIDER)
                    || (result == Result.ALLFOUND && p.originalSeeker)
                    || (p.role == Role.FOUND && p.points >= 0);
            if (win) {
                winners.add(p);
            }
        }
        int top = ranking.isEmpty() ? 0 : ranking.get(0).points;
        List<P> mvps = new ArrayList<>();
        if (top > 0) {
            for (P p : ranking) {
                if (p.points == top) {
                    mvps.add(p);
                }
            }
        }

        List<String> winnerNames = new ArrayList<>();
        for (P p : winners) {
            winnerNames.add(p.name);
        }
        String winText = winnerNames.isEmpty() ? plugin.messages().raw("nobody") : String.join("&7, &f", winnerNames);
        String reasonKey = result == Result.TIMEUP ? "result.timeup" : "result.allfound";

        List<String> lines = new ArrayList<>(plugin.messages().list("result.header", "reason", t(reasonKey), "winners", winText));
        int rank = 0;
        for (P p : ranking) {
            if (++rank > 5) {
                break;
            }
            lines.add(t("result.line", "rank", String.valueOf(rank), "player", p.name, "points", String.valueOf(p.points),
                    "finds", String.valueOf(p.finds), "role", plugin.messages().raw("role." + p.role.name().toLowerCase())));
        }
        if (!mvps.isEmpty()) {
            List<String> mv = new ArrayList<>();
            for (P p : mvps) {
                mv.add(p.name);
            }
            lines.add(t("result.mvp", "players", String.join("&7, &f", mv), "points", String.valueOf(top)));
        }
        lines.addAll(plugin.messages().list("result.footer"));

        for (Player p : online()) {
            for (String l : lines) {
                p.sendMessage(Text.parse(l));
            }
            P me = players.get(p.getUniqueId());
            boolean won = winners.contains(me);
            title(p, won ? "title.win" : "title.lose", won ? "title.win-sub" : "title.lose-sub", 5, 80, 20,
                    "points", String.valueOf(me.points));
            sound(p, won ? "win" : "lose");
            p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        if (plugin.settings().announceServer()) {
            Component summary = Text.parse(t("result.server", "winners", winText, "players", String.valueOf(players.size())));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!players.containsKey(p.getUniqueId())) {
                    p.sendMessage(summary);
                }
            }
        }

        // stats and rewards
        for (P p : ranking) {
            plugin.data().addStats(p.id, 1, winners.contains(p) ? 1 : 0, p.finds);
            runRewards("participation", p);
            if (winners.contains(p)) {
                runRewards("win", p);
            }
            if (mvps.contains(p)) {
                runRewards("mvp", p);
            }
        }
        plugin.data().flush(true);
        if (bar != null) {
            bar.name(Text.parse(t("bossbar.ended")));
            bar.progress(1f);
            bar.color(BossBar.Color.WHITE);
        }
    }

    private void runRewards(String key, P p) {
        for (String cmd : plugin.settings().rewards(key)) {
            String c = cmd.replace("{player}", p.name).replace("{points}", String.valueOf(p.points));
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.startsWith("/") ? c.substring(1) : c);
            } catch (Exception e) {
                plugin.getLogger().warning("Reward command failed '" + c + "': " + e.getMessage());
            }
        }
    }

    /** Gives everybody their things back and resets the game. */
    public void cleanup() {
        lastSeekers.clear();
        for (P p : players.values()) {
            if (p.originalSeeker) {
                lastSeekers.add(p.id);
            }
        }
        for (UUID id : new ArrayList<>(players.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                if (bar != null) {
                    bar.removeViewer(p);
                }
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                restore(p);
            }
        }
        players.clear();
        bar = null;
        phase = Phase.IDLE;
        countdownLeft = -1;
        chosen = new ArrayList<>();
    }

    private void restore(Player p) {
        Location override = plugin.settings().returnToEnd() ? plugin.arena().end() : null;
        teleporting = true;
        try {
            plugin.states().restore(p, override);
        } finally {
            teleporting = false;
        }
    }

    // ------------------------------------------------------------------ displays

    private void updateBar() {
        if (bar == null) {
            return;
        }
        boolean hiding = phase == Phase.HIDING;
        bar.name(Text.parse(t(hiding ? "bossbar.hiding" : "bossbar.seeking", "time", fmtTime(timer),
                "left", String.valueOf(hidersLeft()))));
        bar.progress(Math.max(0f, Math.min(1f, totalTime <= 0 ? 0f : timer / (float) totalTime)));
        bar.color(hiding ? BossBar.Color.YELLOW : BossBar.Color.RED);
        for (Player p : online()) {
            bar.addViewer(p);
        }
    }

    private void actionBars() {
        for (Player p : online()) {
            P me = players.get(p.getUniqueId());
            String key = switch (me.role) {
                case HIDER -> "actionbar.hider";
                case SEEKER -> "actionbar.seeker";
                default -> "actionbar.helper";
            };
            p.sendActionBar(Text.parse(t(key, "points", String.valueOf(me.points), "left", String.valueOf(hidersLeft()),
                    "time", fmtTime(timer))));
        }
    }

    private static String fmtTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /** Called every few ticks: keeps players inside the play area. */
    public void boundaryTick() {
        if (!running()) {
            return;
        }
        Arena arena = plugin.arena();
        for (Player p : online()) {
            P me = players.get(p.getUniqueId());
            if (arena.contains(p.getLocation())) {
                me.lastInside = p.getLocation();
            } else {
                Location back = me.lastInside != null ? me.lastInside : arena.hide();
                tp(p, back);
                tell(p, "out-of-arena");
            }
        }
    }

    // ------------------------------------------------------------------ schedule

    private void checkSchedule() {
        if (phase != Phase.IDLE || !plugin.getConfig().getBoolean("schedule.enabled", false)) {
            return;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("schedule.timezone", "Asia/Makassar"));
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        ZonedDateTime now = ZonedDateTime.now(zone);
        String key = LocalDate.from(now) + " " + now.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (key.equals(lastScheduledKey)) {
            return;
        }
        for (String s : plugin.getConfig().getStringList("schedule.times")) {
            try {
                if (LocalTime.parse(s.trim()).equals(now.toLocalTime().withSecond(0).withNano(0))) {
                    lastScheduledKey = key;
                    String err = open();
                    if (err != null) {
                        plugin.getLogger().info("Scheduled game not opened: " + err);
                    }
                    return;
                }
            } catch (Exception ignored) {
                // invalid time in config
            }
        }
    }
}
