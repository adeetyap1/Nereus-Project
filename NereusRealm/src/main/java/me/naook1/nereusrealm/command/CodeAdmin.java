package me.naook1.nereusrealm.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusrealm.NereusRealm;
import me.naook1.nereusrealm.redeem.RedeemManager;
import me.naook1.nereusrealm.redeem.RedeemManager.Code;
import org.bukkit.command.CommandSender;
import org.bukkit.util.StringUtil;

/** /nereus code ... (admin only): create and edit redeem codes. */
public final class CodeAdmin {

    private static final List<String> SUBS = List.of("create", "delete", "list", "info", "money", "addcmd",
            "clearcmds", "text", "expire", "max", "on", "off", "resetuses", "reload");
    private static final List<String> NEEDS_CODE = List.of("delete", "info", "money", "addcmd", "clearcmds",
            "text", "expire", "max", "on", "off", "resetuses");

    private final NereusRealm plugin;

    public CodeAdmin(NereusRealm plugin) {
        this.plugin = plugin;
    }

    /** args = everything after "code". */
    public void handle(CommandSender s, String[] args) {
        var m = plugin.messages();
        var r = plugin.redeem();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> {
                list(s);
                return;
            }
            case "reload" -> {
                r.reload();
                m.send(s, "redeem.admin.reloaded", "count", String.valueOf(r.all().size()));
                return;
            }
            case "create" -> {
                create(s, args);
                return;
            }
            default -> {
            }
        }
        if (!NEEDS_CODE.contains(sub)) {
            m.sendList(s, "redeem.admin.help");
            return;
        }
        if (args.length < 2) {
            m.sendList(s, "redeem.admin.help");
            return;
        }
        if (sub.equals("delete")) {
            if (r.delete(args[1])) {
                m.send(s, "redeem.admin.deleted", "code", args[1].toUpperCase(Locale.ROOT));
            } else {
                m.send(s, "redeem.admin.not-found", "code", args[1]);
            }
            return;
        }
        Code c = r.get(args[1]);
        if (c == null) {
            m.send(s, "redeem.admin.not-found", "code", args[1]);
            return;
        }
        String rest = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";

        switch (sub) {
            case "info" -> {
                info(s, c);
                return;
            }
            case "money" -> {
                long amount;
                try {
                    amount = Long.parseLong(rest.trim());
                } catch (NumberFormatException e) {
                    m.send(s, "redeem.admin.bad-number");
                    return;
                }
                c.money = Math.max(0, amount);
            }
            case "addcmd" -> {
                if (rest.isBlank()) {
                    m.sendList(s, "redeem.admin.help");
                    return;
                }
                c.commands.add(rest.startsWith("/") ? rest.substring(1) : rest);
            }
            case "clearcmds" -> c.commands.clear();
            case "text" -> c.text = rest;
            case "expire" -> {
                long ms = r.parseExpiry(rest.trim());
                if (rest.isBlank() || ms == Long.MIN_VALUE) {
                    m.send(s, "redeem.admin.bad-time");
                    return;
                }
                c.expires = ms;
            }
            case "max" -> {
                try {
                    c.maxUses = Math.max(0, Integer.parseInt(rest.trim()));
                } catch (NumberFormatException e) {
                    m.send(s, "redeem.admin.bad-number");
                    return;
                }
            }
            case "on" -> c.enabled = true;
            case "off" -> c.enabled = false;
            case "resetuses" -> c.used.clear();
            default -> {
                return;
            }
        }
        r.changed();
        m.send(s, "redeem.admin.updated", "code", c.id);
        info(s, c);
    }

    private void create(CommandSender s, String[] args) {
        var m = plugin.messages();
        var r = plugin.redeem();
        if (args.length < 2) {
            m.sendList(s, "redeem.admin.help");
            return;
        }
        long expires = 0;
        if (args.length >= 3) {
            expires = r.parseExpiry(args[2]);
            if (expires == Long.MIN_VALUE) {
                m.send(s, "redeem.admin.bad-time");
                return;
            }
        }
        int max = 0;
        if (args.length >= 4) {
            try {
                max = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException e) {
                m.send(s, "redeem.admin.bad-number");
                return;
            }
        }
        String err = r.create(args[1], expires, max);
        if (err != null) {
            m.send(s, err, "code", args[1]);
            return;
        }
        m.send(s, "redeem.admin.created", "code", args[1].toUpperCase(Locale.ROOT));
        info(s, r.get(args[1]));
    }

    private void list(CommandSender s) {
        var m = plugin.messages();
        var r = plugin.redeem();
        List<Code> all = r.all();
        if (all.isEmpty()) {
            m.send(s, "redeem.admin.empty");
            return;
        }
        m.send(s, "redeem.admin.list-header", "count", String.valueOf(all.size()));
        long now = System.currentTimeMillis();
        for (Code c : all) {
            m.send(s, "redeem.admin.list-line", "code", c.id, "status", r.status(c),
                    "uses", c.used.size() + (c.maxUses > 0 ? "/" + c.maxUses : ""),
                    "time", timeText(c, now));
        }
    }

    private String timeText(Code c, long now) {
        if (c.expires <= 0) {
            return "tanpa batas";
        }
        if (c.expired(now)) {
            return "sudah lewat";
        }
        return RedeemManager.left(c.expires - now);
    }

    private void info(CommandSender s, Code c) {
        var r = plugin.redeem();
        long now = System.currentTimeMillis();
        plugin.messages().sendList(s, "redeem.admin.info",
                "code", c.id,
                "status", r.status(c),
                "expires", c.expires > 0 ? r.stamp(c.expires) : "never",
                "left", timeText(c, now),
                "uses", String.valueOf(c.used.size()),
                "max", c.maxUses > 0 ? String.valueOf(c.maxUses) : "tanpa batas",
                "money", String.valueOf(c.money),
                "commands", String.valueOf(c.commands.size()),
                "text", c.text == null || c.text.isBlank() ? "-" : c.text);
        for (String cmd : c.commands) {
            s.sendMessage(net.kyori.adventure.text.Component.text("  - " + cmd,
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    public List<String> tab(String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBS, new ArrayList<>());
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && NEEDS_CODE.contains(sub)) {
            return StringUtil.copyPartialMatches(args[1], plugin.redeem().ids(), new ArrayList<>());
        }
        if (args.length == 3 && (sub.equals("create") || sub.equals("expire"))) {
            return StringUtil.copyPartialMatches(args[2], List.of("1d", "7d", "30d", "12h", "never"), new ArrayList<>());
        }
        return List.of();
    }
}
