package me.naook1.nereusarmorysets;

import me.naook1.nereusarmorysets.Model.Bonus;
import me.naook1.nereusarmorysets.Model.SetDef;
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
import java.util.Map;

public final class ArmoryCommand implements CommandExecutor, TabCompleter {
    private final NereusArmorySets plugin;

    public ArmoryCommand(NereusArmorySets plugin) {
        this.plugin = plugin;
    }

    private void msg(CommandSender s, String legacy) {
        s.sendMessage(ItemFactory.text(legacy));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission("nereusarmorysets.admin");
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player p) {
                plugin.gui().open(p);
            } else {
                msg(sender, "&cMenu hanya bisa dibuka pemain.");
            }
            if (args.length == 0 && admin) {
                msg(sender, "&7Admin: /" + label + " give|list|info|reload");
            }
            return true;
        }
        if (!admin) {
            msg(sender, "&cKamu tidak punya izin untuk perintah ini.");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, label, args);
            case "debug" -> {
                if (sender instanceof Player p) {
                    plugin.display().debug(p).forEach(l -> msg(sender, "&7" + l));
                } else {
                    msg(sender, "&cDebug hanya untuk pemain.");
                }
            }
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                msg(sender, "&aConfig dimuat ulang.");
            }
            default -> msg(sender, "&cSubperintah tidak dikenal. Ketik /" + label + " untuk bantuan.");
        }
        return true;
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            msg(sender, "&cPakai: /" + label + " give <pemain> <set> [head|chest|legs|feet|all]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "&cPemain '" + args[1] + "' tidak online.");
            return;
        }
        SetDef set = plugin.registry().get(args[2]);
        if (set == null) {
            msg(sender, "&cSet '" + args[2] + "' tidak ditemukan. Lihat /" + label + " list");
            return;
        }
        String which = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "all";
        List<String> slots = which.equals("all") ? List.of(SetRegistry.SLOTS) : List.of(which);

        int given = 0;
        for (String slot : slots) {
            if (!List.of(SetRegistry.SLOTS).contains(slot)) {
                msg(sender, "&cPiece '" + slot + "' tidak valid (head/chest/legs/feet/all).");
                return;
            }
            ItemStack item = plugin.items().create(set, slot);
            if (item == null) {
                msg(sender, "&cGagal membuat item untuk set '" + set.id() + "'.");
                return;
            }
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
            leftover.values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
            given++;
        }
        msg(sender, "&aMemberi " + given + " item " + set.display() + " ke " + target.getName() + ".");
    }

    private void list(CommandSender sender) {
        for (var tier : plugin.registry().tiers().values()) {
            msg(sender, tier.color() + "Tier " + tier.id() + " - " + tier.name()
                    + " &7(x" + tier.multiplier() + ")");
            StringBuilder sb = new StringBuilder("&7  ");
            for (SetDef s : plugin.registry().sets().values()) {
                if (s.tier().id() == tier.id()) {
                    sb.append(s.id()).append(" (").append(s.armorClass()).append(", armor ")
                            .append(ItemFactory.trim(plugin.items().totalArmor(s))).append("), ");
                }
            }
            msg(sender, sb.toString().replaceAll(", $", ""));
        }
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "&cPakai: /armoryset info <set>");
            return;
        }
        SetDef set = plugin.registry().get(args[1]);
        if (set == null) {
            msg(sender, "&cSet '" + args[1] + "' tidak ditemukan.");
            return;
        }
        msg(sender, set.tier().color() + set.display() + " &8(" + set.tier().name() + ", x"
                + set.tier().multiplier() + ")");
        set.pieces().forEach((slot, name) -> msg(sender, "&7  " + slot + ": &f" + name));
        for (Map.Entry<Integer, List<Bonus>> e : set.bonuses().entrySet()) {
            msg(sender, "&7Bonus " + e.getKey() + " piece:");
            for (Bonus b : e.getValue()) {
                msg(sender, "&a  " + ItemFactory.describe(b, set.tier().multiplier()));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("nereusarmorysets.admin")) {
            return out;
        }
        if (args.length == 1) {
            out.addAll(List.of("menu", "give", "list", "info", "reload", "debug"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if ((args.length == 3 && args[0].equalsIgnoreCase("give"))
                || (args.length == 2 && args[0].equalsIgnoreCase("info"))) {
            out.addAll(plugin.registry().sets().keySet());
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("all", "head", "chest", "legs", "feet"));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
