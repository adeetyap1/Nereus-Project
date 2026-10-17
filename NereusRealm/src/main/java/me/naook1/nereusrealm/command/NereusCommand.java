package me.naook1.nereusrealm.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.joinleave.CustomMessages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /nereus - opens the menu and handles sub commands. */
public final class NereusCommand implements TabExecutor {

    private final NereusRealm plugin;

    public NereusCommand(NereusRealm plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("nereus.admin")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.messages().send(sender, "reloaded");
                return true;
            }
            case "code", "codes" -> {
                if (!sender.hasPermission("nereus.admin")) {
                    plugin.messages().send(sender, "no-permission");
                } else {
                    new CodeAdmin(plugin).handle(sender, Arrays.copyOfRange(args, 1, args.length));
                }
                return true;
            }
            case "setpoint", "delpoint", "points" -> {
                points(sender, sub, args);
                return true;
            }
            case "give" -> {
                if (!sender.hasPermission("nereus.admin")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
                if (target == null) {
                    plugin.messages().send(sender, "player-not-found");
                } else {
                    plugin.gadgets().ensure(target, true);
                    plugin.messages().send(sender, "gadget.given", "player", target.getName());
                }
                return true;
            }
            case "clearcustom" -> {
                clearCustom(sender, args);
                return true;
            }
            case "about", "info" -> {
                about(sender);
                return true;
            }
            case "help", "?" -> {
                plugin.messages().sendList(sender, "help");
                return true;
            }
            default -> {
            }
        }

        if (!(sender instanceof Player p)) {
            about(sender);
            return true;
        }
        if (!p.hasPermission("nereus.use")) {
            plugin.messages().send(p, "no-permission");
            return true;
        }

        switch (sub) {
            case "" -> plugin.menus().open(p, "main");
            case "join" -> plugin.menus().open(p, "join-styles");
            case "leave" -> plugin.menus().open(p, "leave-styles");
            case "daily" -> plugin.menus().open(p, "daily");
            case "settings" -> plugin.menus().open(p, "settings");
            case "ranks", "rank" -> plugin.menus().open(p, "ranks");
            case "feedback", "rating", "review" -> plugin.menus().open(p, "feedback");
            case "redeem" -> {
                if (args.length < 2) {
                    plugin.prompts().beginRedeem(p);
                } else {
                    plugin.redeem().claim(p, args[1]);
                }
            }
            case "toggle", "gadget" -> plugin.gadgets().toggle(p);
            case "icon", "icons" -> icons(p, args);
            case "kits" -> p.performCommand(plugin.getConfig().getString("kits.command", "kit"));
            case "afk" -> afk(p, args);
            case "setjoin", "setleave" -> setCustom(p, sub.equals("setjoin"), args);
            case "resetjoin", "resetleave" -> {
                plugin.custom().reset(p, sub.equals("resetjoin"));
                plugin.messages().send(p, "custom.reset");
            }
            case "menu" -> {
                if (args.length < 2) {
                    plugin.menus().open(p, "main");
                } else if (p.hasPermission("nereus.admin")) {
                    plugin.menus().open(p, args[1]);
                } else {
                    plugin.messages().send(p, "no-permission");
                }
            }
            default -> plugin.messages().sendList(p, "help");
        }
        return true;
    }

    /** /nereus afk opens the AFK menu, /nereus afk <alasan> toggles AFK. */
    private void afk(Player p, String[] args) {
        if (!p.hasPermission("nereus.afk")) {
            plugin.messages().send(p, "no-permission");
        } else if (!plugin.afk().isEnabled()) {
            plugin.messages().send(p, "afk.disabled");
        } else if (args.length < 2) {
            plugin.menus().open(p, "afk");
        } else {
            plugin.afk().toggle(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        }
    }

    private void setCustom(Player p, boolean join, String[] args) {
        if (args.length < 2) {
            plugin.messages().sendList(p, "help");
            return;
        }
        String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        CustomMessages.Check c = plugin.custom().set(p, join, text);
        if (!c.ok()) {
            plugin.custom().sendError(p, c);
            return;
        }
        plugin.messages().send(p, "custom.saved");
        plugin.styles().previewCurrent(p, join);
    }

    private void clearCustom(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nereus.admin")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().sendList(sender, "help");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        plugin.custom().reset(target.getUniqueId(), true);
        plugin.custom().reset(target.getUniqueId(), false);
        plugin.messages().send(sender, "custom.cleared", "player", args[1]);
    }

    private void points(CommandSender sender, String sub, String[] args) {
        var m = plugin.messages();
        if (!sender.hasPermission("nereus.admin")) {
            m.send(sender, "no-permission");
            return;
        }
        if (sub.equals("points")) {
            m.send(sender, "point.list", "points", String.join(", ", plugin.points().names()));
            return;
        }
        if (args.length < 2) {
            m.send(sender, "point.usage");
            return;
        }
        String name = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("setpoint")) {
            if (!(sender instanceof Player p)) {
                m.send(sender, "players-only");
                return;
            }
            plugin.points().set(name, p.getLocation());
            m.send(sender, "point.set", "point", name);
        } else if (plugin.points().remove(name)) {
            m.send(sender, "point.deleted", "point", name);
        } else {
            m.send(sender, "point.missing", "point", name);
        }
    }

    private void about(CommandSender sender) {
        plugin.messages().sendList(sender, "about",
                "version", plugin.getDescription().getVersion(),
                "developer", plugin.getConfig().getString("developer", "naook1/adityap"));
    }

    private void applyIcons(Player p) {
        if (!p.hasPermission("nereus.admin")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            plugin.messages().send(p, "icon.no-luckperms");
            return;
        }
        int priority = 100;
        int count = 0;
        for (java.util.Map.Entry<String, String> e : plugin.icons().groupIcons().entrySet()) {
            String g = e.getKey();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp creategroup " + g);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp group " + g + " meta setprefix " + (g.equals("default") ? 10 : priority) + " \"" + e.getValue() + " \"");
            if (!g.equals("default")) {
                priority -= 10;
            }
            count++;
        }
        plugin.messages().send(p, "icon.applied", "count", String.valueOf(count));
    }

    private void icons(Player p, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("apply")) {
            applyIcons(p);
            return;
        }
        if (plugin.icons().ids().isEmpty()) {
            plugin.messages().send(p, "icon.none");
            return;
        }
        plugin.messages().send(p, "icon.header");
        for (String id : plugin.icons().ids()) {
            plugin.messages().send(p, "icon.line", "id", id, "icon", plugin.icons().icon(id));
        }
        String mine = plugin.icons().rank(p);
        if (mine == null) {
            plugin.messages().send(p, "icon.no-rank");
        } else {
            plugin.messages().send(p, "icon.me", "icon", mine);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("join", "leave", "daily", "kits", "ranks", "feedback",
                    "afk", "redeem", "settings", "toggle", "icon", "setjoin", "setleave", "resetjoin", "resetleave", "about", "help"));
            if (sender.hasPermission("nereus.admin")) {
                subs.add("reload");
                subs.add("menu");
                subs.add("clearcustom");
                subs.add("code");
                subs.add("give");
                subs.add("setpoint");
                subs.add("delpoint");
                subs.add("points");
            }
            return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("code") && sender.hasPermission("nereus.admin")) {
            return new CodeAdmin(plugin).tab(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2 && sender.hasPermission("nereus.admin")
                && (args[0].equalsIgnoreCase("setpoint") || args[0].equalsIgnoreCase("delpoint"))) {
            List<String> names = plugin.points().names();
            if (args[0].equalsIgnoreCase("setpoint")) {
                names.addAll(List.of("gacha", "survival", "rtp"));
            }
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("clearcustom") && sender.hasPermission("nereus.admin")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }
        return List.of();
    }
}
