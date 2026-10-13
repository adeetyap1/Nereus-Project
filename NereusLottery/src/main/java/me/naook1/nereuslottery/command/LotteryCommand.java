package me.naook1.nereuslottery.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereuslottery.LotteryManager;
import me.naook1.nereuslottery.NereusLottery;
import me.naook1.nereuslottery.gui.LotteryMenu;
import me.naook1.nereuslottery.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /lottery - menu, buy, info, history and admin commands. */
public final class LotteryCommand implements TabExecutor {

    private final NereusLottery plugin;

    public LotteryCommand(NereusLottery plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var m = plugin.messages();
        LotteryManager l = plugin.lottery();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "draw", "cancel", "addpot", "reload" -> {
                if (!sender.hasPermission("lottery.admin")) {
                    m.send(sender, "no-permission");
                    return true;
                }
                admin(sender, sub, args);
                return true;
            }
            case "info" -> {
                info(sender);
                return true;
            }
            case "history" -> {
                history(sender);
                return true;
            }
            case "help", "?" -> {
                m.sendList(sender, "help");
                return true;
            }
            default -> {
            }
        }

        if (!(sender instanceof Player p)) {
            info(sender);
            return true;
        }
        if (!p.hasPermission("lottery.use")) {
            m.send(p, "no-permission");
            return true;
        }
        switch (sub) {
            case "" -> {
                if (!l.enabled()) {
                    m.send(p, "disabled");
                } else {
                    new LotteryMenu(plugin, p).open();
                }
            }
            case "buy", "beli" -> {
                int n = 1;
                if (args.length >= 2) {
                    try {
                        n = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        m.send(p, "invalid-number");
                        return true;
                    }
                }
                l.buy(p, n);
            }
            default -> m.sendList(p, "help");
        }
        return true;
    }

    private void admin(CommandSender s, String sub, String[] args) {
        var m = plugin.messages();
        LotteryManager l = plugin.lottery();
        switch (sub) {
            case "draw" -> l.draw();
            case "cancel" -> m.send(s, "admin.cancelled", "count", String.valueOf(l.cancel()));
            case "addpot" -> {
                long amount;
                try {
                    amount = Long.parseLong(args.length > 1 ? args[1] : "");
                } catch (NumberFormatException e) {
                    m.send(s, "invalid-number");
                    return;
                }
                l.addPot(amount);
                m.send(s, "admin.pot-added", "amount", l.fmt(amount), "pot", l.fmt(l.pot()), "symbol", l.symbol());
            }
            case "reload" -> {
                plugin.reloadAll();
                m.send(s, "reloaded");
            }
            default -> {
            }
        }
    }

    private void info(CommandSender s) {
        LotteryManager l = plugin.lottery();
        var id = s instanceof Player p ? p.getUniqueId() : null;
        plugin.messages().sendList(s, "info",
                "symbol", l.symbol(),
                "pot", l.fmt(l.pot()),
                "price", l.fmt(l.price()),
                "max", String.valueOf(l.maxTickets()),
                "players", String.valueOf(l.participants()),
                "tickets", String.valueOf(l.totalTickets()),
                "mine", id == null ? "-" : String.valueOf(l.ticketsOf(id)),
                "chance", id == null ? "-" : l.chance(id),
                "next", l.nextClock(),
                "time", l.nextText());
    }

    private void history(CommandSender s) {
        var m = plugin.messages();
        LotteryManager l = plugin.lottery();
        if (l.history().isEmpty()) {
            m.send(s, "history.empty");
            return;
        }
        m.send(s, "history.header");
        long now = System.currentTimeMillis();
        int shown = 0;
        for (LotteryManager.Win w : l.history()) {
            if (shown++ >= 10) {
                break;
            }
            m.send(s, "history.line", "player", w.player(), "prize", l.fmt(w.prize()),
                    "symbol", l.symbol(), "ago", TimeUtil.ago(now - w.time()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("buy", "info", "history", "help"));
            if (sender.hasPermission("lottery.admin")) {
                subs.addAll(List.of("draw", "cancel", "addpot", "reload"));
            }
            return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            return StringUtil.copyPartialMatches(args[1], List.of("1", "5", "10"), new ArrayList<>());
        }
        return List.of();
    }
}
