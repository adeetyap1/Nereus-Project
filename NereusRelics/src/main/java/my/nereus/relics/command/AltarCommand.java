package my.nereus.relics.command;

import my.nereus.relics.NereusRelics;
import my.nereus.relics.altar.Altar;
import my.nereus.relics.altar.AltarLocation;
import my.nereus.relics.offering.Requirement;
import my.nereus.relics.util.Text;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AltarCommand implements CommandExecutor, TabCompleter {

    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final List<String> SUBS = List.of("create", "setpos", "setweapon", "additem", "removeitem", "setamount",
            "setname", "setlore", "setduration", "setannouncement", "activate", "deactivate", "reset", "remove", "info",
            "list", "set", "reload", "help");
    private static final List<String> NEEDS_ID = SUBS.stream().filter(s -> !List.of("list", "reload", "help", "create").contains(s)).toList();
    private static final List<String> SET_KEYS = List.of("ambient-particle", "ambient-sound", "ritual-particle", "ritual-sound",
            "claim-sound", "offer-sound", "offer-radius", "hologram-height", "weapon-height");

    private final NereusRelics plugin;

    public AltarCommand(NereusRelics plugin) {
        this.plugin = plugin;
    }

    private void say(CommandSender s, String legacy) {
        plugin.tellRaw(s, legacy);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("nereusrelics.admin")) {
            say(sender, "&cKamu tidak punya izin.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (sub) {
                case "list" -> list(sender);
                case "reload" -> {
                    plugin.reloadAll();
                    say(sender, "&aconfig.yml dimuat ulang (interval task butuh restart).");
                }
                case "create" -> create(sender, args);
                default -> {
                    if (args.length < 2) {
                        say(sender, "&cPakai: /altar " + sub + " <id> ...");
                        return true;
                    }
                    Altar a = plugin.altars().get(args[1]);
                    if (a == null) {
                        say(sender, "&cAltar '" + args[1] + "' tidak ada. Lihat /altar list.");
                        return true;
                    }
                    withAltar(sender, sub, a, args);
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Error command /altar " + sub + ": " + e);
            say(sender, "&cTerjadi galat: " + e.getMessage());
        }
        return true;
    }

    private void help(CommandSender s) {
        for (String l : new String[]{
                "&6&lNereusRelics &7- perintah admin",
                "&e/altar create <id> &7buat altar baru",
                "&e/altar setpos <id> &7posisi altar = tempatmu berdiri",
                "&e/altar setweapon <id> &7senjata = item di tangan",
                "&e/altar additem <id> [jumlah] [material|exact] &7tambah persembahan (item di tangan)",
                "&e/altar removeitem <id> <no> &7hapus persembahan",
                "&e/altar setamount <id> <no> <jumlah> &7ubah jumlah",
                "&e/altar setname <id> <nama> &7nama senjata (boleh &-warna)",
                "&e/altar setlore <id> <baris|baris|...> &7deskripsi senjata",
                "&e/altar setduration <id> <detik> &7durasi ritual",
                "&e/altar setannouncement <id> <PUBLIC|HIDDEN|SECRET|ADMIN> [teks] &7mode lokasi",
                "&e/altar set <id> <kunci> <nilai> &7efek/ketinggian per altar",
                "&e/altar activate|deactivate|reset|remove|info <id>",
                "&e/altar list &7daftar altar"}) say(s, l);
    }

    private void create(CommandSender s, String[] args) {
        if (args.length < 2) {
            say(s, "&cPakai: /altar create <id>");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!ID.matcher(id).matches()) {
            say(s, "&cID hanya huruf kecil, angka, - dan _ (maks 32).");
            return;
        }
        if (plugin.altars().create(id) == null) {
            say(s, "&cAltar '" + id + "' sudah ada.");
            return;
        }
        say(s, "&aAltar &f" + id + " &adibuat. Atur dengan setpos, setweapon, additem, lalu activate.");
    }

    private void list(CommandSender s) {
        if (plugin.altars().all().isEmpty()) {
            say(s, "&7Belum ada altar.");
            return;
        }
        for (Altar a : plugin.altars().all()) {
            say(s, "&f" + a.id() + " &8- &e" + a.state() + " &8- &7" + a.weaponName()
                    + (a.location() == null ? " &8(tanpa posisi)" : " &8@ &7" + a.location().describe()));
        }
    }

    private boolean editable(CommandSender s, Altar a) {
        if (a.isRunning()) {
            say(s, "&cAltar sedang " + a.state() + ". Nonaktifkan dulu (/altar deactivate " + a.id() + ").");
            return false;
        }
        return true;
    }

    private void withAltar(CommandSender s, String sub, Altar a, String[] args) {
        switch (sub) {
            case "setpos" -> {
                Player p = player(s);
                if (p == null || !editable(s, a)) return;
                a.location(AltarLocation.of(p.getLocation()));
                plugin.altars().save(a);
                say(s, "&aPosisi altar diatur ke &f" + a.location().describe());
            }
            case "setweapon" -> {
                Player p = player(s);
                if (p == null || !editable(s, a)) return;
                ItemStack held = p.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    say(s, "&cPegang item senjata di tangan utama.");
                    return;
                }
                ItemStack w = held.clone();
                w.setAmount(1);
                a.weaponItem(w);
                plugin.altars().save(a);
                say(s, "&aSenjata diatur: &f" + Text.itemLabel(w) + " &7(item di tanganmu tidak berkurang).");
            }
            case "additem" -> {
                Player p = player(s);
                if (p == null || !editable(s, a)) return;
                ItemStack held = p.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    say(s, "&cPegang item persembahan di tangan utama.");
                    return;
                }
                int amount = held.getAmount();
                Requirement.Mode forced = null;
                for (int i = 2; i < args.length; i++) {
                    String x = args[i].toLowerCase(Locale.ROOT);
                    if (x.equals("exact")) forced = Requirement.Mode.EXACT;
                    else if (x.equals("material")) forced = Requirement.Mode.MATERIAL;
                    else amount = parseInt(s, x, 1, 100000);
                    if (amount < 0) return;
                }
                if (a.requirements().size() >= 10) {
                    say(s, "&cMaksimal 10 jenis persembahan.");
                    return;
                }
                Requirement r = Requirement.fromHeld(held, amount, forced);
                a.requirements().add(r);
                plugin.altars().save(a);
                say(s, "&aDitambahkan #" + a.requirements().size() + ": &f" + r.amount() + "x " + r.label()
                        + " &7(mode " + r.mode() + ")");
            }
            case "removeitem" -> {
                if (!editable(s, a)) return;
                int n = index(s, a, args, 2);
                if (n < 0) return;
                Requirement r = a.requirements().remove(n);
                plugin.altars().save(a);
                say(s, "&aDihapus: &f" + r.label());
            }
            case "setamount" -> {
                if (!editable(s, a)) return;
                int n = index(s, a, args, 2);
                if (n < 0) return;
                if (args.length < 4) {
                    say(s, "&cPakai: /altar setamount <id> <no> <jumlah>");
                    return;
                }
                int v = parseInt(s, args[3], 1, 100000);
                if (v < 0) return;
                a.requirements().get(n).amount(v);
                plugin.altars().save(a);
                say(s, "&aJumlah #" + (n + 1) + " = " + v);
            }
            case "setname" -> {
                if (!editable(s, a)) return;
                if (args.length < 3) {
                    say(s, "&cPakai: /altar setname <id> <nama>");
                    return;
                }
                a.weaponName(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                plugin.altars().save(a);
                say(s, "&aNama senjata: &r" + a.weaponName());
            }
            case "setlore" -> {
                if (!editable(s, a)) return;
                if (args.length < 3) {
                    say(s, "&cPakai: /altar setlore <id> <baris1|baris2|...>");
                    return;
                }
                a.lore(Text.splitPipe(String.join(" ", Arrays.copyOfRange(args, 2, args.length))));
                plugin.altars().save(a);
                say(s, "&aLore diatur (" + a.lore().size() + " baris).");
            }
            case "setduration" -> {
                if (!editable(s, a)) return;
                if (args.length < 3) {
                    say(s, "&cPakai: /altar setduration <id> <detik>");
                    return;
                }
                int v = parseInt(s, args[2], 1, 120);
                if (v < 0) return;
                a.ritualSeconds(v);
                plugin.altars().save(a);
                say(s, "&aDurasi ritual: " + a.ritualSeconds() + " detik.");
            }
            case "setannouncement" -> {
                if (!editable(s, a)) return;
                if (args.length < 3) {
                    say(s, "&cPakai: /altar setannouncement <id> <PUBLIC|HIDDEN|SECRET|ADMIN> [teks, pisah baris dengan |]");
                    return;
                }
                Altar.AnnounceMode mode;
                try {
                    mode = Altar.AnnounceMode.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    say(s, "&cMode: PUBLIC, HIDDEN, SECRET, atau ADMIN.");
                    return;
                }
                a.announceMode(mode);
                a.announceText(args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "");
                plugin.altars().save(a);
                say(s, "&aMode pengumuman: &f" + mode + (mode == Altar.AnnounceMode.ADMIN && a.announceText().isBlank()
                        ? " &e(teks kosong: lokasi tidak ditampilkan)" : ""));
            }
            case "set" -> {
                if (!editable(s, a)) return;
                if (args.length < 4 || !SET_KEYS.contains(args[2].toLowerCase(Locale.ROOT))) {
                    say(s, "&cPakai: /altar set <id> <kunci> <nilai>. Kunci: " + String.join(", ", SET_KEYS)
                            + ". Nilai '-' menghapus (pakai default config).");
                    return;
                }
                String key = args[2].toLowerCase(Locale.ROOT);
                String val = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                if (key.equals("offer-radius") || key.endsWith("height")) {
                    if (!val.equals("-")) {
                        try {
                            Double.parseDouble(val);
                        } catch (NumberFormatException e) {
                            say(s, "&cNilai harus angka.");
                            return;
                        }
                    }
                }
                if (val.equals("-")) a.settings().remove(key);
                else a.settings().put(key, val);
                plugin.altars().save(a);
                say(s, "&a" + key + " = " + (val.equals("-") ? "(default)" : val));
            }
            case "activate" -> done(s, plugin.altars().activate(a), "&aAltar &f" + a.id() + " &aAKTIF dan diumumkan.");
            case "deactivate" -> done(s, plugin.altars().deactivate(a), "&aAltar dinonaktifkan; persembahan dikembalikan.");
            case "reset" -> done(s, plugin.altars().reset(a), "&aAltar di-reset ke INACTIVE.");
            case "remove" -> done(s, plugin.altars().remove(a), "&aAltar dihapus.");
            case "info" -> info(s, a);
            default -> say(s, "&cSubcommand tidak dikenal. /altar help");
        }
    }

    private void done(CommandSender s, String err, String ok) {
        say(s, err == null ? ok : "&c" + err);
    }

    private void info(CommandSender s, Altar a) {
        say(s, "&6Altar &f" + a.id() + " &8- &e" + a.state());
        say(s, "&7Senjata: &r" + a.weaponName() + " &8| &7Ritual: &f" + a.ritualSeconds() + "s &8| &7Pengumuman: &f" + a.announceMode());
        say(s, "&7Posisi: &f" + (a.location() == null ? "-" : a.location().describe()));
        say(s, "&7Item senjata: &f" + (a.weaponItem() == null ? "-" : Text.itemLabel(a.weaponItem())));
        int i = 1;
        for (Requirement r : a.requirements()) {
            say(s, "&7 #" + i++ + " &f" + r.amount() + "x " + r.label() + " &8(" + r.mode() + ")");
        }
        if (!a.settings().isEmpty()) say(s, "&7Setting: &f" + a.settings());
        if (a.claimant() != null) say(s, "&7Pemegang ritual: &f" + plugin.nameOf(a.claimant()));
        if (a.ownerUuid() != null) say(s, "&7Pemilik: &f" + a.ownerName());
        for (String l : plugin.offerings().progressLines(a)) say(s, "&7 progres &f" + l);
        say(s, "&7Entity: weapon=" + (plugin.weapons().find(a) != null) + " hologram=" + (plugin.holograms().find(a) != null));
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        say(s, "&cPerintah ini hanya untuk pemain di dalam game.");
        return null;
    }

    private int index(CommandSender s, Altar a, String[] args, int pos) {
        if (args.length <= pos) {
            say(s, "&cSebutkan nomor persembahan (lihat /altar info).");
            return -1;
        }
        int n = parseInt(s, args[pos], 1, a.requirements().size());
        return n < 0 ? -1 : n - 1;
    }

    private int parseInt(CommandSender s, String v, int min, int max) {
        try {
            int n = Integer.parseInt(v);
            if (n < min || n > max) {
                say(s, "&cAngka harus " + min + " sampai " + max + ".");
                return -1;
            }
            return n;
        } catch (NumberFormatException e) {
            say(s, "&c'" + v + "' bukan angka.");
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("nereusrelics.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && NEEDS_ID.contains(args[0].toLowerCase(Locale.ROOT))) {
            for (Altar a : plugin.altars().all()) if (a.id().startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(a.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setannouncement")) {
            for (String m : List.of("PUBLIC", "HIDDEN", "SECRET", "ADMIN")) if (m.startsWith(args[2].toUpperCase(Locale.ROOT))) out.add(m);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            for (String k : SET_KEYS) if (k.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(k);
        } else if (args.length >= 3 && args[0].equalsIgnoreCase("additem")) {
            for (String k : List.of("exact", "material")) if (k.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))) out.add(k);
        }
        return out;
    }
}
