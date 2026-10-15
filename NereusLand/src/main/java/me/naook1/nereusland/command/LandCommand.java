package me.naook1.nereusland.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusland.NereusLand;
import me.naook1.nereusland.RedProtectHook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /landbar [toggle] for players, /landbar reload|debug for admins. */
public final class LandCommand implements TabExecutor {

    private final NereusLand plugin;

    public LandCommand(NereusLand plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var m = plugin.messages();
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("nereusland.admin")) {
                    m.send(sender, "no-permission");
                } else {
                    plugin.reloadAll();
                    m.send(sender, "reloaded");
                }
            }
            case "debug" -> {
                if (!sender.hasPermission("nereusland.admin")) {
                    m.send(sender, "no-permission");
                    return true;
                }
                sender.sendMessage("NereusLand " + plugin.getDescription().getVersion());
                sender.sendMessage("RedProtect: " + plugin.hook().status());
                sender.sendMessage("Bossbar aktif: " + plugin.bars().active());
                sender.sendMessage("Error terakhir: " + plugin.hook().lastError());
                if (sender instanceof Player p) {
                    RedProtectHook.Land land = plugin.hook().landAt(p);
                    sender.sendMessage("Land di posisimu: " + (land == null ? "-"
                            : land.name() + " | owner " + land.owner() + " | peranmu " + land.role()));
                }
            }
            default -> {
                if (!(sender instanceof Player p)) {
                    m.send(sender, "players-only");
                } else if (!p.hasPermission("nereusland.use")) {
                    m.send(p, "no-permission");
                } else {
                    m.send(p, plugin.bars().toggle(p) ? "toggle-on" : "toggle-off");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> subs = new ArrayList<>(List.of("toggle"));
        if (sender.hasPermission("nereusland.admin")) {
            subs.addAll(List.of("reload", "debug"));
        }
        return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
    }
}
