package me.naook1.nereusthrone.afk;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import me.naook1.nereusthrone.NereusThrone;
import me.naook1.nereusthrone.util.SmallCaps;
import me.naook1.nereusthrone.util.Text;
import me.naook1.nereusthrone.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** AFK state + AFK rewards (money that grows every cycle, random CrazyCrates keys by weight). */
public final class AfkManager {

    public static final class State {
        boolean afk;
        String reason = "";
        long lastActivity = System.currentTimeMillis();
        long afkSeconds;
    }

    private record KeyEntry(String crate, String display, double weight, int amount) {
    }

    private final NereusThrone plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final List<KeyEntry> pool = new ArrayList<>();
    private final List<String> moneyCommands = new ArrayList<>();
    private final Random random = new Random();
    private BukkitTask task;

    private boolean enabled;
    private boolean unafkOnActivity;
    private int autoAfkSeconds;
    private int maxReason;
    private String defaultReason = "";
    private String autoReason = "";

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

    public AfkManager(NereusThrone plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        boolean sc = c.getBoolean("small-caps.messages", true);
        enabled = c.getBoolean("afk.enabled", true);
        unafkOnActivity = c.getBoolean("afk.unafk-on-activity", true);
        autoAfkSeconds = Math.max(0, c.getInt("afk.auto-afk-minutes", 5)) * 60;
        maxReason = c.getInt("afk.max-reason-length", 40);
        defaultReason = c.getString("afk.default-reason", "");
        autoReason = c.getString("afk.auto-reason", "");
        if (sc) {
            defaultReason = SmallCaps.convert(defaultReason);
            autoReason = SmallCaps.convert(autoReason);
        }

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

    public boolean isEnabled() {
        return enabled;
    }

    public int intervalMinutes() {
        return intervalMinutes;
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
            broadcast("afk.on", "player", p.getName(), "reason", s.reason);
        } else {
            long secs = s.afkSeconds;
            s.afkSeconds = 0;
            broadcast("afk.off", "player", p.getName(), "time", TimeUtil.format(secs));
        }
    }

    private void broadcast(String key, String... kv) {
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (plugin.data().getBoolean(v.getUniqueId(), "show-afk", true)) {
                plugin.messages().send(v, key, kv);
            }
        }
    }

    private void tick() {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        int interval = intervalMinutes * 60;
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
        }
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
            long amount = moneyBase + moneyStep * (cycle - 1);
            if (moneyMax > 0) {
                amount = Math.min(amount, moneyMax);
            }
            if (amount > 0) {
                for (String cmd : moneyCommands) {
                    dispatch(Text.fill(cmd, "player", p.getName(), "amount", String.valueOf(amount),
                            "minutes", String.valueOf(minutes), "cycle", String.valueOf(cycle)));
                }
                if (notify) {
                    plugin.messages().send(p, "afk.reward-money",
                            "amount", NumberFormat.getIntegerInstance(Locale.US).format(amount),
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
