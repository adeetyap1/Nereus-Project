package com.nereus.rpg;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Semua perintah: /rpg /quest /story /class /stats /skill /rank. */
public final class Commands implements CommandExecutor, TabCompleter {
    private static final String ADMIN = "nereusrpg.admin";
    private final Core core;

    public Commands(Core core) {
        this.core = core;
    }

    public static void showStory(Core core, Player p) {
        Profile pr = core.profile(p);
        Defs.Mission m = core.tracker.mission(pr);
        if (m == null) {
            core.msg(p, "&6Kisah utama sudah tamat. Legenda dirimu terus berlanjut!");
            return;
        }
        if (m.minRank() > pr.rank) {
            core.msg(p, "&dMisi berikutnya: " + m.title() + " &7- butuh " + core.rank(m.minRank()).label() + "&7.");
            return;
        }
        p.sendMessage(Text.c("&8&m---&r " + m.title() + " &8&m---"));
        for (String l : m.story()) {
            p.sendMessage(Text.c(l));
        }
        String prog = m.obj().type() == Objective.Type.KILL || m.obj().type() == Objective.Type.BREAK
                ? " &8(" + pr.storyProgress + "/" + m.obj().amount() + ")" : "";
        p.sendMessage(Text.c("&3Tugas: &f" + m.obj().describe() + prog));
        if (m.obj().type() == Objective.Type.GET) {
            p.sendMessage(Text.c("&7Serahkan item dengan &f/story serahkan"));
        }
        p.sendMessage(Text.c("&3Hadiah: &b" + m.gp() + " GP &7| &a" + m.xp() + " XP"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("rpg") && args.length > 0 && (args[0].equalsIgnoreCase("admin") || args[0].equalsIgnoreCase("reload"))) {
            if (!sender.hasPermission(ADMIN)) {
                core.msg(sender, "&cKamu tidak punya izin.");
                return true;
            }
            admin(sender, args[0].equalsIgnoreCase("reload") ? new String[]{"reload"} : sliceFrom(args, 1));
            return true;
        }
        if (!(sender instanceof Player p)) {
            core.msg(sender, "&cPerintah ini hanya untuk pemain.");
            return true;
        }
        Profile pr = core.profile(p);
        switch (name) {
            case "rpg" -> new Menus.HubMenu(core, p).open();
            case "quest" -> new Menus.QuestMenu(core, p).open();
            case "story" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("serahkan")) {
                    core.tracker.turnInStory(p);
                } else {
                    showStory(core, p);
                }
            }
            case "class" -> new Menus.ClassMenu(core, p).open();
            case "stats" -> new Menus.StatsMenu(core, p).open();
            case "rank" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("abandon")) {
                    core.msg(p, core.cancelExam(p) ? "&7Ujian dibatalkan." : "&cTidak ada ujian yang berjalan.");
                } else {
                    new Menus.RankMenu(core, p).open();
                }
            }
            case "skill" -> skill(p, pr, args);
            default -> {
            }
        }
        return true;
    }

    private void skill(Player p, Profile pr, String[] args) {
        Defs.Clazz c = core.clazz(pr);
        if (c == null) {
            core.msg(p, "&cPilih class dulu dengan &f/class&c.");
            return;
        }
        if (args.length == 0) {
            for (int i = 0; i < c.skills().size(); i++) {
                Defs.Skill s = c.skills().get(i);
                p.sendMessage(Text.c("&e" + (i + 1) + ". &f" + s.name() + " &8(Lv " + s.level() + ", " + s.mana()
                        + " mana, cd " + (int) s.cooldown() + "d) &7" + s.desc()));
            }
            p.sendMessage(Text.c("&8Klik kanan = pakai skill (busur: jongkok + klik kanan). Jongkok + klik kiri = ganti skill. F / Shift+F / /skill <nomor> juga bisa."));
            return;
        }
        try {
            core.plugin.skills().cast(p, Integer.parseInt(args[0]) - 1);
        } catch (NumberFormatException e) {
            core.msg(p, "&cPakai: /skill <nomor>");
        }
    }

    private static String[] sliceFrom(String[] a, int from) {
        String[] out = new String[Math.max(0, a.length - from)];
        System.arraycopy(a, from, out, 0, out.length);
        return out;
    }

    private Player target(CommandSender s, String[] a, int i) {
        Player t = a.length > i ? Bukkit.getPlayerExact(a[i]) : null;
        if (t == null) {
            core.msg(s, "&cPemain tidak ditemukan atau offline.");
        }
        return t;
    }

    private void admin(CommandSender s, String[] a) {
        if (a.length == 0) {
            core.msg(s, "&6/rpg admin &7setrank|setclass|setlevel|addxp|addgp|examdone|resetdaily|sync|weapon &6| /rpg reload");
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                core.reload();
                core.msg(s, "&aKonfigurasi dimuat ulang.");
            }
            case "weapon" -> weapon(s, a);
            default -> {
                Player t = target(s, a, 1);
                if (t == null) {
                    return;
                }
                Profile pr = core.profile(t);
                switch (sub) {
                    case "setrank" -> {
                        int idx = a.length > 2 ? core.rankIndex(a[2]) : -1;
                        if (idx < 0) {
                            core.msg(s, "&cRank tidak dikenal.");
                        } else {
                            core.setRank(t, idx);
                            core.msg(s, "&aRank " + t.getName() + " = " + core.rank(idx).label());
                        }
                    }
                    case "setclass" -> {
                        String k = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "";
                        if (k.equals("none")) {
                            pr.cls = null;
                            core.applyBuffs(t);
                            core.msg(s, "&aClass " + t.getName() + " dikosongkan.");
                        } else if (core.classes.containsKey(k)) {
                            core.setClass(t, k);
                            core.msg(s, "&aClass " + t.getName() + " = " + k);
                        } else {
                            core.msg(s, "&cClass: " + String.join(", ", core.classes.keySet()) + ", none");
                        }
                    }
                    case "setlevel" -> {
                        int n = num(a, 2, 1);
                        pr.level = Math.max(1, Math.min(core.plugin.getConfig().getInt("level.max", 100), n));
                        pr.xp = 0;
                        pr.mana = core.maxMana(pr);
                        core.applyBuffs(t);
                        core.msg(s, "&aLevel " + t.getName() + " = " + pr.level);
                    }
                    case "addxp" -> {
                        core.addXp(t, num(a, 2, 0));
                        core.msg(s, "&aXP ditambahkan.");
                    }
                    case "addgp" -> {
                        core.addGp(t, num(a, 2, 0));
                        core.msg(s, "&aGP " + t.getName() + " = " + pr.gp);
                    }
                    case "examdone" -> core.msg(s, core.completeExam(t) ? "&aUjian diselesaikan." : "&cTidak ada ujian.");
                    case "resetdaily" -> {
                        pr.day = "";
                        core.checkDay(pr);
                        core.msg(s, "&aQuest harian direset.");
                    }
                    case "sync" -> {
                        core.syncRank(t, "");
                        core.msg(s, "&aRank disinkronkan.");
                    }
                    default -> core.msg(s, "&cSubperintah tidak dikenal.");
                }
            }
        }
    }

    private static int num(String[] a, int i, int def) {
        try {
            return a.length > i ? Integer.parseInt(a[i]) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void weapon(CommandSender s, String[] a) {
        if (a.length < 3 || !core.classes.containsKey(a[2].toLowerCase(Locale.ROOT))) {
            core.msg(s, "&cPakai: /rpg admin weapon <add|list|remove> <" + String.join("|", core.classes.keySet()) + "> [nomor]");
            return;
        }
        String cls = a[2].toLowerCase(Locale.ROOT);
        List<String> list = core.weaponList(cls);
        switch (a[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (!(s instanceof Player p)) {
                    core.msg(s, "&cHanya untuk pemain (pegang itemnya).");
                    return;
                }
                ItemStack it = p.getInventory().getItemInMainHand();
                if (it.getType().isAir()) {
                    core.msg(s, "&cPegang item senjata di tangan utama.");
                    return;
                }
                String entry = it.getType().name() + "|" + Core.plainName(it);
                if (!list.contains(entry)) {
                    list.add(entry);
                    core.saveWeapons();
                }
                core.msg(s, "&aDitambahkan ke " + cls + ": &f" + entry);
            }
            case "remove" -> {
                int n = num(a, 3, 0);
                if (n < 1 || n > list.size()) {
                    core.msg(s, "&cPakai nomor dari /rpg admin weapon list " + cls);
                } else {
                    core.msg(s, "&aDihapus: &f" + list.remove(n - 1));
                    core.saveWeapons();
                }
            }
            default -> {
                if (list.isEmpty()) {
                    core.msg(s, "&7Belum ada senjata untuk " + cls + " (semua item diterima).");
                }
                for (int i = 0; i < list.size(); i++) {
                    core.msg(s, "&e" + (i + 1) + ". &f" + list.get(i));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission(ADMIN);
        if (name.equals("story") && args.length == 1) {
            out.add("serahkan");
        } else if (name.equals("rank") && args.length == 1) {
            out.add("abandon");
        } else if (name.equals("rpg") && admin) {
            if (args.length == 1) {
                out.add("admin");
                out.add("reload");
            } else if (args[0].equalsIgnoreCase("admin")) {
                if (args.length == 2) {
                    out.addAll(List.of("setrank", "setclass", "setlevel", "addxp", "addgp", "examdone", "resetdaily", "sync", "weapon"));
                } else if (args[1].equalsIgnoreCase("weapon")) {
                    if (args.length == 3) {
                        out.addAll(List.of("add", "list", "remove"));
                    } else if (args.length == 4) {
                        out.addAll(core.classes.keySet());
                    }
                } else if (args.length == 3) {
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                } else if (args.length == 4 && args[1].equalsIgnoreCase("setrank")) {
                    core.ranks.forEach(r -> out.add(r.key()));
                } else if (args.length == 4 && args[1].equalsIgnoreCase("setclass")) {
                    out.addAll(core.classes.keySet());
                    out.add("none");
                }
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
