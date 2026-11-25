package my.nereus.afk;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AfkCommand implements CommandExecutor, TabCompleter {
    private final NereusAFK plugin;

    AfkCommand(NereusAFK plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            if (!s.hasPermission("nereusafk.admin")) {
                plugin.tell(s, "no-permission");
                return true;
            }
            admin(s, a);
            return true;
        }
        if (!(s instanceof Player p)) {
            plugin.raw(s, "Perintah ini untuk pemain.");
            return true;
        }
        if (sub.equals("shop") && plugin.getConfig().getBoolean("shop.allow-command", true)) {
            plugin.shop().open(p);
            return true;
        }
        PlayerData d = plugin.store().get(p.getUniqueId());
        int cap = plugin.getConfig().getInt("afk.daily-cap", 180);
        plugin.tell(p, "info", "fragments", String.valueOf(d.fragments), "today", String.valueOf(d.today),
                "cap", cap == 0 ? "∞" : String.valueOf(cap), "total", String.valueOf(d.total));
        return true;
    }

    private void admin(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "zone" -> zone(s, a);
            case "setshop" -> {
                if (!(s instanceof Player p)) return;
                Location l = p.getLocation();
                l.setYaw(l.getYaw() + 180f);
                l.setPitch(0f);
                plugin.shop().spawnShop(l);
                plugin.raw(s, "&aVillager toko dibuat di sini.");
            }
            case "removeshop" -> {
                if (!(s instanceof Player p)) return;
                plugin.raw(s, plugin.shop().removeNearest(p.getLocation()) ? "&aVillager toko terdekat dihapus." : "&cTidak ada villager toko dalam 5 blok.");
            }
            case "give", "take", "set" -> money(s, a, sub);
            case "reload" -> {
                plugin.reloadConfig();
                plugin.raw(s, "&aconfig.yml dimuat ulang.");
            }
            default -> {
                plugin.raw(s, "&e/afk admin zone create <id> [radius] [tinggi] &8| &ezone remove <id> &8| &ezone list");
                plugin.raw(s, "&e/afk admin setshop &8| &eremoveshop &8| &egive|take|set <pemain> <jumlah> &8| &ereload");
            }
        }
    }

    private void zone(CommandSender s, String[] a) {
        String act = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        switch (act) {
            case "create" -> {
                if (!(s instanceof Player p) || a.length < 4) {
                    plugin.raw(s, "&cPakai: /afk admin zone create <id> [radius] [tinggi] (di dalam game)");
                    return;
                }
                double r = a.length > 4 ? num(a[4], 8) : 8;
                double h = a.length > 5 ? num(a[5], 6) : 6;
                boolean ok = plugin.zones().create(a[3], p.getLocation(), Math.max(1, r), Math.max(1, h));
                plugin.raw(s, ok ? "&aZona &f" + a[3] + " &adibuat (radius " + r + ", tinggi " + h + ")." : "&cZona dengan ID itu sudah ada.");
            }
            case "remove" -> {
                if (a.length < 4) return;
                plugin.raw(s, plugin.zones().remove(a[3]) ? "&aZona dihapus." : "&cZona tidak ada.");
            }
            default -> {
                if (plugin.zones().all().isEmpty()) plugin.raw(s, "&7Belum ada zona.");
                for (Zone z : plugin.zones().all()) {
                    plugin.raw(s, "&f" + z.id() + " &8- &7" + z.world() + " " + (int) z.x() + "," + (int) z.y() + "," + (int) z.z()
                            + " r=" + z.radius() + " t=" + z.height());
                }
            }
        }
    }

    private void money(CommandSender s, String[] a, String sub) {
        if (a.length < 4) {
            plugin.raw(s, "&cPakai: /afk admin " + sub + " <pemain> <jumlah>");
            return;
        }
        OfflinePlayer op = Bukkit.getOfflinePlayer(a[2]);
        if (!op.hasPlayedBefore() && !op.isOnline()) {
            plugin.raw(s, "&cPemain tidak dikenal.");
            return;
        }
        long n = (long) num(a[3], -1);
        if (n < 0) {
            plugin.raw(s, "&cJumlah harus angka positif.");
            return;
        }
        PlayerData d = plugin.store().get(op.getUniqueId());
        switch (sub) {
            case "give" -> d.fragments += n;
            case "take" -> d.fragments = Math.max(0, d.fragments - n);
            default -> d.fragments = n;
        }
        plugin.store().markDirty();
        plugin.raw(s, "&aFragmen " + a[2] + " sekarang &f" + d.fragments);
    }

    private double num(String v, double def) {
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.add("shop");
            if (s.hasPermission("nereusafk.admin")) out.add("admin");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin") && s.hasPermission("nereusafk.admin")) {
            out.addAll(List.of("zone", "setshop", "removeshop", "give", "take", "set", "reload"));
        } else if (a.length == 3 && a[1].equalsIgnoreCase("zone")) {
            out.addAll(List.of("create", "remove", "list"));
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.startsWith(last));
        return out;
    }
}
