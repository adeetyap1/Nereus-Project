package me.naook1.nereusfarmprice.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusfarmprice.FarmManager;
import me.naook1.nereusfarmprice.FarmManager.Crop;
import me.naook1.nereusfarmprice.NereusFarmPrice;
import me.naook1.nereusfarmprice.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /farmprice - opens the text menu; admin sub commands manage buffs. */
public final class FarmCommand implements TabExecutor {

    private final NereusFarmPrice plugin;

    public FarmCommand(NereusFarmPrice plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var m = plugin.messages();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "buff", "unbuff", "rotate", "reset", "reload", "debug" -> {
                if (!sender.hasPermission("farmprice.admin")) {
                    m.send(sender, "no-permission");
                } else {
                    admin(sender, sub, args);
                }
                return true;
            }
            case "help", "?" -> {
                m.sendList(sender, "usage");
                return true;
            }
            default -> {
            }
        }
        if (!(sender instanceof Player p)) {
            m.send(sender, "players-only");
            return true;
        }
        if (!p.hasPermission("farmprice.use")) {
            m.send(p, "no-permission");
            return true;
        }
        String mode = sub.equals("chat") || sub.equals("book") ? sub : plugin.getConfig().getString("display", "book");
        plugin.display().show(p, mode);
        return true;
    }

    private void admin(CommandSender s, String sub, String[] args) {
        var m = plugin.messages();
        FarmManager fm = plugin.farm();
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                m.send(s, "reloaded", "count", String.valueOf(plugin.prices().itemCount()));
            }
            case "rotate" -> {
                fm.rotate();
                m.send(s, "rotated");
            }
            case "reset" -> {
                fm.resetMarket();
                m.send(s, "reset-done");
            }
            case "unbuff" -> {
                Crop c = crop(s, args);
                if (c != null) {
                    fm.unbuff(c);
                    m.send(s, "unbuffed", "crop", c.name());
                }
            }
            case "buff" -> buff(s, args);
            case "debug" -> debug(s);
            default -> {
            }
        }
    }

    private Crop crop(CommandSender s, String[] args) {
        Crop c = args.length > 1 ? plugin.farm().crop(args[1]) : null;
        if (c == null) {
            List<String> ids = new ArrayList<>();
            plugin.farm().crops().forEach(x -> ids.add(x.id()));
            plugin.messages().send(s, "unknown-crop", "crop", args.length > 1 ? args[1] : "-",
                    "list", String.join(", ", ids));
        }
        return c;
    }

    private void buff(CommandSender s, String[] args) {
        var m = plugin.messages();
        FarmManager fm = plugin.farm();
        Crop c = crop(s, args);
        if (c == null) {
            return;
        }
        if (args.length < 3) {
            m.sendList(s, "usage");
            return;
        }
        double mult;
        String v = args[2].toLowerCase(Locale.ROOT);
        try {
            if (v.startsWith("x")) {
                mult = Double.parseDouble(v.substring(1));
            } else {
                double target = Double.parseDouble(v.replace("$", ""));
                double base = fm.basePrice(c);
                if (base <= 0) {
                    m.send(s, "no-price", "crop", c.name());
                    return;
                }
                mult = target / base;
            }
        } catch (NumberFormatException e) {
            m.send(s, "bad-number");
            return;
        }
        if (mult < 1.0) {
            m.send(s, "bad-number");
            return;
        }
        long duration = args.length > 3 ? TimeUtil.parse(args[3]) : plugin.getConfig().getLong("rotation.interval-hours", 6) * 3_600_000L;
        if (duration <= 0) {
            m.send(s, "bad-time");
            return;
        }
        fm.buff(c, mult, duration);
        m.send(s, "buffed", "crop", c.name(), "mult", String.format(Locale.ROOT, "%.2f", mult),
                "time", TimeUtil.left(plugin, duration));
    }

    private void debug(CommandSender s) {
        FarmManager fm = plugin.farm();
        s.sendMessage("NereusFarmPrice " + plugin.getDescription().getVersion());
        s.sendMessage("EconomyShopGUI: " + plugin.hook().status());
        s.sendMessage("Harga terbaca: " + plugin.prices().itemCount() + " item dari " + plugin.prices().fileCount() + " file");
        s.sendMessage("Penjualan terakhir: " + plugin.hook().lastSale());
        s.sendMessage("Error terakhir: " + plugin.hook().lastError() + " | item tanpa harga: " + plugin.hook().unknownItems());
        for (Crop c : fm.crops()) {
            s.sendMessage(" - " + c.id() + ": base " + fm.basePrice(c) + " | buff x"
                    + String.format(Locale.ROOT, "%.2f", fm.buffMult(c)) + " | pasar x"
                    + String.format(Locale.ROOT, "%.2f", fm.saturation(c)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("farmprice.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("chat", "book", "help"));
            if (admin) {
                subs.addAll(List.of("buff", "unbuff", "rotate", "reset", "reload", "debug"));
            }
            return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
        }
        if (admin && args.length == 2 && (args[0].equalsIgnoreCase("buff") || args[0].equalsIgnoreCase("unbuff"))) {
            List<String> ids = new ArrayList<>();
            plugin.farm().crops().forEach(c -> ids.add(c.id()));
            return StringUtil.copyPartialMatches(args[1], ids, new ArrayList<>());
        }
        if (admin && args.length == 3 && args[0].equalsIgnoreCase("buff")) {
            return StringUtil.copyPartialMatches(args[2], List.of("x1.5", "x2"), new ArrayList<>());
        }
        if (admin && args.length == 4 && args[0].equalsIgnoreCase("buff")) {
            return StringUtil.copyPartialMatches(args[3], List.of("1h", "6h", "1d"), new ArrayList<>());
        }
        return List.of();
    }
}
