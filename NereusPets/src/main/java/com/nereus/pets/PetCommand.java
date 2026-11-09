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
    private static final List<String> SUBS = List.of("recall", "rename", "info", "release", "give", "reload", "help");

    private final NereusPets plugin;
    private final PetManager mgr;
    private final Orbs orbs;

    public PetCommand(NereusPets plugin, PetManager mgr, Orbs orbs) {
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
        if (args[1].equalsIgnoreCase("orb")) {
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
        };
        for (String l : lines) {
            sender.sendMessage(NereusPets.LEGACY.deserialize(l));
        }
        if (sender.hasPermission("nereuspets.admin")) {
            sender.sendMessage(NereusPets.LEGACY.deserialize("&c/pets give orb <pemain> <tier> [jumlah]"));
            sender.sendMessage(NereusPets.LEGACY.deserialize("&c/pets give pet <pemain> <JENIS> [level]"));
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
                if ((s.equals("give") || s.equals("reload")) && !admin) {
                    continue;
                }
                out.add(s);
            }
        } else if (admin && args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                out.add("orb");
                out.add("pet");
            } else if (args.length == 3) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
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
