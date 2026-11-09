package com.nereus.guild;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** /guild dan subperintahnya. */
public final class GuildCommand implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "nereusguild.admin";

    private final NereusGuild plugin;
    private final GuildManager mgr;

    public GuildCommand(NereusGuild plugin, GuildManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                new GuildMenu(plugin, mgr, p).open();
            } else {
                plugin.send(sender, "players-only");
            }
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> info(sender, args);
            case "abandon" -> {
                if (!(sender instanceof Player p)) {
                    plugin.send(sender, "players-only");
                } else if (mgr.cancelExam(p)) {
                    plugin.send(p, "exam-abandoned");
                } else {
                    plugin.send(p, "no-exam");
                }
            }
            case "set" -> {
                if (!admin(sender)) {
                    return true;
                }
                set(sender, args);
            }
            case "exam" -> {
                if (!admin(sender)) {
                    return true;
                }
                exam(sender, args);
            }
            case "sync" -> {
                if (!admin(sender)) {
                    return true;
                }
                Player t = target(sender, args, 1);
                if (t != null) {
                    mgr.sync(t);
                    plugin.send(sender, "sync-done", "{player}", t.getName());
                }
            }
            case "reload" -> {
                if (!admin(sender)) {
                    return true;
                }
                plugin.reloadAll();
                plugin.send(sender, "reload-done");
            }
            default -> sender.sendMessage(NereusGuild.LEGACY.deserialize(
                    "&6/guild &7| &6/guild info [pemain] &7| &6/guild abandon"
                            + (sender.hasPermission(ADMIN) ? " &7| &c/guild set|exam|sync|reload" : "")));
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission(ADMIN)) {
            return true;
        }
        plugin.send(sender, "no-permission");
        return false;
    }

    private Player target(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            plugin.send(sender, "unknown-player");
            return null;
        }
        Player t = Bukkit.getPlayerExact(args[index]);
        if (t == null) {
            plugin.send(sender, "unknown-player");
        }
        return t;
    }

    private void info(CommandSender sender, String[] args) {
        Player t;
        if (args.length > 1) {
            if (!admin(sender)) {
                return;
            }
            t = target(sender, args, 1);
        } else if (sender instanceof Player p) {
            t = p;
        } else {
            plugin.send(sender, "players-only");
            return;
        }
        if (t == null) {
            return;
        }
        PlayerData d = mgr.data(t.getUniqueId());
        RankDef cur = mgr.rank(d.rank);
        sender.sendMessage(NereusGuild.LEGACY.deserialize(plugin.raw("info-header", "{player}", t.getName())));
        sender.sendMessage(NereusGuild.LEGACY.deserialize(plugin.raw("info-rank", "{rank}", cur.label())));
        sender.sendMessage(NereusGuild.LEGACY.deserialize(plugin.raw("info-gp",
                "{gp}", mgr.gpEnabled() ? String.valueOf(mgr.gp(t)) : "-")));
        if (d.exam != null && mgr.rank(d.exam.targetRank) != null) {
            RankDef target = mgr.rank(d.exam.targetRank);
            ExamDef ex = target.exam();
            String progress = ex != null && ex.type() == ExamDef.Type.KILL
                    ? d.exam.progress + "/" + ex.amount() : "berjalan";
            sender.sendMessage(NereusGuild.LEGACY.deserialize(plugin.raw("info-exam",
                    "{desc}", target.label() + "&f: " + mgr.describe(ex), "{progress}", progress)));
        }
    }

    private void set(CommandSender sender, String[] args) {
        Player t = target(sender, args, 1);
        if (t == null) {
            return;
        }
        int idx = args.length > 2 ? mgr.indexOf(args[2]) : -1;
        if (idx < 0) {
            plugin.send(sender, "unknown-rank", "{ranks}", rankList());
            return;
        }
        mgr.setRank(t, idx);
        plugin.send(sender, "rank-set", "{player}", t.getName(), "{rank}", mgr.rank(idx).label());
    }

    private void exam(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(NereusGuild.LEGACY.deserialize("&cPakai: /guild exam <complete|cancel> <pemain>"));
            return;
        }
        Player t = target(sender, args, 2);
        if (t == null) {
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("complete")) {
            if (mgr.completeExam(t)) {
                plugin.send(sender, "exam-completed", "{player}", t.getName());
            } else {
                plugin.send(sender, "no-exam");
            }
        } else if (action.equals("cancel")) {
            if (mgr.cancelExam(t)) {
                plugin.send(sender, "exam-cancelled", "{player}", t.getName());
            } else {
                plugin.send(sender, "no-exam");
            }
        } else {
            sender.sendMessage(NereusGuild.LEGACY.deserialize("&cPakai: /guild exam <complete|cancel> <pemain>"));
        }
    }

    private String rankList() {
        return mgr.ranks().stream().map(RankDef::key).collect(Collectors.joining(", "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            out.add("info");
            out.add("abandon");
            if (admin) {
                out.add("set");
                out.add("exam");
                out.add("sync");
                out.add("reload");
            }
        } else if (admin) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2 && (sub.equals("info") || sub.equals("set") || sub.equals("sync"))) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 2 && sub.equals("exam")) {
                out.add("complete");
                out.add("cancel");
            } else if (args.length == 3 && sub.equals("set")) {
                mgr.ranks().forEach(r -> out.add(r.key()));
            } else if (args.length == 3 && sub.equals("exam")) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
