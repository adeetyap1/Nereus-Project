package me.naook1.nereusrealm.redeem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Redeem codes: admin makes a code with expiry + custom rewards, every player can claim it once.
 * Codes live in codes.yml (hand editable, /nereus code reload re-reads it).
 */
public final class RedeemManager {

    public static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{3,32}");
    private static final Pattern DURATION_PART = Pattern.compile("(\\d+)([wdhms])");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** One redeem code. Mutated only on the main thread. */
    public static final class Code {
        public final String id;
        public boolean enabled = true;
        public long expires;          // epoch millis, 0 = never
        public int maxUses;           // total claims allowed, 0 = unlimited
        public long money;
        public String text = "";
        public final List<String> commands = new ArrayList<>();
        public final Set<UUID> used = new LinkedHashSet<>();

        Code(String id) {
            this.id = id;
        }

        public boolean expired(long now) {
            return expires > 0 && now >= expires;
        }

        public boolean full() {
            return maxUses > 0 && used.size() >= maxUses;
        }
    }

    private record Attempts(int fails, long lockedUntil) {
    }

    private final NereusRealm plugin;
    private final File file;
    private final Object writeLock = new Object();
    private final Map<String, Code> codes = new LinkedHashMap<>();
    private final Map<UUID, Attempts> attempts = new ConcurrentHashMap<>();
    private boolean dirty;
    private boolean enabled = true;
    private String moneyCommand = "eco give {player} {amount}";
    private String sound = "entity.player.levelup";
    private int maxFails = 5;
    private int lockSeconds = 300;
    private ZoneId zone = ZoneId.systemDefault();

    public RedeemManager(NereusRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "codes.yml");
    }

    // ------------------------------------------------------------------ load / save

    public void reload() {
        var c = plugin.getConfig();
        enabled = c.getBoolean("redeem.enabled", true);
        moneyCommand = c.getString("redeem.money-command", "eco give {player} {amount}");
        sound = c.getString("redeem.sound", "entity.player.levelup");
        maxFails = Math.max(1, c.getInt("redeem.max-fails", 5));
        lockSeconds = Math.max(10, c.getInt("redeem.lock-seconds", 300));
        try {
            zone = ZoneId.of(c.getString("redeem.timezone", "Asia/Makassar"));
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid redeem.timezone, using server timezone.");
            zone = ZoneId.systemDefault();
        }
        load();
    }

    private void load() {
        codes.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("codes");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            String id = key.toUpperCase(Locale.ROOT);
            if (s == null || !VALID.matcher(id).matches()) {
                plugin.getLogger().warning("codes.yml: skipped invalid code '" + key + "'");
                continue;
            }
            Code code = new Code(id);
            code.enabled = s.getBoolean("enabled", true);
            code.maxUses = Math.max(0, s.getInt("max-uses", 0));
            code.money = Math.max(0, s.getLong("money", 0));
            code.text = s.getString("text", "");
            code.commands.addAll(s.getStringList("commands"));
            String exp = s.getString("expires", "never");
            long ms = parseStamp(exp);
            if (ms < 0) {
                plugin.getLogger().warning("codes.yml: bad 'expires' for " + id + " ('" + exp
                        + "'), use yyyy-MM-dd HH:mm or never. Code disabled.");
                code.enabled = false;
                ms = 0;
            }
            code.expires = ms;
            for (String u : s.getStringList("used-by")) {
                try {
                    code.used.add(UUID.fromString(u));
                } catch (IllegalArgumentException ignored) {
                    // skip bad uuid
                }
            }
            codes.put(id, code);
        }
    }

    public void flush(boolean async) {
        if (!dirty) {
            return;
        }
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        y.options().header("codes.yml - NereusRealm redeem codes (developer: naook1/adityap)\n"
                + "Bisa diedit manual lalu /nereus code reload. expires: 'yyyy-MM-dd HH:mm' atau never.\n"
                + "commands: dijalankan console, {player} = nama pemain, {code} = kode.");
        for (Code c : codes.values()) {
            String p = "codes." + c.id + ".";
            y.set(p + "enabled", c.enabled);
            y.set(p + "expires", c.expires > 0 ? stamp(c.expires) : "never");
            y.set(p + "max-uses", c.maxUses);
            y.set(p + "money", c.money);
            y.set(p + "text", c.text);
            y.set(p + "commands", new ArrayList<>(c.commands));
            List<String> ids = new ArrayList<>();
            for (UUID u : c.used) {
                ids.add(u.toString());
            }
            y.set(p + "used-by", ids);
        }
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
                    plugin.getLogger().warning("Could not save codes.yml: " + e.getMessage());
                }
            }
        };
        if (async && plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    private void touch() {
        dirty = true;
    }

    // ------------------------------------------------------------------ time helpers

    public String stamp(long millis) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone).format(STAMP);
    }

    /** "never" -> 0, "yyyy-MM-dd[ HH:mm]" -> millis, anything else -> -1. */
    private long parseStamp(String s) {
        if (s == null) {
            return 0;
        }
        String t = s.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("never") || t.equals("0")
                || t.equalsIgnoreCase("selamanya") || t.equalsIgnoreCase("permanent")) {
            return 0;
        }
        try {
            return LocalDateTime.parse(t, STAMP).atZone(zone).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            // try date only
        }
        try {
            return LocalDate.parse(t).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 60_000L;
        } catch (Exception ignored) {
            return -1;
        }
    }

    /** "1d12h" -> seconds, -1 if not a duration. */
    public static long parseDuration(String s) {
        if (s == null || s.isBlank()) {
            return -1;
        }
        String t = s.trim().toLowerCase(Locale.ROOT);
        Matcher m = DURATION_PART.matcher(t);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) {
                return -1;
            }
            end = m.end();
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "w" -> 604_800L;
                case "d" -> 86_400L;
                case "h" -> 3_600L;
                case "m" -> 60L;
                default -> 1L;
            };
        }
        return end == t.length() && total > 0 ? total : -1;
    }

    /**
     * Admin input -> expiry millis: duration (7d, 12h), "never", or a date. Returns Long.MIN_VALUE if invalid.
     * Durations are rounded up to the next minute because the file stores minutes.
     */
    public long parseExpiry(String input) {
        long dur = parseDuration(input);
        if (dur > 0) {
            long ms = System.currentTimeMillis() + dur * 1000L;
            return ((ms + 59_999L) / 60_000L) * 60_000L;
        }
        long ms = parseStamp(input);
        return ms < 0 ? Long.MIN_VALUE : ms;
    }

    /** "6 hari 3 jam" style remaining time. */
    public static String left(long millis) {
        long s = Math.max(0, millis / 1000);
        long d = s / 86_400;
        long h = (s % 86_400) / 3_600;
        long m = (s % 3_600) / 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) {
            sb.append(d).append(" hari ");
        }
        if (h > 0) {
            sb.append(h).append(" jam ");
        }
        if (m > 0 && d == 0) {
            sb.append(m).append(" menit ");
        }
        if (sb.length() == 0) {
            sb.append(s).append(" detik");
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ admin API

    public boolean isEnabled() {
        return enabled;
    }

    public Code get(String id) {
        return id == null ? null : codes.get(id.toUpperCase(Locale.ROOT));
    }

    public List<Code> all() {
        return new ArrayList<>(codes.values());
    }

    public List<String> ids() {
        return new ArrayList<>(codes.keySet());
    }

    /** Returns null if created, else a message key explaining why not. */
    public String create(String id, long expires, int maxUses) {
        String up = id.toUpperCase(Locale.ROOT);
        if (!VALID.matcher(up).matches()) {
            return "redeem.admin.bad-code";
        }
        if (codes.containsKey(up)) {
            return "redeem.admin.exists";
        }
        Code c = new Code(up);
        c.expires = expires;
        c.maxUses = Math.max(0, maxUses);
        codes.put(up, c);
        touch();
        flush(true);
        return null;
    }

    public boolean delete(String id) {
        boolean removed = codes.remove(id.toUpperCase(Locale.ROOT)) != null;
        if (removed) {
            touch();
            flush(true);
        }
        return removed;
    }

    /** Call after changing a Code from the admin command. */
    public void changed() {
        touch();
        flush(true);
    }

    public String status(Code c) {
        long now = System.currentTimeMillis();
        if (!c.enabled) {
            return "&cMati";
        }
        if (c.expired(now)) {
            return "&cKedaluwarsa";
        }
        if (c.full()) {
            return "&6Habis";
        }
        return "&aAktif";
    }

    // ------------------------------------------------------------------ player claim

    private boolean locked(Player p) {
        Attempts a = attempts.get(p.getUniqueId());
        if (a == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (a.lockedUntil() > now) {
            plugin.messages().send(p, "redeem.locked", "time", left(a.lockedUntil() - now));
            return true;
        }
        if (a.lockedUntil() != 0) {
            attempts.remove(p.getUniqueId());
        }
        return false;
    }

    private void fail(Player p) {
        UUID id = p.getUniqueId();
        Attempts a = attempts.getOrDefault(id, new Attempts(0, 0));
        int fails = a.fails() + 1;
        if (fails >= maxFails) {
            attempts.put(id, new Attempts(0, System.currentTimeMillis() + lockSeconds * 1000L));
            plugin.messages().send(p, "redeem.locked", "time", left(lockSeconds * 1000L));
        } else {
            attempts.put(id, new Attempts(fails, 0));
        }
    }

    /** Claims a code for a player. Sends all messages itself. Main thread only. */
    public boolean claim(Player p, String input) {
        if (!enabled) {
            plugin.messages().send(p, "redeem.disabled");
            return false;
        }
        if (!p.hasPermission("nereus.redeem")) {
            plugin.messages().send(p, "no-permission");
            return false;
        }
        if (locked(p)) {
            return false;
        }
        String id = input == null ? "" : input.trim().toUpperCase(Locale.ROOT);
        Code c = VALID.matcher(id).matches() ? codes.get(id) : null;
        if (c == null) {
            plugin.messages().send(p, "redeem.invalid");
            fail(p);
            return false;
        }
        long now = System.currentTimeMillis();
        if (!c.enabled) {
            plugin.messages().send(p, "redeem.invalid");
            fail(p);
            return false;
        }
        if (c.expired(now)) {
            plugin.messages().send(p, "redeem.expired", "code", c.id);
            return false;
        }
        if (c.used.contains(p.getUniqueId())) {
            plugin.messages().send(p, "redeem.already", "code", c.id);
            return false;
        }
        if (c.full()) {
            plugin.messages().send(p, "redeem.full", "code", c.id);
            return false;
        }

        // mark first, so a failing reward command can never allow a second claim
        c.used.add(p.getUniqueId());
        attempts.remove(p.getUniqueId());
        touch();
        flush(true);

        if (c.money > 0) {
            run(p, c, moneyCommand.replace("{amount}", String.valueOf(c.money)));
        }
        for (String cmd : c.commands) {
            run(p, c, cmd);
        }
        String reward = c.text == null || c.text.isBlank()
                ? (c.money > 0 ? "$" + String.format(Locale.US, "%,d", c.money) : "Hadiah spesial")
                : c.text;
        plugin.messages().send(p, "redeem.success", "code", c.id, "reward", reward);
        if (!sound.isBlank()) {
            p.playSound(p.getLocation(), sound, 0.8f, 1f);
        }
        plugin.getLogger().info(p.getName() + " redeemed code " + c.id
                + " (" + c.used.size() + (c.maxUses > 0 ? "/" + c.maxUses : "") + ")");
        return true;
    }

    private void run(Player p, Code c, String raw) {
        String filled = Text.fill(raw, "player", p.getName(), "code", c.id);
        String cmd = filled.startsWith("/") ? filled.substring(1) : filled;
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } catch (Exception e) {
            plugin.getLogger().warning("Redeem " + c.id + ": command failed '" + cmd + "': " + e.getMessage());
        }
    }
}
