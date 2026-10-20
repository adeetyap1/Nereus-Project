package me.naook1.nereusbank.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusbank.Account;
import me.naook1.nereusbank.BankManager;
import me.naook1.nereusbank.NereusBank;
import me.naook1.nereusbank.Tier;
import me.naook1.nereusbank.util.Fmt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class CardCommand implements CommandExecutor, TabCompleter {

    private final NereusBank plugin;

    public CardCommand(NereusBank plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "player-only");
            return true;
        }
        if (!p.hasPermission("nereusbank.use")) {
            m.send(p, "no-permission");
            return true;
        }
        if (args.length == 0) {
            plugin.gui().openCards(p);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("replace")) {
            BankManager.Result r = plugin.cards().replace(p);
            if (r.success()) {
                m.send(p, "card-replaced");
            } else {
                m.send(p, r.key(), r.kv());
            }
            return true;
        }
        if (sub.equals("info")) {
            Account a = plugin.bank().account(p);
            m.send(p, "card-info", "tier", plugin.bank().tier(a).label(), "card", String.valueOf(a.cardNo));
            return true;
        }
        Tier t = plugin.bank().tiers().get(sub.toUpperCase(Locale.ROOT));
        if (t == null || t.order() == 0) {
            m.send(p, "card-usage");
            return true;
        }
        BankManager.Result r = plugin.cards().buy(p, t);
        if (r.success()) {
            m.send(p, "card-bought", "tier", t.label());
        } else {
            m.send(p, r.key(), r.kv());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (Tier t : plugin.bank().tiers().values()) {
                if (t.order() > 0) {
                    out.add(t.id().toLowerCase(Locale.ROOT));
                }
            }
            out.add("replace");
            out.add("info");
            String last = a[0].toLowerCase(Locale.ROOT);
            out.removeIf(x -> !x.startsWith(last));
        }
        return out;
    }
}
