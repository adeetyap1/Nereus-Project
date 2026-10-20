package me.naook1.nereusbank.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusbank.Account;
import me.naook1.nereusbank.BankManager;
import me.naook1.nereusbank.NereusBank;
import me.naook1.nereusbank.Tier;
import me.naook1.nereusbank.util.Fmt;
import me.naook1.nereusbank.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class BankCommand implements CommandExecutor, TabCompleter {

    private final NereusBank plugin;

    public BankCommand(NereusBank plugin) {
        this.plugin = plugin;
    }

    private boolean admin(CommandSender s) {
        return s.hasPermission("nereusbank.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var m = plugin.messages();
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand(sender, args);
        }
        if (!(sender instanceof Player p)) {
            m.send(sender, "player-only");
            return true;
        }
        if (!p.hasPermission("nereusbank.use")) {
            m.send(p, "no-permission");
            return true;
        }
        var bank = plugin.bank();
        if (args.length == 0) {
            if (plugin.getConfig().getBoolean("card.require-card-for-gui", false) && !admin(p)
                    && !plugin.cards().holdsOwnCard(p)) {
                m.send(p, "need-card");
                return true;
            }
            plugin.gui().openMain(p);
            return true;
        }
        Account a = bank.account(p);
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> m.sendList(p, "help");
            case "info", "balance", "bal" -> info(p, a);
            case "deposit", "dep" -> {
                if (args.length < 2) {
                    plugin.gui().openPicker(p, me.naook1.nereusbank.BankGui.Mode.DEPOSIT, null, 0, 0);
                    return true;
                }
                long amt = amount(args[1], Math.min(plugin.economy().wallet(p), bank.depositRoom(a)));
                var r = bank.deposit(p, amt);
                if (r.success()) {
                    m.send(p, "deposit-done", "amount", Fmt.money(amt), "balance", Fmt.money(a.balance));
                } else {
                    m.send(p, r.key(), r.kv());
                }
            }
            case "withdraw", "wd" -> {
                if (args.length < 2) {
                    plugin.gui().openPicker(p, me.naook1.nereusbank.BankGui.Mode.WITHDRAW, null, 0, 0);
                    return true;
                }
                long amt = amount(args[1], a.balance);
                var r = bank.withdraw(p, amt);
                if (r.success()) {
                    m.send(p, "withdraw-done", "amount", Fmt.money(amt), "balance", Fmt.money(a.balance));
                } else {
                    m.send(p, r.key(), r.kv());
                }
            }
            case "transfer", "pay", "send" -> {
                if (args.length < 3) {
                    plugin.gui().openTargets(p, 0);
                    return true;
                }
                Player online = Bukkit.getPlayerExact(args[1]);
                Account to = online != null ? bank.account(online) : bank.findByName(args[1]);
                if (to == null) {
                    m.send(p, "no-account", "player", args[1]);
                    return true;
                }
                long amt = amount(args[2], a.balance);
                long fee = bank.feeFor(a, amt);
                var r = bank.transfer(p, to, amt);
                if (r.success()) {
                    m.send(p, "transfer-done", "amount", Fmt.money(amt), "player", to.name, "fee", Fmt.money(fee));
                } else {
                    m.send(p, r.key(), r.kv());
                }
            }
            case "history", "statement" -> plugin.gui().openHistory(p, 0);
            case "savings", "fd" -> plugin.gui().openSavings(p);
            case "loan" -> {
                if (args.length >= 3 && args[1].equalsIgnoreCase("borrow")) {
                    long amt = Fmt.parse(args[2]);
                    var r = bank.borrow(a, amt);
                    if (r.success()) {
                        m.send(p, "loan-borrowed", "amount", Fmt.money(amt), "owed", Fmt.money(a.loan));
                    } else {
                        m.send(p, r.key(), r.kv());
                    }
                } else {
                    plugin.gui().openLoan(p);
                }
            }
            case "repay" -> {
                if (args.length < 2) {
                    plugin.gui().openLoan(p);
                    return true;
                }
                long amt = amount(args[1], Math.min(a.loan, a.balance));
                long owed = a.loan;
                var r = bank.repay(a, amt);
                if (r.success()) {
                    m.send(p, "loan-repaid", "amount", Fmt.money(Math.min(amt, owed)), "left", Fmt.money(a.loan));
                } else {
                    m.send(p, r.key(), r.kv());
                }
            }
            case "top" -> plugin.gui().openTop(p);
            case "cards", "card" -> plugin.gui().openCards(p);
            default -> m.send(p, "usage");
        }
        return true;
    }

    private long amount(String s, long all) {
        if (s.equalsIgnoreCase("all")) {
            return all;
        }
        if (s.equalsIgnoreCase("half")) {
            return all / 2;
        }
        return Fmt.parse(s);
    }

    private void info(Player p, Account a) {
        Tier t = plugin.bank().tier(a);
        long left = plugin.bank().nextInterestAt(a) - System.currentTimeMillis();
        plugin.messages().sendList(p, "info",
                "player", a.name, "tier", t.label(), "card", String.valueOf(a.cardNo),
                "balance", Fmt.money(a.balance), "wallet", Fmt.money(plugin.economy().wallet(p)),
                "deposits", Fmt.money(a.lockedInDeposits()), "loan", Fmt.money(a.loan),
                "interest", Fmt.money(plugin.bank().interestPreview(a)), "interest_in", Fmt.duration(left));
    }

    // ---------------- admin ----------------

    private boolean adminCommand(CommandSender s, String[] args) {
        var m = plugin.messages();
        if (!admin(s)) {
            m.send(s, "no-permission");
            return true;
        }
        if (args.length < 2) {
            m.send(s, "admin-usage");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        var bank = plugin.bank();
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                m.send(s, "reloaded");
            }
            case "interest" -> {
                for (Account a : bank.all()) {
                    a.lastInterest = 0;
                }
                bank.tick();
                m.send(s, "admin-interest");
            }
            case "give", "take", "set" -> {
                if (args.length < 4) {
                    m.send(s, "admin-usage");
                    return true;
                }
                Account a = bank.findByName(args[2]);
                if (a == null) {
                    m.send(s, "no-account", "player", args[2]);
                    return true;
                }
                long v = Fmt.parse(args[3]);
                if (v < 0) {
                    m.send(s, "invalid-amount");
                    return true;
                }
                if (sub.equals("give")) {
                    bank.adminAdjust(a, v, "give");
                } else if (sub.equals("take")) {
                    bank.adminAdjust(a, -v, "take");
                } else {
                    bank.adminSet(a, v);
                }
                m.send(s, "admin-done", "player", a.name, "balance", Fmt.money(a.balance));
            }
            case "tier" -> {
                if (args.length < 4) {
                    m.send(s, "admin-usage");
                    return true;
                }
                Account a = bank.findByName(args[2]);
                Tier t = bank.tiers().get(args[3].toUpperCase(Locale.ROOT));
                if (a == null || t == null) {
                    m.send(s, "admin-usage");
                    return true;
                }
                bank.setTier(a, t);
                m.send(s, "admin-done", "player", a.name, "balance", Fmt.money(a.balance));
            }
            case "card" -> {
                if (args.length < 4) {
                    m.send(s, "admin-usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[2]);
                Tier t = bank.tiers().get(args[3].toUpperCase(Locale.ROOT));
                if (target == null || t == null) {
                    m.send(s, "admin-usage");
                    return true;
                }
                Account a = bank.account(target);
                plugin.cards().give(target, plugin.cards().create(a, t));
                m.send(s, "admin-done", "player", a.name, "balance", Fmt.money(a.balance));
            }
            case "atm" -> {
                if (!(s instanceof Player p)) {
                    m.send(s, "player-only");
                    return true;
                }
                String op = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
                if (op.equals("list")) {
                    p.sendMessage(Text.parse("&7ATM: &f" + plugin.atms().size()));
                    for (String l : plugin.atms()) {
                        p.sendMessage(Text.parse("&8 - &7" + l));
                    }
                    return true;
                }
                Block b = p.getTargetBlockExact(6);
                if (b == null) {
                    m.send(p, "atm-look");
                    return true;
                }
                if (op.equals("add")) {
                    m.send(p, plugin.addAtm(b) ? "atm-added" : "atm-exists");
                } else if (op.equals("remove")) {
                    m.send(p, plugin.removeAtm(b) ? "atm-removed" : "atm-not-found");
                } else {
                    m.send(p, "admin-usage");
                }
            }
            default -> m.send(s, "admin-usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : List.of("deposit", "withdraw", "transfer", "history", "savings", "loan", "repay",
                    "top", "cards", "info", "help")) {
                out.add(x);
            }
            if (admin(s)) {
                out.add("admin");
            }
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("transfer") || a[0].equalsIgnoreCase("pay"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                out.add(p.getName());
            }
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("deposit") || a[0].equalsIgnoreCase("withdraw")
                || a[0].equalsIgnoreCase("repay"))) {
            out.addAll(List.of("all", "half", "1000", "10k"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("loan")) {
            out.add("borrow");
        } else if (admin(s) && a[0].equalsIgnoreCase("admin")) {
            if (a.length == 2) {
                out.addAll(List.of("give", "take", "set", "tier", "card", "interest", "atm", "reload"));
            } else if (a.length == 3 && a[1].equalsIgnoreCase("atm")) {
                out.addAll(List.of("add", "remove", "list"));
            } else if (a.length == 3) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
            } else if (a.length == 4 && (a[1].equalsIgnoreCase("tier") || a[1].equalsIgnoreCase("card"))) {
                out.addAll(plugin.bank().tiers().keySet());
            }
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
