package id.nereusmc.totemstack;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CommandManager implements CommandExecutor, TabCompleter {

    private static final String USAGE = "/nereustotem [reload|give <player> <amount>|check <player>|stats]";

    private final Main plugin;

    public CommandManager(Main plugin) {
        this.plugin = plugin;
    }

    private ConfigManager cfg() {
        return plugin.config();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(cfg().message("usage", "%usage%", USAGE));
                return true;
            }
            plugin.gui().open(player, GUIManager.MenuType.MAIN);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!requireAdmin(sender)) {
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(cfg().message("reloaded"));
            }
            case "give" -> give(sender, args);
            case "check" -> check(sender, args);
            case "stats" -> stats(sender);
            default -> sender.sendMessage(cfg().message("usage", "%usage%", USAGE));
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(cfg().message("usage", "%usage%", "/nereustotem give <player> <amount>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(cfg().message("player-not-found", "%player%", args[1]));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(cfg().message("invalid-amount"));
            return;
        }
        if (amount < 1 || amount > TotemManager.MAX_GIVE) {
            sender.sendMessage(cfg().message("invalid-amount"));
            return;
        }
        plugin.totems().give(target, amount);
        sender.sendMessage(cfg().message("given", "%amount%", String.valueOf(amount), "%player%", target.getName()));
    }

    private void check(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(cfg().message("usage", "%usage%", "/nereustotem check <player>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(cfg().message("player-not-found", "%player%", args[1]));
            return;
        }
        sender.sendMessage(cfg().message("check", "%player%", target.getName(),
                "%amount%", String.valueOf(plugin.totems().count(target))));
    }

    private void stats(CommandSender sender) {
        if (!sender.hasPermission(GUIManager.PERM_GUI)) {
            sender.sendMessage(cfg().message("no-permission"));
            return;
        }
        StatisticsManager stats = plugin.statistics();
        sender.sendMessage(cfg().prefixed("&7Total activations: &e" + stats.totalActivations()));
        sender.sendMessage(cfg().prefixed("&7Totems consumed: &e" + stats.totalConsumed()));
        List<StatisticsManager.Snapshot> top = stats.top(5);
        int rank = 1;
        for (StatisticsManager.Snapshot s : top) {
            String name = s.name() == null ? s.uuid().toString().substring(0, 8) : s.name();
            sender.sendMessage(cfg().prefixed("&6#" + rank++ + " &f" + name + " &8- &e" + s.activations()));
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission(GUIManager.PERM_ADMIN)) {
            return true;
        }
        sender.sendMessage(cfg().message("no-permission"));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission(GUIManager.PERM_ADMIN)) {
                for (String sub : List.of("reload", "give", "check")) {
                    if (sub.startsWith(prefix)) {
                        out.add(sub);
                    }
                }
            }
            if (sender.hasPermission(GUIManager.PERM_GUI) && "stats".startsWith(prefix)) {
                out.add("stats");
            }
        } else if (args.length == 2 && sender.hasPermission(GUIManager.PERM_ADMIN)
                && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("check"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")
                && sender.hasPermission(GUIManager.PERM_ADMIN)) {
            out.addAll(List.of("1", "16", "64"));
        }
        return out;
    }
}
