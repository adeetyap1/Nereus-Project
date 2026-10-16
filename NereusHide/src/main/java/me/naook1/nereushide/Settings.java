package me.naook1.nereushide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

/** Config values (with the admin-menu overrides applied). */
public final class Settings {

    private final NereusHide plugin;

    public Settings(NereusHide plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    public boolean enabled() {
        return c().getBoolean("enabled", true);
    }

    public int minPlayers() {
        return Math.max(2, c().getInt("game.min-players", 3));
    }

    public int maxPlayers() {
        return Math.max(minPlayers(), c().getInt("game.max-players", 20));
    }

    public int lobbyWait() {
        return Math.max(10, c().getInt("game.lobby-wait-seconds", 180));
    }

    public int countdown() {
        return Math.max(5, c().getInt("game.countdown-seconds", 30));
    }

    public int hideTime() {
        return Math.max(10, plugin.data().override("hide-time", c().getInt("game.hide-seconds", 60)));
    }

    public int seekTime() {
        return Math.max(30, plugin.data().override("seek-time", c().getInt("game.seek-seconds", 300)));
    }

    public int resultTime() {
        return Math.max(3, c().getInt("game.result-seconds", 10));
    }

    /** How many seekers for this many players (always at least 1 and leaves at least 1 hider). */
    public int seekerCount(int players) {
        int fixedOverride = plugin.data().override("seekers", 0);
        int n;
        if (fixedOverride > 0) {
            n = fixedOverride;
        } else if (c().getString("seekers.mode", "ratio").toLowerCase(Locale.ROOT).equals("fixed")) {
            n = c().getInt("seekers.fixed", 1);
        } else {
            n = (int) Math.round(players * c().getDouble("seekers.ratio", 0.2));
        }
        return Math.max(1, Math.min(n, Math.max(1, players - 1)));
    }

    public boolean hideNametags() {
        return plugin.getConfig().getBoolean("hide-nametags", true);
    }

    public boolean avoidRepeat() {
        return c().getBoolean("seekers.avoid-repeat", true);
    }

    /** Penalty for the n-th hider that gets found (0-based). The last value repeats. */
    public int penalty(int index) {
        List<Integer> list = c().getIntegerList("points.found-penalties");
        if (list.isEmpty()) {
            return -5;
        }
        return list.get(Math.min(index, list.size() - 1));
    }

    public int pointsPerFind() {
        return c().getInt("points.per-find", 1);
    }

    public boolean returnToEnd() {
        return c().getString("return-to", "saved").equalsIgnoreCase("end");
    }

    public boolean isolateChat() {
        return c().getBoolean("chat.isolate", true);
    }

    public boolean announceServer() {
        return c().getBoolean("announce.server-results", true);
    }

    public List<String> allowedCommands() {
        List<String> out = new ArrayList<>();
        for (String s : c().getStringList("allowed-commands")) {
            out.add(s.toLowerCase(Locale.ROOT).replace("/", ""));
        }
        out.add("hide");
        return out;
    }

    public List<String> rewards(String key) {
        return c().getStringList("rewards." + key);
    }

    public String sound(String key) {
        return c().getString("sounds." + key, "");
    }
}
