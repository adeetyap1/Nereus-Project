package me.naook1.nereusfishing.command;

import java.util.ArrayList;
import java.util.List;
import me.naook1.nereusfishing.EventsHook;
import me.naook1.nereusfishing.FishManager;
import me.naook1.nereusfishing.FishStore;
import me.naook1.nereusfishing.NereusFishing;
import me.naook1.nereusfishing.util.Messages;
import me.naook1.nereusfishing.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FishCommand implements CommandExecutor, TabCompleter {

    private final NereusFishing plugin;

    public FishCommand(NereusFishing plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages m = plugin.messages();
        if (!sender.hasPermission("fishing.use")) {
            m.send(sender, "no-permission");
            return true;
        }
        if (command.getName().equalsIgnoreCase("fishsell")) {
            sell(sender);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) {
            if (sender instanceof Player p) {
                plugin.gui().open(p, true, 0);
            } else {
                m.send(sender, "player-only");
            }
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "rods" -> {
                m.send(sender, "rods-header");
                for (FishManager.Rod r : plugin.fish().rods().values()) {
                    sender.sendMessage(Text.parse(m.format("rods-line", "rod", plugin.fish().sc(r.name()),
                            "luck", FishManager.fmt(r.luck()), "speed", FishManager.fmt(r.speed()))));
                }
            }
            case "top" -> {
                List<FishStore.Entry> top = plugin.store().top(10);
                if (top.isEmpty()) {
                    m.send(sender, "top-empty");
                    return true;
                }
                m.send(sender, "top-header");
                int rank = 1;
                for (FishStore.Entry e : top) {
                    sender.sendMessage(Text.parse(m.format("top-line", "rank", String.valueOf(rank++),
                            "player", e.name(), "fish", e.fish(), "weight", FishManager.fmt(e.biggest()))));
                }
            }
            case "upgrade" -> {
                if (sender instanceof Player up) {
                    plugin.gui().upgrade(up);
                } else {
                    m.send(sender, "player-only");
                }
            }
            case "sell" -> sell(sender);
            case "give" -> {
                if (!sender.hasPermission("fishing.admin")) {
                    m.send(sender, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    m.send(sender, "usage");
                    return true;
                }
                FishManager.Rod rod = plugin.fish().rod(args[1]);
                if (rod == null) {
                    m.send(sender, "unknown-rod", "rod", args[1]);
                    return true;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                        : (sender instanceof Player p ? p : null);
                if (target == null) {
                    m.send(sender, "unknown-player");
                    return true;
                }
                var left = target.getInventory().addItem(plugin.fish().createRod(rod));
                left.values().forEach(i -> target.getWorld().dropItemNaturally(target.getLocation(), i));
                String name = plugin.fish().sc(rod.name());
                m.send(sender, "given", "rod", name, "player", target.getName());
                if (target != sender) {
                    m.send(target, "received", "rod", name);
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("fishing.admin")) {
                    m.send(sender, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                m.send(sender, "reloaded");
            }
            default -> m.send(sender, "usage");
        }
        return true;
    }

    private void sell(CommandSender sender) {
        Messages m = plugin.messages();
        if (!(sender instanceof Player p)) {
            m.send(sender, "player-only");
            return;
        }
        if (!plugin.economy().ready()) {
            m.send(p, "sell-no-economy");
            return;
        }
        ItemStack[] contents = p.getInventory().getContents();
        List<Integer> slots = new ArrayList<>();
        double total = 0;
        int count = 0;
        for (int i = 0; i < contents.length; i++) {
            double v = plugin.fish().valueOf(contents[i]);
            if (v >= 0) {
                slots.add(i);
                total += v * contents[i].getAmount();
                count += contents[i].getAmount();
            }
        }
        if (slots.isEmpty()) {
            m.send(p, "sell-none");
            return;
        }
        total = Math.round(total * EventsHook.multiplier("fishing_sell") * 100.0) / 100.0;
        if (!plugin.economy().depositExact(p, total)) {
            m.send(p, "sell-no-economy");
            return;
        }
        for (int slot : slots) {
            p.getInventory().setItem(slot, null);
        }
        m.send(p, "sell-done", "count", String.valueOf(count), "money", FishManager.fmt(total));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("fishsell")) {
            return out;
        }
        if (args.length == 1) {
            for (String s : List.of("menu", "rods", "upgrade", "top", "sell", "give", "reload")) {
                if (s.startsWith(args[0].toLowerCase()) && (sender.hasPermission("fishing.admin")
                        || !(s.equals("give") || s.equals("reload")))) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (String id : plugin.fish().rods().keySet()) {
                if (id.startsWith(args[1].toLowerCase())) {
                    out.add(id);
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(pl -> {
                if (pl.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                    out.add(pl.getName());
                }
            });
        }
        return out;
    }
}
