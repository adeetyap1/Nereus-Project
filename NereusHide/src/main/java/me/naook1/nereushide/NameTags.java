package me.naook1.nereushide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Hides the name above players' heads during the game with a scoreboard team (name tag visibility: never) and
 * gives every player back their previous team (rank prefix teams etc.) afterwards. Main thread only.
 */
public final class NameTags {

    private static final String TEAM = "nereushide";

    private final NereusHide plugin;
    private final Set<String> hidden = new HashSet<>();
    private final Map<String, String> previous = new HashMap<>();

    public NameTags(NereusHide plugin) {
        this.plugin = plugin;
    }

    private Scoreboard board() {
        return Bukkit.getScoreboardManager().getMainScoreboard();
    }

    /** Removes a leftover team from a crash. Called on enable. */
    public void purge() {
        Team t = board().getTeam(TEAM);
        if (t != null) {
            t.unregister();
        }
        hidden.clear();
        previous.clear();
    }

    public void hide(Player p) {
        if (!plugin.settings().hideNametags()) {
            return;
        }
        try {
            Scoreboard sb = board();
            Team t = sb.getTeam(TEAM);
            if (t == null) {
                t = sb.registerNewTeam(TEAM);
            }
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            String name = p.getName();
            Team current = sb.getEntryTeam(name);
            if (current != null && !current.getName().equals(TEAM)) {
                previous.put(name, current.getName());
            }
            t.addEntry(name);
            hidden.add(name);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not hide the name tag of " + p.getName() + ": " + e.getMessage());
        }
    }

    /** Works for offline players too (by name). */
    public void show(String name) {
        if (!hidden.remove(name)) {
            return;
        }
        try {
            Scoreboard sb = board();
            Team t = sb.getTeam(TEAM);
            if (t != null) {
                t.removeEntry(name);
            }
            String prev = previous.remove(name);
            if (prev != null) {
                Team pt = sb.getTeam(prev);
                if (pt != null) {
                    pt.addEntry(name);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not restore the name tag of " + name + ": " + e.getMessage());
        }
    }

    public void showAll() {
        for (String name : new ArrayList<>(hidden)) {
            show(name);
        }
        Team t = board().getTeam(TEAM);
        if (t != null && t.getEntries().isEmpty()) {
            t.unregister();
        }
    }
}
