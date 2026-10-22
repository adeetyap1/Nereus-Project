package me.naook1.nereuslottery;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import me.naook1.nereuslottery.util.Text;
import me.naook1.nereuslottery.util.TimeUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Tickets, jackpot, schedule and draws. All state changes happen on the main thread. */
public final class LotteryManager {

    public record Win(String player, long prize, int tickets, long time) {
    }

    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID"));
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("EEEE HH:mm", Locale.forLanguageTag("id-ID"));

    private final NereusLottery plugin;
    private final File file;
    private final Object writeLock = new Object();
    private final Map<UUID, Integer> tickets = new LinkedHashMap<>();
    private final Map<UUID, Long> pending = new HashMap<>();
    private final List<Win> history = new ArrayList<>();
    private final Set<Integer> announced = new HashSet<>();
    private final Random random = new Random();
    private boolean dirty;
    private long pot = -1;
    private long nextDraw;

    // config
    private boolean enabled = true;
    private long price = 1000;
    private int maxTickets = 20;
    private int minPlayers = 1;
    private long seed = 10000;
    private List<Integer> winnerPct = List.of(100);
    private ZoneId zone = ZoneId.systemDefault();
    private final List<LocalTime> times = new ArrayList<>();
    private final Set<DayOfWeek> days = new HashSet<>();
    private List<Integer> announceBefore = List.of();
    private List<Integer> buyAmounts = List.of(1, 5, 10);
    private String symbol = "$";
    private String buySound = "";
    private String winSound = "";
    private String loseSound = "";

    public LotteryManager(NereusLottery plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ config + storage

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("enabled", true);
        price = Math.max(1, c.getLong("ticket-price", 1000));
        maxTickets = Math.max(1, c.getInt("max-tickets-per-player", 20));
        minPlayers = Math.max(1, c.getInt("min-players", 1));
        seed = Math.max(0, c.getLong("seed", 10000));
        symbol = c.getString("currency-symbol", "$");
        buySound = c.getString("sounds.buy", "");
        winSound = c.getString("sounds.win", "");
        loseSound = c.getString("sounds.lose", "");

        List<Integer> pct = new ArrayList<>();
        for (int v : c.getIntegerList("winners")) {
            if (v > 0) {
                pct.add(v);
            }
        }
        winnerPct = pct.isEmpty() ? List.of(100) : pct;

        try {
            zone = ZoneId.of(c.getString("draw.timezone", "Asia/Makassar"));
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid draw.timezone, using server timezone.");
            zone = ZoneId.systemDefault();
        }
        times.clear();
        for (String t : c.getStringList("draw.times")) {
            try {
                times.add(LocalTime.parse(t.trim()));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid draw time '" + t + "' (use HH:mm)");
            }
        }
        days.clear();
        for (String d : c.getStringList("draw.days")) {
            try {
                days.add(DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT)));
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid draw day '" + d + "' (use MONDAY..SUNDAY)");
            }
        }
        announceBefore = c.getIntegerList("announce-before");
        List<Integer> amounts = new ArrayList<>();
        for (int a : c.getIntegerList("menu.buy-amounts")) {
            if (a > 0 && amounts.size() < 3) {
                amounts.add(a);
            }
        }
        buyAmounts = amounts.isEmpty() ? List.of(1, 5, 10) : amounts;

        if (pot < seed) {
            pot = seed;
            dirty = true;
        }
        nextDraw = computeNext();
        resetAnnounced();
    }

    public void load() {
        tickets.clear();
        pending.clear();
        history.clear();
        pot = -1;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        pot = y.getLong("pot", -1);
        var t = y.getConfigurationSection("tickets");
        if (t != null) {
            for (String k : t.getKeys(false)) {
                try {
                    tickets.put(UUID.fromString(k), t.getInt(k));
                } catch (IllegalArgumentException ignored) {
                    // skip bad entry
                }
            }
        }
        var p = y.getConfigurationSection("pending");
        if (p != null) {
            for (String k : p.getKeys(false)) {
                try {
                    pending.put(UUID.fromString(k), p.getLong(k));
                } catch (IllegalArgumentException ignored) {
                    // skip bad entry
                }
            }
        }
        for (Map<?, ?> m : y.getMapList("history")) {
            try {
                history.add(new Win(String.valueOf(m.get("player")), ((Number) m.get("prize")).longValue(),
                        ((Number) m.get("tickets")).intValue(), ((Number) m.get("time")).longValue()));
            } catch (Exception ignored) {
                // skip bad entry
            }
        }
    }

    public void flush(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.set("pot", pot);
        for (var e : tickets.entrySet()) {
            y.set("tickets." + e.getKey(), e.getValue());
        }
        for (var e : pending.entrySet()) {
            y.set("pending." + e.getKey(), e.getValue());
        }
        List<Map<String, Object>> hist = new ArrayList<>();
        for (Win w : history) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("player", w.player());
            m.put("prize", w.prize());
            m.put("tickets", w.tickets());
            m.put("time", w.time());
            hist.add(m);
        }
        y.set("history", hist);
        final String content = y.saveToString();
        Runnable write = () -> {
            synchronized (writeLock) {
                try {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
                }
            }
        };
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    // ------------------------------------------------------------------ schedule

    /** Next draw after now (epoch ms), or 0 when no schedule is configured. */
    private long computeNext() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        long best = Long.MAX_VALUE;
        for (int d = 0; d <= 7; d++) {
            LocalDate date = now.toLocalDate().plusDays(d);
            if (!days.isEmpty() && !days.contains(date.getDayOfWeek())) {
                continue;
            }
            for (LocalTime t : times) {
                ZonedDateTime c = date.atTime(t).atZone(zone);
                if (c.isAfter(now)) {
                    best = Math.min(best, c.toInstant().toEpochMilli());
                }
            }
        }
        return best == Long.MAX_VALUE ? 0 : best;
    }

    private void resetAnnounced() {
        announced.clear();
        if (nextDraw <= 0) {
            return;
        }
        long left = nextDraw - System.currentTimeMillis();
        for (int s : announceBefore) {
            if (left <= s * 1000L) {
                announced.add(s);   // that window already passed (e.g. server restarted)
            }
        }
    }

    public void tick() {
        if (!enabled || nextDraw <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= nextDraw) {
            draw();
            return;
        }
        long left = nextDraw - now;
        for (int s : announceBefore) {
            if (left <= s * 1000L && announced.add(s)) {
                plugin.messages().broadcast("countdown", "time", TimeUtil.left(plugin, left),
                        "pot", fmt(pot), "symbol", symbol);
            }
        }
    }

    // ------------------------------------------------------------------ player actions

    public void buy(Player p, int n) {
        var m = plugin.messages();
        if (!enabled) {
            m.send(p, "disabled");
            return;
        }
        if (n <= 0) {
            m.send(p, "invalid-number");
            return;
        }
        if (!plugin.economy().ready()) {
            m.send(p, "no-economy");
            return;
        }
        UUID id = p.getUniqueId();
        int have = tickets.getOrDefault(id, 0);
        if ((long) have + n > maxTickets) {
            m.send(p, "max-tickets", "max", String.valueOf(maxTickets), "have", String.valueOf(have));
            return;
        }
        long cost = price * n;
        if (!plugin.economy().has(p, cost) || !plugin.economy().withdraw(p, cost)) {
            m.send(p, "not-enough-money", "cost", fmt(cost), "symbol", symbol);
            return;
        }
        tickets.merge(id, n, Integer::sum);
        pot += cost;
        dirty = true;
        flush(true);
        m.send(p, "bought", "amount", String.valueOf(n), "cost", fmt(cost), "symbol", symbol,
                "total", String.valueOf(have + n), "chance", chance(id));
        play(p, buySound);
    }

    public void claimPending(Player p) {
        Long amount = pending.get(p.getUniqueId());
        if (amount == null || amount <= 0) {
            return;
        }
        if (plugin.economy().deposit(p, amount)) {
            pending.remove(p.getUniqueId());
            dirty = true;
            flush(true);
            plugin.messages().send(p, "pending-paid", "amount", fmt(amount), "symbol", symbol);
        }
    }

    // ------------------------------------------------------------------ draw / cancel / admin

    /** Runs the draw now. Returns false when it was postponed (not enough players). */
    public boolean draw() {
        var m = plugin.messages();
        if (tickets.size() < minPlayers) {
            m.broadcast("draw.skipped", "min", String.valueOf(minPlayers), "pot", fmt(pot), "symbol", symbol);
            nextDraw = computeNext();
            resetAnnounced();
            return false;
        }
        long total = Math.round(pot * EventsHook.multiplier("lottery_pot"));
        int totalTickets = totalTickets();
        int players = tickets.size();
        List<UUID> pool = new ArrayList<>(tickets.keySet());
        int k = Math.min(winnerPct.size(), pool.size());
        int sum = 0;
        for (int i = 0; i < k; i++) {
            sum += winnerPct.get(i);
        }

        List<String> shown = new ArrayList<>();
        List<Runnable> notify = new ArrayList<>();
        long distributed = 0;
        Set<UUID> winners = new HashSet<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < k; i++) {
            UUID w = pick(pool);
            pool.remove(w);
            winners.add(w);
            long prize = total * winnerPct.get(i) / sum;
            distributed += prize;
            int wt = tickets.getOrDefault(w, 0);
            OfflinePlayer op = Bukkit.getOfflinePlayer(w);
            String name = op.getName() == null ? "???" : op.getName();
            boolean paid = plugin.economy().deposit(op, prize);
            if (!paid) {
                pending.merge(w, prize, Long::sum);
            }
            history.add(0, new Win(name, prize, wt, now));
            shown.add(m.format("draw.winner-line", "rank", String.valueOf(i + 1), "player", name,
                    "prize", fmt(prize), "tickets", String.valueOf(wt), "symbol", symbol));
            final long fp = prize;
            notify.add(() -> {
                Player online = Bukkit.getPlayer(w);
                if (online == null) {
                    return;
                }
                m.send(online, paid ? "win.paid" : "win.pending", "prize", fmt(fp), "symbol", symbol);
                online.showTitle(Title.title(
                        Text.parse(m.format("win.title")),
                        Text.parse(m.format("win.subtitle", "prize", fmt(fp), "symbol", symbol))));
                play(online, winSound);
            });
        }
        while (history.size() > 10) {
            history.remove(history.size() - 1);
        }

        pot = Math.max(seed, total - distributed);
        Set<UUID> participants = new HashSet<>(tickets.keySet());
        tickets.clear();
        nextDraw = computeNext();
        resetAnnounced();
        dirty = true;
        flush(true);

        String next = nextClock();
        String[] kv = {"players", String.valueOf(players), "tickets", String.valueOf(totalTickets),
                "pot", fmt(total), "symbol", symbol, "next", next};
        for (String line : m.list("draw.header", kv)) {
            Bukkit.broadcast(Text.parse(line));
        }
        for (String line : shown) {
            Bukkit.broadcast(Text.parse(line));
        }
        for (String line : m.list("draw.footer", kv)) {
            Bukkit.broadcast(Text.parse(line));
        }
        notify.forEach(Runnable::run);
        for (UUID u : participants) {
            if (!winners.contains(u)) {
                Player online = Bukkit.getPlayer(u);
                if (online != null) {
                    play(online, loseSound);
                }
            }
        }
        plugin.getLogger().info("Draw finished: " + shown.size() + " winner(s), pot " + total + ".");
        return true;
    }

    /** Refunds every ticket and starts a new round. Returns how many players were refunded. */
    public int cancel() {
        int count = tickets.size();
        long refunded = 0;
        for (var e : tickets.entrySet()) {
            long amount = price * e.getValue();
            refunded += amount;
            OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
            if (!plugin.economy().deposit(op, amount)) {
                pending.merge(e.getKey(), amount, Long::sum);
            }
        }
        tickets.clear();
        pot = Math.max(seed, pot - refunded);
        dirty = true;
        flush(true);
        return count;
    }

    public void addPot(long amount) {
        pot = Math.max(0, pot + amount);
        dirty = true;
        flush(true);
    }

    private UUID pick(List<UUID> pool) {
        int total = 0;
        for (UUID u : pool) {
            total += tickets.getOrDefault(u, 0);
        }
        int r = random.nextInt(Math.max(1, total));
        for (UUID u : pool) {
            r -= tickets.getOrDefault(u, 0);
            if (r < 0) {
                return u;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private void play(Player p, String sound) {
        if (sound != null && !sound.isBlank()) {
            p.playSound(p.getLocation(), sound, 0.8f, 1f);
        }
    }

    // ------------------------------------------------------------------ read access

    public String fmt(long number) {
        return NUMBER.format(number);
    }

    public boolean enabled() {
        return enabled;
    }

    public long pot() {
        return pot;
    }

    public long price() {
        return price;
    }

    public int maxTickets() {
        return maxTickets;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public String symbol() {
        return symbol;
    }

    public int winnerCount() {
        return winnerPct.size();
    }

    public List<Integer> buyAmounts() {
        return buyAmounts;
    }

    public List<Win> history() {
        return history;
    }

    public int participants() {
        return tickets.size();
    }

    public int totalTickets() {
        int t = 0;
        for (int v : tickets.values()) {
            t += v;
        }
        return t;
    }

    public int ticketsOf(UUID id) {
        return tickets.getOrDefault(id, 0);
    }

    /** Winning chance in percent with one decimal, e.g. "12.5". */
    public String chance(UUID id) {
        int total = totalTickets();
        int mine = ticketsOf(id);
        double pct = total == 0 ? 0 : mine * 100.0 / total;
        return String.format(Locale.ROOT, "%.1f", pct);
    }

    /** Countdown text, "-" when there is no schedule. */
    public String nextText() {
        if (nextDraw <= 0) {
            return plugin.messages().raw("time.none");
        }
        return TimeUtil.left(plugin, nextDraw - System.currentTimeMillis());
    }

    /** Clock text like "Rabu 20:00", "-" when there is no schedule. */
    public String nextClock() {
        if (nextDraw <= 0) {
            return plugin.messages().raw("time.none");
        }
        String s = Instant.ofEpochMilli(nextDraw).atZone(zone).format(CLOCK);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
