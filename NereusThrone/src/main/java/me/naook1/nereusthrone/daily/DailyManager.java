package me.naook1.nereusthrone.daily;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import me.naook1.nereusthrone.NereusThrone;
import me.naook1.nereusthrone.util.SmallCaps;
import me.naook1.nereusthrone.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Built-in daily reward with streak (resets at midnight in the configured timezone). */
public final class DailyManager {

    public record Reward(int day, String name, Material icon, List<String> lore, List<String> commands) {
    }

    private final NereusThrone plugin;
    private final TreeMap<Integer, Reward> rewards = new TreeMap<>();
    private boolean enabled;
    private boolean loop;
    private ZoneId zone = ZoneId.systemDefault();

    public DailyManager(NereusThrone plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        boolean sc = c.getBoolean("small-caps.gui", true);
        enabled = c.getBoolean("daily.enabled", true);
        loop = c.getBoolean("daily.loop", true);
        try {
            zone = ZoneId.of(c.getString("daily.timezone", "Asia/Makassar"));
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid daily.timezone, using server timezone.");
            zone = ZoneId.systemDefault();
        }
        rewards.clear();
        ConfigurationSection sec = c.getConfigurationSection("daily.rewards");
        if (sec == null) {
            return;
        }
        for (String key : sec.getKeys(false)) {
            int day;
            try {
                day = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection r = sec.getConfigurationSection(key);
            if (r == null) {
                continue;
            }
            String name = r.getString("name", "Day " + day);
            List<String> lore = new ArrayList<>();
            for (String line : r.getStringList("lore")) {
                lore.add(sc ? SmallCaps.convert(line) : line);
            }
            Material icon = Material.matchMaterial(r.getString("icon", "GOLD_NUGGET"));
            if (icon == null || !icon.isItem()) {
                icon = Material.GOLD_NUGGET;
            }
            rewards.put(day, new Reward(day, sc ? SmallCaps.convert(name) : name, icon, lore,
                    new ArrayList<>(r.getStringList("commands"))));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int maxDay() {
        return rewards.isEmpty() ? 0 : rewards.lastKey();
    }

    public Reward reward(int day) {
        return rewards.get(day);
    }

    private long today() {
        return LocalDate.now(zone).toEpochDay();
    }

    private long lastDay(UUID u) {
        return plugin.data().getLong(u, "daily.last-day", -1L);
    }

    public boolean claimedToday(Player p) {
        return lastDay(p.getUniqueId()) == today();
    }

    /** Day number reached by the last claim (position in the cycle). */
    public int streak(Player p) {
        return (int) plugin.data().getLong(p.getUniqueId(), "daily.streak", 0L);
    }

    /** Streak that is still alive (claimed today or yesterday), otherwise 0. */
    public int currentStreak(Player p) {
        long last = lastDay(p.getUniqueId());
        long t = today();
        return (last == t || last == t - 1) ? streak(p) : 0;
    }

    /** Day the player would claim now, or -1 if already claimed today / no rewards. */
    public int nextDay(Player p) {
        int max = maxDay();
        if (max <= 0 || claimedToday(p)) {
            return -1;
        }
        long last = lastDay(p.getUniqueId());
        int next = (last == today() - 1) ? streak(p) + 1 : 1;
        if (next > max) {
            next = loop ? 1 : max;
        }
        return next;
    }

    public long secondsUntilReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime next = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        return Duration.between(now, next).getSeconds();
    }

    public boolean claim(Player p) {
        if (!enabled) {
            plugin.messages().send(p, "daily.disabled");
            return false;
        }
        if (claimedToday(p)) {
            plugin.messages().send(p, "daily.already",
                    "reset", me.naook1.nereusthrone.util.TimeUtil.format(secondsUntilReset()));
            return false;
        }
        int day = nextDay(p);
        Reward r = rewards.get(day);
        if (r == null) {
            return false;
        }
        plugin.data().set(p.getUniqueId(), "daily.last-day", today());
        plugin.data().set(p.getUniqueId(), "daily.streak", day);
        for (String cmd : r.commands()) {
            String filled = Text.fill(cmd, "player", p.getName(), "day", String.valueOf(day));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    filled.startsWith("/") ? filled.substring(1) : filled);
        }
        plugin.messages().send(p, "daily.claimed", "day", String.valueOf(day), "reward", r.name());
        return true;
    }
}
