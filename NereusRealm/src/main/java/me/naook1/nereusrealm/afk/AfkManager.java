package me.naook1.nereusrealm.afk;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.util.SmallCaps;
import me.naook1.nereusrealm.util.Text;
import me.naook1.nereusrealm.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** AFK state, live display (actionbar/title) and AFK rewards (growing money + random CrazyCrates keys). */
public final class AfkManager {

    private static final class State {
        boolean afk;
        String reason = "";
        long lastActivity = System.currentTimeMillis();
        long afkSeconds;
        long sessionMoney;
        int sessionKeys;
    }

    /** Snapshot for menus, placeholders and the actionbar. */
    public record Info(boolean afk, long seconds, String reason, long nextIn, long nextMoney, int percent,
                       long sessionMoney, int sessionKeys, boolean capped) {
    }

    public record KeyRate(String display, double share, double overall) {
    }

    private record KeyEntry(String crate, String display, double weight, int amount) {
    }

    private final NereusRealm plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final List<KeyEntry> pool = new ArrayList<>();
    private final List<String> moneyCommands = new ArrayList<>();
    private final Random random = new Random();
    private BukkitTask task;

    private boolean enabled;
    private boolean unafkOnActivity;
    private int autoAfkSeconds;
    private int autoAfkMinutes;
    private int maxReason;
    private String defaultReason = "";
    private String autoReason = "";
    private boolean actionbar;
    private boolean titleEnabled;

    private boolean rewardEnabled;
    private int intervalMinutes = 5;
    private int maxRewardMinutes;
    private String rewardSound = "";
    private boolean moneyEnabled;
    private long moneyBase;
    private long moneyStep;
    private long moneyMax;
    private boolean keysEnabled;
    private double keyChance;
    private String keyCommand = "";

    public AfkManager(NereusRealm plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        boolean sc = c.getBoolean("small-caps.messages", true);
        enabled = c.getBoolean("afk.enabled", true);
        unafkOnActivity = c.getBoolean("afk.unafk-on-activity", true);
        autoAfkMinutes = Math.max(0, c.getInt("afk.auto-afk-minutes", 5));
        autoAfkSeconds = autoAfkMinutes * 60;
        maxReason = c.getInt("afk.max-reason-length", 40);
        defaultReason = c.getString("afk.default-reason", "");
        autoReason = c.getString("afk.auto-reason", "");
        if (sc) {
            defaultReason = SmallCaps.convert(defaultReason);
            autoReason = SmallCaps.convert(autoReason);
        }
        actionbar = c.getBoolean("afk.display.actionbar", true);
        titleEnabled = c.getBoolean("afk.display.title", true);

        rewardEnabled = c.getBoolean("afk.reward.enabled", true);
        intervalMinutes = Math.max(1, c.getInt("afk.reward.interval-minutes", 5));
        maxRewardMinutes = c.getInt("afk.reward.max-afk-minutes", 120);
        rewardSound = c.getString("afk.reward.sound", "");

        moneyEnabled = c.getBoolean("afk.reward.money.enabled", true);
        moneyBase = c.getLong("afk.reward.money.base", 1000);
        moneyStep = c.getLong("afk.reward.money.increase-per-cycle", 0);
        moneyMax = c.getLong("afk.reward.money.max", 0);
        moneyCommands.clear();
        moneyCommands.addAll(c.getStringList("afk.reward.money.commands"));

        keysEnabled = c.getBoolean("afk.reward.keys.enabled", true);
        keyChance = c.getDouble("afk.reward.keys.chance", 0);
        keyCommand = c.getString("afk.reward.keys.command", "");
        pool.clear();
        for (Map<?, ?> m : c.getMapList("afk.reward.keys.pool")) {
            Object crate = m.get("crate");
            if (crate == null) {
                continue;
            }
            Object display = m.get("display");
            Object weight = m.get("weight");
            Object amount = m.get("amount");
            String disp = display == null ? String.valueOf(crate) : String.valueOf(display);
            pool.add(new KeyEntry(String.valueOf(crate), sc ? SmallCaps.convert(disp) : disp,
                    weight instanceof Number n ? n.doubleValue() : 1.0,
                    amount instanceof Number n ? n.intValue() : 1));
        }
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------------ settings for menus

    public boolean isEnabled() {
        return enabled;
    }

    public boolean rewardEnabled() {
        return rewardEnabled;
    }

    public boolean moneyEnabled() {
        return rewardEnabled && moneyEnabled;
    }

    public boolean keysEnabled() {
        return rewardEnabled && keysEnabled && !pool.isEmpty();
    }

    public int intervalMinutes() {
        return intervalMinutes;
    }

    public int autoAfkMinutes() {
        return autoAfkMinutes;
    }

    public long moneyBase() {
        return moneyBase;
    }

    public long moneyStep() {
        return moneyStep;
    }

    public long moneyMax() {
        return moneyMax;
    }

    public double keyChance() {
        return keyChance;
    }

    /** Reward for the n-th reward cycle (1 = first). */
    public long moneyForCycle(long cycle) {
        long amount = moneyBase + moneyStep * Math.max(0, cycle - 1);
        if (moneyMax > 0) {
            amount = Math.min(amount, moneyMax);
        }
        return Math.max(0, amount);
    }

    public List<KeyRate> keyRates() {
        double total = 0;
        for (KeyEntry k : pool) {
            total += Math.max(0, k.weight());
        }
        List<KeyRate> out = new ArrayList<>();
        if (total <= 0) {
            return out;
        }
        for (KeyEntry k : pool) {
            double share = Math.max(0, k.weight()) / total * 100.0;
            out.add(new KeyRate(k.display(), share, share * keyChance / 100.0));
        }
        return out;
    }

    public static String fmt(long number) {
        return NumberFormat.getIntegerInstance(Locale.US).format(number);
    }

    // ------------------------------------------------------------------ state

    private State state(UUID uuid) {
        return states.computeIfAbsent(uuid, k -> new State());
    }

    public boolean isAfk(Player p) {
        State s = states.get(p.getUniqueId());
        return s != null && s.afk;
    }

    public void remove(UUID uuid) {
        states.remove(uuid);
    }

    public Info info(Player p) {
        State s = states.get(p.getUniqueId());
        boolean afk = s != null && s.afk;
        long secs = afk ? s.afkSeconds : 0;
        long interval = intervalMinutes * 60L;
        long nextIn = interval - (secs % interval);
        long cycle = secs / interval + 1;
        long nextMoney = moneyEnabled() ? moneyForCycle(cycle) : 0;
        int pct = (int) ((secs % interval) * 100 / interval);
        boolean capped = maxRewardMinutes > 0 && (secs + nextIn) / 60 > maxRewardMinutes;
        return new Info(afk, secs, afk ? s.reason : "-", nextIn, nextMoney, pct,
                s == null ? 0 : s.sessionMoney, s == null ? 0 : s.sessionKeys, capped);
    }

    /** Called on any player activity (move, chat, command, interact). Safe from async threads. */
    public void activity(Player p) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> activity(p));
            return;
        }
        if (!enabled || !p.isOnline()) {
            return;
        }
        State s = state(p.getUniqueId());
        s.lastActivity = System.currentTimeMillis();
        if (s.afk && unafkOnActivity) {
            setAfk(p, false, null, false);
        }
    }

    public void toggle(Player p, String reason) {
        State s = state(p.getUniqueId());
        if (s.afk) {
            setAfk(p, false, null, false);
        } else {
            setAfk(p, true, Text.sanitize(reason, maxReason), false);
        }
    }

    public void setAfk(Player p, boolean afk, String reason, boolean auto) {
        State s = state(p.getUniqueId());
        if (s.afk == afk) {
            return;
        }
        s.afk = afk;
        s.lastActivity = System.currentTimeMillis();
        if (afk) {
            s.reason = (reason == null || reason.isBlank()) ? (auto ? autoReason : defaultReason) : reason;
            s.afkSeconds = 0;
            s.sessionMoney = 0;
            s.sessionKeys = 0;
            broadcast("afk.went-afk", "player", p.getName(), "reason", s.reason);
            if (titleEnabled) {
                showTitle(p, s.reason);
            }
        } else {
            long secs = s.afkSeconds;
            String backFrom = s.reason == null || s.reason.isBlank() || s.reason.equals("-") ? defaultReason : s.reason;
            long money = s.sessionMoney;
            int keys = s.sessionKeys;
            s.afkSeconds = 0;
            s.sessionMoney = 0;
            s.sessionKeys = 0;
            broadcast("afk.returned", "player", p.getName(), "time", TimeUtil.format(secs), "reason", backFrom);
            if (actionbar) {
                p.sendActionBar(Component.empty());
            }
            if (money > 0 || keys > 0) {
                plugin.messages().send(p, "afk.summary", "money", fmt(money),
                        "keys", String.valueOf(keys), "time", TimeUtil.format(secs));
            }
        }
    }

    private void showTitle(Player p, String reason) {
        Component main = Text.parse(plugin.messages().format("afk.title"));
        Component sub = Text.parse(plugin.messages().format("afk.subtitle", "reason", reason));
        p.showTitle(Title.title(main, sub, Title.Times.times(
                Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
    }

    private void broadcast(String key, String... kv) {
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (plugin.data().getBoolean(v.getUniqueId(), "show-afk", true)) {
                plugin.messages().send(v, key, kv);
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    private void tick() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        long interval = intervalMinutes * 60L;
        for (Player p : Bukkit.getOnlinePlayers()) {
            State s = state(p.getUniqueId());
            if (!s.afk) {
                if (autoAfkSeconds > 0
                        && now - s.lastActivity >= autoAfkSeconds * 1000L
                        && !p.hasPermission("nereus.afk.exempt")) {
                    setAfk(p, true, null, true);
                }
                continue;
            }
            s.afkSeconds++;
            if (rewardEnabled && s.afkSeconds % interval == 0) {
                reward(p, s);
            }
            showActionbar(p);
        }
    }

    private void showActionbar(Player p) {
        if (!actionbar) {
            return;
        }
        Info i = info(p);
        String key;
        if (!moneyEnabled()) {
            key = "afk.actionbar-simple";
        } else if (i.capped()) {
            key = "afk.actionbar-capped";
        } else {
            key = "afk.actionbar";
        }
        String line = plugin.messages().format(key,
                "time", TimeUtil.clock(i.seconds()),
                "next_in", TimeUtil.clock(i.nextIn()),
                "money", fmt(i.nextMoney()),
                "reason", i.reason());
        p.sendActionBar(Text.parse(line));
    }

    private void reward(Player p, State s) {
        if (!p.hasPermission("nereus.afk.reward")) {
            return;
        }
        long minutes = s.afkSeconds / 60;
        if (maxRewardMinutes > 0 && minutes > maxRewardMinutes) {
            return;
        }
        long cycle = s.afkSeconds / (intervalMinutes * 60L);
        boolean notify = plugin.data().getBoolean(p.getUniqueId(), "show-rewards", true);
        boolean gave = false;

        if (moneyEnabled) {
            long amount = moneyForCycle(cycle);
            if (amount > 0) {
                for (String cmd : moneyCommands) {
                    dispatch(Text.fill(cmd, "player", p.getName(), "amount", String.valueOf(amount),
                            "minutes", String.valueOf(minutes), "cycle", String.valueOf(cycle)));
                }
                s.sessionMoney += amount;
                if (notify) {
                    plugin.messages().send(p, "afk.reward-money", "amount", fmt(amount),
                            "minutes", String.valueOf(minutes), "cycle", String.valueOf(cycle));
                }
                gave = true;
            }
        }

        if (keysEnabled && !pool.isEmpty() && random.nextDouble() * 100.0 < keyChance) {
            KeyEntry k = pick();
            if (k != null && !keyCommand.isBlank()) {
                dispatch(Text.fill(keyCommand, "player", p.getName(), "crate", k.crate(),
                        "amount", String.valueOf(k.amount())));
                s.sessionKeys += k.amount();
                if (notify) {
                    plugin.messages().send(p, "afk.reward-key", "key", k.display(),
                            "amount", String.valueOf(k.amount()));
                }
                gave = true;
            }
        }

        if (gave && notify && rewardSound != null && !rewardSound.isBlank()) {
            p.playSound(p.getLocation(), rewardSound, 0.8f, 1f);
        }
    }

    private KeyEntry pick() {
        double total = 0;
        for (KeyEntry k : pool) {
            total += Math.max(0, k.weight());
        }
        if (total <= 0) {
            return null;
        }
        double r = random.nextDouble() * total;
        for (KeyEntry k : pool) {
            r -= Math.max(0, k.weight());
            if (r < 0) {
                return k;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private void dispatch(String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
