package me.naook1.nereusbarter.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusbarter.NereusBarter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /barter <player> | accept | deny | cancel | toggle | history | reload */
public final class BarterCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("accept", "deny", "cancel", "toggle", "history", "help");

    private final NereusBarter plugin;

    public BarterCommand(NereusBarter plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var m = plugin.messages();
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("barter.admin")) {
                m.send(sender, "no-permission");
            } else {
                plugin.reloadAll();
                m.send(sender, "reloaded");
            }
            return true;
        }
        if (!(sender instanceof Player p)) {
            m.send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("barter.use")) {
            m.send(p, "no-permission");
            return true;
        }
        var bm = plugin.barter();
        switch (sub) {
            case "help", "?" -> m.sendList(p, "usage");
            case "accept", "terima" -> bm.accept(p);
            case "deny", "tolak" -> bm.deny(p);
            case "toggle" -> bm.toggle(p);
            case "history", "riwayat" -> bm.history(p);
            case "cancel", "batal" -> {
                if (bm.sessionOf(p) == null) {
                    m.send(p, "no-session");
                } else {
                    bm.cancelFor(p, "reason-cmd");
                }
            }
            default -> {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    m.send(p, "not-found");
                } else {
                    bm.request(p, target);
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
        List<String> options = new ArrayList<>(SUBS);
        if (sender.hasPermission("barter.admin")) {
            options.add("reload");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(sender)) {
                options.add(p.getName());
            }
        }
        return StringUtil.copyPartialMatches(args[0], options, new ArrayList<>());
    }
}
