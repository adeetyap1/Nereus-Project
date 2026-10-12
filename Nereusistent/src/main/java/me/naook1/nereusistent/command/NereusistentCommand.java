package me.naook1.nereusistent.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusistent.Nereusistent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /nereusistent [give [player] | reload | about] */
public final class NereusistentCommand implements TabExecutor {

    private final Nereusistent plugin;

    public NereusistentCommand(Nereusistent plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("nereusistent.admin")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.messages().send(sender, "reloaded");
            }
            case "give" -> {
                if (!sender.hasPermission("nereusistent.admin")) {
                    plugin.messages().send(sender, "no-permission");
                    return true;
                }
                Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    plugin.messages().send(sender, "not-found");
                    return true;
                }
                plugin.gadgets().ensure(target, true);
                plugin.messages().send(sender, "given", "player", target.getName());
            }
            case "about", "info" -> about(sender);
            default -> {
                if (sender instanceof Player p && p.hasPermission("nereusistent.use")) {
                    plugin.gadgets().open(p);
                } else {
                    about(sender);
                }
            }
        }
        return true;
    }

    private void about(CommandSender sender) {
        plugin.messages().sendList(sender, "about",
                "version", plugin.getDescription().getVersion(),
                "developer", plugin.getConfig().getString("developer", "naook1/adityap"),
                "server_name", plugin.getConfig().getString("server-name", "Nereus Throne"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("about"));
            if (sender.hasPermission("nereusistent.admin")) {
                subs.add("give");
                subs.add("reload");
            }
            return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
        }
        return List.of();
    }
}
