package me.naook1.nereusthrone.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusthrone.NereusThrone;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /nereus - opens the menu and handles sub commands. */
public final class NereusCommand implements TabExecutor {

    private final NereusThrone plugin;

    public NereusCommand(NereusThrone plugin) {
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
            case "kits" -> p.performCommand(plugin.getConfig().getString("kits.command", "kit"));
            case "afk" -> {
                if (!p.hasPermission("nereus.afk")) {
                    plugin.messages().send(p, "no-permission");
                } else if (!plugin.afk().isEnabled()) {
                    plugin.messages().send(p, "afk.disabled");
                } else {
                    plugin.afk().toggle(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                }
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

    private void about(CommandSender sender) {
        plugin.messages().sendList(sender, "about",
                "version", plugin.getDescription().getVersion(),
                "developer", plugin.getConfig().getString("developer", "naook1/adityap"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("join", "leave", "daily", "kits", "settings", "afk", "about", "help"));
            if (sender.hasPermission("nereus.admin")) {
                subs.add("reload");
                subs.add("menu");
            }
            return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
        }
        return List.of();
    }
}
