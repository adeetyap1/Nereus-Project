package my.nereus.duel;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DuelCommand implements CommandExecutor, TabCompleter {

    /** Pemegang inventory menu: slot -> mode ("own", id kit, atau "__leave"). */
    static final class MenuHolder implements InventoryHolder {
        private final Map<Integer, String> modes = new HashMap<>();
        private Inventory inv;

        Map<Integer, String> modes() { return modes; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final NereusDuel plugin;

    DuelCommand(NereusDuel plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) {
            if (!s.hasPermission("nereusduel.admin")) {
                plugin.tell(s, "no-permission");
                return true;
            }
            admin(s, a);
            return true;
        }
        if (sub.equals("top")) {
            plugin.raw(s, "&6Top Duel");
            int i = 1;
            for (Stats.Entry e : plugin.stats().top(10)) {
                plugin.raw(s, "&e" + i++ + ". &f" + e.name + " &8- &a" + e.wins + "W &c" + e.losses + "L");
            }
            return true;
        }
        if (!(s instanceof Player p)) {
            plugin.raw(s, "Perintah ini untuk pemain.");
            return true;
        }
        switch (sub) {
            case "queue", "join" -> plugin.duels().join(p, a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "own");
            case "leave", "keluar", "surrender" -> plugin.duels().leave(p);
            case "accept", "terima" -> plugin.duels().accept(p);
            case "deny", "tolak" -> plugin.duels().deny(p);
            case "challenge", "tantang" -> {
                if (a.length < 2) {
                    plugin.raw(p, "&cPakai: /duel challenge <pemain> [own|" + kitIds() + "]");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) {
                    plugin.tell(p, "player-not-found");
                    return true;
                }
                plugin.duels().challenge(p, t, a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "own");
            }
            case "stats" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : p;
                if (t == null) {
                    plugin.tell(p, "player-not-found");
                    return true;
                }
                Stats.Entry e = plugin.stats().get(t.getUniqueId());
                plugin.tell(p, "stats", "player", t.getName(), "wins", String.valueOf(e.wins), "losses", String.valueOf(e.losses));
            }
            default -> menu(p);
        }
        return true;
    }

    private String kitIds() {
        List<String> ids = new ArrayList<>();
        for (KitManager.Kit k : plugin.kits().all()) ids.add(k.id);
        return String.join("|", ids);
    }

    private void menu(Player p) {
        MenuHolder h = new MenuHolder();
        Inventory inv = Bukkit.createInventory(h, 27, Text.color(plugin.getConfig().getString("menu.title", "Duel")));
        h.inv = inv;

        ItemStack own = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta om = own.getItemMeta();
        om.displayName(Text.item(plugin.getConfig().getString("menu.own-name", "&6Armor Sendiri")));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String l : plugin.getConfig().getStringList("menu.own-lore")) lore.add(Text.item(l));
        lore.add(Text.item(""));
        lore.add(Text.item("&eKlik untuk masuk antrean"));
        om.lore(lore);
        own.setItemMeta(om);
        inv.setItem(11, own);
        h.modes.put(11, "own");

        int slot = 12;
        for (KitManager.Kit k : plugin.kits().all()) {
            if (slot > 16) break;
            ItemStack it = new ItemStack(k.icon);
            ItemMeta m = it.getItemMeta();
            m.displayName(Text.item(k.name));
            m.lore(List.of(Text.item("&7Semua pemain memakai kit yang sama."), Text.item("&7Kit hilang otomatis setelah duel."),
                    Text.item(""), Text.item("&eKlik untuk masuk antrean")));
            it.setItemMeta(m);
            inv.setItem(slot, it);
            h.modes.put(slot, k.id);
            slot++;
        }

        Stats.Entry e = plugin.stats().get(p.getUniqueId());
        ItemStack st = new ItemStack(Material.PAPER);
        ItemMeta sm = st.getItemMeta();
        sm.displayName(Text.item("&6Statistikmu"));
        sm.lore(List.of(Text.item("&aMenang: &f" + e.wins), Text.item("&cKalah: &f" + e.losses)));
        st.setItemMeta(sm);
        inv.setItem(4, st);

        ItemStack out = new ItemStack(Material.BARRIER);
        ItemMeta bm = out.getItemMeta();
        bm.displayName(Text.item("&cKeluar antrean / menyerah"));
        out.setItemMeta(bm);
        inv.setItem(22, out);
        h.modes.put(22, "__leave");

        p.openInventory(inv);
    }

    private void admin(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "help";
        if (!sub.equals("arena")) {
            if (sub.equals("reload")) {
                plugin.reloadConfig();
                plugin.kits().load();
                plugin.raw(s, "&aconfig dan kit dimuat ulang.");
            } else if (sub.equals("forceend") && a.length > 2) {
                Player t = Bukkit.getPlayerExact(a[2]);
                Match m = t == null ? null : plugin.duels().matchOf(t.getUniqueId());
                if (m == null) {
                    plugin.raw(s, "&cPemain tidak sedang duel.");
                } else {
                    plugin.duels().endMatch(m, null, true);
                    plugin.raw(s, "&aDuel dihentikan, inventory dikembalikan.");
                }
            } else {
                plugin.raw(s, "&e/duel admin arena create <id> &8| &esetspawn <id> <1|2> &8| &eremove <id> &8| &elist");
                plugin.raw(s, "&e/duel admin forceend <pemain> &8| &ereload");
            }
            return;
        }
        String act = a.length > 2 ? a[2].toLowerCase(Locale.ROOT) : "list";
        switch (act) {
            case "create" -> {
                if (a.length < 4) {
                    plugin.raw(s, "&cPakai: /duel admin arena create <id>");
                    return;
                }
                plugin.raw(s, plugin.arenas().create(a[3]) ? "&aArena dibuat. Atur dua titik: /duel admin arena setspawn " + a[3] + " 1 dan 2"
                        : "&cArena itu sudah ada.");
            }
            case "setspawn" -> {
                if (!(s instanceof Player p) || a.length < 5) {
                    plugin.raw(s, "&cPakai (di game): /duel admin arena setspawn <id> <1|2>");
                    return;
                }
                Arena ar = plugin.arenas().get(a[3]);
                if (ar == null) {
                    plugin.raw(s, "&cArena tidak ada.");
                    return;
                }
                if (ar.busy != null) {
                    plugin.raw(s, "&cArena sedang dipakai.");
                    return;
                }
                if (a[4].equals("1")) ar.s1 = Spot.of(p.getLocation());
                else if (a[4].equals("2")) ar.s2 = Spot.of(p.getLocation());
                else {
                    plugin.raw(s, "&cTitik harus 1 atau 2.");
                    return;
                }
                plugin.arenas().save();
                plugin.raw(s, "&aSpawn " + a[4] + " diatur." + (ar.ready() ? " &7Arena siap dipakai." : ""));
            }
            case "remove" -> {
                if (a.length < 4) return;
                plugin.raw(s, plugin.arenas().remove(a[3]) ? "&aArena dihapus." : "&cArena tidak ada atau sedang dipakai.");
            }
            default -> {
                if (plugin.arenas().all().isEmpty()) plugin.raw(s, "&7Belum ada arena.");
                for (Arena ar : plugin.arenas().all()) {
                    plugin.raw(s, "&f" + ar.id + " &8- " + (ar.ready() ? "&asiap" : "&ctidak lengkap") + (ar.busy != null ? " &e(dipakai)" : ""));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("queue", "leave", "challenge", "accept", "deny", "stats", "top"));
            if (s.hasPermission("nereusduel.admin")) out.add("admin");
        } else if (a.length == 2 && (a[0].equalsIgnoreCase("challenge") || a[0].equalsIgnoreCase("stats"))) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        } else if ((a.length == 2 && a[0].equalsIgnoreCase("queue")) || (a.length == 3 && a[0].equalsIgnoreCase("challenge"))) {
            out.add("own");
            for (KitManager.Kit k : plugin.kits().all()) out.add(k.id);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("admin")) {
            out.addAll(List.of("arena", "forceend", "reload"));
        } else if (a.length == 3 && a[1].equalsIgnoreCase("arena")) {
            out.addAll(List.of("create", "setspawn", "remove", "list"));
        } else if (a.length == 4 && a[1].equalsIgnoreCase("arena")) {
            for (Arena ar : plugin.arenas().all()) out.add(ar.id);
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
