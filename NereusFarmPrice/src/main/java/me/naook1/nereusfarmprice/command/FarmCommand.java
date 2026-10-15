package me.naook1.nereusfarmprice.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.naook1.nereusfarmprice.FarmManager;
import me.naook1.nereusfarmprice.NereusFarmPrice;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

/** /farmprice [chat|book] - shows today's boosted farms. Admin: rotate, reload, debug. */
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
            case "rotate", "reload", "debug" -> {
                if (!sender.hasPermission("farmprice.admin")) {
                    m.send(sender, "no-permission");
                } else {
                    admin(sender, sub);
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
        String mode = sub.equals("chat") || sub.equals("book") ? sub : plugin.getConfig().getString("display", "chat");
        plugin.display().show(p, mode);
        return true;
    }

    private void admin(CommandSender s, String sub) {
        var m = plugin.messages();
        switch (sub) {
            case "reload" -> {
                plugin.reloadAll();
                m.send(s, "reloaded", "count", String.valueOf(plugin.prices().itemCount()));
            }
            case "rotate" -> {
                if (plugin.farm().rotate(true)) {
                    m.send(s, "rotated");
                } else {
                    m.send(s, "no-valid");
                }
            }
            case "debug" -> debug(s);
            default -> {
            }
        }
    }

    private void debug(CommandSender s) {
        FarmManager fm = plugin.farm();
        var hook = plugin.hook();
        s.sendMessage("NereusFarmPrice " + plugin.getDescription().getVersion()
                + " | Minecraft " + Bukkit.getMinecraftVersion() + " | Java " + System.getProperty("java.version"));
        s.sendMessage("EconomyShopGUI: " + hook.esguiVersion());
        s.sendMessage("Integrasi: " + hook.status());
        s.sendMessage("Harga terbaca: " + plugin.prices().itemCount() + " item dari " + plugin.prices().fileCount() + " file");
        s.sendMessage("Boost: " + fm.percentText() + "% | " + fm.count() + " farm | " + fm.durationHours() + " jam | zona "
                + fm.zone() + " | ganti jam " + fm.rotationTime());
        s.sendMessage("Aktif sekarang:");
        for (Material mat : fm.active()) {
            s.sendMessage(" - " + mat + ": normal " + fm.normalPrice(mat) + " -> boost "
                    + (fm.normalPrice(mat) * (1 + fm.percent() / 100.0)));
        }
        s.sendMessage("Kandidat tanpa harga jual: " + noPrice(fm));
        s.sendMessage("Transaksi boost diterapkan: " + hook.applied() + " | tidak cocok: " + hook.mismatches()
                + " | bonus via Vault: " + hook.fallbacks());
        s.sendMessage("Transaksi terakhir: " + hook.lastSale());
        s.sendMessage("Error terakhir: " + hook.lastError());
    }

    private String noPrice(FarmManager fm) {
        List<String> out = new ArrayList<>();
        for (Material m : plugin.farm().candidateMaterials()) {
            if (fm.normalPrice(m) <= 0) {
                out.add(m.name());
            }
        }
        return out.isEmpty() ? "-" : String.join(", ", out);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("chat", "book", "help"));
            if (sender.hasPermission("farmprice.admin")) {
                subs.addAll(List.of("rotate", "reload", "debug"));
            }
            return StringUtil.copyPartialMatches(args[0], subs, new ArrayList<>());
        }
        return List.of();
    }
}
