package me.naook1.nereushide.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereushide.GameManager;
import me.naook1.nereushide.NereusHide;
import me.naook1.nereushide.gui.AdminMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /hide join|leave|status|stats for players; open|start|stop|admin|set*|check|kick|reload for admins. */
public final class HideCommand implements TabExecutor {

    private static final List<String> PLAYER_SUBS = List.of("join", "leave", "status", "stats", "help");
    private static final List<String> ADMIN_SUBS = List.of("open", "start", "stop", "admin", "setlobby", "sethide",
            "setseeker", "setend", "setpos1", "setpos2", "check", "kick", "reload");

    private final NereusHide plugin;

    public HideCommand(NereusHide plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var m = plugin.messages();
        var g = plugin.game();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        if (ADMIN_SUBS.contains(sub)) {
            if (!sender.hasPermission("hide.admin")) {
                m.send(sender, "no-permission");
                return true;
            }
            admin(sender, sub, args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            m.send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("hide.play")) {
            m.send(p, "no-permission");
            return true;
        }
        switch (sub) {
            case "join" -> {
                String err = g.join(p);
                if (err != null) {
                    m.send(p, err);
                }
            }
            case "leave" -> {
                if (g.isIn(p)) {
                    g.leave(p, false);
                } else {
                    m.send(p, "not-in");
                }
            }
            case "status" -> status(p);
            case "stats" -> {
                int[] s = plugin.data().stats(p.getUniqueId());
                m.send(p, "stats", "games", String.valueOf(s[0]), "wins", String.valueOf(s[1]), "finds", String.valueOf(s[2]));
            }
            default -> m.sendList(p, "usage");
        }
        return true;
    }

    private void status(Player p) {
        var m = plugin.messages();
        var g = plugin.game();
        if (g.phase() == GameManager.Phase.IDLE) {
            m.send(p, "status-idle");
            return;
        }
        m.send(p, "status", "phase", g.phase().name(), "n", String.valueOf(g.count()),
                "min", String.valueOf(plugin.settings().minPlayers()), "left", String.valueOf(g.hidersLeft()),
                "time", String.valueOf(g.timeLeft()));
        GameManager.P me = g.get(p.getUniqueId());
        if (me != null) {
            m.send(p, "status-you", "role", m.raw("role." + me.role.name().toLowerCase(Locale.ROOT)), "points", String.valueOf(me.points));
        }
    }

    private void admin(CommandSender s, String sub, String[] args) {
        var m = plugin.messages();
        var g = plugin.game();
        switch (sub) {
            case "open" -> {
                String err = g.open();
                m.send(s, err == null ? "admin-opened" : err);
            }
            case "start" -> {
                String err = g.forceStart();
                if (err != null) {
                    m.send(s, err);
                }
            }
            case "stop" -> {
                g.stop();
                m.send(s, "admin-stopped");
            }
            case "admin" -> {
                if (s instanceof Player p) {
                    new AdminMenu(plugin).open(p);
                } else {
                    m.send(s, "players-only");
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                m.send(s, "reloaded");
            }
            case "check" -> {
                if (plugin.arena().complete() && !plugin.arena().hideInside()) {
                    m.send(s, "arena-hide-outside");
                } else if (plugin.arena().complete()) {
                    m.send(s, "arena-ok");
                } else {
                    m.send(s, "arena-missing", "missing", plugin.arena().missing());
                }
            }
            case "kick" -> {
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (target == null || !g.isIn(target)) {
                    m.send(s, "not-in-game");
                } else {
                    g.leave(target, false);
                    m.send(s, "kicked", "player", target.getName());
                }
            }
            default -> {
                if (!(s instanceof Player p)) {
                    m.send(s, "players-only");
                    return;
                }
                String what = sub.substring(3);     // setlobby -> lobby, setpos1 -> pos1
                plugin.arena().set(what, p.getLocation());
                m.send(p, "set-done", "what", what);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (sender.hasPermission("hide.admin")) {
                subs.addAll(ADMIN_SUBS);
            }
            return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kick")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }
        return List.of();
    }
}
