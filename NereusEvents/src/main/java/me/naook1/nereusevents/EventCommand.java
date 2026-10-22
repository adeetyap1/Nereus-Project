package me.naook1.nereusevents;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class EventCommand implements CommandExecutor, TabCompleter {

    private final NereusEvents plugin;

    public EventCommand(NereusEvents plugin) {
        this.plugin = plugin;
    }

    private void send(CommandSender s, String key, String... kv) {
        var m = plugin.messages();
        s.sendMessage(Text.parse(m.get("prefix") + Text.fill(m.get(key), kv)));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        EventManager em = plugin.events();
        String sub = a.length == 0 ? "list" : a[0].toLowerCase();
        boolean admin = s.hasPermission("nereusevents.admin");
        switch (sub) {
            case "list" -> {
                if (em.active().isEmpty()) {
                    send(s, "none-active");
                } else {
                    for (var act : em.active()) {
                        s.sendMessage(Text.parse(Text.fill(plugin.messages().get("active-line"), "name", act.def().name(),
                                "left", EventManager.fmt(act.end() - System.currentTimeMillis()))));
                    }
                }
                if (em.nextAuto() > 0) {
                    send(s, "next-auto", "left", EventManager.fmt(em.nextAuto() - System.currentTimeMillis()));
                }
                if (s instanceof Player p && a.length == 0) {
                    plugin.gui().open(p);
                }
            }
            case "gui" -> {
                if (s instanceof Player p) {
                    plugin.gui().open(p);
                }
            }
            case "start" -> {
                if (!admin) {
                    send(s, "no-permission");
                    return true;
                }
                if (a.length < 2) {
                    send(s, "usage");
                    return true;
                }
                var def = em.def(a[1]);
                if (def == null) {
                    send(s, "unknown-event", "id", a[1]);
                    return true;
                }
                int minutes = def.minutes();
                if (a.length >= 3) {
                    try {
                        minutes = Math.max(1, Integer.parseInt(a[2]));
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }
                if (!em.start(def, minutes)) {
                    send(s, "already-active");
                }
            }
            case "stop" -> {
                if (!admin) {
                    send(s, "no-permission");
                    return true;
                }
                if (a.length < 2) {
                    send(s, "usage");
                    return true;
                }
                if (a[1].equalsIgnoreCase("all")) {
                    em.stopAll();
                } else if (!em.stop(a[1])) {
                    send(s, "unknown-event", "id", a[1]);
                }
            }
            case "random" -> {
                if (!admin) {
                    send(s, "no-permission");
                    return true;
                }
                em.randomStart();
            }
            case "reload" -> {
                if (!admin) {
                    send(s, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                send(s, "reloaded");
            }
            default -> send(s, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        boolean admin = s.hasPermission("nereusevents.admin");
        if (a.length == 1) {
            out.addAll(List.of("list", "gui"));
            if (admin) {
                out.addAll(List.of("start", "stop", "random", "reload"));
            }
        } else if (a.length == 2 && admin && (a[0].equalsIgnoreCase("start") || a[0].equalsIgnoreCase("stop"))) {
            plugin.events().defs().forEach(d -> out.add(d.id()));
            if (a[0].equalsIgnoreCase("stop")) {
                out.add("all");
            }
        }
        out.removeIf(x -> !x.startsWith(a[a.length - 1].toLowerCase()));
        return out;
    }
}
