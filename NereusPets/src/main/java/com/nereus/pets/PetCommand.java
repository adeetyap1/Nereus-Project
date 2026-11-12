package com.nereus.pets;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /pets dan subperintahnya. */
public final class PetCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("recall", "rename", "info", "release", "duel", "fuse", "legend", "give", "wild", "reload", "help");

    private final NereusPets plugin;
    private final PetManager mgr;
    private final Orbs orbs;
    private final ItemListener itemListener;

    public PetCommand(NereusPets plugin, PetManager mgr, Orbs orbs, ItemListener itemListener) {
        this.itemListener = itemListener;
        this.plugin = plugin;
        this.mgr = mgr;
        this.orbs = orbs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            Player p = playerOnly(sender);
            if (p != null) {
                new PetMenu(plugin, mgr, p).open();
            }
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "recall" -> {
                Player p = playerOnly(sender);
                if (p == null) {
                    return true;
                }
                if (mgr.activeOf(p) == null) {
                    plugin.send(p, "no-active");
                } else {
                    mgr.manualRecall(p);
                }
            }
            case "rename" -> {
                Player p = playerOnly(sender);
                if (p == null) {
                    return true;
                }
                ActivePet ap = mgr.activeOf(p);
                if (ap == null) {
                    plugin.send(p, "no-active");
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                        .replaceAll("[&§]", "").trim();
                if (name.length() > 16) {
                    name = name.substring(0, 16);
                }
                ap.data.nickname = name.isEmpty() ? null : name;
                mgr.refreshName(ap);
                plugin.send(p, "renamed", "{name}", name.isEmpty() ? "(nama asli)" : name);
            }
            case "info" -> {
                Player p = playerOnly(sender);
                if (p == null) {
                    return true;
                }
                ActivePet ap = mgr.activeOf(p);
                if (ap == null) {
                    plugin.send(p, "no-active");
                    return true;
                }
                int idx = mgr.box(p.getUniqueId()).indexOf(ap.data);
                p.sendMessage(NereusPets.LEGACY.deserialize("&8&m---&r " + mgr.plain(mgr.displayName(ap.data)) + " &8&m---"));
                for (String line : mgr.detailLines(ap.data, Math.max(0, idx))) {
                    p.sendMessage(NereusPets.LEGACY.deserialize(line));
                }
            }
            case "release" -> {
                Player p = playerOnly(sender);
                if (p == null) {
                    return true;
                }
                List<PetData> list = mgr.box(p.getUniqueId());
                int idx = args.length > 1 ? parseInt(args[1], -1) - 1 : -1;
                if (idx < 0 || idx >= list.size()) {
                    plugin.send(p, "invalid-index");
                    return true;
                }
                PetData d = list.get(idx);
                String name = mgr.plain(mgr.displayName(d));
                if (args.length > 2 && args[2].equalsIgnoreCase("confirm")) {
                    mgr.release(p, d);
                    plugin.send(p, "released", "{pet}", name);
                } else {
                    plugin.send(p, "release-confirm", "{index}", String.valueOf(idx + 1), "{pet}", name);
                }
            }
            case "duel" -> {
                Player p = playerOnly(sender);
                if (p == null) {
                    return true;
                }
                if (args.length < 2) {
                    new DuelMenu(plugin, mgr, p).open();
                } else if (args[1].equalsIgnoreCase("accept")) {
                    plugin.duels().accept(p);
                } else if (args[1].equalsIgnoreCase("deny")) {
                    plugin.duels().deny(p);
                } else {
                    Player other = Bukkit.getPlayerExact(args[1]);
                    if (other == null) {
                        plugin.send(p, "unknown-player");
                    } else {
                        plugin.duels().challenge(p, other);
                    }
                }
            }
            case "fuse" -> {
                Player p = playerOnly(sender);
                if (p == null) {
                    return true;
                }
                fuse(p, args);
            }
            case "legend" -> sender.sendMessage(plugin.wild().statusLine());
            case "wild" -> {
                if (!sender.hasPermission("nereuspets.admin")) {
                    plugin.send(sender, "no-permission");
                    return true;
                }
                Player p = playerOnly(sender);
                if (p == null) {
                    return true;
                }
                wildAdmin(p, args);
            }
            case "give" -> {
                if (!sender.hasPermission("nereuspets.admin")) {
                    plugin.send(sender, "no-permission");
                    return true;
                }
                give(sender, args);
            }
            case "reload" -> {
                if (!sender.hasPermission("nereuspets.admin")) {
                    plugin.send(sender, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.send(sender, "reload-done");
            }
            default -> help(sender);
        }
        return true;
    }

    private void fuse(Player p, String[] args) {
        // /pets fuse <target#> <fodder#> [confirm]
        List<PetData> list = mgr.box(p.getUniqueId());
        int ti = args.length > 1 ? parseInt(args[1], -1) - 1 : -1;
        int fi = args.length > 2 ? parseInt(args[2], -1) - 1 : -1;
        if (ti < 0 || fi < 0 || ti >= list.size() || fi >= list.size()) {
            plugin.send(p, "fuse-usage");
            return;
        }
        PetData t = list.get(ti);
        PetData f = list.get(fi);
        int maxStars = plugin.getConfig().getInt("fuse.max-stars", 5);
        int minLevel = plugin.getConfig().getInt("fuse.min-fodder-level", 10);
        if (t == f || t.type != f.type) {
            plugin.send(p, "fuse-same");
        } else if (t.stars >= maxStars) {
            plugin.send(p, "fuse-max");
        } else if (f.level < minLevel) {
            plugin.send(p, "fuse-level", "{level}", String.valueOf(minLevel));
        } else if (mgr.isActive(f)) {
            plugin.send(p, "fuse-active");
        } else if (args.length > 3 && args[3].equalsIgnoreCase("confirm")) {
            String fn = mgr.plain(mgr.displayName(f));
            mgr.release(p, f);
            t.stars++;
            ActivePet ap = mgr.activeOf(p);
            if (ap != null && ap.data == t) {
                mgr.applyStats(ap, false);
            }
            plugin.send(p, "fuse-done", "{pet}", mgr.plain(mgr.displayName(t)), "{fodder}", fn);
        } else {
            plugin.send(p, "fuse-confirm", "{target}", mgr.plain(mgr.displayName(t)),
                    "{fodder}", mgr.plain(mgr.displayName(f)), "{a}", String.valueOf(ti + 1), "{b}", String.valueOf(fi + 1));
        }
    }

    private void wildAdmin(Player p, String[] args) {
        // /pets wild legendary | /pets wild <JENIS>
        if (args.length < 2) {
            p.sendMessage(Component.text("Pakai: /pets wild legendary  atau  /pets wild <JENIS>"));
            return;
        }
        if (args[1].equalsIgnoreCase("legendary")) {
            if (!plugin.wild().spawnLegendaryFor(p)) {
                plugin.send(p, "wild-failed");
            }
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.send(p, "unknown-species");
            return;
        }
        Species sp = mgr.species(type);
        if (sp == null || plugin.wild().spawnWild(p.getLocation().add(p.getLocation().getDirection().multiply(4)), sp, false) == null) {
            plugin.send(p, "wild-failed");
        }
    }

    private void give(CommandSender sender, String[] args) {
        // /pets give orb <pemain> <tier> [jumlah]
        // /pets give pet <pemain> <JENIS> [level]
        if (args.length < 4) {
            sender.sendMessage(Component.text("Pakai: /pets give orb <pemain> <tier> [jumlah]  atau  /pets give pet <pemain> <JENIS> [level]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            plugin.send(sender, "unknown-player");
            return;
        }
        if (args[1].equalsIgnoreCase("egg") || args[1].equalsIgnoreCase("candy")) {
            boolean egg = args[1].equalsIgnoreCase("egg");
            String id = args[3].toLowerCase(Locale.ROOT);
            int amount = args.length > 4 ? parseInt(args[4], 1) : 1;
            ItemStack item = egg ? plugin.items().egg(id, amount) : plugin.items().candy(id, amount);
            if (item == null) {
                plugin.send(sender, "unknown-tier");
                return;
            }
            target.getInventory().addItem(item).values()
                    .forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
            plugin.send(sender, "item-given", "{amount}", String.valueOf(Math.max(1, amount)),
                    "{item}", args[1] + " " + id, "{player}", target.getName());
        } else if (args[1].equalsIgnoreCase("hatch")) {
            // langsung menetaskan pet acak (untuk hadiah crate): /pets give hatch <pemain> <tier>
            PetData d = plugin.items().hatch(args[3].toLowerCase(Locale.ROOT));
            if (d == null) {
                plugin.send(sender, "unknown-tier");
            } else if (!mgr.addPet(target.getUniqueId(), d)) {
                plugin.send(sender, "box-full", "{limit}", String.valueOf(mgr.boxLimit()));
            } else {
                itemListener.announceHatch(target, d);
            }
        } else if (args[1].equalsIgnoreCase("orb")) {
            String tier = args[3].toLowerCase(Locale.ROOT);
            int amount = args.length > 4 ? parseInt(args[4], 1) : 1;
            ItemStack item = orbs.create(tier, amount);
            if (item == null) {
                plugin.send(sender, "unknown-tier");
                return;
            }
            target.getInventory().addItem(item).values()
                    .forEach(rest -> target.getWorld().dropItem(target.getLocation(), rest));
            plugin.send(sender, "orb-given", "{amount}", String.valueOf(Math.max(1, amount)),
                    "{orb}", orbs.displayName(tier), "{player}", target.getName());
        } else if (args[1].equalsIgnoreCase("pet")) {
            EntityType type;
            try {
                type = EntityType.valueOf(args[3].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.send(sender, "unknown-species");
                return;
            }
            int level = args.length > 4 ? parseInt(args[4], 1) : 1;
            PetData d = mgr.createPet(type, level);
            if (d == null) {
                plugin.send(sender, "unknown-species");
                return;
            }
            if (!mgr.addPet(target.getUniqueId(), d)) {
                plugin.send(sender, "box-full", "{limit}", String.valueOf(mgr.boxLimit()));
                return;
            }
            plugin.send(sender, "pet-given", "{pet}", mgr.plain(mgr.displayName(d)), "{player}", target.getName());
        } else {
            plugin.send(sender, "unknown-species");
        }
    }

    private void help(CommandSender sender) {
        String[] lines = {
                "&8&m---&r &bNereusPets &8&m---",
                "&b/pets &7- buka Pet Box",
                "&b/pets recall &7- simpan pet aktif",
                "&b/pets rename <nama> &7- ganti nama pet aktif",
                "&b/pets info &7- detail pet aktif",
                "&b/pets release <nomor> &7- lepas pet",
                "&b/pets duel [pemain|accept|deny] &7- adu pet dengan pemain lain",
                "&b/pets fuse <nomor> <nomor> &7- gabung 2 pet sejenis jadi bintang",
                "&b/pets legend &7- status pet Legendaris",
        };
        for (String l : lines) {
            sender.sendMessage(NereusPets.LEGACY.deserialize(l));
        }
        if (sender.hasPermission("nereuspets.admin")) {
            sender.sendMessage(NereusPets.LEGACY.deserialize("&c/pets give orb <pemain> <tier> [jumlah]"));
            sender.sendMessage(NereusPets.LEGACY.deserialize("&c/pets give pet <pemain> <JENIS> [level]"));
            sender.sendMessage(NereusPets.LEGACY.deserialize("&c/pets give egg|candy|hatch <pemain> <tier> [jumlah]"));
            sender.sendMessage(NereusPets.LEGACY.deserialize("&c/pets wild legendary|<JENIS> &7(uji spawn)"));
            sender.sendMessage(NereusPets.LEGACY.deserialize("&c/pets reload"));
        }
    }

    private Player playerOnly(CommandSender sender) {
        if (sender instanceof Player p) {
            return p;
        }
        plugin.send(sender, "players-only");
        return null;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("nereuspets.admin");
        if (args.length == 1) {
            for (String s : SUBS) {
                if ((s.equals("give") || s.equals("reload") || s.equals("wild")) && !admin) {
                    continue;
                }
                out.add(s);
            }
        } else if (args[0].equalsIgnoreCase("duel") && args.length == 2) {
            out.add("accept");
            out.add("deny");
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (admin && args[0].equalsIgnoreCase("wild") && args.length == 2) {
            out.add("legendary");
            mgr.speciesTypes().forEach(t -> out.add(t.name()));
        } else if (admin && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                out.add("orb");
                out.add("pet");
                out.add("egg");
                out.add("candy");
                out.add("hatch");
            } else if (args.length == 3) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            } else if (args.length == 4 && (args[1].equalsIgnoreCase("egg") || args[1].equalsIgnoreCase("hatch"))) {
                out.addAll(plugin.items().eggTiers());
            } else if (args.length == 4 && args[1].equalsIgnoreCase("candy")) {
                out.addAll(plugin.items().candyIds());
            } else if (args.length == 4 && args[1].equalsIgnoreCase("orb")) {
                out.addAll(orbs.tiers());
            } else if (args.length == 4 && args[1].equalsIgnoreCase("pet")) {
                mgr.speciesTypes().forEach(t -> out.add(t.name()));
            }
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
