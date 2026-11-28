package com.nereus.rpg;

import com.nereus.rpg.guild.Guild;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.Locale;

/** Semua perintah: /nereusrpg (alias /gm, /rpg) serta pintasan /quest /story /class /stats /skill /rank. */
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

    private final Map<UUID, long[]> resetConfirm = new HashMap<>();
    private final Map<UUID, UUID> resetTarget = new HashMap<>();

    private Guild g() {
        return core.guild;
    }

    private boolean can(CommandSender s, String sub) {
        return s.hasPermission(ADMIN) || s.hasPermission(ADMIN + "." + sub);
    }

    private void say(CommandSender to, String key, String... kv) {
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            vars.put(kv[i], kv[i + 1]);
        }
        core.msg(to, g().msgs.get(key, vars));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("nereusrpg")) {
            main(sender, args);
            return true;
        }
        if (!(sender instanceof Player p)) {
            say(sender, "player-only");
            return true;
        }
        Profile pr = core.profile(p);
        if (!pr.member()) {
            g().notMember(p);
            return true;
        }
        switch (name) {
            case "quest" -> new QuestUi.Menu(core, p).open();
            case "story" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("serahkan")) {
                    core.tracker.turnInStory(p);
                } else {
                    showStory(core, p);
                }
            }
            case "race", "class" -> new Menus.ClassMenu(core, p).open();
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

    // ------------------------------------------------------------ /nereusrpg dan /gm

    private void main(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                new Menus.HubMenu(core, p).open();
            } else {
                help(sender);
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = sliceFrom(args, 1);
        switch (sub) {
            case "help", "?" -> help(sender);
            case "menu" -> {
                if (!(sender instanceof Player p)) {
                    say(sender, "player-only");
                } else if (rest.length >= 1 && core.menus.ids().contains(rest[0])) {
                    if (core.member(p)) {
                        core.menus.open(p, rest[0]);
                    } else {
                        g().notMember(p);
                    }
                } else {
                    new Menus.HubMenu(core, p).open();
                }
            }
            case "choose" -> {
                if (sender instanceof Player p && rest.length >= 1) {
                    try {
                        g().dialogue.chooseNumber(p, Integer.parseInt(rest[0]));
                    } catch (NumberFormatException e) {
                        say(sender, "usage-choose");
                    }
                }
            }
            case "talk" -> {
                if (!(sender instanceof Player p)) {
                    say(sender, "player-only");
                } else if (!can(sender, "talk")) {
                    say(sender, "no-permission");
                } else {
                    g().dialogue.open(p);
                }
            }
            case "reload" -> {
                if (!can(sender, "reload")) {
                    say(sender, "no-permission");
                    return;
                }
                core.reload();
                core.guild.load();
                core.plugin.afterReload();
                say(sender, "reloaded");
            }
            case "info", "data" -> {
                if (!can(sender, sub)) {
                    say(sender, "no-permission");
                } else if (rest.length < 1) {
                    say(sender, "usage-" + sub);
                } else {
                    target(sender, rest[0], false, (pr, online) -> {
                        if (pr == null) {
                            say(sender, "player-unknown");
                        } else if (sub.equals("info")) {
                            showInfo(sender, pr);
                        } else {
                            showData(sender, pr);
                        }
                    });
                }
            }
            case "register", "revoke", "restore" -> {
                if (!can(sender, sub)) {
                    say(sender, "no-permission");
                } else if (rest.length < 1) {
                    say(sender, "usage-" + sub);
                } else {
                    target(sender, rest[0], sub.equals("register"), (pr, online) -> {
                        if (pr == null) {
                            say(sender, "player-unknown");
                            return;
                        }
                        Guild.Result r = switch (sub) {
                            case "register" -> g().adminRegister(pr, online);
                            case "revoke" -> g().revoke(pr, online);
                            default -> g().restore(pr, online);
                        };
                        report(sender, "admin-" + sub, r, pr.name);
                    });
                }
            }
            case "rank" -> rank(sender, rest);
            case "backup" -> {
                if (!can(sender, "backup")) {
                    say(sender, "no-permission");
                    return;
                }
                say(sender, "backup-start");
                core.plugin.backups().backupPlayerdata(f -> {
                    if (f == null) {
                        say(sender, "backup-fail");
                    } else {
                        say(sender, "backup-done", "file", f.getName());
                    }
                });
            }
            case "migrate" -> {
                if (!can(sender, "migrate")) {
                    say(sender, "no-permission");
                    return;
                }
                core.plugin.migration().run(sender);
            }
            case "admin" -> {
                if (!sender.hasPermission(ADMIN)) {
                    say(sender, "no-permission");
                    return;
                }
                admin(sender, rest);
            }
            default -> say(sender, "unknown-sub");
        }
    }

    private void report(CommandSender s, String okKey, Guild.Result r, String name) {
        if (r == Guild.Result.OK) {
            say(s, okKey, "player", name);
        } else {
            say(s, "res-" + r.name().toLowerCase(Locale.ROOT), "player", name);
        }
    }

    private void help(CommandSender s) {
        for (String l : g().msgs.list("help-player")) {
            s.sendMessage(Text.c(l));
        }
        if (s.hasPermission(ADMIN) || s.getEffectivePermissions().stream().anyMatch(a -> a.getPermission().startsWith(ADMIN + "."))) {
            for (String l : g().msgs.list("help-admin")) {
                s.sendMessage(Text.c(l));
            }
        }
    }

    /** Cari pemain (online dulu, lalu cache server) dan muat datanya. */
    private void target(CommandSender sender, String name, boolean create, java.util.function.BiConsumer<Profile, Player> then) {
        Player online = Bukkit.getPlayerExact(name);
        UUID id;
        String real;
        if (online != null) {
            id = online.getUniqueId();
            real = online.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
            if (off == null) {
                then.accept(null, null);
                return;
            }
            id = off.getUniqueId();
            real = off.getName() == null ? name : off.getName();
        }
        g().load(id, real, create, pr -> {
            if (pr != null && (pr.name == null || pr.name.isEmpty())) {
                pr.name = real;
            }
            then.accept(pr, Bukkit.getPlayer(id));
        });
    }

    private void rank(CommandSender sender, String[] a) {
        if (!can(sender, "rank")) {
            say(sender, "no-permission");
            return;
        }
        if (a.length >= 3 && a[0].equalsIgnoreCase("set")) {
            target(sender, a[1], false, (pr, online) -> {
                if (pr == null) {
                    say(sender, "player-unknown");
                    return;
                }
                Guild.Result r = g().setRank(pr, a[2], online);
                if (r == Guild.Result.OK) {
                    say(sender, "admin-rank-set", "player", pr.name, "rank", core.rank(pr.rank).label());
                } else {
                    say(sender, "res-" + r.name().toLowerCase(Locale.ROOT), "player", pr.name);
                }
            });
        } else if (a.length >= 2 && a[0].equalsIgnoreCase("reset")) {
            UUID who = sender instanceof Player sp ? sp.getUniqueId() : new UUID(0, 0);
            target(sender, a[1], false, (pr, online) -> {
                if (pr == null) {
                    say(sender, "player-unknown");
                    return;
                }
                long[] exp = resetConfirm.get(who);
                boolean confirmed = a.length >= 3 && a[2].equalsIgnoreCase("confirm") && exp != null
                        && exp[0] > System.currentTimeMillis() && pr.id.equals(resetTarget.get(who));
                if (!confirmed) {
                    resetConfirm.put(who, new long[]{System.currentTimeMillis() + 30_000L});
                    resetTarget.put(who, pr.id);
                    say(sender, "admin-rank-reset-confirm", "player", pr.name);
                    return;
                }
                resetConfirm.remove(who);
                resetTarget.remove(who);
                Guild.Result r = g().resetRank(pr, online);
                if (r == Guild.Result.OK) {
                    say(sender, "admin-rank-reset", "player", pr.name);
                } else {
                    say(sender, "res-" + r.name().toLowerCase(Locale.ROOT), "player", pr.name);
                }
            });
        } else {
            say(sender, "usage-rank");
        }
    }

    private void showInfo(CommandSender s, Profile pr) {
        Defs.Rank r = core.activeRank(pr);
        Defs.Race c = core.clazz(pr);
        s.sendMessage(Text.c("&8&m---&r &6Nerus Guild &7- &f" + pr.name + " &8&m---"));
        s.sendMessage(Text.c("&7Status: &f" + g().statusLabel(pr.status)));
        s.sendMessage(Text.c("&7Rank: " + core.label(r, null) + (pr.status == GuildStatus.REVOKED && core.rank(pr.rank) != null
                ? " &8(tersimpan: " + core.rank(pr.rank).tag() + "&8)" : "")));
        s.sendMessage(Text.c("&7Level: &f" + pr.level + " &8| &7EXP: &f" + pr.xp + "&7/&f" + core.xpNeeded(Math.max(1, pr.level))
                + " &8(total " + pr.totalXp + ")"));
        s.sendMessage(Text.c("&7Terdaftar: &f" + g().date(pr.registeredAt)));
        s.sendMessage(Text.c("&7Quest selesai: &f" + pr.totalQuests + " &8| &7aktif: &f" + pr.active.size()));
        s.sendMessage(Text.c("&7Class: &f" + (c == null ? "-" : Text.strip(c.name()))));
    }

    private void showData(CommandSender s, Profile pr) {
        showInfo(s, pr);
        s.sendMessage(Text.c("&7UUID: &f" + pr.id));
        s.sendMessage(Text.c("&7GP: &f" + pr.gp + " &8| &7Kill: &f" + pr.totalKills + " &8| &7Misi cerita: &f" + pr.story
                + " &8| &7Login beruntun: &f" + pr.streak));
        s.sendMessage(Text.c("&7Didaftarkan oleh: &f" + (pr.registeredBy.isEmpty() ? "-" : pr.registeredBy)
                + " &8| &7Update terakhir: &f" + g().date(pr.updatedAt)));
        s.sendMessage(Text.c("&7Quest aktif: &f" + (pr.active.isEmpty() ? "-" : String.join(", ", pr.active.keySet()))));
        s.sendMessage(Text.c("&7Quest tercatat selesai: &f" + pr.completedQuestIds.size()));
        int from = Math.max(0, pr.rankHistory.size() - 5);
        s.sendMessage(Text.c("&7Riwayat rank (" + pr.rankHistory.size() + "):"));
        for (int i = from; i < pr.rankHistory.size(); i++) {
            String[] p = pr.rankHistory.get(i).split("\\|", 4);
            if (p.length >= 4) {
                long t = 0;
                try {
                    t = Long.parseLong(p[0]);
                } catch (NumberFormatException ignored) {
                    // abaikan
                }
                s.sendMessage(Text.c("&8 - &7" + g().date(t) + " &f" + p[1] + " &7-> &f" + p[2] + " &8(" + p[3] + ")"));
            }
        }
    }

    private void skill(Player p, Profile pr, String[] args) {
        Defs.Race c = core.clazz(pr);
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
            core.msg(s, "&6/gm admin &7setclass|setlevel|addxp|addgp|examdone|resetdaily|sync|weapon &6| rank: /gm rank set <pemain> <rank>");
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                core.reload();
                core.guild.load();
                core.msg(s, "&aKonfigurasi dimuat ulang.");
            }
            case "weapon" -> weapon(s, a);
            default -> {
                Player t = target(s, a, 1);
                if (t == null) {
                    return;
                }
                Profile pr = core.profile(t);
                if (!pr.member() && !sub.equals("setrank") && !sub.equals("sync") && !sub.equals("resetdaily")) {
                    core.msg(s, "&c" + t.getName() + " belum menjadi anggota Nerus Guild aktif (/gm register).");
                    return;
                }
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
                        pr.questCooldowns.clear();
                        core.msg(s, "&aStatistik harian dan cooldown quest direset (progres quest aktif tidak dihapus).");
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
        if (name.equals("story") && args.length == 1) {
            out.add("serahkan");
        } else if (name.equals("rank") && args.length == 1) {
            out.add("abandon");
        } else if (name.equals("nereusrpg")) {
            if (args.length == 1) {
                out.addAll(List.of("help", "menu", "talk"));
                for (String sub : List.of("reload", "info", "data", "register", "revoke", "restore", "rank", "backup", "migrate")) {
                    if (can(sender, sub)) {
                        out.add(sub);
                    }
                }
                if (sender.hasPermission(ADMIN)) {
                    out.add("admin");
                }
            } else {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("menu") && args.length == 2) {
                    out.addAll(core.menus.ids());
                } else if (List.of("info", "data", "register", "revoke", "restore").contains(sub) && args.length == 2 && can(sender, sub)) {
                    Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                } else if (sub.equals("rank") && can(sender, "rank")) {
                    if (args.length == 2) {
                        out.addAll(List.of("set", "reset"));
                    } else if (args.length == 3) {
                        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                    } else if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
                        core.ranks.forEach(r -> out.add(r.key()));
                    } else if (args.length == 4 && args[1].equalsIgnoreCase("reset")) {
                        out.add("confirm");
                    }
                } else if (sub.equals("admin") && sender.hasPermission(ADMIN)) {
                    adminTab(args, out);
                }
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private void adminTab(String[] args, List<String> out) {
        // args[0] = "admin"; sisanya sama dengan perintah admin lama
        if (args.length == 2) {
            out.addAll(List.of("setclass", "setlevel", "addxp", "addgp", "examdone", "resetdaily", "sync", "weapon"));
        } else if (args[1].equalsIgnoreCase("weapon")) {
            if (args.length == 3) {
                out.addAll(List.of("add", "list", "remove"));
            } else if (args.length == 4) {
                out.addAll(core.classes.keySet());
            }
        } else if (args.length == 3) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (args.length == 4 && args[1].equalsIgnoreCase("setclass")) {
            out.addAll(core.classes.keySet());
            out.add("none");
        }
    }
}
