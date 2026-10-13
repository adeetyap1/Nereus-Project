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

    private void about(CommandSender sender) {
        plugin.messages().sendList(sender, "about",
                "version", plugin.getDescription().getVersion(),
                "developer", plugin.getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("join", "leave", "daily", "kits", "ranks", "feedback",
                    "afk", "redeem", "settings", "setjoin", "setleave", "resetjoin", "resetleave", "about", "help"));
            if (sender.hasPermission("nereus.admin")) {
                subs.add("reload");
                subs.add("menu");
                subs.add("clearcustom");
                subs.add("code");
            }
            return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("code") && sender.hasPermission("nereus.admin")) {
            return new CodeAdmin(plugin).tab(Arrays.copyOfRange(args, 1, args.length));
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
