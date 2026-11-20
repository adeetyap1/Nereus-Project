package my.nereus.intro.command;

import my.nereus.intro.NereusIntro;
import my.nereus.intro.config.IntroConfig;
import my.nereus.intro.manager.IntroService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /intro dan /nereusintro (alias aman bila /intro bentrok dengan plugin lain). */
public final class IntroCommand implements TabExecutor {

    private static final String ADMIN = "nereusintro.admin";

    private final NereusIntro plugin;
    private final java.util.Map<java.util.UUID, String> lastSound = new java.util.concurrent.ConcurrentHashMap<>();

    public IntroCommand(NereusIntro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        IntroConfig cfg = plugin.config();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission(ADMIN);

        switch (sub) {
            case "" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(cfg.message("player-only"));
                    return true;
                }
                report(sender, plugin.intro().start(p, false, admin, null), admin);
            }
            case "help", "?" -> help(sender, label, admin);
            case "play" -> {
                if (!requireAdmin(sender, admin)) return true;
                Player t = target(sender, args, 1);
                if (t == null) return true;
                report(sender, plugin.intro().start(t, false, true, null), true);
            }
            case "test" -> {
                if (!requireAdmin(sender, admin)) return true;
                Boolean bedrock = null;
                String name = null;
                for (int i = 1; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("--bedrock")) bedrock = true;
                    else if (args[i].equalsIgnoreCase("--java")) bedrock = false;
                    else name = args[i];
                }
                Player t = resolveTarget(sender, name);
                if (t == null) return true;
                report(sender, plugin.intro().start(t, true, true, bedrock), true);
            }
            case "skip" -> {
                Player t;
                boolean other = args.length > 1;
                if (other) {
                    if (!requireAdmin(sender, admin)) return true;
                    t = target(sender, args, 1);
                } else if (sender instanceof Player p) {
                    t = p;
                } else {
                    sender.sendMessage(cfg.message("player-only"));
                    return true;
                }
                if (t == null) return true;
                switch (plugin.intro().skip(t, admin)) {
                    case NONE_ACTIVE -> sender.sendMessage(cfg.message("not-active"));
                    case DISABLED -> sender.sendMessage(cfg.message("skip-disabled"));
                    case TOO_EARLY -> sender.sendMessage(cfg.message("skip-too-early"));
                    case CONFIRM -> sender.sendMessage(cfg.message("skip-confirm"));
                    case OK -> { /* pesan dikirim saat pemulihan selesai */ }
                }
            }
            case "reload" -> {
                if (!requireAdmin(sender, admin)) return true;
                plugin.reloadAll();
                sender.sendMessage(plugin.config().message("reloaded"));
            }
            case "reset" -> {
                if (!requireAdmin(sender, admin)) return true;
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/" + label + " reset <pemain>"));
                    return true;
                }
                var off = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (off == null) {
                    sender.sendMessage(cfg.message("player-not-found"));
                    return true;
                }
                plugin.progress().reset(off.getUniqueId());
                sender.sendMessage(cfg.message("reset-done", Placeholder.unparsed("player", args[1])));
            }
            case "sound" -> {
                if (!requireAdmin(sender, admin)) return true;
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(cfg.message("player-only"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(Component.text("/" + label + " sound <key> | stop   contoh: nereusintro:music.whiteroom"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("stop")) {
                    String last = lastSound.remove(p.getUniqueId());
                    if (last != null) p.stopSound(last);
                    p.sendMessage(Component.text("[TEST] Suara dihentikan."));
                } else {
                    String key = args[1].contains(":") ? args[1] : "minecraft:" + args[1];
                    lastSound.put(p.getUniqueId(), key);
                    p.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key(key),
                            net.kyori.adventure.sound.Sound.Source.RECORD, 1f, 1f), net.kyori.adventure.sound.Sound.Emitter.self());
                    p.sendMessage(Component.text("[TEST] Memutar " + key + " (" + (plugin.platform().isBedrock(p) ? "Bedrock" : "Java")
                            + "). Tidak terdengar = resource pack belum termuat di klien ini."));
                }
            }
            case "setpos" -> {
                if (!requireAdmin(sender, admin)) return true;
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(cfg.message("player-only"));
                    return true;
                }
                setPos(p, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "");
            }
            default -> help(sender, label, admin);
        }
        return true;
    }

    private void report(CommandSender sender, IntroService.StartResult r, boolean admin) {
        IntroConfig cfg = plugin.config();
        switch (r.status()) {
            case OK -> { /* sesi mulai; pesan lewat cinematic */ }
            case DISABLED -> sender.sendMessage(cfg.message("disabled"));
            case NO_TEST -> sender.sendMessage(cfg.message("no-permission"));
            case NOT_CONFIGURED -> {
                sender.sendMessage(cfg.message("not-configured", Placeholder.unparsed("problems", r.detail())));
                if (admin) sender.sendMessage(cfg.message("not-configured-admin-hint"));
            }
            case EMPTY_TIMELINE -> sender.sendMessage(cfg.message("empty-timeline"));
            case ACTIVE -> sender.sendMessage(cfg.message("already-active"));
            case ALREADY_COMPLETED -> sender.sendMessage(cfg.message("already-completed"));
            case RECOVERY_PENDING -> sender.sendMessage(cfg.message("recovery-pending"));
            case DEAD -> sender.sendMessage(cfg.message("dead"));
        }
    }

    private boolean requireAdmin(CommandSender s, boolean admin) {
        if (!admin) s.sendMessage(plugin.config().message("no-permission"));
        return admin;
    }

    private Player target(CommandSender sender, String[] args, int idx) {
        return resolveTarget(sender, args.length > idx ? args[idx] : null);
    }

    private Player resolveTarget(CommandSender sender, String name) {
        if (name == null) {
            if (sender instanceof Player p) return p;
            sender.sendMessage(plugin.config().message("player-only"));
            return null;
        }
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) sender.sendMessage(plugin.config().message("player-not-found"));
        return t;
    }

    private void setPos(Player p, String which) {
        IntroConfig cfg = plugin.config();
        String path = switch (which) {
            case "room" -> "locations.room";
            case "spawn" -> "locations.spawn";
            case "nerith" -> "locations.nerith";
            default -> null;
        };
        if (path == null) {
            p.sendMessage(cfg.message("location-invalid"));
            return;
        }
        Location l = p.getLocation();
        var c = plugin.getConfig();
        c.set(path + ".world", l.getWorld().getName());
        c.set(path + ".x", round(l.getX()));
        c.set(path + ".y", round(l.getY()));
        c.set(path + ".z", round(l.getZ()));
        c.set(path + ".yaw", round(l.getYaw()));
        c.set(path + ".pitch", round(l.getPitch()));
        if (which.equals("spawn")) c.set("locations.spawn.use-world-spawn-of", "");
        plugin.saveConfig();
        plugin.reloadAll();
        p.sendMessage(plugin.config().message("location-saved",
                Placeholder.unparsed("name", which),
                Placeholder.unparsed("where", String.format(Locale.ROOT, "%s %.1f %.1f %.1f (yaw %.0f)",
                        l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw()))));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private void help(CommandSender s, String label, boolean admin) {
        s.sendMessage(Component.text("NereusIntro — /" + label + " | skip" + (admin
                ? " | test [pemain] [--bedrock|--java] | play <pemain> | reload | reset <pemain> | setpos <room|spawn|nerith> | sound <key|stop>" : "")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            List<String> subs = admin
                    ? List.of("help", "skip", "test", "play", "reload", "reset", "setpos", "sound")
                    : List.of("help", "skip");
            for (String s : subs) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (admin && args.length >= 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
            if (sub.equals("setpos") && args.length == 2) {
                for (String s : List.of("room", "spawn", "nerith")) if (s.startsWith(cur)) out.add(s);
            } else if (List.of("test", "skip", "play", "reset").contains(sub)) {
                if (sub.equals("test")) {
                    for (String s : List.of("--bedrock", "--java")) if (s.startsWith(cur)) out.add(s);
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(cur)) out.add(p.getName());
                }
            }
        }
        return out;
    }
}
