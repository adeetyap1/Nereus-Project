package me.naook1.nereussize;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** NereusSize - ubah ukuran badan pemain (atribut scale). Developer: naook1/adityap */
public final class NereusSize extends JavaPlugin implements TabExecutor, Listener {

    private static final double ABS_MIN = 0.0625;
    private static final double ABS_MAX = 16.0;

    private FileConfiguration messages;
    private final Map<UUID, Long> lastChange = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMessages();
        var cmd = getCommand("ukuran");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("NereusSize aktif.");
    }

    private void loadMessages() {
        File f = new File(getDataFolder(), "messages.yml");
        if (!f.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(f);
        var in = getResource("messages.yml");
        if (in != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    private Component msg(String key, String... pairs) {
        String s = messages.getString("prefix", "") + messages.getString(key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    private static String fmt(double d) {
        String s = String.format(Locale.US, "%.2f", d);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    /** Returns {min, max} for this player. */
    private double[] limits(Player p) {
        if (p.hasPermission("nereus.size.bypass")) {
            return new double[]{ABS_MIN, ABS_MAX};
        }
        double min = getConfig().getDouble("min", 0.7);
        double max = getConfig().getDouble("max", 1.5);
        List<Map<?, ?>> tiers = getConfig().getMapList("tiers");
        for (Map<?, ?> t : tiers) {
            Object perm = t.get("permission");
            if (perm != null && p.hasPermission(String.valueOf(perm))) {
                Object tmin = t.get("min");
                Object tmax = t.get("max");
                if (tmin instanceof Number n) {
                    min = Math.min(min, n.doubleValue());
                }
                if (tmax instanceof Number n) {
                    max = Math.max(max, n.doubleValue());
                }
            }
        }
        return new double[]{Math.max(ABS_MIN, min), Math.min(ABS_MAX, max)};
    }

    private double current(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_SCALE);
        return a == null ? 1.0 : a.getBaseValue();
    }

    private void apply(Player p, double v) {
        AttributeInstance a = p.getAttribute(Attribute.GENERIC_SCALE);
        if (a != null) {
            a.setBaseValue(Math.max(ABS_MIN, Math.min(ABS_MAX, v)));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("nereus.size.others")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            reloadConfig();
            loadMessages();
            sender.sendMessage(msg("reloaded"));
            return true;
        }

        // /ukuran <pemain> <angka>
        if (args.length >= 2) {
            if (!sender.hasPermission("nereus.size.others")) {
                sender.sendMessage(msg("no-permission"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(msg("not-found"));
                return true;
            }
            double v;
            if (args[1].equalsIgnoreCase("reset")) {
                v = 1.0;
            } else {
                Double parsed = parse(args[1]);
                if (parsed == null) {
                    sender.sendMessage(msg("invalid"));
                    return true;
                }
                v = parsed;
            }
            apply(target, v);
            sender.sendMessage(msg("set-other", "player", target.getName(), "size", fmt(current(target))));
            if (!target.equals(sender)) {
                target.sendMessage(msg("set-by", "size", fmt(current(target))));
            }
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(msg("player-only"));
            return true;
        }
        if (!p.hasPermission("nereus.size.use")) {
            p.sendMessage(msg("no-permission"));
            return true;
        }
        double[] lim = limits(p);

        if (args.length == 0) {
            p.sendMessage(msg("current", "size", fmt(current(p)), "min", fmt(lim[0]), "max", fmt(lim[1])));
            return true;
        }

        boolean bypass = p.hasPermission("nereus.size.bypass");
        if (!bypass && getConfig().getStringList("disabled-worlds").contains(p.getWorld().getName())) {
            p.sendMessage(msg("disabled-world"));
            return true;
        }
        int cd = getConfig().getInt("cooldown-seconds", 5);
        if (!bypass && cd > 0) {
            long now = System.currentTimeMillis();
            Long last = lastChange.get(p.getUniqueId());
            if (last != null && now - last < cd * 1000L) {
                long left = (cd * 1000L - (now - last) + 999) / 1000;
                p.sendMessage(msg("cooldown", "seconds", String.valueOf(left)));
                return true;
            }
        }

        if (args[0].equalsIgnoreCase("reset")) {
            apply(p, 1.0);
            lastChange.put(p.getUniqueId(), System.currentTimeMillis());
            p.sendMessage(msg("reset"));
            return true;
        }

        Double v = parse(args[0]);
        if (v == null) {
            p.sendMessage(msg("invalid"));
            return true;
        }
        if (v < lim[0] - 1e-9) {
            p.sendMessage(msg("too-small", "min", fmt(lim[0])));
            return true;
        }
        if (v > lim[1] + 1e-9) {
            p.sendMessage(msg("too-big", "max", fmt(lim[1])));
            return true;
        }
        apply(p, v);
        lastChange.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendMessage(msg("set", "size", fmt(current(p))));
        return true;
    }

    private static Double parse(String s) {
        try {
            double d = Double.parseDouble(s.replace(',', '.'));
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String a = args[0].toLowerCase(Locale.ROOT);
            for (String s : new String[]{"reset", "0.8", "1", "1.2", "1.5"}) {
                if (s.startsWith(a)) {
                    out.add(s);
                }
            }
            if (sender.hasPermission("nereus.size.others")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(a)) {
                        out.add(p.getName());
                    }
                }
                if ("reload".startsWith(a)) {
                    out.add("reload");
                }
            }
        } else if (args.length == 2 && sender.hasPermission("nereus.size.others")) {
            for (String s : new String[]{"reset", "0.5", "1", "2"}) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getConfig().getBoolean("enforce-on-join", true)) {
            return;
        }
        Player p = e.getPlayer();
        if (p.hasPermission("nereus.size.bypass")) {
            return;
        }
        double[] lim = limits(p);
        double cur = current(p);
        if (cur < lim[0] || cur > lim[1]) {
            // pemain tanpa izin sama sekali kembali normal, selain itu dijepit ke batas
            double target = Math.abs(cur - 1.0) < 1e-9 ? 1.0 : Math.max(lim[0], Math.min(lim[1], cur));
            apply(p, target);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastChange.remove(e.getPlayer().getUniqueId());
    }
}
